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
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ExportResult(
    val internalFile: File,
    val mediaStoreUri: Uri?,
    val displayPath: String
)

object VideoVocalsExporter {

    private const val TAG = "VideoVocalsExporter"
    private const val TIMEOUT_USEC = 10_000L

    /**
     * Muxes the original video stream with the isolated vocal audio track (encoded to AAC)
     * and saves the resulting MP4 video to internal storage and MediaStore (Movies/VocalExtractor).
     */
    suspend fun exportVideoWithVocals(
        context: Context,
        videoUriString: String,
        vocalWavPath: String,
        outputTitle: String,
        onProgress: (Float) -> Unit = {}
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val vocalWav = File(vocalWavPath)
        if (!vocalWav.exists() || vocalWav.length() < 44) {
            return@withContext Result.failure(IllegalStateException("Vocal track file not found or empty: $vocalWavPath"))
        }

        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val sanitizedTitle = outputTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val timestamp = System.currentTimeMillis()
        val tempExportFile = File(exportDir, "${sanitizedTitle}_vocals_$timestamp.mp4")

        val videoExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var audioEncoder: MediaCodec? = null
        var wavInputStream: FileInputStream? = null

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
                // The source is an audio-only file (e.g. MP3, WAV, M4A, FLAC)
                Log.i(TAG, "Source media has no video track; exporting as lossless WAV audio")
                val audioExportFile = File(exportDir, "${sanitizedTitle}_vocals_$timestamp.wav")
                File(vocalWavPath).copyTo(audioExportFile, overwrite = true)
                onProgress(0.8f)
                val mediaStore = saveAudioToMediaStore(context, audioExportFile, "${sanitizedTitle}_vocals")
                onProgress(1.0f)
                return@withContext Result.success(ExportResult(audioExportFile, mediaStore.first, mediaStore.second))
            }

            // Check video orientation
            val retriever = MediaMetadataRetriever()
            var rotation = 0
            try {
                setRetrieverDataSource(context, retriever, videoUriString)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotation = rotationStr?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract video rotation", e)
            } finally {
                retriever.release()
            }

            // 2. Read WAV Header Info
            wavInputStream = FileInputStream(vocalWav)
            val wavInfo = readWavHeader(wavInputStream)
            Log.i(TAG, "WAV Info: sampleRate=${wavInfo.sampleRate}, channels=${wavInfo.channels}, dataSize=${wavInfo.dataSize}")

            // 3. Configure Audio AAC Encoder
            val aacFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                wavInfo.sampleRate,
                wavInfo.channels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            // 4. Setup MediaMuxer
            muxer = MediaMuxer(tempExportFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }

            val muxerVideoTrack = muxer.addTrack(videoFormat)
            var muxerAudioTrack = -1
            var muxerStarted = false

            // We need to feed some audio to get the encoder output format and add the audio track to muxer
            val encoderBufferInfo = MediaCodec.BufferInfo()
            val rawPcmBuffer = ByteArray(4096)
            var pcmTotalBytesRead = 0L
            val totalPcmBytes = wavInfo.dataSize.coerceAtLeast(1)

            var sawPcmEos = false
            var sawEncoderEos = false
            var audioPtsUs = 0L
            val bytesPerSec = wavInfo.sampleRate * wavInfo.channels * (wavInfo.bitsPerSample / 8)

            // Step 1: Pump encoder until output format is available
            while (!muxerStarted) {
                if (!sawPcmEos) {
                    val inputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex)!!
                        inputBuffer.clear()
                        val bytesToRead = minOf(rawPcmBuffer.size, inputBuffer.remaining())
                        val bytesRead = wavInputStream.read(rawPcmBuffer, 0, bytesToRead)

                        if (bytesRead > 0) {
                            inputBuffer.put(rawPcmBuffer, 0, bytesRead)
                            val pts = (pcmTotalBytesRead * 1_000_000L) / bytesPerSec
                            audioEncoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, pts, 0)
                            pcmTotalBytesRead += bytesRead
                        } else {
                            sawPcmEos = true
                            audioEncoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }

                val outIdx = audioEncoder.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_USEC)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = audioEncoder.outputFormat
                    Log.i(TAG, "Audio encoder output format: $newFormat")
                    muxerAudioTrack = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIdx >= 0) {
                    // This shouldn't happen before format changed, but if it does, ignore/release
                    audioEncoder.releaseOutputBuffer(outIdx, false)
                }
            }

            onProgress(0.20f)

            // Step 2: Multiplex Video samples
            val videoBuffer = ByteBuffer.allocate(1024 * 1024)
            val videoBufferInfo = MediaCodec.BufferInfo()

            while (true) {
                videoBuffer.clear()
                val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                if (sampleSize < 0) {
                    break
                }
                videoBufferInfo.offset = 0
                videoBufferInfo.size = sampleSize
                videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                videoBufferInfo.flags = videoExtractor.sampleFlags

                muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
                videoExtractor.advance()
            }

            onProgress(0.50f)

            // Step 3: Complete encoding & multiplexing Audio
            while (!sawEncoderEos) {
                if (!sawPcmEos) {
                    val inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC)
                    if (inIdx >= 0) {
                        val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val bytesToRead = minOf(rawPcmBuffer.size, inBuf.remaining())
                        val bytesRead = wavInputStream.read(rawPcmBuffer, 0, bytesToRead)

                        if (bytesRead > 0) {
                            inBuf.put(rawPcmBuffer, 0, bytesRead)
                            val pts = (pcmTotalBytesRead * 1_000_000L) / bytesPerSec
                            audioEncoder.queueInputBuffer(inIdx, 0, bytesRead, pts, 0)
                            pcmTotalBytesRead += bytesRead
                            val progress = 0.50f + (pcmTotalBytesRead.toFloat() / totalPcmBytes) * 0.40f
                            onProgress(progress.coerceIn(0.50f, 0.90f))
                        } else {
                            sawPcmEos = true
                            audioEncoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }

                val outIdx = audioEncoder.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_USEC)
                if (outIdx >= 0) {
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

            onProgress(0.95f)

            // Stop and release muxer
            try {
                muxer.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping muxer", e)
            }
            muxer.release()
            muxer = null

            // 5. Save to Android MediaStore (Movies/VocalExtractor)
            val (mediaStoreUri, publicDisplay) = saveToMediaStore(context, tempExportFile, sanitizedTitle)
            onProgress(1.0f)

            Log.i(TAG, "Export completed successfully: ${tempExportFile.absolutePath} (Public: $publicDisplay)")
            Result.success(
                ExportResult(
                    internalFile = tempExportFile,
                    mediaStoreUri = mediaStoreUri,
                    displayPath = publicDisplay
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Video export failed", e)
            Result.failure(e)
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioEncoder?.stop(); audioEncoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { wavInputStream?.close() } catch (_: Exception) {}
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

    private data class WavInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataSize: Long
    )

    private fun readWavHeader(input: InputStream): WavInfo {
        val header = ByteArray(44)
        var totalRead = 0
        while (totalRead < 44) {
            val r = input.read(header, totalRead, 44 - totalRead)
            if (r == -1) break
            totalRead += r
        }

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val channels = buffer.getShort(22).toInt().coerceAtLeast(1)
        val sampleRate = buffer.getInt(24).coerceAtLeast(8000)
        val bitsPerSample = buffer.getShort(34).toInt().coerceAtLeast(16)
        val dataSize = buffer.getInt(40).toLong().and(0xFFFFFFFFL)

        return WavInfo(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            dataSize = if (dataSize > 0) dataSize else 10_000_000L
        )
    }

    /**
     * Saves a copy of the exported MP4 to MediaStore (Movies/VocalExtractor)
     * so it immediately appears in the user's Gallery and file manager.
     */
    private fun saveToMediaStore(
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
            Log.w(TAG, "Could not write to MediaStore directly, using internal storage path", e)
        }

        return Pair(null, sourceFile.name)
    }

    private fun saveAudioToMediaStore(
        context: Context,
        sourceFile: File,
        baseName: String
    ): Pair<Uri?, String> {
        val fileName = "${baseName}.wav"
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/VocalExtractor")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
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
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, values, null, null)
                }

                return Pair(itemUri, "Music/VocalExtractor/$fileName")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write audio to MediaStore directly, using internal storage path", e)
        }

        return Pair(null, sourceFile.name)
    }
}
