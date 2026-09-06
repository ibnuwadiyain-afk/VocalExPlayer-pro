package com.example.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

/**
 * Executes UVR MDX-Net model inference using ONNX Runtime.
 * Supports hardware acceleration (CPU, NNAPI, XNNPACK) and custom model loading.
 */
class UvrMdxInferenceEngine(private val context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "UvrMdxEngine"
        const val DEFAULT_MODEL_NAME = "UVR_MDXNET_1_9703.onnx"
        private const val ASSETS_MODEL_PATH = "models/$DEFAULT_MODEL_NAME"
    }

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null

    var currentModelFile: File? = null
        private set
    var currentAcceleration: AccelerationMode = AccelerationMode.AUTO
        private set

    /**
     * Initializes or switches the active ONNX model and acceleration backend.
     */
    fun initialize(customModelPath: String? = null, mode: AccelerationMode = AccelerationMode.AUTO) {
        val modelFile = if (!customModelPath.isNullOrBlank()) {
            val file = File(customModelPath)
            if (file.exists()) file else getOrExtractBundledModel()
        } else {
            getOrExtractBundledModel()
        }

        currentModelFile = modelFile
        currentAcceleration = mode

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

            when (mode) {
                AccelerationMode.NNAPI -> {
                    try {
                        addNnapi()
                        Log.i(TAG, "NNAPI acceleration enabled successfully")
                    } catch (e: Exception) {
                        Log.w(TAG, "NNAPI not available on this device, falling back to CPU: ${e.message}")
                    }
                }
                AccelerationMode.XNNPACK -> {
                    try {
                        addXnnpack(emptyMap())
                        Log.i(TAG, "XNNPACK acceleration enabled successfully")
                    } catch (e: Exception) {
                        Log.w(TAG, "XNNPACK failed: ${e.message}")
                    }
                }
                AccelerationMode.AUTO -> {
                    // Try NNAPI; if fails, standard optimized CPU is used
                    try {
                        addNnapi()
                        Log.i(TAG, "Auto selected NNAPI")
                    } catch (_: Exception) {
                        Log.i(TAG, "Auto selected multi-threaded CPU")
                    }
                }
                AccelerationMode.CPU -> {
                    Log.i(TAG, "Configured CPU execution")
                }
            }
        }

        try {
            ortSession?.close()
            ortSession = ortEnv.createSession(modelFile.absolutePath, sessionOptions)
            Log.i(TAG, "OrtSession successfully created for ${modelFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session with selected mode, retrying plain CPU: ${e.message}")
            val fallbackOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            ortSession = ortEnv.createSession(modelFile.absolutePath, fallbackOptions)
        }
    }

    fun getSession(): OrtSession {
        if (ortSession == null) {
            initialize(null, currentAcceleration)
        }
        return ortSession ?: throw IllegalStateException("Failed to initialize OrtSession")
    }

    fun getEnvironment(): OrtEnvironment = ortEnv

    private fun getOrExtractBundledModel(): File {
        val internalFile = File(context.filesDir, "models/$DEFAULT_MODEL_NAME")
        if (internalFile.exists() && internalFile.length() > 1000000) {
            return internalFile
        }

        internalFile.parentFile?.mkdirs()
        try {
            context.assets.open(ASSETS_MODEL_PATH).use { input ->
                FileOutputStream(internalFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            Log.i(TAG, "Extracted bundled UVR model to ${internalFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract model from assets: ${e.message}")
        }
        return internalFile
    }

    override fun close() {
        try {
            ortSession?.close()
            ortEnv.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ONNX engine: ${e.message}")
        }
    }
}
