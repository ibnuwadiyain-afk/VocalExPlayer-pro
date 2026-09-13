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
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encapsulates decoded audio data.
 * Supports both zero-memory-overhead disk-backed chunk streaming (for long videos/audio)
 * and in-memory arrays (for short clips or unit tests).
 */
class DecodedAudio(
    val sampleRate: Int,
    val durationMs: Long,
    val totalFrames: Long,
    private val inMemoryLeft: FloatArray? = null,
    private val inMemoryRight: FloatArray? = null,
    val pcmFile: File? = null,
    private val autoDeletePcmFile: Boolean = true
) : AutoCloseable {

    constructor(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int,
        durationMs: Long
    ) : this(
        sampleRate = sampleRate,
        durationMs = durationMs,
        totalFrames = minOf(leftChannel.size, rightChannel.size).toLong(),
        inMemoryLeft = leftChannel,
        inMemoryRight = rightChannel,
        pcmFile = null,
        autoDeletePcmFile = false
    )

    val leftChannel: FloatArray by lazy {
        inMemoryLeft ?: loadChannel(0)
    }

    val rightChannel: FloatArray by lazy {
        inMemoryRight ?: loadChannel(1)
    }

    private var raf: RandomAccessFile? = null
    private val readBuffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)

    /**
     * Reads a chunk of stereo samples into the destination Float32 arrays.
     * Each frame consists of 2 channels (Left, Right) in the range [-1.0f, 1.0f].
     * Memory usage is strictly O(1) buffer-based.
     */
    @Synchronized
    fun readStereoChunk(
        startFrame: Long,
        count: Int,
        destL: FloatArray,
        destR: FloatArray,
        destOffset: Int
    ): Int {
        if (count <= 0) return 0

        // If in-memory arrays are available, copy directly
        if (inMemoryLeft != null && inMemoryRight != null) {
            val validFrames = minOf(count.toLong(), totalFrames - startFrame).coerceAtLeast(0L).toInt()
            if (validFrames > 0 && startFrame < totalFrames) {
                System.arraycopy(inMemoryLeft, startFrame.toInt(), destL, destOffset, validFrames)
                System.arraycopy(inMemoryRight, startFrame.toInt(), destR, destOffset, validFrames)
            }
            return validFrames
        }

        // Stream from disk PCM file (16-bit stereo: 4 bytes per frame)
        val file = pcmFile ?: return 0
        if (startFrame >= totalFrames) return 0

        try {
            if (raf == null) {
                raf = RandomAccessFile(file, "r")
            }
            val currentRaf = raf ?: return 0
            val validFrames = minOf(count.toLong(), totalFrames - startFrame).toInt()
            val startByte = startFrame * 4L
            currentRaf.seek(startByte)

            var framesLoaded = 0
            while (framesLoaded < validFrames) {
                val framesToRead = minOf(validFrames - framesLoaded, readBuffer.capacity() / 4)
                val bytesToRead = framesToRead * 4
                readBuffer.clear()
                val bytesRead = currentRaf.read(readBuffer.array(), 0, bytesToRead)
                if (bytesRead <= 0) break
                val framesRead = bytesRead / 4
                readBuffer.position(0)
                readBuffer.limit(bytesRead)
                val sBuf = readBuffer.asShortBuffer()
                for (f in 0 until framesRead) {
                    val idx = destOffset + framesLoaded + f
                    destL[idx] = sBuf.get() / 32768.0f
                    destR[idx] = sBuf.get() / 32768.0f
                }
                framesLoaded += framesRead
            }
            return framesLoaded
        } catch (e: Exception) {
            Log.e("DecodedAudio", "Error reading chunk at frame $startFrame: ${e.message}", e)
            return 0
        }
    }

    private fun loadChannel(channelIndex: Int): FloatArray {
        val size = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val channel = FloatArray(size)
        val tempL = FloatArray(4096)
        val tempR = FloatArray(4096)
        var pos = 0L
        while (pos < totalFrames) {
            val chunk = minOf(4096L, totalFrames - pos).toInt()
            val read = readStereoChunk(pos, chunk, tempL, tempR, 0)
            if (read <= 0) break
            val src = if (channelIndex == 0) tempL else tempR
            System.arraycopy(src, 0, channel, pos.toInt(), read)
            pos += read
        }
        return channel
    }

    override fun close() {
        try {
            raf?.close()
            raf = null
        } catch (_: Exception) {}
        if (autoDeletePcmFile) {
            try {
                pcmFile?.delete()
            } catch (_: Exception) {}
        }
    }
}

object AudioExtractor {
    private const val TAG = "AudioExtractor"
    private const val TIMEOUT_US = 5000L

    /**
     * Extracts and decodes the audio track of the given video into a streamable DecodedAudio.
     * Uses zero-allocation stream-to-disk buffering to eliminate Dalvik heap OutOfMemoryError
     * on long videos (from short clips to multi-hour recordings).
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
                Log.i(TAG, "Successfully extracted audio via direct WAV decoder: ${directWav.sampleRate}Hz, ${directWav.durationMs}ms, frames=${directWav.totalFrames}")
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
                val file = File(path)
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
        var consecutiveTryAgainCount = 0

        // Intermediate buffer for channel normalization (mono -> stereo, multi -> stereo)
        val normBuffer = ByteBuffer.allocateDirect(32 * 1024).order(ByteOrder.LITTLE_ENDIAN)

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
                    consecutiveTryAgainCount = 0
                    Log.i(TAG, "Decoder output format changed: sampleRate=$actualSampleRate, channelCount=$actualChannelCount")
                } else if (outputBufferIndex >= 0) {
                    consecutiveTryAgainCount = 0
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        if (actualChannelCount == 2) {
                            // Standard stereo 16-bit PCM: write directly
                            fileChannel.write(outputBuffer)
                        } else if (actualChannelCount == 1) {
                            // Mono 16-bit PCM: duplicate each sample to stereo L/R
                            val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            normBuffer.clear()
                            val normShorts = normBuffer.asShortBuffer()
                            while (shortBuf.hasRemaining()) {
                                if (normShorts.remaining() < 2) {
                                    normBuffer.position(0)
                                    normBuffer.limit(normShorts.position() * 2)
                                    fileChannel.write(normBuffer)
                                    normBuffer.clear()
                                    normShorts.position(0)
                                }
                                val sample = shortBuf.get()
                                normShorts.put(sample)
                                normShorts.put(sample)
                            }
                            if (normShorts.position() > 0) {
                                normBuffer.position(0)
                                normBuffer.limit(normShorts.position() * 2)
                                fileChannel.write(normBuffer)
                            }
                        } else {
                            // Multi-channel (>2): downmix to stereo
                            val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            normBuffer.clear()
                            val normShorts = normBuffer.asShortBuffer()
                            while (shortBuf.remaining() >= actualChannelCount) {
                                if (normShorts.remaining() < 2) {
                                    normBuffer.position(0)
                                    normBuffer.limit(normShorts.position() * 2)
                                    fileChannel.write(normBuffer)
                                    normBuffer.clear()
                                    normShorts.position(0)
                                }
                                val l = shortBuf.get()
                                val r = shortBuf.get()
                                val c = (shortBuf.get() * 0.707f).toInt().toShort()
                                for (ch in 3 until actualChannelCount) {
                                    shortBuf.get()
                                }
                                normShorts.put(((l + c).coerceIn(-32768, 32767)).toShort())
                                normShorts.put(((r + c).coerceIn(-32768, 32767)).toShort())
                            }
                            if (normShorts.position() > 0) {
                                normBuffer.position(0)
                                normBuffer.limit(normShorts.position() * 2)
                                fileChannel.write(normBuffer)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (sawInputEOS) {
                        consecutiveTryAgainCount++
                        if (consecutiveTryAgainCount > 50) {
                            Log.w(TAG, "MediaCodec decoder safety EOS triggered after $consecutiveTryAgainCount idle polls")
                            sawOutputEOS = true
                        }
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

        // tempPcmFile is now guaranteed to be 16-bit 2-channel stereo PCM (4 bytes per frame)
        val totalBytes = tempPcmFile.length()
        val numFrames = totalBytes / 4L

        val calculatedDurationMs = if (actualSampleRate > 0) {
            (numFrames * 1000L) / actualSampleRate
        } else {
            durationUs / 1000L
        }

        Log.i(TAG, "Extracted audio stream: $numFrames frames at $actualSampleRate Hz (${calculatedDurationMs}ms), file=${tempPcmFile.length()} bytes")

        return DecodedAudio(
            sampleRate = actualSampleRate,
            durationMs = calculatedDurationMs,
            totalFrames = numFrames,
            pcmFile = tempPcmFile,
            autoDeletePcmFile = true
        )
    }

    /**
     * Directly decodes uncompressed 16-bit PCM WAV audio from a file or content URI
     * without needing MediaCodec. Returns null if the source is not a standard PCM WAV.
     * Streams data directly to disk to prevent OOM on large WAV files.
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
                val dataSize = buffer.int.toLong() and 0xFFFFFFFFL

                val tempPcmFile = File.createTempFile("direct_wav_pcm_", ".raw", context.cacheDir)
                FileOutputStream(tempPcmFile).use { fos ->
                    val inBuf = ByteArray(64 * 1024)
                    var remaining = if (dataSize > 0) dataSize else Long.MAX_VALUE

                    if (numChannels == 2) {
                        while (remaining > 0) {
                            val toRead = minOf(remaining, inBuf.size.toLong()).toInt()
                            val r = stream.read(inBuf, 0, toRead)
                            if (r <= 0) break
                            fos.write(inBuf, 0, r)
                            if (dataSize > 0) remaining -= r
                        }
                    } else if (numChannels == 1) {
                        val stereoBuf = ByteBuffer.allocate(inBuf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        while (remaining > 0) {
                            val toRead = minOf(remaining, inBuf.size.toLong()).toInt()
                            val r = stream.read(inBuf, 0, toRead)
                            if (r <= 0) break
                            val sIn = ByteBuffer.wrap(inBuf, 0, r).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            stereoBuf.clear()
                            val sOut = stereoBuf.asShortBuffer()
                            while (sIn.hasRemaining()) {
                                val s = sIn.get()
                                sOut.put(s)
                                sOut.put(s)
                            }
                            fos.write(stereoBuf.array(), 0, sOut.position() * 2)
                            if (dataSize > 0) remaining -= r
                        }
                    } else {
                        // Multi-channel
                        val stereoBuf = ByteBuffer.allocate(inBuf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        while (remaining > 0) {
                            val toRead = minOf(remaining, inBuf.size.toLong()).toInt()
                            val r = stream.read(inBuf, 0, toRead)
                            if (r <= 0) break
                            val sIn = ByteBuffer.wrap(inBuf, 0, r).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            stereoBuf.clear()
                            val sOut = stereoBuf.asShortBuffer()
                            while (sIn.remaining() >= numChannels) {
                                val l = sIn.get()
                                val right = sIn.get()
                                val c = (sIn.get() * 0.707f).toInt().toShort()
                                for (ch in 3 until numChannels) {
                                    sIn.get()
                                }
                                sOut.put(((l + c).coerceIn(-32768, 32767)).toShort())
                                sOut.put(((right + c).coerceIn(-32768, 32767)).toShort())
                            }
                            fos.write(stereoBuf.array(), 0, sOut.position() * 2)
                            if (dataSize > 0) remaining -= r
                        }
                    }
                    fos.flush()
                }

                val totalBytes = tempPcmFile.length()
                val totalFrames = totalBytes / 4L
                if (totalFrames <= 0) {
                    tempPcmFile.delete()
                    return null
                }

                val durationMs = (totalFrames * 1000L) / sampleRate
                return DecodedAudio(
                    sampleRate = sampleRate,
                    durationMs = durationMs,
                    totalFrames = totalFrames,
                    pcmFile = tempPcmFile,
                    autoDeletePcmFile = true
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct WAV decoding failed: ${e.message}")
            return null
        }
    }
}

