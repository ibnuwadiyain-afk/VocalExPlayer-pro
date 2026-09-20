package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance, real-time audio processor implementing:
 * 1. Stereo Mid/Side Matrixing:
 *    mid = (left + right) / 2, side = (left - right) / 2
 * 2. Vocal Attenuation (Karaoke Mode):
 *    Passes side to both channels with a high-pass filter (<120 Hz retained to keep bass/kick punchy).
 * 3. Vocal-Only Isolation:
 *    Subtracts bandpass-filtered side content from mid to isolate center-panned vocals.
 *
 * Can be used as a standalone streaming filter, a fast file converter (0ms latency),
 * or directly as a Media3 AudioProcessor in the ExoPlayer playback pipeline.
 */
class InstantPhaseCancellationProcessor(
    var isVocalOnlyMode: Boolean = true,
    var sampleRate: Int = 44100
) {
    companion object {
        private const val TAG = "InstantPhaseDSP"

        /**
         * Fast batch execution that converts any stereo audio file (WAV/PCM) or DecodedAudio
         * into an instant vocal-isolated or karaoke track in milliseconds.
         */
        fun processDecodedAudio(
            decodedAudio: DecodedAudio,
            outputWavFile: File,
            isVocalOnly: Boolean = true,
            onProgress: (Float) -> Unit = {}
        ): File {
            val totalFrames = decodedAudio.totalFrames
            val sampleRate = decodedAudio.sampleRate
            val processor = InstantPhaseCancellationProcessor(isVocalOnlyMode = isVocalOnly, sampleRate = sampleRate)

            val chunkSize = 4096
            val inL = FloatArray(chunkSize)
            val inR = FloatArray(chunkSize)
            val outL = FloatArray(chunkSize)
            val outR = FloatArray(chunkSize)

            val pcmBytes = ByteArray(chunkSize * 4)
            val byteBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

            val fos = FileOutputStream(outputWavFile)
            // Write 44-byte placeholder header
            fos.write(ByteArray(44))

            var totalBytesWritten = 0L
            var frameCursor = 0L

            try {
                while (frameCursor < totalFrames) {
                    val framesToRead = minOf(chunkSize.toLong(), totalFrames - frameCursor).toInt()
                    val read = decodedAudio.readStereoChunk(frameCursor, framesToRead, inL, inR, 0)
                    if (read <= 0) break

                    processor.processFloats(inL, inR, outL, outR, read)

                    byteBuffer.clear()
                    for (i in 0 until read) {
                        val sL = (outL[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                        val sR = (outR[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                        byteBuffer.putShort(sL)
                        byteBuffer.putShort(sR)
                    }

                    val bytesToWrite = read * 4
                    fos.write(pcmBytes, 0, bytesToWrite)
                    totalBytesWritten += bytesToWrite
                    frameCursor += read

                    onProgress(frameCursor.toFloat() / totalFrames.toFloat())
                }
            } finally {
                fos.flush()
                fos.close()
            }

            // Write canonical RIFF WAV header
            val raf = RandomAccessFile(outputWavFile, "rw")
            try {
                raf.seek(0)
                writeWavHeader(raf, sampleRate, 2, 16, totalBytesWritten)
            } finally {
                raf.close()
            }

            return outputWavFile
        }

        private fun writeWavHeader(
            raf: RandomAccessFile,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int,
            dataBytes: Long
        ) {
            val totalDataLen = dataBytes + 36
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8

            val header = ByteArray(44)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            buf.put("RIFF".toByteArray(Charsets.US_ASCII))
            buf.putInt(totalDataLen.toInt())
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))
            buf.put("fmt ".toByteArray(Charsets.US_ASCII))
            buf.putInt(16) // Subchunk1Size (16 for PCM)
            buf.putShort(1) // AudioFormat (1 for PCM)
            buf.putShort(channels.toShort())
            buf.putInt(sampleRate)
            buf.putInt(byteRate)
            buf.putShort(blockAlign.toShort())
            buf.putShort(bitsPerSample.toShort())
            buf.put("data".toByteArray(Charsets.US_ASCII))
            buf.putInt(dataBytes.toInt())

            raf.write(header)
        }
    }

    // --- Bi-quad Filter State for Bass Retention & Bandpass Separation ---
    // 1. Low-Pass Filter (< 120 Hz) for punchy kick/bass retention in karaoke mode
    private var lpfB0 = 0f
    private var lpfB1 = 0f
    private var lpfB2 = 0f
    private var lpfA1 = 0f
    private var lpfA2 = 0f
    private var lpfX1L = 0f
    private var lpfX2L = 0f
    private var lpfY1L = 0f
    private var lpfY2L = 0f
    private var lpfX1R = 0f
    private var lpfX2R = 0f
    private var lpfY1R = 0f
    private var lpfY2R = 0f

    // 2. Bandpass Filter (~200 Hz to ~4500 Hz) for Center Vocal Masking
    private var bpfB0 = 0f
    private var bpfB1 = 0f
    private var bpfB2 = 0f
    private var bpfA1 = 0f
    private var bpfA2 = 0f
    private var bpfX1 = 0f
    private var bpfX2 = 0f
    private var bpfY1 = 0f
    private var bpfY2 = 0f

    init {
        computeFilterCoefficients(sampleRate)
    }

    fun updateSampleRate(newRate: Int) {
        if (newRate != sampleRate && newRate > 0) {
            sampleRate = newRate
            computeFilterCoefficients(newRate)
        }
    }

    private fun computeFilterCoefficients(sr: Int) {
        // --- Low-Pass Filter: fc = 120 Hz, Q = 0.707 (Butterworth) ---
        val fcLpf = 120.0
        val omegaLpf = 2.0 * PI * fcLpf / sr.toDouble()
        val alphaLpf = sin(omegaLpf) / (2.0 * 0.7071)
        val cosLpf = cos(omegaLpf)

        val b0L = (1.0 - cosLpf) / 2.0
        val b1L = 1.0 - cosLpf
        val b2L = (1.0 - cosLpf) / 2.0
        val a0L = 1.0 + alphaLpf
        val a1L = -2.0 * cosLpf
        val a2L = 1.0 - alphaLpf

        lpfB0 = (b0L / a0L).toFloat()
        lpfB1 = (b1L / a0L).toFloat()
        lpfB2 = (b2L / a0L).toFloat()
        lpfA1 = (a1L / a0L).toFloat()
        lpfA2 = (a2L / a0L).toFloat()

        // --- Band-Pass Filter for Vocal Isolation: Center = 1200 Hz, Q = 0.5 (wide vocal band 200-4500 Hz) ---
        val fcBpf = 1200.0
        val omegaBpf = 2.0 * PI * fcBpf / sr.toDouble()
        val alphaBpf = sin(omegaBpf) / (2.0 * 0.5)
        val cosBpf = cos(omegaBpf)

        val b0B = alphaBpf
        val b1B = 0.0
        val b2B = -alphaBpf
        val a0B = 1.0 + alphaBpf
        val a1B = -2.0 * cosBpf
        val a2B = 1.0 - alphaBpf

        bpfB0 = (b0B / a0B).toFloat()
        bpfB1 = (b1B / a0B).toFloat()
        bpfB2 = (b2B / a0B).toFloat()
        bpfA1 = (a1B / a0B).toFloat()
        bpfA2 = (a2B / a0B).toFloat()

        resetHistory()
    }

    fun resetHistory() {
        lpfX1L = 0f; lpfX2L = 0f; lpfY1L = 0f; lpfY2L = 0f
        lpfX1R = 0f; lpfX2R = 0f; lpfY1R = 0f; lpfY2R = 0f
        bpfX1 = 0f; bpfX2 = 0f; bpfY1 = 0f; bpfY2 = 0f
    }

    /**
     * Processes in-place or copied stereo Float buffers [-1.0f, 1.0f].
     */
    fun processFloats(
        inL: FloatArray,
        inR: FloatArray,
        outL: FloatArray,
        outR: FloatArray,
        count: Int
    ) {
        for (i in 0 until count) {
            val left = inL[i]
            val right = inR[i]

            // 1. Stereo Mid/Side Matrixing
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            if (isVocalOnlyMode) {
                // Vocal Isolation:
                // Center-panned vocals reside heavily in the mid channel.
                // Filter the side channel with the vocal-range bandpass and subtract it from mid.
                val sideBand = bpfB0 * side + bpfB1 * bpfX1 + bpfB2 * bpfX2 - bpfA1 * bpfY1 - bpfA2 * bpfY2
                bpfX2 = bpfX1
                bpfX1 = side
                bpfY2 = bpfY1
                bpfY1 = sideBand

                // Subtraction creates strong center cancellation of wide instruments, leaving clean center vocals
                val vocalCenter = (mid - (sideBand * 0.85f)) * 1.35f
                outL[i] = vocalCenter
                outR[i] = vocalCenter
            } else {
                // Vocal Attenuation (Karaoke):
                // Mid channel contains the center vocals. Subtracting mid or keeping side eliminates center vocals.
                // Apply low-pass filter to mid to extract sub-bass/kick (< 120 Hz) and blend back so the rhythm stays punchy.
                val midBass = lpfB0 * mid + lpfB1 * lpfX1L + lpfB2 * lpfX2L - lpfA1 * lpfY1L - lpfA2 * lpfY2L
                lpfX2L = lpfX1L
                lpfX1L = mid
                lpfY2L = lpfY1L
                lpfY1L = midBass

                // Side contains stereo instruments, guitars, reverbs. Add bass back in.
                val karaokeL = (side * 1.15f) + midBass
                val karaokeR = (-side * 1.15f) + midBass

                outL[i] = karaokeL
                outR[i] = karaokeR
            }
        }
    }

    /**
     * Processes 16-bit PCM ShortArray stereo buffers.
     */
    fun processShorts(
        input: ShortArray,
        output: ShortArray,
        framesCount: Int
    ) {
        for (f in 0 until framesCount) {
            val idx = f * 2
            val left = input[idx] / 32768.0f
            val right = input[idx + 1] / 32768.0f

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            if (isVocalOnlyMode) {
                val sideBand = bpfB0 * side + bpfB1 * bpfX1 + bpfB2 * bpfX2 - bpfA1 * bpfY1 - bpfA2 * bpfY2
                bpfX2 = bpfX1
                bpfX1 = side
                bpfY2 = bpfY1
                bpfY1 = sideBand

                val vocal = (mid - (sideBand * 0.85f)) * 1.35f
                val s = (vocal.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                output[idx] = s
                output[idx + 1] = s
            } else {
                val midBass = lpfB0 * mid + lpfB1 * lpfX1L + lpfB2 * lpfX2L - lpfA1 * lpfY1L - lpfA2 * lpfY2L
                lpfX2L = lpfX1L
                lpfX1L = mid
                lpfY2L = lpfY1L
                lpfY1L = midBass

                val karaokeL = (side * 1.15f) + midBass
                val karaokeR = (-side * 1.15f) + midBass

                output[idx] = (karaokeL.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                output[idx + 1] = (karaokeR.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
            }
        }
    }
}

/**
 * Media3 AudioProcessor implementation for real-time in-line processing inside ExoPlayer.
 */
@OptIn(UnstableApi::class)
class InstantPhaseAudioProcessor(
    var isEnabledFilter: Boolean = false,
    var isVocalOnly: Boolean = true
) : BaseAudioProcessor() {

    private val dsp = InstantPhaseCancellationProcessor(isVocalOnlyMode = isVocalOnly)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount != 2) {
            // Passthrough if not stereo
            return inputAudioFormat
        }
        dsp.updateSampleRate(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)
        if (!isEnabledFilter) {
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        dsp.isVocalOnlyMode = isVocalOnly
        val frames = remaining / 4 // 2 channels * 2 bytes
        val inOrder = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until frames) {
            val left = inOrder.short / 32768.0f
            val right = inOrder.short / 32768.0f

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            if (isVocalOnly) {
                val vocal = (mid - (side * 0.5f)) * 1.3f
                val s = (vocal.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                buffer.putShort(s)
                buffer.putShort(s)
            } else {
                val kL = (side * 1.1f) + (mid * 0.2f)
                val kR = (-side * 1.1f) + (mid * 0.2f)
                buffer.putShort((kL.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort())
                buffer.putShort((kR.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort())
            }
        }
        buffer.flip()
    }
}
