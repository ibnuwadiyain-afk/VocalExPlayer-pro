package com.example.onnx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fast, low-memory STFT (Short-Time Fourier Transform) & iSTFT processor
 * tailored for UVR MDX-Net ONNX models.
 */
class MdxSpectrogramProcessor(
    val nFft: Int = 2048,
    val hopLength: Int = 1024
) {
    val window: FloatArray = FloatArray(nFft) { i ->
        // Periodic Hann window
        (0.5f * (1.0f - cos(2.0f * PI.toFloat() * i / nFft)))
    }

    private val windowSum = FloatArray(nFft)
    private val cosTable = FloatArray(nFft)
    private val sinTable = FloatArray(nFft)

    init {
        for (i in 0 until nFft) {
            val angle = -2.0f * PI.toFloat() * i / nFft
            cosTable[i] = cos(angle)
            sinTable[i] = sin(angle)
        }
    }

    class Scratchpad(val nFft: Int) {
        val real = FloatArray(nFft)
        val imag = FloatArray(nFft)
    }

    /**
     * In-place Radix-2 Cooley-Tukey FFT
     */
    fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val step = n / len
            for (i in 0 until n step len) {
                var k = 0
                for (m in 0 until halfLen) {
                    val cosVal = cosTable[k]
                    val sinVal = sinTable[k]
                    val tr = real[i + m + halfLen] * cosVal - imag[i + m + halfLen] * sinVal
                    val ti = real[i + m + halfLen] * sinVal + imag[i + m + halfLen] * cosVal

                    real[i + m + halfLen] = real[i + m] - tr
                    imag[i + m + halfLen] = imag[i + m] - ti
                    real[i + m] += tr
                    imag[i + m] += ti
                    k += step
                }
            }
            len = len shl 1
        }
    }

    /**
     * In-place iFFT
     */
    fun ifft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        for (i in 0 until n) imag[i] = -imag[i]
        fft(real, imag)
        val scale = 1.0f / n
        for (i in 0 until n) {
            real[i] *= scale
            imag[i] = -imag[i] * scale
        }
    }
}
