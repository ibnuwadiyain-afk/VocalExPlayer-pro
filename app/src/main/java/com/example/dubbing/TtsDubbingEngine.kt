package com.example.dubbing

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.audio.AudioExtractor
import com.example.audio.WavWriter
import com.example.whisper.TranscriptionSegment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object TtsDubbingEngine {

    private const val TAG = "TtsDubbingEngine"
    private const val TARGET_SAMPLE_RATE = 44100
    private const val UTTERANCE_TIMEOUT_MS = 20_000L

    /**
     * Initializes a TextToSpeech instance on the main thread and awaits readiness.
     */
    suspend fun createTts(context: Context): TextToSpeech? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<TextToSpeech?>()
        var ttsInstance: TextToSpeech? = null
        try {
            ttsInstance = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    deferred.complete(ttsInstance)
                } else {
                    Log.e(TAG, "TextToSpeech init failed with status: $status")
                    deferred.complete(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing TextToSpeech", e)
            deferred.complete(null)
        }
        deferred.await()
    }

    /**
     * Returns a list of languages available for TTS synthesis on this device.
     */
    fun getAvailableLocales(tts: TextToSpeech): List<Locale> {
        val commonLocales = listOf(
            Locale.ENGLISH,
            Locale("es", "ES"),
            Locale("ar", "SA"),
            Locale.FRENCH,
            Locale.GERMAN,
            Locale.JAPANESE,
            Locale.CHINESE,
            Locale("ru", "RU"),
            Locale("pt", "BR"),
            Locale.ITALIAN,
            Locale.KOREAN,
            Locale.getDefault()
        ).distinctBy { it.language }

        return try {
            val supported = tts.availableLanguages ?: emptySet()
            if (supported.isNotEmpty()) {
                val list = supported.filter { loc ->
                    try {
                        tts.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE
                    } catch (_: Exception) {
                        false
                    }
                }.sortedBy { it.displayName }
                if (list.isNotEmpty()) list else commonLocales
            } else {
                commonLocales
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching available languages from TTS", e)
            commonLocales
        }
    }

    /**
     * Previews synthesized speech through device speakers.
     */
    fun previewSpeech(
        tts: TextToSpeech,
        text: String,
        config: DubbingConfig
    ) {
        try {
            tts.stop()
            tts.language = config.locale
            tts.setSpeechRate(config.speechRate)
            tts.setPitch(config.pitch)
            val params = Bundle()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "preview_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error previewing speech", e)
        }
    }

    /**
     * Synthesizes all transcription segments into individual audio clips,
     * time-aligns them according to subtitle timestamps, and mixes them with
     * background/instrumental audio (with automatic ducking) into a master 44.1kHz stereo WAV.
     */
    suspend fun generateDubbedAudioTrack(
        context: Context,
        tts: TextToSpeech,
        segments: List<TranscriptionSegment>,
        totalDurationMs: Long,
        config: DubbingConfig,
        backgroundAudioFile: File? = null,
        outputWavFile: File,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "tts_segments_${System.currentTimeMillis()}")
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            onProgress(0.05f, "Configuring TTS Engine (${config.locale.displayLanguage})...")
            withContext(Dispatchers.Main) {
                tts.stop()
                try {
                    val langResult = tts.isLanguageAvailable(config.locale)
                    if (langResult >= TextToSpeech.LANG_AVAILABLE) {
                        tts.language = config.locale
                    } else {
                        Log.w(TAG, "Locale ${config.locale} not fully available; falling back to default")
                        tts.language = Locale.getDefault()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting language", e)
                }
                tts.setSpeechRate(config.speechRate)
                tts.setPitch(config.pitch)
            }

            // 1. Prepare segments with optional translation
            val segmentsToSynthesize: List<TranscriptionSegment> = if (config.enableTranslation) {
                if (!config.preTranslatedSegments.isNullOrEmpty()) {
                    onProgress(0.04f, "Using pre-translated subtitle cues...")
                    config.preTranslatedSegments
                } else {
                    onProgress(0.02f, "Translating transcript (${config.sourceLanguageCode} -> ${config.targetLanguageCode})...")
                    val transRes = com.example.dubbing.translation.DubbingTranslationManager.translateSegments(
                        context = context,
                        segments = segments,
                        sourceLang = config.sourceLanguageCode,
                        targetLang = config.targetLanguageCode,
                        service = config.translationService,
                        onProgress = { p, msg ->
                            onProgress(0.02f + (p * 0.08f), msg)
                        }
                    )
                    if (transRes.isSuccess) {
                        transRes.getOrThrow()
                    } else {
                        Log.w(TAG, "Translation failed; proceeding with original transcript", transRes.exceptionOrNull())
                        segments
                    }
                }
            } else {
                segments
            }

            // 2. Synthesize each subtitle segment to an individual WAV file
            val validSegments = segmentsToSynthesize.filter { it.text.isNotBlank() }
            if (validSegments.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No subtitle text available for dubbing"))
            }

            data class RenderedUtterance(
                val segment: TranscriptionSegment,
                val wavFile: File,
                val samplesLeft: FloatArray,
                val samplesRight: FloatArray
            )

            val renderedUtterances = mutableListOf<RenderedUtterance>()
            val totalSegCount = validSegments.size

            for ((i, seg) in validSegments.withIndex()) {
                val utteranceId = "dub_seg_${seg.index}_${System.currentTimeMillis()}"
                val segFile = File(tempDir, "seg_${seg.index}.wav")
                val progressVal = 0.05f + (i.toFloat() / totalSegCount) * 0.45f
                onProgress(progressVal, "Synthesizing dubbed cue ${i + 1}/$totalSegCount...")

                val synthSuccess = synthesizeSegmentToFile(tts, seg.text, segFile, utteranceId)
                if (synthSuccess && segFile.exists() && segFile.length() > 44) {
                    val decoded = readAndResampleWav(segFile, TARGET_SAMPLE_RATE)
                    if (decoded != null) {
                        renderedUtterances.add(
                            RenderedUtterance(
                                segment = seg,
                                wavFile = segFile,
                                samplesLeft = decoded.first,
                                samplesRight = decoded.second
                            )
                        )
                    }
                } else {
                    Log.w(TAG, "Skipping failed TTS segment #${seg.index}: '${seg.text}'")
                }
            }

            if (renderedUtterances.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("TextToSpeech failed to synthesize any subtitle segments"))
            }

            // 2. Timeline Assembly & Audio Mixing
            onProgress(0.55f, "Preparing audio timeline & background mixing...")

            val effectiveDurationMs = totalDurationMs.coerceAtLeast(
                renderedUtterances.maxOfOrNull { it.segment.endMs } ?: 5000L
            )
            val totalFrames = ((effectiveDurationMs * TARGET_SAMPLE_RATE) / 1000).toInt()

            // Optional background audio (e.g. Instrumental Karaoke track or original audio for ducking)
            var bgLeft: FloatArray? = null
            var bgRight: FloatArray? = null

            if (backgroundAudioFile != null && backgroundAudioFile.exists()) {
                onProgress(0.60f, "Loading background accompaniment track...")
                try {
                    val bgDecoded = AudioExtractor.readWavDirectly(context, Uri.fromFile(backgroundAudioFile))
                        ?: AudioExtractor.extractAudioFromVideo(context, Uri.fromFile(backgroundAudioFile))
                    if (bgDecoded != null) {
                        bgLeft = bgDecoded.leftChannel
                        bgRight = bgDecoded.rightChannel
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load background track, continuing without it", e)
                }
            }

            onProgress(0.65f, "Mixing dubbed voice with timeline...")

            // Allocate full stereo speech buffer for frame-accurate placement
            val masterSpeechLeft = FloatArray(totalFrames)
            val masterSpeechRight = FloatArray(totalFrames)
            val speechActiveMask = BooleanArray(totalFrames)

            for (item in renderedUtterances) {
                val startFrame = ((item.segment.startMs * TARGET_SAMPLE_RATE) / 1000).toInt().coerceIn(0, totalFrames - 1)
                val utteranceLen = item.samplesLeft.size

                for (s in 0 until utteranceLen) {
                    val frameIndex = startFrame + s
                    if (frameIndex >= totalFrames) break

                    // Overlap-add in case adjacent subtitle segments overlap slightly
                    masterSpeechLeft[frameIndex] = (masterSpeechLeft[frameIndex] + item.samplesLeft[s]).coerceIn(-1.0f, 1.0f)
                    masterSpeechRight[frameIndex] = (masterSpeechRight[frameIndex] + item.samplesRight[s]).coerceIn(-1.0f, 1.0f)
                    speechActiveMask[frameIndex] = true
                }
            }

            // 3. Render mixed frames with audio ducking to output WAV
            onProgress(0.75f, "Writing final synchronized 44.1kHz audio...")
            WavWriter.createStreamingWriter(outputWavFile, TARGET_SAMPLE_RATE, 2).use { writer ->
                val chunkSize = 4096
                val chunkL = FloatArray(chunkSize)
                val chunkR = FloatArray(chunkSize)
                var currentDuckingGain = 1.0f
                val minDuckingGain = config.backgroundDuckingVolume.coerceIn(0.05f, 1.0f)

                var frameOffset = 0
                while (frameOffset < totalFrames) {
                    val framesToWrite = minOf(chunkSize, totalFrames - frameOffset)

                    for (f in 0 until framesToWrite) {
                        val currentFrame = frameOffset + f
                        val isSpeaking = speechActiveMask[currentFrame]

                        // Smooth gain envelope (approx 100ms fade transition)
                        val targetGain = if (isSpeaking) minDuckingGain else 1.0f
                        currentDuckingGain += (targetGain - currentDuckingGain) * 0.02f

                        val speechL = masterSpeechLeft[currentFrame]
                        val speechR = masterSpeechRight[currentFrame]

                        val bgL = if (bgLeft != null && currentFrame < bgLeft.size) bgLeft[currentFrame] else 0f
                        val bgR = if (bgRight != null && currentFrame < bgRight.size) bgRight[currentFrame] else 0f

                        chunkL[f] = (speechL + bgL * currentDuckingGain).coerceIn(-1.0f, 1.0f)
                        chunkR[f] = (speechR + bgR * currentDuckingGain).coerceIn(-1.0f, 1.0f)
                    }

                    writer.writeStereoChunk(chunkL, chunkR, 0, framesToWrite)
                    frameOffset += framesToWrite

                    val progressFraction = 0.75f + (frameOffset.toFloat() / totalFrames) * 0.20f
                    onProgress(progressFraction, "Finalizing synchronized dubbed WAV...")
                }
            }

            onProgress(1.0f, "Dubbed audio track complete!")
            Result.success(outputWavFile)
        } catch (e: Exception) {
            Log.e(TAG, "Dubbing pipeline failed", e)
            Result.failure(e)
        } finally {
            try {
                tempDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    /**
     * Synthesizes text to a WAV file using TextToSpeech and suspends until completion.
     */
    private suspend fun synthesizeSegmentToFile(
        tts: TextToSpeech,
        text: String,
        outputFile: File,
        utteranceId: String
    ): Boolean {
        val completion = CompletableDeferred<Boolean>()

        withContext(Dispatchers.Main) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}

                override fun onDone(id: String) {
                    if (id == utteranceId) {
                        completion.complete(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String) {
                    if (id == utteranceId) {
                        Log.e(TAG, "TTS synthesize error for $id")
                        completion.complete(false)
                    }
                }

                override fun onError(id: String, errorCode: Int) {
                    if (id == utteranceId) {
                        Log.e(TAG, "TTS synthesize error for $id code: $errorCode")
                        completion.complete(false)
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val result = tts.synthesizeToFile(text, params, outputFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "tts.synthesizeToFile returned code: $result")
                completion.complete(false)
            }
        }

        return withTimeoutOrNull(UTTERANCE_TIMEOUT_MS) {
            completion.await()
        } ?: false
    }

    /**
     * Reads a standard 16-bit PCM WAV generated by TTS and resamples it to targetSampleRate (44100Hz stereo).
     */
    private fun readAndResampleWav(
        wavFile: File,
        targetSampleRate: Int
    ): Pair<FloatArray, FloatArray>? {
        if (!wavFile.exists() || wavFile.length() < 44) return null

        try {
            FileInputStream(wavFile).use { fis ->
                val header = ByteArray(44)
                if (fis.read(header) < 44) return null
                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

                val magic = String(header, 0, 4)
                val format = String(header, 8, 4)
                if (magic != "RIFF" || format != "WAVE") return null

                val numChannels = buf.getShort(22).toInt().coerceAtLeast(1)
                val sampleRate = buf.getInt(24).coerceAtLeast(8000)
                val bitsPerSample = buf.getShort(34).toInt()

                if (bitsPerSample != 16) {
                    Log.w(TAG, "Unsupported bits per sample from TTS: $bitsPerSample")
                    return null
                }

                // Read remaining PCM bytes
                val pcmBytes = fis.readBytes()
                val totalSamples = pcmBytes.size / 2
                val frames = totalSamples / numChannels
                if (frames <= 0) return null

                val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                val srcLeft = FloatArray(frames)
                val srcRight = FloatArray(frames)

                for (i in 0 until frames) {
                    val l = pcmBuffer.short.toFloat() / 32768f
                    srcLeft[i] = l
                    if (numChannels >= 2) {
                        srcRight[i] = pcmBuffer.short.toFloat() / 32768f
                    } else {
                        srcRight[i] = l // Duplicate mono to stereo
                    }
                }

                // If sample rate matches target, return directly
                if (sampleRate == targetSampleRate) {
                    return Pair(srcLeft, srcRight)
                }

                // High-quality linear resampling
                val targetFrames = ((frames.toLong() * targetSampleRate) / sampleRate).toInt()
                if (targetFrames <= 0) return null

                val dstLeft = FloatArray(targetFrames)
                val dstRight = FloatArray(targetFrames)

                val ratio = sampleRate.toDouble() / targetSampleRate.toDouble()
                for (i in 0 until targetFrames) {
                    val srcPos = i * ratio
                    val idx0 = srcPos.toInt().coerceIn(0, frames - 1)
                    val idx1 = (idx0 + 1).coerceIn(0, frames - 1)
                    val frac = (srcPos - idx0).toFloat()

                    dstLeft[i] = (1f - frac) * srcLeft[idx0] + frac * srcLeft[idx1]
                    dstRight[i] = (1f - frac) * srcRight[idx0] + frac * srcRight[idx1]
                }

                return Pair(dstLeft, dstRight)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading & resampling TTS WAV: ${wavFile.name}", e)
            return null
        }
    }
}
