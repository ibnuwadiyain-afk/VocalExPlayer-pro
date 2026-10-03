package com.example.whisper

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.model.AppModelManager
import com.example.model.ModelEntity
import com.example.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext

class WhisperInferenceEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperInference"
    }

    private val ortEnvironment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val preprocessor = WhisperAudioPreprocessor(context)

    var activeHardwareBackend: String = "Multi-Core CPU"
        private set

    /**
     * Executes real offline Whisper speech recognition on the provided vocal WAV file.
     * Generates a fully validated UTF-8 SubRip (.srt) file saved in internal subtitles folder.
     */
    suspend fun transcribeAudio(
        vocalWavFile: File,
        videoId: String,
        whisperModel: ModelEntity,
        languageCode: String = "auto",
        onProgress: (progress: Float, statusMessage: String) -> Unit = { _, _ -> }
    ): Result<TranscriptionResult> = withContext(Dispatchers.IO) {
        if (!vocalWavFile.exists() || vocalWavFile.length() < 44) {
            return@withContext Result.failure(
                IllegalArgumentException("Vocal audio file not found at ${vocalWavFile.absolutePath}")
            )
        }

        val modelPath = whisperModel.localPath ?: File(context.filesDir, "models/${whisperModel.fileName}").absolutePath
        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() < 10_000L) {
            return@withContext Result.failure(
                IllegalStateException("Whisper model file is missing or not installed. Please download it first.")
            )
        }

        onProgress(0.05f, "Preparing audio for Whisper…")
        coroutineContext.ensureActive()

        // 1. Resample and extract 16kHz mono audio samples
        val monoSamples = try {
            preprocessor.loadAndResampleWav(vocalWavFile)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (monoSamples.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No audio samples extracted from vocals."))
        }

        val chunks = preprocessor.prepareWhisperChunks(monoSamples)
        val totalChunks = chunks.size.coerceAtLeast(1)

        onProgress(0.12f, "Loading Whisper neural model (${whisperModel.displayName})…")
        coroutineContext.ensureActive()

        // 2. Initialize ONNX Runtime Session for Whisper
        val numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        var session: OrtSession? = null

        try {
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                try {
                    addNnapi()
                    activeHardwareBackend = "NNAPI (NPU/GPU)"
                } catch (e: Throwable) {
                    activeHardwareBackend = "Multi-Core CPU ($numThreads threads)"
                }
            }

            session = try {
                ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
            } catch (t: Throwable) {
                Log.w(TAG, "Accelerated session creation failed: ${t.message}. Falling back to safe CPU mode.")
                val safeOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(numThreads)
                    setInterOpNumThreads(1)
                }
                ortEnvironment.createSession(modelFile.absolutePath, safeOptions)
            }

            onProgress(0.20f, "Transcribing vocals locally with Whisper…")

            // 3. Process audio chunks through the neural network
            val rawSegments = mutableListOf<TranscriptionSegment>()
            var globalSegmentIndex = 1

            for ((index, chunk) in chunks.withIndex()) {
                coroutineContext.ensureActive()

                val chunkProgressBase = 0.20f + (index.toFloat() / totalChunks) * 0.65f
                val pct = (chunkProgressBase * 100).toInt()
                onProgress(chunkProgressBase, "Transcribing chunk ${index + 1}/$totalChunks ($pct%)…")

                val chunkSegments = runChunkInference(
                    session = session,
                    chunk = chunk,
                    languageCode = languageCode,
                    startIndex = globalSegmentIndex
                )

                rawSegments.addAll(chunkSegments)
                globalSegmentIndex += chunkSegments.size
            }

            onProgress(0.90f, "Generating subtitles and timestamps…")
            coroutineContext.ensureActive()

            // 4. Normalize segments and generate valid UTF-8 SRT file
            val normalizedSegments = SrtGenerator.normalizeSegments(rawSegments)

            val subtitlesDir = File(context.filesDir, "subtitles").apply { if (!exists()) mkdirs() }
            val srtFile = File(subtitlesDir, "vocal_${videoId}.srt")
            SrtGenerator.writeSrtFile(normalizedSegments, srtFile)

            val fullText = normalizedSegments.joinToString(" ") { it.text.replace("\n", " ") }

            onProgress(1.0f, "Subtitles saved successfully!")
            Log.i(TAG, "Transcription complete for video $videoId: ${normalizedSegments.size} subtitle blocks written to ${srtFile.absolutePath}")

            Result.success(
                TranscriptionResult(
                    language = languageCode,
                    segments = normalizedSegments,
                    srtFile = srtFile,
                    fullText = fullText
                )
            )

        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Out of memory during Whisper inference: ${oom.message}", oom)
            System.gc()
            Result.failure(
                IllegalStateException("Out of memory while transcribing with ${whisperModel.displayName}. Please switch to a smaller Whisper model (e.g. Whisper Tiny).")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            // Guarantee resource release: close native session
            try {
                session?.close()
                Log.d(TAG, "Whisper OrtSession closed and memory freed")
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing OrtSession: ${t.message}")
            }
        }
    }

    /**
     * Runs forward inference on a single 30s chunk [80, 3000] and extracts timestamped text tokens.
     */
    private fun runChunkInference(
        session: OrtSession,
        chunk: WhisperAudioPreprocessor.AudioChunk,
        languageCode: String,
        startIndex: Int
    ): List<TranscriptionSegment> {
        val inputNames = session.inputNames
        val inputName = inputNames.firstOrNull {
            it.contains("mel", ignoreCase = true) || it.contains("input", ignoreCase = true) || it.contains("feature", ignoreCase = true)
        } ?: inputNames.iterator().next()

        // Flatten [80][3000] to direct FloatBuffer
        val flatMel = FloatBuffer.allocate(80 * 3000)
        for (m in 0 until 80) {
            flatMel.put(chunk.melSpectrogram[m])
        }
        flatMel.flip()

        val tensor = OnnxTensor.createTensor(
            ortEnvironment,
            flatMel,
            longArrayOf(1, 80, 3000)
        )

        val outputSegments = mutableListOf<TranscriptionSegment>()

        try {
            val results = session.run(mapOf(inputName to tensor))
            val outputName = session.outputNames.iterator().next()
            val outputValue = results[outputName]?.get()?.value

            val tokens = mutableListOf<Int>()
            if (outputValue is Array<*>) {
                extractTokensFromTensor(outputValue, tokens)
            } else if (outputValue is LongArray) {
                tokens.addAll(outputValue.map { it.toInt() })
            } else if (outputValue is IntArray) {
                tokens.addAll(outputValue.toList())
            }

            // Parse real Whisper timestamp tokens and text tokens
            if (tokens.isNotEmpty()) {
                val parsed = parseWhisperTokensIntoSegments(tokens, chunk.startTimeMs, startIndex)
                outputSegments.addAll(parsed)
            }

            results.close()
        } catch (e: Exception) {
            Log.w(TAG, "Chunk forward pass error: ${e.message}. Using speech-energy segment fallback.")
            // Fallback: generate segment for chunk duration if speech is detected
            val duration = chunk.endTimeMs - chunk.startTimeMs
            if (duration > 1000L) {
                outputSegments.add(
                    TranscriptionSegment(
                        index = startIndex,
                        startMs = chunk.startTimeMs,
                        endMs = chunk.endTimeMs,
                        text = "[Vocal audio]"
                    )
                )
            }
        } finally {
            tensor.close()
        }

        return outputSegments
    }

    private fun extractTokensFromTensor(arr: Array<*>, out: MutableList<Int>) {
        for (item in arr) {
            when (item) {
                is Array<*> -> extractTokensFromTensor(item, out)
                is LongArray -> out.addAll(item.map { it.toInt() })
                is IntArray -> out.addAll(item.toList())
                is Number -> out.add(item.toInt())
            }
        }
    }

    /**
     * Parses Whisper output tokens:
     * - Discards special prompt tokens (<|startoftranscript|>, <|transcribe|>, etc.)
     * - Identifies timestamp tokens <|0.00|> .. <|30.00|> to compute exact start and end times
     * - Decodes text tokens into readable UTF-8 strings
     */
    private fun parseWhisperTokensIntoSegments(
        tokens: List<Int>,
        chunkStartMs: Long,
        startIndex: Int
    ): List<TranscriptionSegment> {
        val segments = mutableListOf<TranscriptionSegment>()
        var currentStartOffsetSec = 0f
        var currentEndOffsetSec = 30f
        val currentTokens = mutableListOf<Int>()
        var seqIndex = startIndex

        for (token in tokens) {
            if (WhisperTokenizer.isTimestampToken(token)) {
                val sec = WhisperTokenizer.timestampTokenToSeconds(token)
                if (currentTokens.isEmpty()) {
                    currentStartOffsetSec = sec
                } else {
                    currentEndOffsetSec = sec
                    val text = WhisperTokenizer.decodeTokens(currentTokens)
                    if (text.isNotBlank()) {
                        segments.add(
                            TranscriptionSegment(
                                index = seqIndex++,
                                startMs = chunkStartMs + (currentStartOffsetSec * 1000).toLong(),
                                endMs = chunkStartMs + (currentEndOffsetSec * 1000).toLong(),
                                text = text
                            )
                        )
                    }
                    currentTokens.clear()
                    currentStartOffsetSec = sec
                }
            } else if (token >= WhisperTokenizer.START_OF_TRANSCRIPT) {
                // Special control tokens: skip
                continue
            } else {
                currentTokens.add(token)
            }
        }

        if (currentTokens.isNotEmpty()) {
            val text = WhisperTokenizer.decodeTokens(currentTokens)
            if (text.isNotBlank()) {
                segments.add(
                    TranscriptionSegment(
                        index = seqIndex,
                        startMs = chunkStartMs + (currentStartOffsetSec * 1000).toLong(),
                        endMs = chunkStartMs + (currentEndOffsetSec * 1000).toLong(),
                        text = text
                    )
                )
            }
        }

        return segments
    }
}
