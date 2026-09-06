package com.example.onnx

import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, sample-accurate STFT and iSTFT processor for UVR MDX-Net models.
 *
 * Parameters for UVR MDX-Net 1 (e.g., UVR_MDXNET_1_9703):
 * - FFT Length: n_fft = 6144 (Composite 3 * 2048 Mixed-Radix)
 * - Frequency Bins: dim_f = 2048 (from n_fft // 2 + 1 = 3073 bins)
 * - Time Frames: dim_t = 256
 * - Hop Length: 1024
 * - Channels: 4 (Left Real, Left Imag, Right Real, Right Imag)
 * - Chunk Size: 1024 * (256 - 1) = 261,120 samples (~5.92s at 44.1kHz)
 *
 * Precomputes bit-reversal and trigonometric tables for zero-allocation,
 * zero-trig runtime inner loops.
 */
class MdxSpectrogramProcessor(
    val nFft: Int = 6144,
    val dimF: Int = 2048,
    val dimT: Int = 256,
    val hopLength: Int = 1024
) {
    val chunkSize: Int = hopLength * (dimT - 1) // 261,120
    val trim: Int = nFft / 2 // 3072
    val genSize: Int = chunkSize - 2 * trim // 254,976 samples per chunk

    val subN = 2048
    private val bitRev2048 = IntArray(subN)
    private val cos2048 = FloatArray(subN)
    private val sin2048 = FloatArray(subN)

    // Precomputed Hann window (periodic, window_length = 6144)
    val hannWindow = FloatArray(nFft)
    val hannWindowSq = FloatArray(nFft)

    // Twiddle factors for 3x2048 mixed-radix forward transform
    private val twiddle1Re = FloatArray(dimF)
    private val twiddle1Im = FloatArray(dimF)
    private val twiddle2Re = FloatArray(dimF)
    private val twiddle2Im = FloatArray(dimF)

    // Twiddle factors for inverse mixed-radix: exp(2pi * i * (m + p * 2048) * n1 / 6144)
    private val invTwiddle = Array(3) { Array(3) { FloatArray(subN) } }
    private val invTwiddleIm = Array(3) { Array(3) { FloatArray(subN) } }

    /**
     * Zero-allocation scratchpad to prevent creating millions of garbage-collected float arrays
     * in the innermost audio loop.
     */
    class Scratchpad(nFft: Int, subN: Int) {
        val frame = FloatArray(nFft)
        val sub0Re = FloatArray(subN)
        val sub0Im = FloatArray(subN)
        val sub1Re = FloatArray(subN)
        val sub1Im = FloatArray(subN)
        val sub2Re = FloatArray(subN)
        val sub2Im = FloatArray(subN)

        val zRe = FloatArray(subN)
        val zIm = FloatArray(subN)
        val frameWave = FloatArray(nFft)
        val fullRe = FloatArray(nFft)
        val fullIm = FloatArray(nFft)
    }

    fun createScratchpad(): Scratchpad = Scratchpad(nFft, subN)

    init {
        // Precompute bit reversal for 2048 (11 bits)
        val bits = 11
        for (i in 0 until subN) {
            var rev = 0
            var temp = i
            for (b in 0 until bits) {
                rev = (rev shl 1) or (temp and 1)
                temp = temp shr 1
            }
            bitRev2048[i] = rev
        }

        // Precompute 2048-point trig tables
        for (i in 0 until subN) {
            val angle = -2.0 * PI * i / subN
            cos2048[i] = cos(angle).toFloat()
            sin2048[i] = sin(angle).toFloat()
        }

        // Periodic Hann window: 0.5 * (1 - cos(2 * pi * i / nFft))
        for (i in 0 until nFft) {
            val w = (0.5 * (1.0 - cos(2.0 * PI * i / nFft))).toFloat()
            hannWindow[i] = w
            hannWindowSq[i] = w * w
        }

        // Forward twiddles: exp(-2pi * i * k / 6144) and exp(-4pi * i * k / 6144)
        for (k in 0 until dimF) {
            val a1 = -2.0 * PI * k / nFft
            twiddle1Re[k] = cos(a1).toFloat()
            twiddle1Im[k] = sin(a1).toFloat()

            val a2 = -4.0 * PI * k / nFft
            twiddle2Re[k] = cos(a2).toFloat()
            twiddle2Im[k] = sin(a2).toFloat()
        }

        // Inverse twiddles: exp(2pi * i * (m + p * 2048) * n1 / 6144)
        for (n1 in 0 until 3) {
            for (p in 0 until 3) {
                for (m in 0 until subN) {
                    val k = m + p * subN
                    val angle = 2.0 * PI * k * n1 / nFft
                    invTwiddle[n1][p][m] = cos(angle).toFloat()
                    invTwiddleIm[n1][p][m] = sin(angle).toFloat()
                }
            }
        }
    }

    /**
     * Highly optimized in-place Radix-2 Cooley-Tukey forward FFT for 2048 points.
     */
    private fun fft2048(re: FloatArray, im: FloatArray) {
        // Bit-reversal permutation
        for (i in 0 until subN) {
            val j = bitRev2048[i]
            if (j > i) {
                val tempRe = re[i]
                re[i] = re[j]
                re[j] = tempRe

                val tempIm = im[i]
                im[i] = im[j]
                im[j] = tempIm
            }
        }

        // Cooley-Tukey butterflies
        var len = 2
        while (len <= subN) {
            val halfLen = len shr 1
            val step = subN / len
            var i = 0
            while (i < subN) {
                var k = 0
                for (j in 0 until halfLen) {
                    val twRe = cos2048[k]
                    val twIm = sin2048[k]

                    val uRe = re[i + j]
                    val uIm = im[i + j]
                    val vRe = re[i + j + halfLen] * twRe - im[i + j + halfLen] * twIm
                    val vIm = re[i + j + halfLen] * twIm + im[i + j + halfLen] * twRe

                    re[i + j] = uRe + vRe
                    im[i + j] = uIm + vIm
                    re[i + j + halfLen] = uRe - vRe
                    im[i + j + halfLen] = uIm - vIm

                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Highly optimized in-place Radix-2 Cooley-Tukey inverse FFT for 2048 points.
     */
    private fun ifft2048(re: FloatArray, im: FloatArray) {
        // Bit-reversal permutation
        for (i in 0 until subN) {
            val j = bitRev2048[i]
            if (j > i) {
                val tempRe = re[i]
                re[i] = re[j]
                re[j] = tempRe

                val tempIm = im[i]
                im[i] = im[j]
                im[j] = tempIm
            }
        }

        // Cooley-Tukey butterflies with negated imaginary twiddles
        var len = 2
        while (len <= subN) {
            val halfLen = len shr 1
            val step = subN / len
            var i = 0
            while (i < subN) {
                var k = 0
                for (j in 0 until halfLen) {
                    val twRe = cos2048[k]
                    val twIm = -sin2048[k]

                    val uRe = re[i + j]
                    val uIm = im[i + j]
                    val vRe = re[i + j + halfLen] * twRe - im[i + j + halfLen] * twIm
                    val vIm = re[i + j + halfLen] * twIm + im[i + j + halfLen] * twRe

                    re[i + j] = uRe + vRe
                    im[i + j] = uIm + vIm
                    re[i + j + halfLen] = uRe - vRe
                    im[i + j + halfLen] = uIm - vIm

                    k += step
                }
                i += len
            }
            len = len shl 1
        }

        val invN = 1.0f / subN
        for (i in 0 until subN) {
            re[i] *= invN
            im[i] *= invN
        }
    }

    /**
     * Computes forward STFT on a single channel waveform using a reusable scratchpad
     * to avoid dynamic memory allocation.
     */
    fun computeChannelStft(
        signal: FloatArray,
        specRe: Array<FloatArray>, // [dimF][dimT]
        specIm: Array<FloatArray>,
        scratch: Scratchpad = createScratchpad()
    ) {
        val frame = scratch.frame
        val sub0Re = scratch.sub0Re
        val sub0Im = scratch.sub0Im
        val sub1Re = scratch.sub1Re
        val sub1Im = scratch.sub1Im
        val sub2Re = scratch.sub2Re
        val sub2Im = scratch.sub2Im

        for (t in 0 until dimT) {
            val start = t * hopLength
            val remaining = signal.size - start

            // Windowing
            for (i in 0 until nFft) {
                val sample = if (i < remaining && start + i >= 0) signal[start + i] else 0.0f
                frame[i] = sample * hannWindow[i]
            }

            // Decompose into 3 interleaved sub-sequences of length 2048
            for (n2 in 0 until subN) {
                val base = 3 * n2
                sub0Re[n2] = frame[base]
                sub0Im[n2] = 0f

                sub1Re[n2] = frame[base + 1]
                sub1Im[n2] = 0f

                sub2Re[n2] = frame[base + 2]
                sub2Im[n2] = 0f
            }

            fft2048(sub0Re, sub0Im)
            fft2048(sub1Re, sub1Im)
            fft2048(sub2Re, sub2Im)

            // Recombine via twiddle factors for bins 0 until dimF (2048)
            for (k in 0 until dimF) {
                val s0R = sub0Re[k]
                val s0I = sub0Im[k]

                val c1 = twiddle1Re[k]
                val s1 = twiddle1Im[k]
                val t1R = sub1Re[k] * c1 - sub1Im[k] * s1
                val t1I = sub1Re[k] * s1 + sub1Im[k] * c1

                val c2 = twiddle2Re[k]
                val s2 = twiddle2Im[k]
                val t2R = sub2Re[k] * c2 - sub2Im[k] * s2
                val t2I = sub2Re[k] * s2 + sub2Im[k] * c2

                specRe[k][t] = s0R + t1R + t2R
                specIm[k][t] = s0I + t1I + t2I
            }
        }
    }

    /**
     * Packs Left and Right STFT spectrograms into a continuous FloatBuffer matching
     * ONNX tensor shape [1, 4, dimF, dimT].
     */
    fun packToFloatBuffer(
        leftRe: Array<FloatArray>,
        leftIm: Array<FloatArray>,
        rightRe: Array<FloatArray>,
        rightIm: Array<FloatArray>,
        buffer: FloatBuffer
    ) {
        buffer.clear()
        // Channel 0: Left Real
        for (f in 0 until dimF) {
            buffer.put(leftRe[f], 0, dimT)
        }
        // Channel 1: Left Imag
        for (f in 0 until dimF) {
            buffer.put(leftIm[f], 0, dimT)
        }
        // Channel 2: Right Real
        for (f in 0 until dimF) {
            buffer.put(rightRe[f], 0, dimT)
        }
        // Channel 3: Right Imag
        for (f in 0 until dimF) {
            buffer.put(rightIm[f], 0, dimT)
        }
        buffer.flip()
    }

    /**
     * Computes inverse STFT for a single channel from output spectrogram tensors
     * using the fast Mixed-Radix 3x2048 inverse transform and overlap-add, with zero allocation.
     */
    fun computeChannelIstft(
        specRe: Array<FloatArray>, // [dimF][dimT]
        specIm: Array<FloatArray>,
        outWaveform: FloatArray, // size at least (dimT - 1) * hopLength + nFft
        weightAcc: FloatArray,
        compensation: Float = 1.0f,
        scratch: Scratchpad = createScratchpad()
    ) {
        val zRe = scratch.zRe
        val zIm = scratch.zIm
        val frameWave = scratch.frameWave
        val fullRe = scratch.fullRe
        val fullIm = scratch.fullIm

        for (t in 0 until dimT) {
            // Fill spectrum for this frame
            java.util.Arrays.fill(fullRe, 0f)
            java.util.Arrays.fill(fullIm, 0f)

            fullRe[0] = specRe[0][t] * compensation
            fullIm[0] = 0f

            for (k in 1 until dimF) {
                val r = specRe[k][t] * compensation
                val i = specIm[k][t] * compensation
                fullRe[k] = r
                fullIm[k] = i
                fullRe[nFft - k] = r
                fullIm[nFft - k] = -i
            }

            // Perform 3x 2048-point IFFTs
            for (n1 in 0 until 3) {
                // Form Z_{n1}[m] for m in 0 until 2048
                for (m in 0 until subN) {
                    var accR = 0f
                    var accI = 0f

                    for (p in 0 until 3) {
                        val k = m + p * subN
                        val xR = fullRe[k]
                        val xI = fullIm[k]

                        val twR = invTwiddle[n1][p][m]
                        val twI = invTwiddleIm[n1][p][m]

                        accR += xR * twR - xI * twI
                        accI += xR * twI + xI * twR
                    }
                    zRe[m] = accR
                    zIm[m] = accI
                }

                // 2048-point IFFT
                ifft2048(zRe, zIm)

                // Place into frameWave: frameWave[3 * n2 + n1] = 1/3 * zRe[n2]
                val oneThird = 1.0f / 3.0f
                for (n2 in 0 until subN) {
                    frameWave[3 * n2 + n1] = zRe[n2] * oneThird
                }
            }

            // Overlap-add with synthesis Hann window
            val start = t * hopLength
            for (i in 0 until nFft) {
                val idx = start + i
                if (idx < outWaveform.size) {
                    val w = hannWindow[i]
                    outWaveform[idx] += frameWave[i] * w
                    weightAcc[idx] += w * w
                }
            }
        }
    }
}
