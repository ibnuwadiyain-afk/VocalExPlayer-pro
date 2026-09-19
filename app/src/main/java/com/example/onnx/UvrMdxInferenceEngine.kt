package com.example.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import com.example.audio.DecodedAudio
import com.example.audio.SpectralVocalFilter
import com.example.audio.VocalCacheManager
import com.example.audio.WavWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

class UvrMdxInferenceEngine(private val context: Context) {

    companion object {
        private const val TAG = "UvrMdxInference"
        const val DEFAULT_MODEL_NAME = "UVR_MDXNET_1_9703.onnx"

        // Default MDX-Net hyperparameters
        const val N_FFT = 6144
        const val DIM_F = 2048
        const val DIM_T = 256
        const val HOP_LENGTH = 1024
        const val COMPENSATION = 1.035f // Official UVR compensation for UVR-MDX-NET 1
    }

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val processor = MdxSpectrogramProcessor(
        nFft = N_FFT,
        dimF = DIM_F,
        dimT = DIM_T,
        hopLength = HOP_LENGTH
    )

    var activeHardwareBackend: String = "Multi-Core CPU"
        private set

    /**
     * Ensures the UVR MDX-Net ONNX model is extracted from assets to internal storage.
     */
    fun getOrExtractModelFile(customPath: String? = null): File {
        if (!customPath.isNullOrBlank()) {
            val custom = File(customPath)
            if (custom.exists() && custom.length() > 10_000L) {
                Log.i(TAG, "Using custom user-supplied UVR ONNX model at: ${custom.absolutePath} (${custom.length() / (1024 * 1024)} MB)")
                return custom
            }
        }

        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val destinationFile = File(modelsDir, DEFAULT_MODEL_NAME)
        // Verify destination exists and has expected size (> 20MB)
        if (!destinationFile.exists() || destinationFile.length() < 20_000_000L) {
            try {
                Log.i(TAG, "Extracting bundled UVR MDX-Net 1 ONNX model to ${destinationFile.absolutePath}...")
                context.assets.open("models/$DEFAULT_MODEL_NAME").use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Extracted bundled UVR ONNX model successfully: ${destinationFile.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled asset model", e)
                throw IllegalStateException("Failed to load UVR MDX-Net ONNX model from assets: ${e.message}", e)
            }
        }
        return destinationFile
    }

    /**
     * Executes neural AI source separation using the UVR MDX-Net ONNX model.
     * Uses sample-accurate STFT -> ONNX Neural Network -> iSTFT with zero-allocation buffers.
     * Supports Live Streaming Sessions (VocalCacheManager.LiveVocalSession) for progressive chunk playback.
     */
    fun separateVocals(
        decodedAudio: DecodedAudio,
        outputWavFile: File,
        customModelPath: String? = null,
        accelerationMode: AccelerationMode = AccelerationMode.TURBO,
        liveSession: VocalCacheManager.LiveVocalSession? = null,
        onProgress: (Float) -> Unit = {}
    ): File {
        val modelFile = try {
            getOrExtractModelFile(customModelPath)
        } catch (e: Throwable) {
            Log.w(TAG, "ONNX model acquisition failed: ${e.message}. Launching Fallback DSP Engine.")
            runFallbackDspPipeline(decodedAudio, outputWavFile, liveSession, onProgress)
            return outputWavFile
        }

        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val numThreads = availableProcessors.coerceIn(4, 8)

        // Create ONNX session with fallback to safe CPU mode if NNAPI or graph transforms fail
        val session = try {
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setInterOpNumThreads(2)
                // Use BASIC_OPT to avoid experimental ConvInteger quantization rewrite nodes missing in mobile ONNX
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)

                // Try hardware acceleration (NNAPI)
                try {
                    addNnapi()
                    activeHardwareBackend = "NNAPI (NPU/GPU Accelerated)"
                    Log.i(TAG, "Hardware Acceleration enabled: NNAPI NPU/GPU")
                } catch (e: Throwable) {
                    activeHardwareBackend = "Multi-Core CPU ($numThreads threads)"
                    Log.i(TAG, "NNAPI not active, fallback to optimized Multi-Core CPU ($numThreads threads): ${e.message}")
                }
            }
            Log.i(TAG, "Opening ONNX Session for model: ${modelFile.name} [Mode: ${accelerationMode.displayName}, Backend: $activeHardwareBackend]")
            ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
        } catch (e: Throwable) {
            Log.w(TAG, "Accelerated session initialization failed (${e.message}), attempting safe CPU mode")
            try {
                activeHardwareBackend = "Multi-Core CPU ($numThreads threads)"
                val safeOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(numThreads)
                    setInterOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                ortEnvironment.createSession(modelFile.absolutePath, safeOptions)
            } catch (e2: Throwable) {
                Log.w(TAG, "ONNX runtime session creation completely failed: ${e2.message}. Using Fallback DSP Engine.")
                runFallbackDspPipeline(decodedAudio, outputWavFile, liveSession, onProgress)
                return outputWavFile
            }
        }

        try {
            val inputNames = session.inputNames
            val inputName = inputNames.iterator().next()
            val outputNames = session.outputNames
            val outputName = outputNames.iterator().next()

            val inputInfo = session.inputInfo[inputName]?.info
            val inputShape = if (inputInfo is TensorInfo) inputInfo.shape else longArrayOf()
            Log.i(TAG, "ONNX Model Inputs: $inputNames (shape: ${inputShape.contentToString()}), Outputs: $outputNames")

            val totalSamples = decodedAudio.totalFrames
            if (totalSamples <= 0L) {
                throw IllegalArgumentException("Audio track has 0 samples")
            }

            // Check if model is MDX-Net 4-channel STFT model
            val isMdxModel = inputShape.size >= 4 && (inputShape[1] == 4L || inputShape[inputShape.size - 3] == 4L)
                || (inputShape.isEmpty() || inputShape.size == 4)

            if (isMdxModel) {
                runMdxSpectrogramPipeline(
                    session = session,
                    inputName = inputName,
                    outputName = outputName,
                    decodedAudio = decodedAudio,
                    outputWavFile = outputWavFile,
                    accelerationMode = accelerationMode,
                    liveSession = liveSession,
                    onProgress = onProgress
                )
            } else {
                runWaveformPipeline(
                    session = session,
                    inputName = inputName,
                    outputName = outputName,
                    decodedAudio = decodedAudio,
                    outputWavFile = outputWavFile,
                    liveSession = liveSession,
                    onProgress = onProgress
                )
            }

            return outputWavFile
        } finally {
            session.close()
        }
    }

    /**
     * Executes the UVR MDX-Net STFT -> Neural ONNX -> iSTFT separation pipeline.
     * Streams input chunks directly from disk and streams output samples directly to the WAV file.
     * Memory usage is strictly O(1) (< 15MB total heap), completely eliminating Dalvik heap OOM
     * on arbitrarily long videos.
     */
    private fun runMdxSpectrogramPipeline(
        session: OrtSession,
        inputName: String,
        outputName: String,
        decodedAudio: DecodedAudio,
        outputWavFile: File,
        accelerationMode: AccelerationMode,
        liveSession: VocalCacheManager.LiveVocalSession? = null,
        onProgress: (Float) -> Unit
    ) {
        val totalSamples = decodedAudio.totalFrames
        val sampleRate = decodedAudio.sampleRate
        val trim = processor.trim // 3072
        val genSize = processor.genSize // 254976
        val chunkSize = processor.chunkSize // 261120

        // Pad signal alignment to genSize
        val remainder = (totalSamples % genSize).toInt()
        val pad = if (remainder == 0) 0L else (genSize - remainder).toLong()

        val totalChunks = ((totalSamples + pad) / genSize).toInt()
        Log.i(TAG, "Running UVR MDX-Net [${accelerationMode.displayName}]: $totalSamples samples, $totalChunks chunks (genSize=$genSize)")

        // Preallocate reusable scratchpads and buffers for chunk processing (strictly O(1) heap)
        val scratchL = processor.createScratchpad()
        val scratchR = processor.createScratchpad()

        val chunkL = FloatArray(chunkSize + processor.nFft)
        val chunkR = FloatArray(chunkSize + processor.nFft)

        val specReL = Array(DIM_F) { FloatArray(DIM_T) }
        val specImL = Array(DIM_F) { FloatArray(DIM_T) }
        val specReR = Array(DIM_F) { FloatArray(DIM_T) }
        val specImR = Array(DIM_F) { FloatArray(DIM_T) }

        val tensorCapacity = 1 * 4 * DIM_F * DIM_T
        val inputFloatBuffer = ByteBuffer.allocateDirect(tensorCapacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val tensorShape = longArrayOf(1, 4, DIM_F.toLong(), DIM_T.toLong())

        val istftWaveL = FloatArray(chunkSize + processor.nFft)
        val istftWaveR = FloatArray(chunkSize + processor.nFft)
        val istftWeightL = FloatArray(chunkSize + processor.nFft)
        val istftWeightR = FloatArray(chunkSize + processor.nFft)

        val outChunkL = FloatArray(genSize)
        val outChunkR = FloatArray(genSize)

        var chunkIdx = 0
        var samplePos = 0L
        var skippedChunks = 0

        val localWriter = if (liveSession == null) WavWriter.createStreamingWriter(outputWavFile, sampleRate, 2) else null
        try {
            while (samplePos < totalSamples + pad) {
                val progressStart = 0.25f + (chunkIdx.toFloat() / totalChunks.coerceAtLeast(1)) * 0.70f
                onProgress(progressStart)

                val validCount = minOf(genSize.toLong(), totalSamples - samplePos).coerceAtLeast(0L).toInt()

                // 1. Copy chunk waveform directly from source audio without allocating padded buffers
                java.util.Arrays.fill(chunkL, 0f)
                java.util.Arrays.fill(chunkR, 0f)

                val audioStart = samplePos - trim
                val srcStart = maxOf(0L, audioStart)
                val srcEnd = minOf(totalSamples, audioStart + chunkSize)
                val activeLen = if (srcEnd > srcStart) (srcEnd - srcStart).toInt() else 0
                val destStart = (srcStart - audioStart).toInt()

                if (activeLen > 0) {
                    decodedAudio.readStereoChunk(
                        startFrame = srcStart,
                        count = activeLen,
                        destL = chunkL,
                        destR = chunkR,
                        destOffset = destStart
                    )
                }

                // Turbo Optimization: Check if chunk is silence/background pause
                if (accelerationMode.silenceSkipThreshold > 0f) {
                    var sumSq = 0.0
                    val step = 8
                    var sampleCount = 0
                    var i = 0
                    while (i < activeLen) {
                        val sL = chunkL[i]
                        val sR = chunkR[i]
                        sumSq += (sL * sL + sR * sR)
                        sampleCount += 2
                        i += step
                    }
                    val rms = if (sampleCount > 0) sqrt(sumSq / sampleCount).toFloat() else 0f
                    if (rms < accelerationMode.silenceSkipThreshold) {
                        // Silence chunk: bypass heavy neural inference and iSTFT, write silence directly
                        skippedChunks++
                        if (validCount > 0) {
                            if (liveSession != null) {
                                liveSession.appendSilence(validCount)
                            } else {
                                localWriter?.writeSilence(validCount)
                            }
                        }
                        samplePos += genSize
                        chunkIdx++
                        continue
                    }
                }

                // 2. Compute STFT for Left and Right channels with zero-allocation scratchpads
                processor.computeChannelStft(chunkL, specReL, specImL, scratchL)
                processor.computeChannelStft(chunkR, specReR, specImR, scratchR)

                // 3. Pack into ONNX FloatBuffer
                processor.packToFloatBuffer(specReL, specImL, specReR, specImR, inputFloatBuffer)

                // 4. Run ONNX neural network
                val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputFloatBuffer, tensorShape)
                var ortResult: OrtSession.Result? = null

                try {
                    ortResult = session.run(mapOf(inputName to inputTensor))
                    val resultValue = ortResult.get(outputName).orElse(ortResult.get(0))

                    if (resultValue is OnnxTensor) {
                        val outBuffer = resultValue.floatBuffer
                        outBuffer.position(0)

                        // Read out 4 channels: outReL, outImL, outReR, outImR
                        for (f in 0 until DIM_F) {
                            outBuffer.get(specReL[f], 0, DIM_T)
                        }
                        for (f in 0 until DIM_F) {
                            outBuffer.get(specImL[f], 0, DIM_T)
                        }
                        for (f in 0 until DIM_F) {
                            outBuffer.get(specReR[f], 0, DIM_T)
                        }
                        for (f in 0 until DIM_F) {
                            outBuffer.get(specImR[f], 0, DIM_T)
                        }

                        // 5. Compute Inverse STFT with zero-allocation scratchpads
                        java.util.Arrays.fill(istftWaveL, 0f)
                        java.util.Arrays.fill(istftWaveR, 0f)
                        java.util.Arrays.fill(istftWeightL, 0f)
                        java.util.Arrays.fill(istftWeightR, 0f)

                        processor.computeChannelIstft(specReL, specImL, istftWaveL, istftWeightL, COMPENSATION, scratchL)
                        processor.computeChannelIstft(specReR, specImR, istftWaveR, istftWeightR, COMPENSATION, scratchR)

                        // 6. Normalize interior window weight and stream directly to disk
                        for (k in 0 until validCount) {
                            val srcIdx = trim + k
                            val wL = istftWeightL[srcIdx]
                            val wR = istftWeightR[srcIdx]
                            outChunkL[k] = if (wL > 1e-4f) (istftWaveL[srcIdx] / wL).coerceIn(-1.0f, 1.0f) else istftWaveL[srcIdx]
                            outChunkR[k] = if (wR > 1e-4f) (istftWaveR[srcIdx] / wR).coerceIn(-1.0f, 1.0f) else istftWaveR[srcIdx]
                        }
                        if (liveSession != null) {
                            liveSession.appendChunk(outChunkL, outChunkR, 0, validCount)
                        } else {
                            localWriter?.writeStereoChunk(outChunkL, outChunkR, 0, validCount)
                        }
                    } else {
                        // Fallback silence if tensor type unexpected
                        if (validCount > 0) {
                            if (liveSession != null) {
                                liveSession.appendSilence(validCount)
                            } else {
                                localWriter?.writeSilence(validCount)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing ONNX chunk inference at chunk $chunkIdx", e)
                    if (validCount > 0) {
                        if (liveSession != null) {
                            liveSession.appendSilence(validCount)
                        } else {
                            localWriter?.writeSilence(validCount)
                        }
                    }
                } finally {
                    inputTensor.close()
                    ortResult?.close()
                }

                samplePos += genSize
                chunkIdx++
            }
        } finally {
            localWriter?.close()
        }

        if (skippedChunks > 0) {
            Log.i(TAG, "Turbo Mode: Fast-skipped $skippedChunks silent chunks (${skippedChunks * 5.78f}s of compute saved)")
        }

        onProgress(0.97f)
        Log.i(TAG, "Finished separated vocal WAV: ${outputWavFile.absolutePath} ($totalSamples samples at $sampleRate Hz)")
        System.gc()
        onProgress(1.0f)
    }

    /**
     * Fallback processor for raw waveform-based ONNX models.
     */
    private fun runWaveformPipeline(
        session: OrtSession,
        inputName: String,
        outputName: String,
        decodedAudio: DecodedAudio,
        outputWavFile: File,
        liveSession: VocalCacheManager.LiveVocalSession? = null,
        onProgress: (Float) -> Unit
    ) {
        val totalSamples = decodedAudio.totalFrames
        val sampleRate = decodedAudio.sampleRate
        val chunkSize = 44100 * 5 // 5 second chunks

        val totalChunks = ((totalSamples + chunkSize - 1) / chunkSize).toInt()
        var chunkIdx = 0
        var samplePos = 0L

        val chunkAudio = FloatArray(chunkSize * 2)
        val floatBuffer = ByteBuffer.allocateDirect(chunkSize * 2 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val tempL = FloatArray(chunkSize)
        val tempR = FloatArray(chunkSize)
        val outL = FloatArray(chunkSize)
        val outR = FloatArray(chunkSize)

        val localWriter = if (liveSession == null) WavWriter.createStreamingWriter(outputWavFile, sampleRate, 2) else null
        try {
            while (samplePos < totalSamples) {
                val progressStart = 0.25f + (chunkIdx.toFloat() / totalChunks.coerceAtLeast(1)) * 0.70f
                onProgress(progressStart)

                val currentChunkLen = minOf(chunkSize.toLong(), totalSamples - samplePos).toInt()
                java.util.Arrays.fill(tempL, 0f)
                java.util.Arrays.fill(tempR, 0f)
                decodedAudio.readStereoChunk(samplePos, currentChunkLen, tempL, tempR, 0)

                floatBuffer.clear()
                for (i in 0 until currentChunkLen) {
                    floatBuffer.put(tempL[i])
                    floatBuffer.put(tempR[i])
                }
                for (i in currentChunkLen until chunkSize) {
                    floatBuffer.put(0f)
                    floatBuffer.put(0f)
                }
                floatBuffer.flip()

                val tensorShape = longArrayOf(1, 2, chunkSize.toLong())
                val inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, tensorShape)
                var ortResult: OrtSession.Result? = null

                try {
                    ortResult = session.run(mapOf(inputName to inputTensor))
                    val resultValue = ortResult.get(outputName).orElse(ortResult.get(0))
                    if (resultValue is OnnxTensor) {
                        val outBuf = resultValue.floatBuffer
                        outBuf.position(0)
                        for (i in 0 until currentChunkLen) {
                            outL[i] = if (outBuf.hasRemaining()) outBuf.get().coerceIn(-1.0f, 1.0f) else 0f
                            outR[i] = if (outBuf.hasRemaining()) outBuf.get().coerceIn(-1.0f, 1.0f) else 0f
                        }
                        if (liveSession != null) {
                            liveSession.appendChunk(outL, outR, 0, currentChunkLen)
                        } else {
                            localWriter?.writeStereoChunk(outL, outR, 0, currentChunkLen)
                        }
                    } else {
                        if (liveSession != null) {
                            liveSession.appendSilence(currentChunkLen)
                        } else {
                            localWriter?.writeSilence(currentChunkLen)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in waveform model inference at chunk $chunkIdx", e)
                    if (liveSession != null) {
                        liveSession.appendSilence(currentChunkLen)
                    } else {
                        localWriter?.writeSilence(currentChunkLen)
                    }
                } finally {
                    inputTensor.close()
                    ortResult?.close()
                }

                samplePos += currentChunkLen
                chunkIdx++
            }
        } finally {
            localWriter?.close()
        }

        onProgress(0.97f)
        onProgress(1.0f)
    }

    /**
     * Fallback Engine: Multi-band Harmonic STFT Spectral Filter (FFT size 2048, hop size 512, formants weighted 220Hz-3.5kHz).
     */
    private fun runFallbackDspPipeline(
        decodedAudio: DecodedAudio,
        outputWavFile: File,
        liveSession: VocalCacheManager.LiveVocalSession? = null,
        onProgress: (Float) -> Unit
    ) {
        Log.i(TAG, "Running Fallback Engine: Multi-band Harmonic STFT Spectral Filter (FFT 2048, formants 220Hz-3.5kHz)")
        val sampleRate = decodedAudio.sampleRate
        val totalSamples = decodedAudio.totalFrames
        val chunkSize = 4096
        val filter = SpectralVocalFilter(sampleRate)

        val inL = FloatArray(chunkSize)
        val inR = FloatArray(chunkSize)
        val outL = FloatArray(chunkSize)
        val outR = FloatArray(chunkSize)

        val localWriter = if (liveSession == null) WavWriter.createStreamingWriter(outputWavFile, sampleRate, 2) else null
        var pos = 0L
        try {
            while (pos < totalSamples) {
                val count = minOf(chunkSize.toLong(), totalSamples - pos).toInt()
                decodedAudio.readStereoChunk(pos, count, inL, inR, 0)
                filter.processChunkStft(inL, inR, outL, outR, count, isolateVocals = true)

                if (liveSession != null) {
                    liveSession.appendChunk(outL, outR, 0, count)
                } else {
                    localWriter?.writeStereoChunk(outL, outR, 0, count)
                }
                pos += count
                onProgress(0.25f + (pos.toFloat() / totalSamples.coerceAtLeast(1L)) * 0.70f)
            }
        } finally {
            localWriter?.close()
        }
        onProgress(1.0f)
    }
}
