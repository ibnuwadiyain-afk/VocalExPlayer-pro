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

    // Precomputed non-zero bounds for sparse Mel filterbank matrix multiplication
    private val melStartBin: IntArray = IntArray(N_MELS) { m ->
        var s = 0
        while (s < (N_FFT / 2 + 1) && melFilterBank[m][s] == 0f) s++
        s
    }

    private val melEndBin: IntArray = IntArray(N_MELS) { m ->
        var e = N_FFT / 2
        while (e >= 0 && melFilterBank[m][e] == 0f) e--
        e.coerceAtLeast(melStartBin[m])
    }

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

            val monoAt16k = if (sampleRate == SAMPLE_RATE) {
                rawMono
            } else {
                resampleLinear(rawMono, sampleRate, SAMPLE_RATE)
            }

            // DC offset removal & gentle peak normalization to provide clear vocal formants to Whisper
            if (monoAt16k.isNotEmpty()) {
                var sum = 0.0
                for (s in monoAt16k) sum += s
                val mean = (sum / monoAt16k.size).toFloat()

                var maxPeak = 0f
                for (i in monoAt16k.indices) {
                    val s = monoAt16k[i] - mean
                    monoAt16k[i] = s
                    val abs = kotlin.math.abs(s)
                    if (abs > maxPeak) maxPeak = abs
                }

                // If audio is quiet (peak < 0.70) but audible (peak > 0.005), apply gentle gain to 0.85
                if (maxPeak in 0.005f..0.70f) {
                    val gain = 0.85f / maxPeak
                    for (i in monoAt16k.indices) {
                        monoAt16k[i] = (monoAt16k[i] * gain).coerceIn(-1.0f, 1.0f)
                    }
                }
            }

            return monoAt16k
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
    /**
     * Computes the 80x3000 log-Mel filterbank spectrogram required by Whisper:
     * - Uses exact librosa Slaney Mel filterbank with area normalization
     * - Uses PyTorch/librosa center reflect padding (200 samples)
     * - Log10 compression with 1e-10 floor
     * - Clamps to max - 8.0 and normalizes to (clamped + 4.0) / 4.0
     */
    fun computeMelSpectrogram(samples30s: FloatArray): Array<FloatArray> {
        val melSpec = Array(N_MELS) { FloatArray(CHUNK_FRAMES) }
        val numFreqs = N_FFT / 2 + 1 // 201
        val pad = N_FFT / 2 // 200 samples reflect padding

        // Create reflect-padded 30s buffer matching PyTorch center=True
        val paddedLen = CHUNK_SAMPLES + N_FFT
        val paddedAudio = FloatArray(paddedLen)

        // Reflect padding at start
        for (i in 0 until pad) {
            val srcIdx = (pad - i).coerceIn(0, samples30s.size - 1)
            paddedAudio[i] = if (srcIdx < samples30s.size) samples30s[srcIdx] else 0f
        }

        // Copy main 30s audio
        val mainCopyCount = samples30s.size.coerceAtMost(CHUNK_SAMPLES)
        System.arraycopy(samples30s, 0, paddedAudio, pad, mainCopyCount)

        // Reflect padding at end
        for (i in 0 until pad) {
            val srcIdx = (CHUNK_SAMPLES - 2 - i).coerceIn(0, samples30s.size - 1)
            paddedAudio[pad + CHUNK_SAMPLES + i] = if (srcIdx >= 0 && srcIdx < samples30s.size) samples30s[srcIdx] else 0f
        }

        val powerSpectrum = FloatArray(numFreqs)
        val windowBuffer = FloatArray(N_FFT)

        for (frame in 0 until CHUNK_FRAMES) {
            val start = frame * HOP_LENGTH

            var frameEnergy = 0f
            for (i in 0 until N_FFT) {
                val s = paddedAudio[start + i]
                windowBuffer[i] = s
                frameEnergy += kotlin.math.abs(s)
            }

            // Quick bypass for complete silence
            if (frameEnergy < 1e-5f) {
                for (m in 0 until N_MELS) {
                    melSpec[m][frame] = -10.0f
                }
                continue
            }

            // 400-point STFT using precomputed Hann basis
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

            // Sparse Slaney Mel filterbank multiplication
            for (m in 0 until N_MELS) {
                var melEnergy = 0f
                val filter = melFilterBank[m]
                val kStart = melStartBin[m]
                val kEnd = melEndBin[m]
                for (k in kStart..kEnd) {
                    val w = filter[k]
                    if (w > 0f) {
                        melEnergy += powerSpectrum[k] * w
                    }
                }
                // Whisper log compression with 1e-10 floor (dynamic range of 10 decades)
                melSpec[m][frame] = log10(melEnergy.coerceAtLeast(1e-10f))
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

    /**
     * Computes the RMS energy of a 30s chunk to detect silence.
     */
    fun calculateChunkRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) {
            sum += (s * s)
        }
        return sqrt((sum / samples.size).toFloat())
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

    /**
     * Generates the exact librosa Slaney Mel filterbank matrix: [nMels, nFft/2 + 1].
     * Matches Python: librosa.filters.mel(sr=16000, n_fft=400, n_mels=80, norm='slaney', mel_scale='slaney')
     */
    private fun createMelFilterBank(nMels: Int, nFft: Int, sampleRate: Int): Array<FloatArray> {
        val numFreqs = nFft / 2 + 1
        val bank = Array(nMels) { FloatArray(numFreqs) }

        val minLogHz = 1000.0
        val minLogMel = 15.0 // (1000.0 - 0.0) / (200.0 / 3.0)
        val logStep = ln(6.4) / 27.0 // ln(6400.0 / 1000.0) / 27.0

        fun hzToMel(hz: Double): Double {
            return if (hz < minLogHz) {
                hz / (200.0 / 3.0)
            } else {
                minLogMel + ln(hz / minLogHz) / logStep
            }
        }

        fun melToHz(mel: Double): Double {
            return if (mel < minLogMel) {
                (200.0 / 3.0) * mel
            } else {
                minLogHz * kotlin.math.exp(logStep * (mel - minLogMel))
            }
        }

        val fMin = 0.0
        val fMax = sampleRate / 2.0 // 8000.0 Hz
        val minMel = hzToMel(fMin)
        val maxMel = hzToMel(fMax)

        val melPoints = DoubleArray(nMels + 2) { i ->
            minMel + i * (maxMel - minMel) / (nMels + 1)
        }
        val fPoints = DoubleArray(nMels + 2) { i -> melToHz(melPoints[i]) }
        val fftFreqs = DoubleArray(numFreqs) { k -> (k.toDouble() * sampleRate) / nFft.toDouble() }

        for (m in 0 until nMels) {
            val fLeft = fPoints[m]
            val fCenter = fPoints[m + 1]
            val fRight = fPoints[m + 2]

            // Slaney normalization: area of triangular filter normalized to 2.0 / (fRight - fLeft)
            val enorm = if (fRight > fLeft) 2.0 / (fRight - fLeft) else 1.0

            for (k in 0 until numFreqs) {
                val f = fftFreqs[k]
                var weight = 0.0
                if (f >= fLeft && f <= fCenter && fCenter > fLeft) {
                    weight = (f - fLeft) / (fCenter - fLeft)
                } else if (f > fCenter && f <= fRight && fRight > fCenter) {
                    weight = (fRight - f) / (fRight - fCenter)
                }
                bank[m][k] = (weight * enorm).toFloat()
            }
        }

        return bank
    }
}
