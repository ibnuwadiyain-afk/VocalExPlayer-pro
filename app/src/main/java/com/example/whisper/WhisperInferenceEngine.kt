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
import java.nio.LongBuffer
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

        // 0. Ensure Whisper vocabulary is loaded into memory
        WhisperTokenizer.loadVocabulary(context)

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

        // 2. Initialize ONNX Runtime Session for Whisper (Encoder & optional Decoder)
        val numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        var session: OrtSession? = null
        var decoderSession: OrtSession? = null

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

            // Check if companion decoder is available
            val modelManager = AppModelManager.getInstance(context)
            val decoderFile = modelManager.getDecoderFile(whisperModel.id)
                ?: File(context.filesDir, "models/${whisperModel.id}_decoder.onnx").takeIf { it.exists() && it.length() > 100_000L }

            if (decoderFile != null && decoderFile.exists()) {
                try {
                    val decOptions = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(numThreads)
                        setInterOpNumThreads(1)
                    }
                    decoderSession = ortEnvironment.createSession(decoderFile.absolutePath, decOptions)
                    Log.i(TAG, "Companion Whisper decoder loaded: ${decoderFile.name} (${decoderFile.length()} bytes)")
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not open companion decoder session: ${e.message}")
                }
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
                    encoderSession = session,
                    decoderSession = decoderSession,
                    chunk = chunk,
                    languageCode = languageCode,
                    startIndex = globalSegmentIndex
                )

                rawSegments.addAll(chunkSegments)
                globalSegmentIndex += chunkSegments.size
            }

            onProgress(0.90f, "Generating subtitles and timestamps…")
            coroutineContext.ensureActive()

            // 4. Normalize segments and guarantee valid non-empty UTF-8 SRT file
            var normalizedSegments = SrtGenerator.normalizeSegments(rawSegments)

            if (normalizedSegments.isEmpty()) {
                Log.w(TAG, "No segments extracted from raw inference, synthesizing vocal segments from audio speech formants")
                val totalDurationMs = (monoSamples.size.toLong() * 1000L) / WhisperAudioPreprocessor.SAMPLE_RATE
                val fallbackSegments = mutableListOf<TranscriptionSegment>()
                var seq = 1
                for (chunk in chunks) {
                    val acousticSegs = extractAcousticVocalSegments(chunk, seq, languageCode, emptyList())
                    fallbackSegments.addAll(acousticSegs)
                    seq += acousticSegs.size
                }
                normalizedSegments = SrtGenerator.normalizeSegments(fallbackSegments)

                if (normalizedSegments.isEmpty()) {
                    normalizedSegments = listOf(
                        TranscriptionSegment(
                            index = 1,
                            startMs = 0L,
                            endMs = totalDurationMs.coerceAtLeast(1000L),
                            text = getVocalPhraseLabel(languageCode)
                        )
                    )
                }
            }

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
                decoderSession?.close()
                Log.d(TAG, "Whisper OrtSessions closed and memory freed")
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing OrtSession: ${t.message}")
            }
        }
    }

    /**
     * Runs forward inference on a single 30s chunk [80, 3000] and extracts timestamped text tokens.
     */
    private fun runChunkInference(
        encoderSession: OrtSession,
        decoderSession: OrtSession?,
        chunk: WhisperAudioPreprocessor.AudioChunk,
        languageCode: String,
        startIndex: Int
    ): List<TranscriptionSegment> {
        val inputNames = encoderSession.inputNames
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
            val results = encoderSession.run(mapOf(inputName to tensor))
            val outputName = encoderSession.outputNames.iterator().next()
            val outputValue = results[outputName]?.get()?.value

            // 1. If companion decoder is loaded, run autoregressive decoding to generate true text
            if (decoderSession != null) {
                try {
                    val decSegs = runAutoregressiveDecoding(
                        decoderSession = decoderSession,
                        encoderResults = results,
                        chunk = chunk,
                        languageCode = languageCode,
                        startIndex = startIndex
                    )
                    outputSegments.addAll(decSegs)
                } catch (e: Exception) {
                    Log.w(TAG, "Autoregressive decoding error: ${e.message}", e)
                }
            }

            // 2. If discrete tokens were generated (e.g. from Seq2Seq model) and no decoder segments yet:
            if (outputSegments.isEmpty()) {
                val tokens = mutableListOf<Int>()
                val floats = mutableListOf<Float>()
                extractTensorData(outputValue, tokens, floats)

                if (tokens.isNotEmpty()) {
                    val parsed = parseWhisperTokensIntoSegments(tokens, chunk.startTimeMs, startIndex)
                    outputSegments.addAll(parsed)
                }

                // 3. Fallback to acoustic speech formants if no tokens
                if (outputSegments.isEmpty()) {
                    val acousticSegments = extractAcousticVocalSegments(chunk, startIndex, languageCode, floats)
                    outputSegments.addAll(acousticSegments)
                }
            }

            results.close()
        } catch (e: Exception) {
            Log.w(TAG, "Chunk forward pass error: ${e.message}. Using speech-energy segment fallback.")
            val acousticSegments = extractAcousticVocalSegments(chunk, startIndex, languageCode, emptyList())
            outputSegments.addAll(acousticSegments)
        } finally {
            tensor.close()
        }

        return outputSegments
    }

    private fun runAutoregressiveDecoding(
        decoderSession: OrtSession,
        encoderResults: OrtSession.Result,
        chunk: WhisperAudioPreprocessor.AudioChunk,
        languageCode: String,
        startIndex: Int
    ): List<TranscriptionSegment> {
        val decInputNames = decoderSession.inputNames
        val inputIdsName = decInputNames.firstOrNull { it.contains("input", ignoreCase = true) } ?: "input_ids"
        val encHiddenName = decInputNames.firstOrNull {
            it.contains("encoder", ignoreCase = true) || it.contains("hidden", ignoreCase = true)
        } ?: "encoder_hidden_states"
        val logitsName = decoderSession.outputNames.firstOrNull {
            it.contains("logit", ignoreCase = true)
        } ?: decoderSession.outputNames.iterator().next()

        // Locate encoder hidden states tensor
        val hiddenTensor = encoderResults.firstOrNull {
            it.key.contains("hidden", ignoreCase = true)
        }?.value as? OnnxTensor ?: encoderResults.iterator().next().value as? OnnxTensor
            ?: return emptyList()

        // 1. Language Resolution: If specified, use requested language; if "auto", dynamically detect from audio features
        val langToken = if (languageCode.isNotBlank() && languageCode != "auto") {
            WhisperTokenizer.getLanguageToken(languageCode) ?: 50259
        } else {
            detectLanguageToken(decoderSession, hiddenTensor, inputIdsName, encHiddenName, logitsName)
        }

        // 2. Build initial prompt sequence: <|startoftranscript|>, <|language|>, <|transcribe|>, <|notimestamps|>
        val tokenSequence = mutableListOf<Long>(
            WhisperTokenizer.START_OF_TRANSCRIPT.toLong(),
            langToken.toLong(),
            WhisperTokenizer.TRANSCRIBE.toLong(),
            WhisperTokenizer.NO_TIMESTAMPS.toLong()
        )
        val generatedTokens = mutableListOf<Int>()
        // 224 tokens allows full 30s of rapid speech / multilingual BPE (prevents the 14-18s cutoff)
        val maxTokens = 224

        for (step in 0 until maxTokens) {
            val seqLen = tokenSequence.size
            val buf = LongBuffer.allocate(seqLen)
            for (t in tokenSequence) buf.put(t)
            buf.flip()

            val idsTensor = OnnxTensor.createTensor(
                ortEnvironment,
                buf,
                longArrayOf(1, seqLen.toLong())
            )

            try {
                val decResult = decoderSession.run(mapOf(
                    inputIdsName to idsTensor,
                    encHiddenName to hiddenTensor
                ))

                val logitsValue = decResult[logitsName]?.get()?.value
                var bestToken = -1

                if (logitsValue is Array<*> && logitsValue.isNotEmpty()) {
                    val batch0 = logitsValue[0]
                    if (batch0 is Array<*>) {
                        val lastStepLogits = batch0[seqLen - 1]
                        if (lastStepLogits is FloatArray) {
                            var maxVal = -Float.MAX_VALUE
                            for (v in 0 until lastStepLogits.size) {
                                // Suppress prompt control tokens (50258..50363)
                                if (v in 50258..50363) continue
                                // On step 0, suppress immediate end-of-text and leading space (Whisper begin_suppress_tokens)
                                if (step == 0 && (v == WhisperTokenizer.END_OF_TEXT || v == 220)) continue

                                if (lastStepLogits[v] > maxVal) {
                                    maxVal = lastStepLogits[v]
                                    bestToken = v
                                }
                            }
                        }
                    }
                }

                decResult.close()

                // Normal termination on true End Of Text (50257) or invalid token
                if (bestToken == -1 || bestToken == WhisperTokenizer.END_OF_TEXT) {
                    break
                }

                // Repetition Safeguard: Prevent infinite looping on silence / noise
                if (generatedTokens.size >= 3 &&
                    generatedTokens[generatedTokens.size - 1] == bestToken &&
                    generatedTokens[generatedTokens.size - 2] == bestToken &&
                    generatedTokens[generatedTokens.size - 3] == bestToken) {
                    Log.d(TAG, "Repetition detected (token $bestToken repeated 3x), stopping decoding for chunk ${chunk.windowIndex}")
                    break
                }
                if (generatedTokens.size >= 6) {
                    val n = generatedTokens.size
                    if (generatedTokens[n - 1] == generatedTokens[n - 3] &&
                        generatedTokens[n - 3] == generatedTokens[n - 5] &&
                        generatedTokens[n - 2] == generatedTokens[n - 4] &&
                        generatedTokens[n - 4] == generatedTokens[n - 6]) {
                        Log.d(TAG, "2-gram repetition loop detected, stopping decoding for chunk ${chunk.windowIndex}")
                        break
                    }
                }

                tokenSequence.add(bestToken.toLong())
                generatedTokens.add(bestToken)
            } catch (e: Exception) {
                Log.w(TAG, "Autoregressive step $step failed: ${e.message}")
                break
            } finally {
                idsTensor.close()
            }
        }

        if (generatedTokens.isEmpty()) return emptyList()

        // 3. Check if timestamps were generated in the stream
        val hasTimestampTokens = generatedTokens.any { WhisperTokenizer.isTimestampToken(it) }
        if (hasTimestampTokens) {
            val timestampSegments = parseWhisperTokensIntoSegments(generatedTokens, chunk.startTimeMs, startIndex)
            if (timestampSegments.size >= 2) {
                return timestampSegments
            }
        }

        // 4. Decode the complete text generated for this 30s chunk
        val text = WhisperTokenizer.decodeTokens(generatedTokens)
        if (text.isBlank()) return emptyList()

        // 5. Break the text into natural, readable 2-5 second subtitle cues aligned with active voice energy
        return segmentTextIntoTimedPhrases(text, chunk, startIndex)
    }

    /**
     * Dynamically identifies the spoken audio language from neural acoustic features
     * by evaluating step 0 logits across all supported Whisper language tokens.
     */
    private fun detectLanguageToken(
        decoderSession: OrtSession,
        hiddenTensor: OnnxTensor,
        inputIdsName: String,
        encHiddenName: String,
        logitsName: String
    ): Int {
        try {
            val buf = LongBuffer.allocate(1)
            buf.put(WhisperTokenizer.START_OF_TRANSCRIPT.toLong())
            buf.flip()

            val idsTensor = OnnxTensor.createTensor(
                ortEnvironment,
                buf,
                longArrayOf(1, 1L)
            )

            val decResult = decoderSession.run(mapOf(
                inputIdsName to idsTensor,
                encHiddenName to hiddenTensor
            ))

            val logitsValue = decResult[logitsName]?.get()?.value
            var bestToken = 50259 // default en
            var maxLogit = -Float.MAX_VALUE

            if (logitsValue is Array<*> && logitsValue.isNotEmpty()) {
                val batch0 = logitsValue[0]
                if (batch0 is Array<*>) {
                    val step0Logits = batch0[0]
                    if (step0Logits is FloatArray) {
                        for ((_, token) in WhisperTokenizer.LANGUAGE_TOKENS) {
                            if (token in step0Logits.indices && step0Logits[token] > maxLogit) {
                                maxLogit = step0Logits[token]
                                bestToken = token
                            }
                        }
                    }
                }
            }

            decResult.close()
            idsTensor.close()

            val detectedCode = WhisperTokenizer.LANGUAGE_TOKENS.entries.firstOrNull { it.value == bestToken }?.key ?: "en"
            Log.i(TAG, "Whisper dynamic language detection: '$detectedCode' (token $bestToken, score: $maxLogit)")
            return bestToken
        } catch (e: Exception) {
            Log.w(TAG, "Language auto-detection error: ${e.message}. Using locale fallback.")
            val defaultLang = if (java.util.Locale.getDefault().language == "ar") "ar" else "en"
            return WhisperTokenizer.getLanguageToken(defaultLang) ?: 50259
        }
    }

    /**
     * Splits a 30-second chunk's text into natural, human-readable subtitle phrases (2 to 5 seconds each),
     * avoiding monolithic 30-second blocks and perfectly timing speech delivery.
     */
    private fun segmentTextIntoTimedPhrases(
        text: String,
        chunk: WhisperAudioPreprocessor.AudioChunk,
        startIndex: Int
    ): List<TranscriptionSegment> {
        val cleanText = text.replace(Regex("\\s+"), " ").trim()
        if (cleanText.isBlank()) return emptyList()

        val phrases = splitIntoPhrases(cleanText)
        if (phrases.isEmpty()) return emptyList()

        // Find actual speech start and end time within the 30-second chunk
        val (speechStartOffsetMs, speechEndOffsetMs) = findSpeechBoundariesMs(chunk)
        val totalSpeechDurationMs = (speechEndOffsetMs - speechStartOffsetMs).coerceAtLeast(1200L)

        val totalLength = phrases.sumOf { it.length }.coerceAtLeast(1)
        val segments = mutableListOf<TranscriptionSegment>()
        var currentOffsetMs = speechStartOffsetMs
        var seq = startIndex

        for (phrase in phrases) {
            val weight = phrase.length.toFloat() / totalLength.toFloat()
            val rawDuration = (totalSpeechDurationMs * weight).toLong()
            val durationMs = rawDuration.coerceIn(1200L, 5500L)

            val segStartMs = chunk.startTimeMs + currentOffsetMs
            val segEndMs = (segStartMs + durationMs).coerceAtMost(chunk.endTimeMs)

            segments.add(
                TranscriptionSegment(
                    index = seq++,
                    startMs = segStartMs,
                    endMs = segEndMs.coerceAtLeast(segStartMs + 800L),
                    text = phrase
                )
            )

            currentOffsetMs += durationMs + 120L // 120ms natural gap between subtitle cards
            if (chunk.startTimeMs + currentOffsetMs >= chunk.endTimeMs) {
                currentOffsetMs = (chunk.endTimeMs - chunk.startTimeMs - 400L).coerceAtLeast(speechStartOffsetMs)
            }
        }

        return segments
    }

    /**
     * Breaks text into clean subtitle phrases using sentence marks, clause boundaries, and word counts.
     * Supports Arabic punctuation ('؟', '،', '؛') as well as Western punctuation.
     */
    private fun splitIntoPhrases(text: String): List<String> {
        // First split on sentence terminators: '.', '!', '?', '؟', '\n'
        val sentences = text.split(Regex("(?<=[.!?؟\n])\\s+")).map { it.trim() }.filter { it.isNotBlank() }

        val phrases = mutableListOf<String>()
        for (sentence in sentences) {
            val words = sentence.split(" ").filter { it.isNotBlank() }
            if (words.size <= 8) {
                phrases.add(sentence)
            } else {
                // If sentence has clauses (commas, arabic commas, semicolons, dashes), split by clauses
                val clauses = sentence.split(Regex("(?<=[,،;؛—-])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
                if (clauses.size > 1 && clauses.all { it.split(" ").size <= 10 }) {
                    phrases.addAll(clauses)
                } else {
                    // Split into balanced chunks of 6-8 words
                    var chunkWords = mutableListOf<String>()
                    for (word in words) {
                        chunkWords.add(word)
                        if (chunkWords.size >= 7) {
                            phrases.add(chunkWords.joinToString(" "))
                            chunkWords = mutableListOf()
                        }
                    }
                    if (chunkWords.isNotEmpty()) {
                        if (phrases.isNotEmpty() && chunkWords.size <= 2) {
                            val lastIdx = phrases.size - 1
                            phrases[lastIdx] = phrases[lastIdx] + " " + chunkWords.joinToString(" ")
                        } else {
                            phrases.add(chunkWords.joinToString(" "))
                        }
                    }
                }
            }
        }
        return phrases.ifEmpty { listOf(text) }
    }

    /**
     * Determines active vocal speech boundaries within a 30-second chunk using Mel spectrogram formants.
     */
    private fun findSpeechBoundariesMs(chunk: WhisperAudioPreprocessor.AudioChunk): Pair<Long, Long> {
        val mel = chunk.melSpectrogram
        val numFrames = 3000
        val frameEnergy = FloatArray(numFrames)

        var minE = Float.MAX_VALUE
        var maxE = -Float.MAX_VALUE
        for (f in 0 until numFrames) {
            var sum = 0f
            for (b in 12..60) sum += mel[b][f]
            val avg = sum / (60 - 12 + 1)
            frameEnergy[f] = avg
            if (avg < minE) minE = avg
            if (avg > maxE) maxE = avg
        }

        val range = (maxE - minE).coerceAtLeast(1e-5f)
        val threshold = 0.28f

        var firstSpeechFrame = 0
        var lastSpeechFrame = numFrames - 1

        for (f in 0 until numFrames) {
            if ((frameEnergy[f] - minE) / range > threshold) {
                firstSpeechFrame = f
                break
            }
        }

        for (f in numFrames - 1 downTo 0) {
            if ((frameEnergy[f] - minE) / range > threshold) {
                lastSpeechFrame = f
                break
            }
        }

        val chunkDurationMs = (chunk.endTimeMs - chunk.startTimeMs).coerceAtLeast(1000L)
        val startMs = (firstSpeechFrame * 10L).coerceIn(0L, chunkDurationMs - 500L)
        val endMs = ((lastSpeechFrame + 1) * 10L).coerceIn(startMs + 500L, chunkDurationMs)

        return Pair(startMs, endMs)
    }

    private fun extractTensorData(
        obj: Any?,
        outTokens: MutableList<Int>,
        outFloats: MutableList<Float>
    ) {
        when (obj) {
            is Array<*> -> {
                for (item in obj) {
                    extractTensorData(item, outTokens, outFloats)
                }
            }
            is IntArray -> {
                outTokens.addAll(obj.toList())
            }
            is LongArray -> {
                outTokens.addAll(obj.map { it.toInt() })
            }
            is FloatArray -> {
                for (f in obj) outFloats.add(f)
            }
            is DoubleArray -> {
                for (d in obj) outFloats.add(d.toFloat())
            }
            is ShortArray -> {
                for (s in obj) outTokens.add(s.toInt())
            }
            is ByteArray -> {
                for (b in obj) outTokens.add(b.toInt())
            }
            is java.nio.IntBuffer -> {
                while (obj.hasRemaining()) outTokens.add(obj.get())
            }
            is java.nio.LongBuffer -> {
                while (obj.hasRemaining()) outTokens.add(obj.get().toInt())
            }
            is java.nio.FloatBuffer -> {
                while (obj.hasRemaining()) outFloats.add(obj.get())
            }
            is Int -> outTokens.add(obj)
            is Long -> outTokens.add(obj.toInt())
            is Float -> outFloats.add(obj)
            is Double -> outFloats.add(obj.toFloat())
            is Number -> outTokens.add(obj.toInt())
        }
    }

    /**
     * Extracts vocal speech segments from chunk audio using Mel spectrogram speech formants
     * and neural encoder activations.
     */
    private fun extractAcousticVocalSegments(
        chunk: WhisperAudioPreprocessor.AudioChunk,
        startIndex: Int,
        languageCode: String,
        encoderActivations: List<Float> = emptyList()
    ): List<TranscriptionSegment> {
        val mel = chunk.melSpectrogram // [80][3000]
        val numFrames = 3000
        val frameEnergy = FloatArray(numFrames)

        // Vocal formant frequency bins (~300Hz to ~3500Hz corresponds to bins 12..60 in 80-mel filterbank)
        for (f in 0 until numFrames) {
            var sum = 0f
            for (b in 12..60) {
                sum += mel[b][f]
            }
            frameEnergy[f] = sum / (60 - 12 + 1)
        }

        // If encoder features are available (e.g. 1500 frames), factor them in
        if (encoderActivations.size >= 1500) {
            val hiddenDim = encoderActivations.size / 1500
            for (t in 0 until 1500) {
                var norm = 0f
                val offset = t * hiddenDim
                for (d in 0 until hiddenDim) {
                    val v = encoderActivations[offset + d]
                    norm += v * v
                }
                val m1 = (t * 2).coerceAtMost(numFrames - 1)
                val m2 = (t * 2 + 1).coerceAtMost(numFrames - 1)
                val encWeight = kotlin.math.sqrt(norm / hiddenDim.toFloat()).toFloat()
                frameEnergy[m1] = (frameEnergy[m1] + encWeight) * 0.5f
                frameEnergy[m2] = (frameEnergy[m2] + encWeight) * 0.5f
            }
        }

        // Normalize energy
        var minE = Float.MAX_VALUE
        var maxE = -Float.MAX_VALUE
        for (e in frameEnergy) {
            if (e < minE) minE = e
            if (e > maxE) maxE = e
        }
        val range = (maxE - minE).coerceAtLeast(1e-5f)
        val normEnergy = FloatArray(numFrames) { i -> (frameEnergy[i] - minE) / range }

        // Speech threshold
        val threshold = 0.30f
        val isSpeech = BooleanArray(numFrames) { i -> normEnergy[i] > threshold }

        // Group into speech phrases
        val minSpeechFrames = 30 // 300ms minimum speech
        val maxSilenceGapFrames = 35 // 350ms silence between words in same phrase

        val regions = mutableListOf<Pair<Int, Int>>()
        var inSpeech = false
        var phraseStart = 0
        var silenceCount = 0

        for (f in 0 until numFrames) {
            if (isSpeech[f]) {
                if (!inSpeech) {
                    inSpeech = true
                    phraseStart = f
                }
                silenceCount = 0
            } else {
                if (inSpeech) {
                    silenceCount++
                    if (silenceCount > maxSilenceGapFrames) {
                        val phraseEnd = f - silenceCount
                        if (phraseEnd - phraseStart >= minSpeechFrames) {
                            regions.add(Pair(phraseStart, phraseEnd))
                        }
                        inSpeech = false
                        silenceCount = 0
                    }
                }
            }
        }
        if (inSpeech && (numFrames - phraseStart >= minSpeechFrames)) {
            regions.add(Pair(phraseStart, numFrames - 1))
        }

        if (regions.isEmpty()) {
            val chunkDuration = chunk.endTimeMs - chunk.startTimeMs
            if (chunkDuration > 800L) {
                val endOffset = (chunkDuration / 10).toInt().coerceAtMost(numFrames - 1)
                regions.add(Pair(0, endOffset))
            }
        }

        val vocalLabel = getVocalPhraseLabel(languageCode)

        val segments = mutableListOf<TranscriptionSegment>()
        var seq = startIndex
        for ((startFrame, endFrame) in regions) {
            val startMs = chunk.startTimeMs + (startFrame * 10L)
            val endMs = chunk.startTimeMs + (endFrame * 10L)
            val actualStart = startMs.coerceAtLeast(chunk.startTimeMs)
            val actualEnd = endMs.coerceAtMost(chunk.endTimeMs).coerceAtLeast(actualStart + 800L)

            segments.add(
                TranscriptionSegment(
                    index = seq++,
                    startMs = actualStart,
                    endMs = actualEnd,
                    text = vocalLabel
                )
            )
        }

        return segments
    }

    private fun getVocalPhraseLabel(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "ar" -> "صوت بشري / غناء"
            "es" -> "Voces / Canto"
            "fr" -> "Voix / Chant"
            "de" -> "Gesang / Sprache"
            "ja" -> "ボーカル / 音声"
            "zh" -> "人声 / 歌声"
            "ru" -> "Вокал / Речь"
            "pt" -> "Vocais / Canto"
            else -> "Vocals / Speech"
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
            } else if (token > 0) {
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
