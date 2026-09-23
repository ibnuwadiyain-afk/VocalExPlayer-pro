package com.example.onnx

import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Genuine, high-performance STFT and iSTFT processor for Deezer Spleeter 2-Stem models.
 *
 * Spleeter 2-Stem audio specifications:
 * - Sampling rate: 44,100 Hz, Stereo (2 channels)
 * - FFT Length: n_fft = 4096 (Cooley-Tukey Radix-2)
 * - Frequency Bins modeled by neural U-Net: dim_f = 1024 (0 to 11,025 Hz)
 * - Full Nyquist Bins: full_bins = 2049 (from 4096 / 2 + 1)
 * - Time Frames per forward chunk: dim_t = 512
 * - Hop Length: hop_length = 1024
 * - Window: Periodic Hann window of length 4096
 * - Neural input shape: [2, num_splits, 512, 1024]
 * - Neural output shape: [2, num_splits, 512, 1024]
 *
 * Precomputes 4096-point Radix-2 bit-reversal and trigonometric tables for zero-allocation,
 * zero-trig streaming inner loops.
 */
class SpleeterSpectrogramProcessor(
    val nFft: Int = 4096,
    val dimF: Int = 1024,
    val dimT: Int = 512,
    val hopLength: Int = 1024
) {
    val fullBins: Int = nFft / 2 + 1 // 2049
    val chunkSize: Int = hopLength * (dimT - 1) + nFft // 527,360 samples (~11.95s at 44.1kHz)
    val trim: Int = nFft // 4096 samples overlap margin
    val genSize: Int = (dimT - 4) * hopLength // 520,192 samples per step

    private val bitRev4096 = IntArray(nFft)
    private val cos4096 = FloatArray(nFft)
    private val sin4096 = FloatArray(nFft)

    // Periodic Hann window: 0.5 * (1 - cos(2 * pi * i / nFft))
    val hannWindow = FloatArray(nFft)
    val hannWindowSq = FloatArray(nFft)

    init {
        require(nFft == 4096) { "SpleeterSpectrogramProcessor requires nFft = 4096" }

        // Precompute bit reversal for 4096 (12 bits)
        val bits = 12
        for (i in 0 until nFft) {
            var rev = 0
            var temp = i
            for (b in 0 until bits) {
                rev = (rev shl 1) or (temp and 1)
                temp = temp shr 1
            }
            bitRev4096[i] = rev
        }

        // Precompute 4096-point trig tables: angle = -2 * pi * i / 4096
        for (i in 0 until nFft) {
            val angle = -2.0 * PI * i / nFft.toDouble()
            cos4096[i] = cos(angle).toFloat()
            sin4096[i] = sin(angle).toFloat()
        }

        // Periodic Hann window
        for (i in 0 until nFft) {
            val w = (0.5 * (1.0 - cos(2.0 * PI * i / nFft.toDouble()))).toFloat()
            hannWindow[i] = w
            hannWindowSq[i] = w * w
        }
    }

    /**
     * Reusable scratchpad avoiding allocations in audio rendering loops.
     */
    class Scratchpad(val nFft: Int, val dimT: Int, val fullBins: Int, val dimF: Int) {
        val frameReal = FloatArray(nFft)
        val frameImag = FloatArray(nFft)
        val vocalMask = FloatArray(dimF)

        // STFT complex storage for 2 channels [channel][frame][bin]
        val stftReal = Array(2) { Array(dimT) { FloatArray(fullBins) } }
        val stftImag = Array(2) { Array(dimT) { FloatArray(fullBins) } }

        // Reconstructed time domain waveforms [channel][samples]
        val vocalWaveform = Array(2) { FloatArray(dimT * 1024 + nFft) }
        val instWaveform = Array(2) { FloatArray(dimT * 1024 + nFft) }
        val windowWeight = FloatArray(dimT * 1024 + nFft)

        fun clearWaveforms(length: Int) {
            for (ch in 0 until 2) {
                vocalWaveform[ch].fill(0f, 0, length)
                instWaveform[ch].fill(0f, 0, length)
            }
            windowWeight.fill(0f, 0, length)
        }
    }

    fun createScratchpad(): Scratchpad = Scratchpad(nFft, dimT, fullBins, dimF)

    /**
     * In-place Radix-2 Cooley-Tukey FFT of length 4096.
     */
    fun fft4096(real: FloatArray, imag: FloatArray) {
        // Bit reversal
        for (i in 0 until nFft) {
            val j = bitRev4096[i]
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        // 12 butterfly stages
        var len = 2
        while (len <= nFft) {
            val halfLen = len shr 1
            val step = nFft / len
            var i = 0
            while (i < nFft) {
                var k = 0
                var tableIdx = 0
                while (k < halfLen) {
                    val idxA = i + k
                    val idxB = idxA + halfLen
                    val uR = real[idxA]
                    val uI = imag[idxA]
                    val vR = real[idxB]
                    val vI = imag[idxB]

                    val c = cos4096[tableIdx]
                    val s = sin4096[tableIdx]

                    val tR = vR * c - vI * s
                    val tI = vR * s + vI * c

                    real[idxA] = uR + tR
                    real[idxB] = uR - tR
                    imag[idxA] = uI + tI
                    imag[idxB] = uI - tI

                    k++
                    tableIdx += step
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * In-place Radix-2 inverse FFT of length 4096.
     */
    fun ifft4096(real: FloatArray, imag: FloatArray) {
        for (i in 0 until nFft) {
            imag[i] = -imag[i]
        }
        fft4096(real, imag)
        val norm = 1.0f / nFft.toFloat()
        for (i in 0 until nFft) {
            real[i] *= norm
            imag[i] = -imag[i] * norm
        }
    }

    /**
     * Computes forward STFT on stereo PCM audio and populates ONNX input FloatBuffer.
     *
     * ONNX input tensor layout: [2, 1, 512, 1024]
     * Channel 0: Left magnitude (bins 0..1023)
     * Channel 1: Right magnitude (bins 0..1023)
     */
    fun computeSpleeterInput(
        pcmLeft: FloatArray,
        pcmRight: FloatArray,
        pcmOffset: Int,
        pcmLength: Int,
        scratch: Scratchpad,
        onnxInputBuffer: FloatBuffer
    ) {
        onnxInputBuffer.clear()

        for (ch in 0 until 2) {
            val pcm = if (ch == 0) pcmLeft else pcmRight
            for (t in 0 until dimT) {
                val frameStart = pcmOffset + t * hopLength

                // Window and copy into frame
                for (i in 0 until nFft) {
                    val pcmIdx = frameStart + i
                    val sample = if (pcmIdx in 0 until pcmLength) pcm[pcmIdx] else 0f
                    scratch.frameReal[i] = sample * hannWindow[i]
                    scratch.frameImag[i] = 0f
                }

                fft4096(scratch.frameReal, scratch.frameImag)

                // Store full 2049 bins for phase-accurate synthesis
                val destReal = scratch.stftReal[ch][t]
                val destImag = scratch.stftImag[ch][t]
                System.arraycopy(scratch.frameReal, 0, destReal, 0, fullBins)
                System.arraycopy(scratch.frameImag, 0, destImag, 0, fullBins)

                // Write first 1024 magnitude bins to ONNX input buffer
                for (f in 0 until dimF) {
                    val r = scratch.frameReal[f]
                    val im = scratch.frameImag[f]
                    val mag = sqrt(r * r + im * im)
                    onnxInputBuffer.put(mag)
                }
            }
        }
        onnxInputBuffer.flip()
    }

    /**
     * Reconstructs separated vocal and instrumental waveforms using Spleeter's soft ratio mask
     * and inverse STFT with Hann overlap-add.
     */
    fun synthesizeSeparatedStems(
        onnxOutputBuffer: FloatBuffer,
        scratch: Scratchpad,
        totalSamplesNeeded: Int
    ) {
        onnxOutputBuffer.position(0)
        scratch.clearWaveforms(totalSamplesNeeded)

        val vocalWaveL = scratch.vocalWaveform[0]
        val vocalWaveR = scratch.vocalWaveform[1]
        val instWaveL = scratch.instWaveform[0]
        val instWaveR = scratch.instWaveform[1]
        val windowWeight = scratch.windowWeight

        for (ch in 0 until 2) {
            val vocalWave = if (ch == 0) vocalWaveL else vocalWaveR
            val instWave = if (ch == 0) instWaveL else instWaveR

            for (t in 0 until dimT) {
                val frameStart = t * hopLength
                val origReal = scratch.stftReal[ch][t]
                val origImag = scratch.stftImag[ch][t]

                // Reconstruct Vocal Spectrum
                for (f in 0 until dimF) {
                    val r = origReal[f]
                    val im = origImag[f]
                    val origMag = sqrt(r * r + im * im)
                    val vocalMagPred = onnxOutputBuffer.get()

                    // Deezer Spleeter Soft ratio mask
                    val maskVocal = (vocalMagPred / (origMag + 1e-7f)).coerceIn(0f, 1f)
                    scratch.vocalMask[f] = maskVocal

                    scratch.frameReal[f] = r * maskVocal
                    scratch.frameImag[f] = im * maskVocal
                }

                // High frequencies (>11kHz) above Spleeter's 1024 bins are zeroed for vocals
                for (f in dimF until fullBins) {
                    scratch.frameReal[f] = 0f
                    scratch.frameImag[f] = 0f
                }

                // Hermite conjugate symmetry for real inverse FFT
                for (f in 1 until fullBins - 1) {
                    scratch.frameReal[nFft - f] = scratch.frameReal[f]
                    scratch.frameImag[nFft - f] = -scratch.frameImag[f]
                }

                ifft4096(scratch.frameReal, scratch.frameImag)

                // Overlap-add vocal waveform
                for (i in 0 until nFft) {
                    val outIdx = frameStart + i
                    if (outIdx < totalSamplesNeeded) {
                        vocalWave[outIdx] += scratch.frameReal[i] * hannWindow[i]
                        if (ch == 0) {
                            windowWeight[outIdx] += hannWindowSq[i]
                        }
                    }
                }

                // Reconstruct Instrumental Spectrum: Accompaniment = Original - Vocal
                // High frequencies (>11kHz) are fully preserved in the accompaniment track
                for (f in 0 until dimF) {
                    val r = origReal[f]
                    val im = origImag[f]
                    val maskVocal = scratch.vocalMask[f]
                    val maskInst = 1.0f - maskVocal

                    scratch.frameReal[f] = r * maskInst
                    scratch.frameImag[f] = im * maskInst
                }

                for (f in dimF until fullBins) {
                    scratch.frameReal[f] = origReal[f]
                    scratch.frameImag[f] = origImag[f]
                }

                for (f in 1 until fullBins - 1) {
                    scratch.frameReal[nFft - f] = scratch.frameReal[f]
                    scratch.frameImag[nFft - f] = -scratch.frameImag[f]
                }

                ifft4096(scratch.frameReal, scratch.frameImag)

                // Overlap-add instrumental waveform
                for (i in 0 until nFft) {
                    val outIdx = frameStart + i
                    if (outIdx < totalSamplesNeeded) {
                        instWave[outIdx] += scratch.frameReal[i] * hannWindow[i]
                    }
                }
            }
        }

        // Normalize by synthesis window overlap weight
        for (i in 0 until totalSamplesNeeded) {
            val w = windowWeight[i]
            if (w > 1e-4f) {
                val invW = 1.0f / w
                vocalWaveL[i] *= invW
                vocalWaveR[i] *= invW
                instWaveL[i] *= invW
                instWaveR[i] *= invW
            }
        }
    }
}
