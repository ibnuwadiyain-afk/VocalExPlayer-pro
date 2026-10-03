package com.example.whisper

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.audio.DecodedAudio
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class WhisperAudioPreprocessor(private val context: Context) {

    companion object {
        private const val TAG = "WhisperPreprocessor"
        const val SAMPLE_RATE = 16000
        const val N_FFT = 400
        const val HOP_LENGTH = 160
        const val N_MELS = 80
        const val CHUNK_LENGTH_SEC = 30
        const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_LENGTH_SEC // 480,000 samples
        const val CHUNK_FRAMES = 3000 // 480,000 / 160 = 3000 frames
    }

    // Precomputed Hann window of size N_FFT (400)
    private val hannWindow: FloatArray = FloatArray(N_FFT) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / N_FFT))).toFloat()
    }

    // Precomputed Slaney Mel filterbank matrix: [N_MELS (80), N_FFT/2 + 1 (201)]
    private val melFilterBank: Array<FloatArray> = createMelFilterBank(N_MELS, N_FFT, SAMPLE_RATE)

    // Precomputed DFT Cosine and Sine Basis Matrices: [numFreqs (201), N_FFT (400)]
    // Window multiplication is folded into the basis matrices for zero-overhead windowing!
    private val cosBasis: Array<FloatArray> = Array(N_FFT / 2 + 1) { k ->
        FloatArray(N_FFT) { n ->
            (cos(2.0 * PI * k * n / N_FFT) * hannWindow[n]).toFloat()
        }
    }

    private val sinBasis: Array<FloatArray> = Array(N_FFT / 2 + 1) { k ->
        FloatArray(N_FFT) { n ->
            (-sin(2.0 * PI * k * n / N_FFT) * hannWindow[n]).toFloat()
        }
    }

    data class AudioChunk(
        val windowIndex: Int,
        val startSample: Long,
        val sampleCount: Int,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val melSpectrogram: Array<FloatArray> // [80][3000]
    )

    /**
     * Loads audio from a 16-bit PCM WAV file and resamples to 16,000 Hz mono FloatArray.
     */
    fun loadAndResampleWav(wavFile: File): FloatArray {
        if (!wavFile.exists() || wavFile.length() < 44) {
            throw IllegalArgumentException("Audio file ${wavFile.name} is missing or invalid.")
        }

        FileInputStream(wavFile).use { fis ->
            val header = ByteArray(44)
            fis.read(header)

            val channels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            val pcmData = fis.readBytes()
            val shortBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val totalSamples = shortBuffer.remaining()

            val rawMono: FloatArray
            if (channels == 2) {
                val frames = totalSamples / 2
                rawMono = FloatArray(frames)
                for (i in 0 until frames) {
                    val left = shortBuffer.get() / 32768.0f
                    val right = shortBuffer.get() / 32768.0f
                    rawMono[i] = (left + right) * 0.5f
                }
            } else {
                rawMono = FloatArray(totalSamples)
                for (i in 0 until totalSamples) {
                    rawMono[i] = shortBuffer.get() / 32768.0f
                }
            }

            return if (sampleRate == SAMPLE_RATE) {
                rawMono
            } else {
                resampleLinear(rawMono, sampleRate, SAMPLE_RATE)
            }
        }
    }

    /**
     * Splits 16kHz mono audio into 30-second Whisper chunks and computes [80, 3000] Mel spectrogram for each.
     */
    fun prepareWhisperChunks(monoSamples: FloatArray): List<AudioChunk> {
        val totalSamples = monoSamples.size
        if (totalSamples == 0) return emptyList()

        val chunks = mutableListOf<AudioChunk>()
        var offset = 0
        var windowIdx = 0

        while (offset < totalSamples) {
            val remaining = totalSamples - offset
            val count = remaining.coerceAtMost(CHUNK_SAMPLES)
            val startTimeMs = (offset.toLong() * 1000L) / SAMPLE_RATE
            val endTimeMs = ((offset + count).toLong() * 1000L) / SAMPLE_RATE

            // Extract 30-sec buffer padded with silence if shorter than CHUNK_SAMPLES
            val chunkBuffer = FloatArray(CHUNK_SAMPLES)
            System.arraycopy(monoSamples, offset, chunkBuffer, 0, count)

            val melSpec = computeMelSpectrogram(chunkBuffer)

            chunks.add(
                AudioChunk(
                    windowIndex = windowIdx++,
                    startSample = offset.toLong(),
                    sampleCount = count,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    melSpectrogram = melSpec
                )
            )

            offset += CHUNK_SAMPLES
        }

        return chunks
    }

    /**
     * Computes the 80x3000 log-Mel filterbank spectrogram required by Whisper.
     */
    fun computeMelSpectrogram(samples30s: FloatArray): Array<FloatArray> {
        // [80][3000]
        val melSpec = Array(N_MELS) { FloatArray(CHUNK_FRAMES) }
        val numFreqs = N_FFT / 2 + 1
        val powerSpectrum = FloatArray(numFreqs)
        val windowBuffer = FloatArray(N_FFT)

        for (frame in 0 until CHUNK_FRAMES) {
            val start = frame * HOP_LENGTH

            // Check if frame has any audio (skip silence optimization)
            var hasEnergy = false
            for (i in 0 until N_FFT) {
                val sampleIdx = start + i
                val s = if (sampleIdx < samples30s.size) samples30s[sampleIdx] else 0f
                windowBuffer[i] = s
                if (s != 0f) hasEnergy = true
            }

            if (!hasEnergy) {
                for (m in 0 until N_MELS) {
                    melSpec[m][frame] = -5.0f // log10(1e-5)
                }
                continue
            }

            // Exact 400-point STFT via precomputed Hann-weighted basis
            for (k in 0 until numFreqs) {
                val cRow = cosBasis[k]
                val sRow = sinBasis[k]
                var realSum = 0f
                var imagSum = 0f
                for (n in 0 until N_FFT) {
                    val s = windowBuffer[n]
                    realSum += s * cRow[n]
                    imagSum += s * sRow[n]
                }
                powerSpectrum[k] = realSum * realSum + imagSum * imagSum
            }

            // Apply 80 Mel filterbanks
            for (m in 0 until N_MELS) {
                var melEnergy = 0f
                val filter = melFilterBank[m]
                for (k in 0 until numFreqs) {
                    val w = filter[k]
                    if (w > 0f) {
                        melEnergy += powerSpectrum[k] * w
                    }
                }
                // Log compression
                val logMel = log10(melEnergy.coerceAtLeast(1e-5f))
                melSpec[m][frame] = logMel
            }
        }

        // Whisper standard normalization: clamp to max - 8.0, and map (x + 4.0) / 4.0
        var maxVal = -1e9f
        for (m in 0 until N_MELS) {
            for (f in 0 until CHUNK_FRAMES) {
                if (melSpec[m][f] > maxVal) maxVal = melSpec[m][f]
            }
        }

        val clampMin = maxVal - 8.0f
        for (m in 0 until N_MELS) {
            for (f in 0 until CHUNK_FRAMES) {
                val clamped = melSpec[m][f].coerceAtLeast(clampMin)
                melSpec[m][f] = (clamped + 4.0f) / 4.0f
            }
        }

        return melSpec
    }

    private fun resampleLinear(input: FloatArray, inRate: Int, outRate: Int): FloatArray {
        if (inRate == outRate) return input
        val ratio = inRate.toDouble() / outRate.toDouble()
        val outLen = (input.size / ratio).toInt()
        val output = FloatArray(outLen)

        for (i in 0 until outLen) {
            val pos = i * ratio
            val index = pos.toInt()
            val frac = (pos - index).toFloat()
            val s0 = if (index < input.size) input[index] else 0f
            val s1 = if (index + 1 < input.size) input[index + 1] else s0
            output[i] = s0 + frac * (s1 - s0)
        }
        return output
    }

    private fun createMelFilterBank(nMels: Int, nFft: Int, sampleRate: Int): Array<FloatArray> {
        val numFreqs = nFft / 2 + 1
        val bank = Array(nMels) { FloatArray(numFreqs) }

        fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

        val minMel = hzToMel(0.0)
        val maxMel = hzToMel(sampleRate / 2.0)
        val melStep = (maxMel - minMel) / (nMels + 1)

        val melPoints = DoubleArray(nMels + 2) { i -> minMel + i * melStep }
        val hzPoints = DoubleArray(nMels + 2) { i -> melToHz(melPoints[i]) }
        val binPoints = IntArray(nMels + 2) { i ->
            ((hzPoints[i] / (sampleRate / 2.0)) * (numFreqs - 1)).toInt().coerceIn(0, numFreqs - 1)
        }

        for (m in 0 until nMels) {
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]

            for (k in left until center) {
                if (center != left) {
                    bank[m][k] = ((k - left).toFloat() / (center - left).toFloat())
                }
            }
            for (k in center until right) {
                if (right != center) {
                    bank[m][k] = ((right - k).toFloat() / (right - center).toFloat())
                }
            }
        }

        return bank
    }
}
