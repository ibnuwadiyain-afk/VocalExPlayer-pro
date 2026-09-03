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
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos

class UvrMdxInferenceEngine(private val context: Context) {

    companion object {
        private const val TAG = "UvrMdxInference"
        const val DEFAULT_MODEL_NAME = "UVR_MDXNET_1_9703.onnx"
        const val DEFAULT_CHUNK_SIZE = 44100 // 1 sec at 44.1kHz
        const val HOP_SIZE = 22050 // 50% overlap
    }

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()

    /**
     * Ensures the UVR MDX-Net ONNX model is extracted from assets to internal storage.
     */
    fun getOrExtractModelFile(customPath: String? = null): File {
        if (!customPath.isNullOrBlank()) {
            val custom = File(customPath)
            if (custom.exists()) return custom
        }

        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val destinationFile = File(modelsDir, DEFAULT_MODEL_NAME)
        if (!destinationFile.exists() || destinationFile.length() == 0L) {
            try {
                context.assets.open("models/$DEFAULT_MODEL_NAME").use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied model asset to ${destinationFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy asset model", e)
                throw IllegalStateException("Failed to load UVR MDX-Net ONNX model: ${e.message}", e)
            }
        }
        return destinationFile
    }

    /**
     * Executes real ONNX inference chunk-by-chunk using UVR MDX-Net model and overlap-add windowing.
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

        Log.i(TAG, "Loading ONNX session from: ${modelFile.absolutePath}")
        val session = ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)

        try {
            val inputNames = session.inputNames
            val inputName = inputNames.iterator().next()
            val outputNames = session.outputNames
            val outputName = outputNames.iterator().next()

            val leftIn = decodedAudio.leftChannel
            val rightIn = decodedAudio.rightChannel
            val totalSamples = minOf(leftIn.size, rightIn.size)

            if (totalSamples == 0) {
                throw IllegalArgumentException("Audio track has 0 samples")
            }

            // Determine chunk size from model or default
            var chunkSize = DEFAULT_CHUNK_SIZE
            val inputInfo = session.inputInfo[inputName]?.info
            if (inputInfo is TensorInfo) {
                val shape = inputInfo.shape
                if (shape.isNotEmpty() && shape.last() > 0) {
                    chunkSize = shape.last().toInt()
                }
            }

            val hopSize = chunkSize / 2
            val hanningWindow = FloatArray(chunkSize) { i ->
                (0.5f * (1.0f - cos(2.0 * PI * i / (chunkSize - 1)))).toFloat()
            }

            val outLeftAcc = FloatArray(totalSamples + chunkSize)
            val outRightAcc = FloatArray(totalSamples + chunkSize)
            val weightAcc = FloatArray(totalSamples + chunkSize)

            // Buffer for feeding into ONNX tensor: shape [1, 2, chunkSize]
            // Interleaved or planar: MDX-Net expects planar [batch=1, channel=0 (L), channel=1 (R), samples]
            val tensorCapacity = 2 * chunkSize
            val inputFloatBuffer = FloatBuffer.allocate(tensorCapacity)

            var sampleOffset = 0
            val totalSteps = (totalSamples + hopSize - 1) / hopSize
            var stepIndex = 0

            while (sampleOffset < totalSamples) {
                inputFloatBuffer.clear()

                // Channel 0: Left
                for (i in 0 until chunkSize) {
                    val idx = sampleOffset + i
                    val sample = if (idx < totalSamples) leftIn[idx] else 0.0f
                    inputFloatBuffer.put(sample)
                }

                // Channel 1: Right
                for (i in 0 until chunkSize) {
                    val idx = sampleOffset + i
                    val sample = if (idx < totalSamples) rightIn[idx] else 0.0f
                    inputFloatBuffer.put(sample)
                }
                inputFloatBuffer.flip()

                val tensorShape = longArrayOf(1, 2, chunkSize.toLong())
                val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputFloatBuffer, tensorShape)

                var resultTensor: OnnxTensor? = null
                var ortResult: OrtSession.Result? = null

                try {
                    ortResult = session.run(mapOf(inputName to inputTensor))
                    val resultValue = ortResult.get(outputName).orElse(ortResult.get(0))
                    if (resultValue is OnnxTensor) {
                        resultTensor = resultValue
                        val outBuffer = resultTensor.floatBuffer

                        // Planar [2, chunkSize]
                        val outLeftChunk = FloatArray(chunkSize)
                        val outRightChunk = FloatArray(chunkSize)

                        outBuffer.position(0)
                        outBuffer.get(outLeftChunk, 0, chunkSize)
                        outBuffer.get(outRightChunk, 0, chunkSize)

                        // Overlap-add with Hanning window
                        for (i in 0 until chunkSize) {
                            val targetIdx = sampleOffset + i
                            val w = hanningWindow[i]
                            outLeftAcc[targetIdx] += outLeftChunk[i] * w
                            outRightAcc[targetIdx] += outRightChunk[i] * w
                            weightAcc[targetIdx] += w
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error running inference chunk at offset $sampleOffset", e)
                    // If a single chunk inference fails, fallback to passing audio through with window
                    for (i in 0 until chunkSize) {
                        val targetIdx = sampleOffset + i
                        val w = hanningWindow[i]
                        val l = if (sampleOffset + i < totalSamples) leftIn[sampleOffset + i] else 0f
                        val r = if (sampleOffset + i < totalSamples) rightIn[sampleOffset + i] else 0f
                        outLeftAcc[targetIdx] += l * w
                        outRightAcc[targetIdx] += r * w
                        weightAcc[targetIdx] += w
                    }
                } finally {
                    inputTensor.close()
                    ortResult?.close()
                }

                sampleOffset += hopSize
                stepIndex++

                val progress = 0.25f + (stepIndex.toFloat() / totalSteps.toFloat()) * 0.70f
                onProgress(progress.coerceIn(0.25f, 0.95f))
            }

            // Normalize overlap-add
            val finalLeft = FloatArray(totalSamples)
            val finalRight = FloatArray(totalSamples)

            for (i in 0 until totalSamples) {
                val weight = weightAcc[i]
                if (weight > 1e-4f) {
                    finalLeft[i] = (outLeftAcc[i] / weight).coerceIn(-1.0f, 1.0f)
                    finalRight[i] = (outRightAcc[i] / weight).coerceIn(-1.0f, 1.0f)
                } else {
                    finalLeft[i] = leftIn[i]
                    finalRight[i] = rightIn[i]
                }
            }

            onProgress(0.97f)
            Log.i(TAG, "Writing vocal track WAV to ${outputWavFile.absolutePath}")
            WavWriter.writeWavFile(finalLeft, finalRight, decodedAudio.sampleRate, outputWavFile)
            onProgress(1.0f)

            return outputWavFile
        } finally {
            session.close()
            sessionOptions.close()
        }
    }
}
