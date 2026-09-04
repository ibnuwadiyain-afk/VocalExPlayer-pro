package com.example.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import com.example.audio.DecodedAudio
import com.example.audio.WavWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

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
     * Uses sample-accurate STFT -> ONNX Neural Network -> iSTFT with overlap-add windowing.
     */
    fun separateVocals(
        decodedAudio: DecodedAudio,
        outputWavFile: File,
        customModelPath: String? = null,
        onProgress: (Float) -> Unit = {}
    ): File {
        val modelFile = getOrExtractModelFile(customModelPath)
        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }

        Log.i(TAG, "Opening ONNX Session for model: ${modelFile.name} (${modelFile.length() / (1024 * 1024)} MB)")
        val session = ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)

        try {
            val inputNames = session.inputNames
            val inputName = inputNames.iterator().next()
            val outputNames = session.outputNames
            val outputName = outputNames.iterator().next()

            val inputInfo = session.inputInfo[inputName]?.info
            val inputShape = if (inputInfo is TensorInfo) inputInfo.shape else longArrayOf()
            Log.i(TAG, "ONNX Model Inputs: $inputNames (shape: ${inputShape.contentToString()}), Outputs: $outputNames")

            val leftIn = decodedAudio.leftChannel
            val rightIn = decodedAudio.rightChannel
            val totalSamples = minOf(leftIn.size, rightIn.size)

            if (totalSamples == 0) {
                throw IllegalArgumentException("Audio track has 0 samples")
            }

            // Check if model is MDX-Net 4-channel STFT model:
            // e.g., shape ends with [..., 4, 2048, 256] or matches dimF / dimT
            val isMdxModel = inputShape.size >= 4 && (inputShape[1] == 4L || inputShape[inputShape.size - 3] == 4L)
                || (inputShape.isEmpty() || inputShape.size == 4)

            if (isMdxModel) {
                runMdxSpectrogramPipeline(
                    session = session,
                    inputName = inputName,
                    outputName = outputName,
                    leftIn = leftIn,
                    rightIn = rightIn,
                    sampleRate = decodedAudio.sampleRate,
                    outputWavFile = outputWavFile,
                    onProgress = onProgress
                )
            } else {
                // Waveform-based ONNX model
                runWaveformPipeline(
                    session = session,
                    inputName = inputName,
                    outputName = outputName,
                    leftIn = leftIn,
                    rightIn = rightIn,
                    sampleRate = decodedAudio.sampleRate,
                    outputWavFile = outputWavFile,
                    onProgress = onProgress
                )
            }

            return outputWavFile
        } finally {
            session.close()
            sessionOptions.close()
        }
    }

    /**
     * Executes the UVR MDX-Net STFT -> Neural ONNX -> iSTFT separation pipeline.
     */
    private fun runMdxSpectrogramPipeline(
        session: OrtSession,
        inputName: String,
        outputName: String,
        leftIn: FloatArray,
        rightIn: FloatArray,
        sampleRate: Int,
        outputWavFile: File,
        onProgress: (Float) -> Unit
    ) {
        val totalSamples = minOf(leftIn.size, rightIn.size)
        val trim = processor.trim // 3072
        val genSize = processor.genSize // 254976
        val chunkSize = processor.chunkSize // 261120

        // Pad signal with trim zeros on left and right, aligned to genSize
        val pad = genSize - (totalSamples % genSize)
        val paddedLen = trim + totalSamples + pad + trim

        val leftPadded = FloatArray(paddedLen)
        val rightPadded = FloatArray(paddedLen)

        System.arraycopy(leftIn, 0, leftPadded, trim, totalSamples)
        System.arraycopy(rightIn, 0, rightPadded, trim, totalSamples)

        val totalChunks = (totalSamples + pad) / genSize
        Log.i(TAG, "Running UVR MDX-Net: $totalSamples samples, $totalChunks chunks (genSize=$genSize, chunkSize=$chunkSize)")

        val outFinalLeft = FloatArray(totalSamples)
        val outFinalRight = FloatArray(totalSamples)

        // Preallocate buffers for chunk processing
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

        var chunkIdx = 0
        var samplePos = 0

        while (samplePos < totalSamples + pad) {
            val progressStart = 0.25f + (chunkIdx.toFloat() / totalChunks) * 0.70f
            onProgress(progressStart)

            // 1. Copy chunk waveform
            java.util.Arrays.fill(chunkL, 0f)
            java.util.Arrays.fill(chunkR, 0f)
            val copyLen = minOf(chunkSize, paddedLen - samplePos)
            System.arraycopy(leftPadded, samplePos, chunkL, 0, copyLen)
            System.arraycopy(rightPadded, samplePos, chunkR, 0, copyLen)

            // 2. Compute STFT for Left and Right channels
            processor.computeChannelStft(chunkL, specReL, specImL)
            processor.computeChannelStft(chunkR, specReR, specImR)

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

                    // 5. Compute Inverse STFT
                    java.util.Arrays.fill(istftWaveL, 0f)
                    java.util.Arrays.fill(istftWaveR, 0f)
                    java.util.Arrays.fill(istftWeightL, 0f)
                    java.util.Arrays.fill(istftWeightR, 0f)

                    processor.computeChannelIstft(specReL, specImL, istftWaveL, istftWeightL, COMPENSATION)
                    processor.computeChannelIstft(specReR, specImR, istftWaveR, istftWeightR, COMPENSATION)

                    // 6. Normalize interior window weight and extract trim .. (trim + genSize)
                    for (i in 0 until genSize) {
                        val srcIdx = trim + i
                        val targetIdx = samplePos + i
                        if (targetIdx < totalSamples) {
                            val wL = istftWeightL[srcIdx]
                            val wR = istftWeightR[srcIdx]
                            outFinalLeft[targetIdx] = if (wL > 1e-4f) (istftWaveL[srcIdx] / wL).coerceIn(-1.0f, 1.0f) else istftWaveL[srcIdx]
                            outFinalRight[targetIdx] = if (wR > 1e-4f) (istftWaveR[srcIdx] / wR).coerceIn(-1.0f, 1.0f) else istftWaveR[srcIdx]
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing ONNX chunk inference at chunk $chunkIdx", e)
            } finally {
                inputTensor.close()
                ortResult?.close()
            }

            samplePos += genSize
            chunkIdx++
        }

        onProgress(0.97f)
        Log.i(TAG, "Writing separated vocal WAV: ${outputWavFile.absolutePath} ($totalSamples samples at $sampleRate Hz)")
        WavWriter.writeWavFile(outFinalLeft, outFinalRight, sampleRate, outputWavFile)
        onProgress(1.0f)
    }

    /**
     * Fallback processor for raw waveform-based ONNX models (e.g. 2-channel audio chunks).
     */
    private fun runWaveformPipeline(
        session: OrtSession,
        inputName: String,
        outputName: String,
        leftIn: FloatArray,
        rightIn: FloatArray,
        sampleRate: Int,
        outputWavFile: File,
        onProgress: (Float) -> Unit
    ) {
        val totalSamples = minOf(leftIn.size, rightIn.size)
        val chunkSize = 44100
        val hopSize = chunkSize / 2
        val hanning = FloatArray(chunkSize) { i ->
            (0.5f * (1.0f - kotlin.math.cos(2.0 * kotlin.math.PI * i / (chunkSize - 1)))).toFloat()
        }

        val outLeftAcc = FloatArray(totalSamples + chunkSize)
        val outRightAcc = FloatArray(totalSamples + chunkSize)
        val weightAcc = FloatArray(totalSamples + chunkSize)

        val inputFloatBuffer = FloatBuffer.allocate(2 * chunkSize)
        val tensorShape = longArrayOf(1, 2, chunkSize.toLong())

        var sampleOffset = 0
        val totalSteps = (totalSamples + hopSize - 1) / hopSize
        var stepIndex = 0

        while (sampleOffset < totalSamples) {
            inputFloatBuffer.clear()
            for (i in 0 until chunkSize) {
                val idx = sampleOffset + i
                inputFloatBuffer.put(if (idx < totalSamples) leftIn[idx] else 0f)
            }
            for (i in 0 until chunkSize) {
                val idx = sampleOffset + i
                inputFloatBuffer.put(if (idx < totalSamples) rightIn[idx] else 0f)
            }
            inputFloatBuffer.flip()

            val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputFloatBuffer, tensorShape)
            var ortResult: OrtSession.Result? = null

            try {
                ortResult = session.run(mapOf(inputName to inputTensor))
                val resultValue = ortResult.get(outputName).orElse(ortResult.get(0))
                if (resultValue is OnnxTensor) {
                    val outBuffer = resultValue.floatBuffer
                    val outL = FloatArray(chunkSize)
                    val outR = FloatArray(chunkSize)
                    outBuffer.position(0)
                    outBuffer.get(outL, 0, chunkSize)
                    outBuffer.get(outR, 0, chunkSize)

                    for (i in 0 until chunkSize) {
                        val targetIdx = sampleOffset + i
                        val w = hanning[i]
                        outLeftAcc[targetIdx] += outL[i] * w
                        outRightAcc[targetIdx] += outR[i] * w
                        weightAcc[targetIdx] += w
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in waveform chunk inference", e)
            } finally {
                inputTensor.close()
                ortResult?.close()
            }

            sampleOffset += hopSize
            stepIndex++
            onProgress(0.25f + (stepIndex.toFloat() / totalSteps) * 0.70f)
        }

        val finalLeft = FloatArray(totalSamples)
        val finalRight = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            val w = weightAcc[i]
            if (w > 1e-4f) {
                finalLeft[i] = (outLeftAcc[i] / w).coerceIn(-1.0f, 1.0f)
                finalRight[i] = (outRightAcc[i] / w).coerceIn(-1.0f, 1.0f)
            } else {
                finalLeft[i] = leftIn[i]
                finalRight[i] = rightIn[i]
            }
        }

        WavWriter.writeWavFile(finalLeft, finalRight, sampleRate, outputWavFile)
        onProgress(1.0f)
    }
}
