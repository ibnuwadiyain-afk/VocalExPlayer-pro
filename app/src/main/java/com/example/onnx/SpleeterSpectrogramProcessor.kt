package com.example.onnx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SpleeterSpectrogramProcessor(
    val nFft: Int = 4096,
    val hopLength: Int = 1024,
    val dimF: Int = 1024,
    val dimT: Int = 512
) {
    val numBins: Int = nFft / 2 + 1 // 2049

    private val hannWindow = FloatArray(nFft) { i ->
        // Periodic Hann window: 0.5 * (1 - cos(2 * pi * i / nFft))
        (0.5 * (1.0 - cos(2.0 * PI * i / nFft))).toFloat()
    }

    private val bitRev = IntArray(nFft)
    private val cosTable = FloatArray(nFft)
    private val sinTable = FloatArray(nFft)

    init {
        val bits = Integer.numberOfTrailingZeros(nFft)
        for (i in 0 until nFft) {
            bitRev[i] = Integer.reverse(i) ushr (32 - bits)
            val angle = 2.0 * PI * i / nFft
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
    }

    fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = nFft
        for (i in 0 until n) {
            val j = bitRev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val step = n / len
            for (i in 0 until n step len) {
                var k = 0
                for (j in 0 until halfLen) {
                    val idx = k * step
                    val cosVal = cosTable[idx]
                    val sinVal = if (inverse) sinTable[idx] else -sinTable[idx]

                    val uR = re[i + j]
                    val uI = im[i + j]
                    val vR = re[i + j + halfLen]
                    val vI = im[i + j + halfLen]

                    val tR = vR * cosVal - vI * sinVal
                    val tI = vR * sinVal + vI * cosVal

                    re[i + j] = uR + tR
                    im[i + j] = uI + tI
                    re[i + j + halfLen] = uR - tR
                    im[i + j + halfLen] = uI - tI

                    k++
                }
            }
            len = len shl 1
        }

        if (inverse) {
            val scale = 1f / n
            for (i in 0 until n) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }

    class StftChannelResult(
        val numFrames: Int,
        val paddedFrames: Int,
        val real: FloatArray, // size: paddedFrames * numBins
        val imag: FloatArray, // size: paddedFrames * numBins
        val mag1024: FloatArray // size: paddedFrames * dimF (dimF = 1024)
    )

    fun computeStft(waveform: FloatArray): StftChannelResult {
        val numSamples = waveform.size
        val numFrames = if (numSamples < nFft) {
            1
        } else {
            (numSamples - nFft) / hopLength + 1
        }

        val remainder = numFrames % dimT
        val paddingFrames = if (remainder == 0) 0 else dimT - remainder
        val paddedFrames = numFrames + paddingFrames

        val real = FloatArray(paddedFrames * numBins)
        val imag = FloatArray(paddedFrames * numBins)
        val mag1024 = FloatArray(paddedFrames * dimF)

        val reScratch = FloatArray(nFft)
        val imScratch = FloatArray(nFft)

        for (frame in 0 until numFrames) {
            val startSample = frame * hopLength
            for (i in 0 until nFft) {
                val sampleIdx = startSample + i
                val s = if (sampleIdx < numSamples) waveform[sampleIdx] else 0f
                reScratch[i] = s * hannWindow[i]
                imScratch[i] = 0f
            }

            fft(reScratch, imScratch, inverse = false)

            val frameOffset = frame * numBins
            val magOffset = frame * dimF
            for (k in 0 until numBins) {
                val r = reScratch[k]
                val im = imScratch[k]
                real[frameOffset + k] = r
                imag[frameOffset + k] = im
                if (k < dimF) {
                    mag1024[magOffset + k] = sqrt(r * r + im * im)
                }
            }
        }

        // Padded frames remain 0
        return StftChannelResult(
            numFrames = numFrames,
            paddedFrames = paddedFrames,
            real = real,
            imag = imag,
            mag1024 = mag1024
        )
    }

    fun computeIstft(
        real: FloatArray,
        imag: FloatArray,
        numFrames: Int,
        targetLength: Int
    ): FloatArray {
        val outputWave = FloatArray(targetLength + nFft + hopLength)
        val windowWeights = FloatArray(outputWave.size)

        val reScratch = FloatArray(nFft)
        val imScratch = FloatArray(nFft)

        for (frame in 0 until numFrames) {
            val frameOffset = frame * numBins

            // DC bin
            reScratch[0] = real[frameOffset]
            imScratch[0] = 0f

            // Positive frequencies
            for (k in 1 until numBins - 1) {
                val r = real[frameOffset + k]
                val im = imag[frameOffset + k]
                reScratch[k] = r
                imScratch[k] = im
                // Hermitian symmetry for negative frequencies
                reScratch[nFft - k] = r
                imScratch[nFft - k] = -im
            }

            // Nyquist bin
            reScratch[numBins - 1] = real[frameOffset + numBins - 1]
            imScratch[numBins - 1] = 0f

            fft(reScratch, imScratch, inverse = true)

            val startSample = frame * hopLength
            for (i in 0 until nFft) {
                val idx = startSample + i
                if (idx < outputWave.size) {
                    outputWave[idx] += reScratch[i] * hannWindow[i]
                    windowWeights[idx] += hannWindow[i] * hannWindow[i]
                }
            }
        }

        val result = FloatArray(targetLength)
        for (i in 0 until targetLength) {
            val weight = windowWeights[i]
            result[i] = if (weight > 1e-4f) {
                outputWave[i] / weight
            } else {
                outputWave[i]
            }
        }

        return result
    }
}
