package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
     * Uses zero-allocation stream-to-disk buffering to prevent Dalvik heap OutOfMemoryError
     * on large audio/video files.
     */
    fun extractAudioFromVideo(
        context: Context,
        videoUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): DecodedAudio {
        // Fast-path for uncompressed WAV audio files
        try {
            val directWav = readWavDirectly(context, videoUri)
            if (directWav != null) {
                Log.i(TAG, "Successfully extracted audio via direct WAV decoder: ${directWav.sampleRate}Hz, ${directWav.durationMs}ms")
                onProgress(0.25f)
                return directWav
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct WAV check bypassed, proceeding with MediaExtractor: ${e.message}")
        }

        val extractor = MediaExtractor()
        try {
            val scheme = videoUri.scheme
            if (scheme == null || scheme == "file") {
                val path = videoUri.path ?: videoUri.toString().removePrefix("file://")
                val file = java.io.File(path)
                if (file.exists()) {
                    extractor.setDataSource(file.absolutePath)
                } else {
                    extractor.setDataSource(context, videoUri, null)
                }
            } else {
                extractor.setDataSource(context, videoUri, null)
            }
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

        val codec = try {
            MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Standard codec creation failed for $mime: ${e.message}. Attempting direct WAV/PCM fallback.")
            extractor.release()
            val fallbackWav = readWavDirectly(context, videoUri)
            if (fallbackWav != null) return fallbackWav
            throw IllegalStateException("Cannot decode audio stream with format $mime", e)
        }

        var actualSampleRate = sampleRate
        var actualChannelCount = channelCount

        val tempPcmFile = File.createTempFile("extracted_pcm_", ".raw", context.cacheDir)
        val fos = FileOutputStream(tempPcmFile)
        val fileChannel = fos.channel

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
                        fileChannel.write(outputBuffer)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                }
            }
        } finally {
            try {
                fileChannel.close()
                fos.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing temp PCM file output stream", e)
            }
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

        // Convert streamed PCM 16-bit to stereo Float32 arrays [-1.0f, 1.0f]
        val totalBytes = tempPcmFile.length()
        val totalShorts = (totalBytes / 2L).toInt()
        val channels = actualChannelCount.coerceAtLeast(1)
        val numFrames = totalShorts / channels
        val left = FloatArray(numFrames)
        val right = FloatArray(numFrames)

        try {
            FileInputStream(tempPcmFile).use { fis ->
                val inChannel = fis.channel
                val readBuffer = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
                var frameIdx = 0

                while (inChannel.read(readBuffer) > 0 && frameIdx < numFrames) {
                    readBuffer.flip()
                    val shortBuf = readBuffer.asShortBuffer()

                    if (channels == 1) {
                        while (shortBuf.hasRemaining() && frameIdx < numFrames) {
                            val mono = shortBuf.get() / 32768.0f
                            left[frameIdx] = mono
                            right[frameIdx] = mono
                            frameIdx++
                        }
                    } else if (channels == 2) {
                        while (shortBuf.remaining() >= 2 && frameIdx < numFrames) {
                            left[frameIdx] = shortBuf.get() / 32768.0f
                            right[frameIdx] = shortBuf.get() / 32768.0f
                            frameIdx++
                        }
                    } else {
                        // Multi-channel downmix: L, R, C
                        while (shortBuf.remaining() >= channels && frameIdx < numFrames) {
                            val l = shortBuf.get() / 32768.0f
                            val r = shortBuf.get() / 32768.0f
                            val c = (shortBuf.get() / 32768.0f) * 0.707f
                            for (ch in 3 until channels) {
                                shortBuf.get()
                            }
                            left[frameIdx] = (l + c).coerceIn(-1.0f, 1.0f)
                            right[frameIdx] = (r + c).coerceIn(-1.0f, 1.0f)
                            frameIdx++
                        }
                    }
                    readBuffer.clear()
                }
            }
        } finally {
            try {
                tempPcmFile.delete()
            } catch (_: Exception) {}
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

    /**
     * Directly decodes uncompressed 16-bit PCM WAV audio from a file or content URI
     * without needing MediaCodec. Returns null if the source is not a standard PCM WAV.
     */
    fun readWavDirectly(context: Context, uri: Uri): DecodedAudio? {
        val inputStream = try {
            val scheme = uri.scheme
            if (scheme == null || scheme == "file") {
                val path = uri.path ?: uri.toString().removePrefix("file://")
                val f = File(path)
                if (f.exists()) FileInputStream(f) else context.contentResolver.openInputStream(uri)
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            return null
        } ?: return null

        try {
            inputStream.use { stream ->
                val header = ByteArray(44)
                var bytesRead = 0
                while (bytesRead < 44) {
                    val r = stream.read(header, bytesRead, 44 - bytesRead)
                    if (r == -1) return null
                    bytesRead += r
                }

                // Verify RIFF and WAVE magic
                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                if (riff != "RIFF" || wave != "WAVE") {
                    return null
                }

                val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(20)
                val audioFormat = buffer.short.toInt() // 1 = PCM
                val numChannels = buffer.short.toInt().coerceAtLeast(1)
                val sampleRate = buffer.int.coerceAtLeast(8000)
                buffer.position(34)
                val bitsPerSample = buffer.short.toInt()

                if (audioFormat != 1 || bitsPerSample != 16) {
                    // Non-PCM or 24/32 bit, let MediaCodec try
                    return null
                }

                buffer.position(40)
                var dataSize = buffer.int.toLong() and 0xFFFFFFFFL

                val pcmData = stream.readBytes()
                val totalShorts = pcmData.size / 2
                val numFrames = totalShorts / numChannels
                if (numFrames <= 0) return null

                val left = FloatArray(numFrames)
                val right = FloatArray(numFrames)
                val sBuf = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

                if (numChannels == 1) {
                    for (i in 0 until numFrames) {
                        val mono = sBuf.get() / 32768.0f
                        left[i] = mono
                        right[i] = mono
                    }
                } else {
                    for (i in 0 until numFrames) {
                        left[i] = sBuf.get() / 32768.0f
                        right[i] = sBuf.get() / 32768.0f
                        for (ch in 2 until numChannels) {
                            sBuf.get()
                        }
                    }
                }

                val durationMs = (numFrames.toLong() * 1000L) / sampleRate
                return DecodedAudio(
                    leftChannel = left,
                    rightChannel = right,
                    sampleRate = sampleRate,
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct WAV decoding failed: ${e.message}")
            return null
        }
    }
}
