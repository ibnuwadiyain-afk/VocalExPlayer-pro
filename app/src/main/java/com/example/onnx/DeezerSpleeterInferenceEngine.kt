package com.example.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.audio.DecodedAudio
import com.example.audio.WavWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class DeezerSpleeterInferenceEngine(private val context: Context) : SeparationEngine {

    companion object {
        private const val TAG = "DeezerSpleeter"
        const val VOCALS_MODEL_NAME = "spleeter_vocals.onnx"
        const val ACCOMPANIMENT_MODEL_NAME = "spleeter_accompaniment.onnx"

        // Deezer Spleeter 2stems STFT parameters
        const val N_FFT = 4096
        const val HOP_LENGTH = 1024
        const val DIM_F = 1024
        const val DIM_T = 512
        const val SAMPLE_RATE = 44100
    }

    override val engineType: SeparationEngineType = SeparationEngineType.DEEZER_SPLEETER

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val processor = SpleeterSpectrogramProcessor(
        nFft = N_FFT,
        hopLength = HOP_LENGTH,
        dimF = DIM_F,
        dimT = DIM_T
    )

    override var activeHardwareBackend: String = "Multi-Core CPU"
        private set

    /**
     * Extracts model file from assets to app internal files directory if not already extracted.
     */
    fun getOrExtractModel(modelFileName: String): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val destinationFile = File(modelsDir, modelFileName)
        if (!destinationFile.exists() || destinationFile.length() < 1_000_000L) {
            Log.i(TAG, "Extracting Deezer Spleeter model $modelFileName to ${destinationFile.absolutePath}...")
            context.assets.open("models/$modelFileName").use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Extracted $modelFileName successfully: ${destinationFile.length()} bytes")
        }
        return destinationFile
    }

    private fun createSession(modelFile: File, numThreads: Int): OrtSession {
        return try {
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                try {
                    addNnapi()
                    activeHardwareBackend = "NNAPI (NPU/GPU Accelerated)"
                    Log.i(TAG, "Hardware Acceleration enabled: NNAPI NPU/GPU")
                } catch (e: Throwable) {
                    activeHardwareBackend = "Multi-Core CPU ($numThreads threads)"
                    Log.i(TAG, "NNAPI not active, fallback to Multi-Core CPU: ${e.message}")
                }
            }
            ortEnvironment.createSession(modelFile.absolutePath, options)
        } catch (e: Throwable) {
            Log.w(TAG, "Accelerated session initialization failed (${e.message}), falling back to safe CPU mode")
            activeHardwareBackend = "Multi-Core CPU ($numThreads threads)"
            val safeOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }
            ortEnvironment.createSession(modelFile.absolutePath, safeOptions)
        }
    }

    override fun separate(
        decodedAudio: DecodedAudio,
        outputVocalFile: File,
        outputInstrumentalFile: File?,
        customModelPath: String?,
        accelerationMode: AccelerationMode,
        onProgress: (Float) -> Unit
    ): SeparationResult {
        val vocalModelFile = if (!customModelPath.isNullOrBlank() && File(customModelPath).exists()) {
            File(customModelPath)
        } else {
            getOrExtractModel(VOCALS_MODEL_NAME)
        }

        val accModelFile = getOrExtractModel(ACCOMPANIMENT_MODEL_NAME)

        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val numThreads = availableProcessors.coerceIn(4, 8)

        Log.i(TAG, "Initializing Spleeter sessions with $numThreads threads...")
        val vocalSession = createSession(vocalModelFile, numThreads)
        var accSession: OrtSession? = null
        try {
            if (accModelFile.exists()) {
                accSession = createSession(accModelFile, numThreads)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Accompaniment model session failed to load: ${e.message}, will use ratio complement")
        }

        try {
            val vocalInputName = vocalSession.inputNames.first()
            val accInputName = accSession?.inputNames?.first()

            val totalSamples = decodedAudio.totalFrames.toInt()
            if (totalSamples <= 0) {
                throw IllegalArgumentException("Audio track has 0 samples")
            }

            onProgress(0.05f)

            // Step 1: Compute STFT for Left and Right channels
            Log.i(TAG, "Computing 4096-point STFT on $totalSamples stereo samples...")
            val leftStft = processor.computeStft(decodedAudio.leftChannel)
            onProgress(0.12f)
            val rightStft = processor.computeStft(decodedAudio.rightChannel)
            onProgress(0.18f)

            val numFrames = leftStft.numFrames
            val paddedFrames = leftStft.paddedFrames
            val numSplits = paddedFrames / processor.dimT
            val numBins = processor.numBins

            Log.i(TAG, "Audio STFT completed: $numFrames frames ($numSplits chunks of ${processor.dimT} frames)")

            // Allocate buffers for separated complex frequency bins
            val vocalRealLeft = FloatArray(numFrames * numBins)
            val vocalImagLeft = FloatArray(numFrames * numBins)
            val vocalRealRight = FloatArray(numFrames * numBins)
            val vocalImagRight = FloatArray(numFrames * numBins)

            val accRealLeft = FloatArray(numFrames * numBins)
            val accImagLeft = FloatArray(numFrames * numBins)
            val accRealRight = FloatArray(numFrames * numBins)
            val accImagRight = FloatArray(numFrames * numBins)

            // Reusable direct buffer for input tensor: shape [2, 1, 512, 1024]
            val tensorElementCount = 2 * 1 * processor.dimT * processor.dimF
            val byteBuffer = ByteBuffer.allocateDirect(tensorElementCount * 4).order(ByteOrder.nativeOrder())
            val floatBuffer = byteBuffer.asFloatBuffer()
            val inputShape = longArrayOf(2L, 1L, processor.dimT.toLong(), processor.dimF.toLong())

            val splitDimElements = processor.dimT * processor.dimF

            for (split in 0 until numSplits) {
                floatBuffer.clear()

                // Channel 0: Left magnitude
                val leftSrcOffset = split * splitDimElements
                floatBuffer.put(leftStft.mag1024, leftSrcOffset, splitDimElements)

                // Channel 1: Right magnitude
                val rightSrcOffset = split * splitDimElements
                floatBuffer.put(rightStft.mag1024, rightSrcOffset, splitDimElements)

                floatBuffer.flip()

                val inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, inputShape)

                val vocalResult = vocalSession.run(mapOf(vocalInputName to inputTensor))
                val vocalOutTensor = vocalResult.get(0) as OnnxTensor
                val vocalOutBuffer = vocalOutTensor.floatBuffer

                val accResult = if (accSession != null && accInputName != null) {
                    accSession.run(mapOf(accInputName to inputTensor))
                } else null
                val accOutTensor = accResult?.get(0) as? OnnxTensor
                val accOutBuffer = accOutTensor?.floatBuffer

                // Apply Deezer Spleeter soft ratio mask
                for (channel in 0 until 2) {
                    val channelTensorOffset = channel * splitDimElements
                    for (t in 0 until processor.dimT) {
                        val globalFrame = split * processor.dimT + t
                        if (globalFrame >= numFrames) continue

                        val frameBinOffset = globalFrame * numBins
                        val magFrameOffset = channelTensorOffset + t * processor.dimF

                        for (k in 0 until processor.dimF) {
                            val vVal = vocalOutBuffer.get(magFrameOffset + k)
                            val aVal = accOutBuffer?.get(magFrameOffset + k) ?: (1f - vVal).coerceAtLeast(0f)

                            val v2 = vVal * vVal
                            val a2 = aVal * aVal
                            val sum = v2 + a2 + 1e-10f
                            val maskV = (v2 + 5e-11f) / sum
                            val maskA = (a2 + 5e-11f) / sum

                            if (channel == 0) {
                                val r = leftStft.real[frameBinOffset + k]
                                val im = leftStft.imag[frameBinOffset + k]
                                vocalRealLeft[frameBinOffset + k] = maskV * r
                                vocalImagLeft[frameBinOffset + k] = maskV * im
                                accRealLeft[frameBinOffset + k] = maskA * r
                                accImagLeft[frameBinOffset + k] = maskA * im
                            } else {
                                val r = rightStft.real[frameBinOffset + k]
                                val im = rightStft.imag[frameBinOffset + k]
                                vocalRealRight[frameBinOffset + k] = maskV * r
                                vocalImagRight[frameBinOffset + k] = maskV * im
                                accRealRight[frameBinOffset + k] = maskA * r
                                accImagRight[frameBinOffset + k] = maskA * im
                            }
                        }

                        // Frequencies above 11kHz (bins 1024..2048): Vocals = 0, Instrumental = original mix
                        for (k in processor.dimF until numBins) {
                            if (channel == 0) {
                                vocalRealLeft[frameBinOffset + k] = 0f
                                vocalImagLeft[frameBinOffset + k] = 0f
                                accRealLeft[frameBinOffset + k] = leftStft.real[frameBinOffset + k]
                                accImagLeft[frameBinOffset + k] = leftStft.imag[frameBinOffset + k]
                            } else {
                                vocalRealRight[frameBinOffset + k] = 0f
                                vocalImagRight[frameBinOffset + k] = 0f
                                accRealRight[frameBinOffset + k] = rightStft.real[frameBinOffset + k]
                                accImagRight[frameBinOffset + k] = rightStft.imag[frameBinOffset + k]
                            }
                        }
                    }
                }

                inputTensor.close()
                vocalResult.close()
                accResult?.close()

                val progress = 0.20f + 0.65f * ((split + 1).toFloat() / numSplits)
                onProgress(progress)
            }

            // Step 2: Inverse STFT reconstruction
            Log.i(TAG, "Reconstructing time-domain waveforms with 4096-point iSTFT...")
            onProgress(0.86f)
            val vocalWaveLeft = processor.computeIstft(vocalRealLeft, vocalImagLeft, numFrames, totalSamples)
            val vocalWaveRight = processor.computeIstft(vocalRealRight, vocalImagRight, numFrames, totalSamples)
            onProgress(0.92f)

            val accWaveLeft = processor.computeIstft(accRealLeft, accImagLeft, numFrames, totalSamples)
            val accWaveRight = processor.computeIstft(accRealRight, accImagRight, numFrames, totalSamples)
            onProgress(0.96f)

            // Step 3: Write Output WAV files
            Log.i(TAG, "Writing separated audio stems to WAV...")
            outputVocalFile.parentFile?.mkdirs()
            WavWriter.writeWavFile(
                leftChannel = vocalWaveLeft,
                rightChannel = vocalWaveRight,
                sampleRate = SAMPLE_RATE,
                outputFile = outputVocalFile
            )

            if (outputInstrumentalFile != null) {
                outputInstrumentalFile.parentFile?.mkdirs()
                WavWriter.writeWavFile(
                    leftChannel = accWaveLeft,
                    rightChannel = accWaveRight,
                    sampleRate = SAMPLE_RATE,
                    outputFile = outputInstrumentalFile
                )
            }

            onProgress(1.0f)
            Log.i(TAG, "Deezer Spleeter 2-stem separation completed successfully! Vocals: ${outputVocalFile.length()} bytes, Instrumental: ${outputInstrumentalFile?.length() ?: 0} bytes")
            return SeparationResult(
                vocalWavFile = outputVocalFile,
                instrumentalWavFile = outputInstrumentalFile
            )
        } finally {
            try { vocalSession.close() } catch (_: Exception) {}
            try { accSession?.close() } catch (_: Exception) {}
        }
    }
}
