package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DecodedAudio(
    val leftChannel: FloatArray,
    val rightChannel: FloatArray,
    val sampleRate: Int,
    val durationMs: Long
)

object AudioExtractor {
    private const val TAG = "AudioExtractor"
    private const val TIMEOUT_US = 5000L

    /**
     * Extracts and decodes the audio track of the given video into stereo Float32 arrays.
     */
    fun extractAudioFromVideo(
        context: Context,
        videoUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): DecodedAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set data source for extractor", e)
            throw e
        }

        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || audioFormat == null) {
            extractor.release()
            throw IllegalStateException("No audio track found in the selected video")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
        val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
            44100
        }
        val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            2
        }
        val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
            audioFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            10_000_000L
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(audioFormat, null, null, 0)
        codec.start()

        var actualSampleRate = sampleRate
        var actualChannelCount = channelCount

        val rawPcmSamples = ArrayList<Short>(sampleRate * channelCount * 10)
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                sawInputEOS = true
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, sampleSize, presentationTimeUs, 0
                                )
                                extractor.advance()
                                if (durationUs > 0) {
                                    val progress = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                                    onProgress(progress * 0.25f) // 0-25% progress during audio extraction
                                }
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = codec.outputFormat
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        actualSampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        actualChannelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    Log.i(TAG, "Decoder output format changed: sampleRate=$actualSampleRate, channelCount=$actualChannelCount")
                } else if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shortBuffer.hasRemaining()) {
                            rawPcmSamples.add(shortBuffer.get())
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                }
            }
        } finally {
            try {
                codec.stop()
                codec.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping codec", e)
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing extractor", e)
            }
        }

        // Convert interleaved Short samples to stereo Float32 arrays [-1.0f, 1.0f]
        val totalShorts = rawPcmSamples.size
        val channels = actualChannelCount.coerceAtLeast(1)
        val numFrames = totalShorts / channels
        val left = FloatArray(numFrames)
        val right = FloatArray(numFrames)

        if (channels == 1) {
            // Mono: duplicate to both channels
            for (i in 0 until numFrames) {
                val mono = rawPcmSamples[i] / 32768.0f
                left[i] = mono
                right[i] = mono
            }
        } else if (channels == 2) {
            // Stereo
            for (i in 0 until numFrames) {
                val lShort = rawPcmSamples[i * 2]
                val rShort = rawPcmSamples[i * 2 + 1]
                left[i] = lShort / 32768.0f
                right[i] = rShort / 32768.0f
            }
        } else {
            // Multi-channel (> 2 channels, e.g. 5.1 surround sound)
            // Downmix standard 5.1 layout: L, R, C, LFE, Ls, Rs
            for (i in 0 until numFrames) {
                val base = i * channels
                val l = rawPcmSamples[base] / 32768.0f
                val r = rawPcmSamples[base + 1] / 32768.0f
                val c = if (channels > 2) (rawPcmSamples[base + 2] / 32768.0f) * 0.707f else 0f
                left[i] = (l + c).coerceIn(-1.0f, 1.0f)
                right[i] = (r + c).coerceIn(-1.0f, 1.0f)
            }
        }

        val calculatedDurationMs = if (actualSampleRate > 0) {
            (numFrames.toLong() * 1000L) / actualSampleRate
        } else {
            durationUs / 1000L
        }

        Log.i(TAG, "Extracted audio: $numFrames frames at $actualSampleRate Hz (${calculatedDurationMs}ms), channels=$channels")

        return DecodedAudio(
            leftChannel = left,
            rightChannel = right,
            sampleRate = actualSampleRate,
            durationMs = calculatedDurationMs
        )
    }
}
