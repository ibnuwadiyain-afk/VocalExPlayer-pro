package com.example.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.audio.AudioExtractor
import com.example.audio.WavWriter
import com.example.data.model.VideoItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class ExportFormat(
    val title: String,
    val subtitle: String,
    val extension: String,
    val mimeType: String,
    val isAudioOnly: Boolean,
    val requiresVideoTrack: Boolean
) {
    AUDIO_VOCALS_WAV(
        title = "Isolated Vocals (Lossless WAV)",
        subtitle = "Studio 16-bit 44.1kHz uncompressed audio • Best for DAWs & editing",
        extension = "wav",
        mimeType = "audio/wav",
        isAudioOnly = true,
        requiresVideoTrack = false
    ),
    AUDIO_VOCALS_M4A(
        title = "Isolated Vocals (M4A / AAC)",
        subtitle = "High-quality 256 kbps AAC audio • Universal playback & small file size",
        extension = "m4a",
        mimeType = "audio/mp4",
        isAudioOnly = true,
        requiresVideoTrack = false
    ),
    AUDIO_INSTRUMENTAL_WAV(
        title = "Instrumental / Karaoke (Lossless WAV)",
        subtitle = "Acapella removed • Full backing music in 16-bit studio WAV",
        extension = "wav",
        mimeType = "audio/wav",
        isAudioOnly = true,
        requiresVideoTrack = false
    ),
    AUDIO_INSTRUMENTAL_M4A(
        title = "Instrumental / Karaoke (M4A / AAC)",
        subtitle = "Backing accompaniment track in compact high-quality AAC",
        extension = "m4a",
        mimeType = "audio/mp4",
        isAudioOnly = true,
        requiresVideoTrack = false
    ),
    VIDEO_VOCALS_MP4(
        title = "Video with Vocals Only (MP4)",
        subtitle = "Combines original video stream with AI-isolated vocal audio",
        extension = "mp4",
        mimeType = "video/mp4",
        isAudioOnly = false,
        requiresVideoTrack = true
    ),
    VIDEO_INSTRUMENTAL_MP4(
        title = "Video with Karaoke Track (MP4)",
        subtitle = "Combines original video stream with instrumental audio",
        extension = "mp4",
        mimeType = "video/mp4",
        isAudioOnly = false,
        requiresVideoTrack = true
    );
}

object AudioExporter {

    private const val TAG = "AudioExporter"
    private const val TIMEOUT_USEC = 10_000L

    /**
     * Exports media to the selected Audio or Video format.
     */
    suspend fun exportMedia(
        context: Context,
        video: VideoItemEntity,
        format: ExportFormat,
        onProgress: (Float) -> Unit = {}
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val vocalPath = video.vocalUri
        if (vocalPath.isNullOrBlank() || !File(vocalPath).exists()) {
            return@withContext Result.failure(
                IllegalStateException("Vocal track not ready yet. Please allow AI separation to complete.")
            )
        }

        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val sanitizedTitle = ExportNameSanitizer.sanitizeTitle(video.title)
        val timestamp = System.currentTimeMillis()

        try {
            when (format) {
                ExportFormat.AUDIO_VOCALS_WAV -> {
                    onProgress(0.2f)
                    val targetFile = File(exportDir, "${sanitizedTitle}_vocals_$timestamp.wav")
                    File(vocalPath).copyTo(targetFile, overwrite = true)
                    onProgress(0.8f)
                    val mediaStoreResult = saveAudioToMediaStore(context, targetFile, "${sanitizedTitle}_vocals", "audio/wav", "wav")
                    onProgress(1.0f)
                    Result.success(ExportResult(targetFile, mediaStoreResult.first, mediaStoreResult.second))
                }

                ExportFormat.AUDIO_VOCALS_M4A -> {
                    onProgress(0.1f)
                    val targetFile = File(exportDir, "${sanitizedTitle}_vocals_$timestamp.m4a")
                    encodeWavToM4a(File(vocalPath), targetFile) { p ->
                        onProgress(0.1f + p * 0.7f)
                    }
                    onProgress(0.85f)
                    val mediaStoreResult = saveAudioToMediaStore(context, targetFile, "${sanitizedTitle}_vocals", "audio/mp4", "m4a")
                    onProgress(1.0f)
                    Result.success(ExportResult(targetFile, mediaStoreResult.first, mediaStoreResult.second))
                }

                ExportFormat.AUDIO_INSTRUMENTAL_WAV -> {
                    onProgress(0.1f)
                    val targetFile = File(exportDir, "${sanitizedTitle}_instrumental_$timestamp.wav")
                    createInstrumentalWav(context, video, File(vocalPath), targetFile) { p ->
                        onProgress(0.1f + p * 0.7f)
                    }
                    onProgress(0.85f)
                    val mediaStoreResult = saveAudioToMediaStore(context, targetFile, "${sanitizedTitle}_instrumental", "audio/wav", "wav")
                    onProgress(1.0f)
                    Result.success(ExportResult(targetFile, mediaStoreResult.first, mediaStoreResult.second))
                }

                ExportFormat.AUDIO_INSTRUMENTAL_M4A -> {
                    onProgress(0.1f)
                    val tempWav = File(context.cacheDir, "temp_inst_$timestamp.wav")
                    createInstrumentalWav(context, video, File(vocalPath), tempWav) { p ->
                        onProgress(0.1f + p * 0.4f)
                    }
                    val targetFile = File(exportDir, "${sanitizedTitle}_instrumental_$timestamp.m4a")
                    encodeWavToM4a(tempWav, targetFile) { p ->
                        onProgress(0.5f + p * 0.35f)
                    }
                    try { tempWav.delete() } catch (_: Exception) {}
                    onProgress(0.9f)
                    val mediaStoreResult = saveAudioToMediaStore(context, targetFile, "${sanitizedTitle}_instrumental", "audio/mp4", "m4a")
                    onProgress(1.0f)
                    Result.success(ExportResult(targetFile, mediaStoreResult.first, mediaStoreResult.second))
                }

                ExportFormat.VIDEO_VOCALS_MP4 -> {
                    VideoVocalsExporter.exportVideoWithVocals(
                        context = context,
                        videoUriString = video.videoUri,
                        vocalWavPath = vocalPath,
                        outputTitle = video.title,
                        videoId = video.id,
                        onProgress = onProgress
                    )
                }

                ExportFormat.VIDEO_INSTRUMENTAL_MP4 -> {
                    onProgress(0.05f)
                    val instWav = File(context.cacheDir, "inst_video_$timestamp.wav")
                    createInstrumentalWav(context, video, File(vocalPath), instWav) { p ->
                        onProgress(0.05f + p * 0.25f)
                    }
                    val videoResult = VideoVocalsExporter.exportVideoWithVocals(
                        context = context,
                        videoUriString = video.videoUri,
                        vocalWavPath = instWav.absolutePath,
                        outputTitle = "${video.title}_karaoke",
                        onProgress = { p -> onProgress(0.3f + p * 0.7f) }
                    )
                    try { instWav.delete() } catch (_: Exception) {}
                    videoResult
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for format ${format.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Computes the instrumental track by subtracting the vocal track from the source audio.
     */
    private fun createInstrumentalWav(
        context: Context,
        video: VideoItemEntity,
        vocalWavFile: File,
        outputWavFile: File,
        onProgress: (Float) -> Unit
    ) {
        onProgress(0.1f)
        val sourceAudio = AudioExtractor.extractAudioFromVideo(context, Uri.parse(video.videoUri))
        onProgress(0.4f)
        val vocalAudio = AudioExtractor.readWavDirectly(context, Uri.fromFile(vocalWavFile))
            ?: AudioExtractor.extractAudioFromVideo(context, Uri.fromFile(vocalWavFile))
        onProgress(0.6f)

        val numFrames = minOf(sourceAudio.leftChannel.size, vocalAudio.leftChannel.size)
        val instLeft = FloatArray(numFrames)
        val instRight = FloatArray(numFrames)

        for (i in 0 until numFrames) {
            instLeft[i] = (sourceAudio.leftChannel[i] - vocalAudio.leftChannel[i]).coerceIn(-1.0f, 1.0f)
            instRight[i] = (sourceAudio.rightChannel[i] - vocalAudio.rightChannel[i]).coerceIn(-1.0f, 1.0f)
        }

        onProgress(0.85f)
        WavWriter.writeWavFile(instLeft, instRight, sourceAudio.sampleRate, outputWavFile)
        onProgress(1.0f)
    }

    /**
     * Encodes 16-bit PCM WAV to AAC within an M4A audio container using Android MediaCodec & MediaMuxer.
     */
    private fun encodeWavToM4a(
        wavFile: File,
        outputM4aFile: File,
        bitRate: Int = 256_000,
        onProgress: (Float) -> Unit = {}
    ) {
        FileInputStream(wavFile).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val channels = buf.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = buf.getInt(24).coerceAtLeast(8000)

            val mime = MediaFormat.MIMETYPE_AUDIO_AAC
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }

            val codec = MediaCodec.createEncoderByType(mime)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer = MediaMuxer(outputM4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val chunk = ByteArray(8192)
            var sawInputEOS = false
            var sawOutputEOS = false
            var presentationTimeUs = 0L
            val bytesPerSec = sampleRate * channels * 2

            try {
                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_USEC)
                        if (inputBufferId >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                            inputBuffer.clear()
                            val bytesRead = fis.read(chunk)
                            if (bytesRead <= 0) {
                                sawInputEOS = true
                                codec.queueInputBuffer(
                                    inputBufferId, 0, 0, presentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            } else {
                                inputBuffer.put(chunk, 0, bytesRead)
                                val durationUs = (bytesRead.toLong() * 1_000_000L) / bytesPerSec
                                codec.queueInputBuffer(
                                    inputBufferId, 0, bytesRead, presentationTimeUs, 0
                                )
                                presentationTimeUs += durationUs
                            }
                        }
                    }

                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                    if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = codec.outputFormat
                        audioTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outputBufferId >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                try { codec.release() } catch (_: Exception) {}
                if (muxerStarted) {
                    try { muxer.stop() } catch (_: Exception) {}
                }
                try { muxer.release() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Saves audio file to MediaStore under Music/VocalExtractor.
     */
    private fun saveAudioToMediaStore(
        context: Context,
        sourceFile: File,
        baseName: String,
        mimeType: String,
        extension: String
    ): Pair<Uri?, String> {
        val fileName = "${baseName}.$extension"
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
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
            Log.w(TAG, "Could not write audio to MediaStore directly, using internal file path", e)
        }

        return Pair(null, sourceFile.name)
    }

    /**
     * Launches Android Share sheet to share the exported audio or video file with external apps.
     */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share Audio / Video")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share file: ${file.absolutePath}", e)
        }
    }
}
