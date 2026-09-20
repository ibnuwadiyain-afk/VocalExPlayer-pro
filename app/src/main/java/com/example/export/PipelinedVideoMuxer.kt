package com.example.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Simultaneous Pipelined Video Muxer for Zero-Wait Post-Processing.
 *
 * Multiplexes incoming 16-bit PCM vocal audio chunks from a streaming Channel concurrently
 * while the neural model runs, interleaving video pass-through samples with hardware AAC-encoded audio.
 * The final MP4 is completely written and finalized at the exact second the neural model finishes.
 */
object PipelinedVideoMuxer {

    private const val TAG = "PipelinedVideoMuxer"
    private const val TIMEOUT_USEC = 10_000L
    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 2
    private const val BIT_RATE = 192_000

    /**
     * Runs the pipelined remux loop concurrently with neural separation.
     */
    suspend fun muxStreamingAudioWithVideo(
        context: Context,
        videoUriString: String,
        outputTitle: String,
        audioChannel: Channel<ShortArray>,
        deleteOriginalAfterExport: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val sanitizedTitle = outputTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val timestamp = System.currentTimeMillis()
        val tempExportFile = File(exportDir, "${sanitizedTitle}_pipelined_$timestamp.mp4")

        val videoExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var audioEncoder: MediaCodec? = null

        try {
            onProgress(0.05f)

            // 1. Configure Video Extractor
            setExtractorDataSource(context, videoExtractor, videoUriString)

            var videoTrackIndexInExtractor = -1
            var videoFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndexInExtractor = i
                    videoFormat = format
                    videoExtractor.selectTrack(i)
                    break
                }
            }

            if (videoTrackIndexInExtractor == -1 || videoFormat == null) {
                // Audio-only source: Drain channel directly into AAC or WAV
                return@withContext drainAudioOnlyChannel(context, audioChannel, sanitizedTitle, timestamp, onProgress)
            }

            // Video rotation check
            val retriever = MediaMetadataRetriever()
            var rotation = 0
            try {
                setRetrieverDataSource(context, retriever, videoUriString)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotation = rotationStr?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading video rotation", e)
            } finally {
                retriever.release()
            }

            // 2. Configure Hardware AAC Encoder
            val aacFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                SAMPLE_RATE,
                CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            // 3. Configure MediaMuxer
            muxer = MediaMuxer(tempExportFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }

            val muxerVideoTrack = muxer.addTrack(videoFormat)
            var muxerAudioTrack = -1
            var muxerStarted = false

            val encoderBufferInfo = MediaCodec.BufferInfo()
            var audioSamplesWritten = 0L
            var audioPtsUs = 0L

            val videoBuffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            val videoBufferInfo = MediaCodec.BufferInfo()

            var sawVideoEos = false
            var channelClosed = false

            val pendingVideoSamples = mutableListOf<Triple<ByteArray, Long, Int>>()

            // Step 1: Drain initial chunks and pump encoder until INFO_OUTPUT_FORMAT_CHANGED arrives to avoid muxer stalling
            while (!muxerStarted) {
                val chunk = audioChannel.receiveCatching().getOrNull()
                if (chunk != null) {
                    feedPcmToEncoder(audioEncoder, chunk, audioSamplesWritten)
                    audioSamplesWritten += chunk.size
                } else {
                    channelClosed = true
                    signalEncoderEos(audioEncoder, audioSamplesWritten)
                }

                val outIdx = audioEncoder.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_USEC)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = audioEncoder.outputFormat
                    Log.i(TAG, "Audio encoder output format received: $newFormat")
                    muxerAudioTrack = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIdx >= 0) {
                    audioEncoder.releaseOutputBuffer(outIdx, false)
                }
            }

            onProgress(0.20f)

            // Step 2: Interleaved Pipelined Muxing Loop
            var sawEncoderEos = false

            while (!sawVideoEos || !sawEncoderEos) {
                // Read from neural streaming channel if not yet closed
                if (!channelClosed) {
                    val chunkResult = audioChannel.tryReceive()
                    if (chunkResult.isSuccess) {
                        val chunk = chunkResult.getOrThrow()
                        feedPcmToEncoder(audioEncoder, chunk, audioSamplesWritten)
                        audioSamplesWritten += chunk.size
                    } else if (chunkResult.isClosed) {
                        channelClosed = true
                        signalEncoderEos(audioEncoder, audioSamplesWritten)
                    } else {
                        // Micro-yield if audio buffers are temporarily starved to give neural inference thread CPU priority
                        delay(10L)
                    }
                }

                // Dequeue audio frames and interleave with video samples in lockstep chronologically
                if (!sawEncoderEos) {
                    val outIdx = audioEncoder.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_USEC)
                    if (outIdx >= 0) {
                        val currentAudioPts = encoderBufferInfo.presentationTimeUs
                        audioPtsUs = currentAudioPts

                        // Advance video track up to current audio timestamp (±200ms tolerance)
                        while (!sawVideoEos && videoExtractor.sampleTime in 0L..(currentAudioPts + 200_000L)) {
                            videoBuffer.clear()
                            val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                            if (sampleSize < 0) {
                                sawVideoEos = true
                                break
                            }
                            videoBufferInfo.offset = 0
                            videoBufferInfo.size = sampleSize
                            videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                            videoBufferInfo.flags = videoExtractor.sampleFlags

                            muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
                            videoExtractor.advance()
                        }

                        // Write audio sample
                        val encodedBuffer = audioEncoder.getOutputBuffer(outIdx)!!
                        if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encoderBufferInfo.size = 0
                        }
                        if (encoderBufferInfo.size > 0 && muxerAudioTrack != -1) {
                            encodedBuffer.position(encoderBufferInfo.offset)
                            encodedBuffer.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                            muxer.writeSampleData(muxerAudioTrack, encodedBuffer, encoderBufferInfo)
                        }
                        audioEncoder.releaseOutputBuffer(outIdx, false)

                        if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawEncoderEos = true
                        }
                    }
                }

                // Drain remaining video frames once audio reaches EOS
                if (sawEncoderEos && !sawVideoEos) {
                    while (!sawVideoEos) {
                        videoBuffer.clear()
                        val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                        if (sampleSize < 0) {
                            sawVideoEos = true
                            break
                        }
                        videoBufferInfo.offset = 0
                        videoBufferInfo.size = sampleSize
                        videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        videoBufferInfo.flags = videoExtractor.sampleFlags

                        muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
                        videoExtractor.advance()
                    }
                }
            }

            onProgress(0.95f)

            try {
                muxer.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaMuxer", e)
            }
            muxer.release()
            muxer = null

            // Save to Android MediaStore
            val (mediaStoreUri, publicPath) = saveVideoToMediaStore(context, tempExportFile, sanitizedTitle)
            onProgress(1.0f)

            // Safe storage lifecycle: delete original source if requested
            if (deleteOriginalAfterExport && (mediaStoreUri != null || tempExportFile.exists())) {
                deleteOriginalSourceFile(context, videoUriString)
            }

            Log.i(TAG, "Pipelined muxing complete: ${tempExportFile.absolutePath} (MediaStore: $publicPath)")
            Result.success(
                ExportResult(
                    internalFile = tempExportFile,
                    mediaStoreUri = mediaStoreUri,
                    displayPath = publicPath
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Pipelined video muxing failed", e)
            Result.failure(e)
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioEncoder?.stop(); audioEncoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun feedPcmToEncoder(encoder: MediaCodec, chunk: ShortArray, samplesWrittenSoFar: Long) {
        val inIdx = encoder.dequeueInputBuffer(TIMEOUT_USEC)
        if (inIdx >= 0) {
            val inBuf = encoder.getInputBuffer(inIdx)!!
            inBuf.clear()

            val bytesNeeded = chunk.size * 2
            val bytesToPut = minOf(bytesNeeded, inBuf.remaining())
            val shortsToPut = bytesToPut / 2

            val byteBuf = inBuf.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until shortsToPut) {
                byteBuf.putShort(chunk[i])
            }

            // PTS derived strictly from sample counts: ptsUs = (samplesWritten * 1_000_000L) / (sampleRate * channels)
            val ptsUs = (samplesWrittenSoFar * 1_000_000L) / (SAMPLE_RATE * CHANNELS)
            encoder.queueInputBuffer(inIdx, 0, bytesToPut, ptsUs, 0)
        }
    }

    private fun signalEncoderEos(encoder: MediaCodec, samplesWrittenSoFar: Long) {
        val inIdx = encoder.dequeueInputBuffer(TIMEOUT_USEC)
        if (inIdx >= 0) {
            val ptsUs = (samplesWrittenSoFar * 1_000_000L) / (SAMPLE_RATE * CHANNELS)
            encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

    private suspend fun drainAudioOnlyChannel(
        context: Context,
        audioChannel: Channel<ShortArray>,
        sanitizedTitle: String,
        timestamp: Long,
        onProgress: (Float) -> Unit
    ): Result<ExportResult> {
        val exportDir = File(context.filesDir, "exports")
        val targetWav = File(exportDir, "${sanitizedTitle}_vocals_$timestamp.wav")
        val fos = java.io.FileOutputStream(targetWav)
        fos.write(ByteArray(44))

        var totalBytes = 0L
        val byteBuf = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)

        for (chunk in audioChannel) {
            byteBuf.clear()
            for (s in chunk) {
                if (!byteBuf.hasRemaining()) {
                    fos.write(byteBuf.array(), 0, byteBuf.position())
                    byteBuf.clear()
                }
                byteBuf.putShort(s)
            }
            if (byteBuf.position() > 0) {
                fos.write(byteBuf.array(), 0, byteBuf.position())
            }
            totalBytes += chunk.size * 2
        }

        fos.flush()
        fos.close()

        val raf = java.io.RandomAccessFile(targetWav, "rw")
        raf.seek(0)
        val header = ByteArray(44)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray())
        b.putInt((totalBytes + 36).toInt())
        b.put("WAVEfmt ".toByteArray())
        b.putInt(16)
        b.putShort(1)
        b.putShort(CHANNELS.toShort())
        b.putInt(SAMPLE_RATE)
        b.putInt(SAMPLE_RATE * CHANNELS * 2)
        b.putShort((CHANNELS * 2).toShort())
        b.putShort(16)
        b.put("data".toByteArray())
        b.putInt(totalBytes.toInt())
        raf.write(header)
        raf.close()

        val (uri, path) = AudioExporter.saveAudioToMediaStore(context, targetWav, "${sanitizedTitle}_vocals", "audio/wav", "wav")
        onProgress(1.0f)
        return Result.success(ExportResult(targetWav, uri, path))
    }

    private fun deleteOriginalSourceFile(context: Context, uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file" || uri.scheme == null) {
                val f = File(uri.path ?: uriString)
                if (f.exists() && f.delete()) {
                    Log.i(TAG, "Deleted original source media file: ${f.absolutePath}")
                }
            } else if (uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null)
                Log.i(TAG, "Deleted original source content URI: $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete original media: ${e.message}")
        }
    }

    private fun setExtractorDataSource(context: Context, extractor: MediaExtractor, uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            extractor.setDataSource(context, uri, null)
        } else {
            val file = if (uriString.startsWith("file://")) File(uri.path ?: uriString) else File(uriString)
            if (file.exists()) {
                extractor.setDataSource(file.absolutePath)
            } else {
                extractor.setDataSource(context, uri, null)
            }
        }
    }

    private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            retriever.setDataSource(context, uri)
        } else {
            val file = if (uriString.startsWith("file://")) File(uri.path ?: uriString) else File(uriString)
            if (file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else {
                retriever.setDataSource(context, uri)
            }
        }
    }

    private fun saveVideoToMediaStore(
        context: Context,
        sourceFile: File,
        baseName: String
    ): Pair<Uri?, String> {
        val fileName = "${baseName}_vocals_only.mp4"
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VocalExtractor")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = context.contentResolver.insert(collection, values)
            if (itemUri != null) {
                context.contentResolver.openOutputStream(itemUri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, values, null, null)
                }

                return Pair(itemUri, "Movies/VocalExtractor/$fileName")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore write fallback to internal storage", e)
        }

        return Pair(null, sourceFile.name)
    }
}
