package com.example.dubbing

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.audio.AudioExtractor
import com.example.audio.WavWriter
import com.example.data.model.VideoItemEntity
import com.example.export.ExportNameSanitizer
import com.example.export.ExportResult
import com.example.export.VideoVocalsExporter
import com.example.whisper.TranscriptionSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DubbedVideoExporter {

    private const val TAG = "DubbedVideoExporter"

    /**
     * Executes the end-to-end TTS dubbing pipeline:
     * 1. Synthesizes subtitle segments with Android's built-in TextToSpeech engine.
     * 2. Time-aligns audio clips along video timestamps (with optional background music ducking).
     * 3. Muxes with video track (or encodes to M4A/WAV) and exports to MediaStore and app storage.
     */
    suspend fun exportDubbedMedia(
        context: Context,
        video: VideoItemEntity,
        segments: List<TranscriptionSegment>,
        config: DubbingConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        var ttsInstance: TextToSpeech? = null
        val tempFilesToClean = mutableListOf<File>()

        try {
            val engineDesc = config.ttsEngineLabel ?: "TextToSpeech engine"
            onProgress(0.02f, "Initializing $engineDesc...")
            ttsInstance = TtsDubbingEngine.createTts(context, config.ttsEnginePackage)
            if (ttsInstance == null && !config.ttsEnginePackage.isNullOrBlank()) {
                Log.w(TAG, "Selected 3rd-party TTS engine '$engineDesc' failed to initialize; falling back to default system TTS")
                onProgress(0.03f, "Falling back to default TTS engine...")
                ttsInstance = TtsDubbingEngine.createTts(context, null)
            }
            if (ttsInstance == null) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to initialize TextToSpeech engine ($engineDesc). Please check device settings.")
                )
            }

            val sanitizedTitle = ExportNameSanitizer.sanitizeTitle(video.title)
            val timestamp = System.currentTimeMillis()
            val exportDir = File(context.filesDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            // Determine background audio file if mixing with music is requested
            var backgroundFile: File? = null
            if (config.mixMode == DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC) {
                onProgress(0.08f, "Preparing background music track...")
                val vocalPath = video.vocalUri
                if (!vocalPath.isNullOrBlank() && File(vocalPath).exists()) {
                    // Compute karaoke backing track by subtracting vocals
                    val tempInstWav = File(context.cacheDir, "temp_inst_dub_${timestamp}.wav")
                    tempFilesToClean.add(tempInstWav)
                    val instCreated = createInstrumentalTrack(context, video, File(vocalPath), tempInstWav)
                    if (instCreated && tempInstWav.exists()) {
                        backgroundFile = tempInstWav
                    }
                }

                // If instrumental was not computed, use original source audio directly for background ducking
                if (backgroundFile == null) {
                    val tempSourceWav = File(context.cacheDir, "temp_source_dub_${timestamp}.wav")
                    tempFilesToClean.add(tempSourceWav)
                    try {
                        val decodedSource = AudioExtractor.extractAudioFromVideo(context, Uri.parse(video.videoUri))
                        WavWriter.writeWavFile(
                            decodedSource.leftChannel,
                            decodedSource.rightChannel,
                            decodedSource.sampleRate,
                            tempSourceWav
                        )
                        decodedSource.close()
                        if (tempSourceWav.exists()) {
                            backgroundFile = tempSourceWav
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not extract background source audio for ducking", e)
                    }
                }
            }

            // Generate master 44.1kHz stereo dubbed WAV
            val masterDubbedWav = File(context.cacheDir, "master_dubbed_${timestamp}.wav")
            tempFilesToClean.add(masterDubbedWav)

            val dubbingResult = TtsDubbingEngine.generateDubbedAudioTrack(
                context = context,
                tts = ttsInstance,
                segments = segments,
                totalDurationMs = video.duration,
                config = config,
                backgroundAudioFile = backgroundFile,
                outputWavFile = masterDubbedWav,
                onProgress = { p, msg ->
                    // Map dubbing engine 0.05f..1.0f to total 0.10f..0.75f
                    val mapped = 0.10f + (p * 0.65f)
                    onProgress(mapped, msg)
                }
            )

            if (dubbingResult.isFailure) {
                return@withContext Result.failure(dubbingResult.exceptionOrNull() ?: Exception("TTS dubbing failed"))
            }

            val finalWav = dubbingResult.getOrThrow()

            // Export according to mixMode
            val langSuffix = if (config.enableTranslation) "_${config.targetLanguageCode}" else ""
            when (config.mixMode) {
                DubbingMixMode.VIDEO_DUBBED_ONLY,
                DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC -> {
                    onProgress(0.80f, "Muxing dubbed audio track with video stream...")
                    val titleSuffix = if (config.mixMode == DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC) "dubbed_music$langSuffix" else "dubbed$langSuffix"

                    // Use VideoVocalsExporter to mux video track with dubbed audio
                    val muxResult = VideoVocalsExporter.exportVideoWithVocals(
                        context = context,
                        videoUriString = video.videoUri,
                        vocalWavPath = finalWav.absolutePath,
                        outputTitle = "${sanitizedTitle}_${titleSuffix}",
                        videoId = null,
                        onProgress = { p ->
                            val mapped = 0.80f + (p * 0.18f)
                            onProgress(mapped, "Encoding and muxing video frames (${(mapped * 100).toInt()}%)...")
                        }
                    )

                    onProgress(1.0f, "Dubbed video exported successfully!")
                    muxResult
                }

                DubbingMixMode.AUDIO_DUBBED_WAV -> {
                    onProgress(0.85f, "Saving lossless dubbed audio track...")
                    val targetFile = File(exportDir, "${sanitizedTitle}_dubbed${langSuffix}_$timestamp.wav")
                    finalWav.copyTo(targetFile, overwrite = true)
                    val mediaStore = saveAudioToMediaStore(context, targetFile, "${sanitizedTitle}_dubbed$langSuffix", "audio/wav", "wav")
                    onProgress(1.0f, "Dubbed audio (WAV) export complete!")
                    Result.success(ExportResult(targetFile, mediaStore.first, mediaStore.second))
                }

                DubbingMixMode.AUDIO_DUBBED_M4A -> {
                    onProgress(0.82f, "Encoding dubbed audio to AAC (M4A)...")
                    val targetFile = File(exportDir, "${sanitizedTitle}_dubbed${langSuffix}_$timestamp.m4a")
                    encodeWavToM4a(finalWav, targetFile) { p ->
                        val mapped = 0.82f + (p * 0.16f)
                        onProgress(mapped, "Encoding M4A audio (${(mapped * 100).toInt()}%)...")
                    }
                    val mediaStore = saveAudioToMediaStore(context, targetFile, "${sanitizedTitle}_dubbed$langSuffix", "audio/mp4", "m4a")
                    onProgress(1.0f, "Dubbed audio (M4A) export complete!")
                    Result.success(ExportResult(targetFile, mediaStore.first, mediaStore.second))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export dubbed media", e)
            Result.failure(e)
        } finally {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (_: Exception) {}
            for (f in tempFilesToClean) {
                try { f.delete() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Subtracts separated vocals from source audio to obtain instrumental backing music.
     */
    private fun createInstrumentalTrack(
        context: Context,
        video: VideoItemEntity,
        vocalWavFile: File,
        outputWavFile: File
    ): Boolean {
        return try {
            val sourceAudio = AudioExtractor.extractAudioFromVideo(context, Uri.parse(video.videoUri))
            val vocalAudio = AudioExtractor.readWavDirectly(context, Uri.fromFile(vocalWavFile))
                ?: AudioExtractor.extractAudioFromVideo(context, Uri.fromFile(vocalWavFile))

            val numFrames = minOf(sourceAudio.leftChannel.size, vocalAudio.leftChannel.size)
            val instLeft = FloatArray(numFrames)
            val instRight = FloatArray(numFrames)

            for (i in 0 until numFrames) {
                instLeft[i] = (sourceAudio.leftChannel[i] - vocalAudio.leftChannel[i]).coerceIn(-1.0f, 1.0f)
                instRight[i] = (sourceAudio.rightChannel[i] - vocalAudio.rightChannel[i]).coerceIn(-1.0f, 1.0f)
            }

            WavWriter.writeWavFile(instLeft, instRight, sourceAudio.sampleRate, outputWavFile)
            sourceAudio.close()
            vocalAudio.close()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error generating instrumental track for dubbing", e)
            false
        }
    }

    /**
     * Encodes PCM WAV to AAC in an M4A container.
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
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val codec = MediaCodec.createEncoderByType(mime)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer = MediaMuxer(outputM4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1
            var muxerStarted = false

            val inBuf = ByteArray(4096)
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var presentationTimeUs = 0L
            val bytesPerSample = 2 * channels
            val bytesPerSecond = sampleRate * bytesPerSample
            val totalBytes = (wavFile.length() - 44).coerceAtLeast(1)
            var totalRead = 0L

            try {
                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inputBufferId = codec.dequeueInputBuffer(10_000L)
                        if (inputBufferId >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                            inputBuffer.clear()
                            val read = fis.read(inBuf, 0, minOf(inBuf.size, inputBuffer.remaining()))
                            if (read > 0) {
                                inputBuffer.put(inBuf, 0, read)
                                presentationTimeUs = (totalRead * 1_000_000L) / bytesPerSecond
                                codec.queueInputBuffer(inputBufferId, 0, read, presentationTimeUs, 0)
                                totalRead += read
                                onProgress((totalRead.toFloat() / totalBytes).coerceIn(0f, 1f))
                            } else {
                                codec.queueInputBuffer(inputBufferId, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            }
                        }
                    }

                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        audioTrackIndex = muxer.addTrack(codec.outputFormat)
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
}
