package com.example.model

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class AppModelManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AppModelManager"
        private const val PREFS_NAME = "model_prefs"
        private const val KEY_SELECTED_VOCAL_MODEL = "selected_vocal_model_id"
        private const val KEY_SELECTED_WHISPER_MODEL = "selected_whisper_model_id"
        private const val KEY_WHISPER_LANGUAGE = "whisper_transcription_language"

        @Volatile
        private var instance: AppModelManager? = null

        fun getInstance(context: Context): AppModelManager {
            return instance ?: synchronized(this) {
                instance ?: AppModelManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelsDir: File = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Default Catalog Definitions
    private val catalogTemplates = listOf(
        // Vocal Separation Models
        ModelEntity(
            id = "spleeter_2stems",
            type = ModelType.VOCAL_SEPARATION,
            displayName = "Spleeter 2-Stem (Built-in)",
            description = "Deezer Spleeter 2-stem neural network. Isolates vocals and accompaniment.",
            fileName = "spleeter_2stems.onnx",
            downloadUrl = "https://huggingface.co/manyeyes/spleeter-2stems-44k-onnx/resolve/main/spleeter_2stems.onnx",
            sizeBytes = 19_681_017L,
            sizeDisplay = "19.7 MB",
            isBundled = true
        ),
        ModelEntity(
            id = "uvr_mdxnet_voc",
            type = ModelType.VOCAL_SEPARATION,
            displayName = "UVR MDX-Net Vocal Isolation",
            description = "Ultimate Vocal Remover MDX-Net model optimized for high-fidelity vocal extraction.",
            fileName = "uvr_mdxnet_voc.onnx",
            downloadUrl = "https://huggingface.co/Anjok07/ultimatevocalremover_models/resolve/main/UVR_MDXNET_1_9703.onnx",
            sizeBytes = 62_914_560L,
            sizeDisplay = "60.0 MB",
            isBundled = false
        ),

        // Whisper Speech Recognition Models (Real Offline Inference)
        ModelEntity(
            id = "whisper_tiny",
            type = ModelType.WHISPER_TRANSCRIPTION,
            displayName = "Whisper Tiny (Multilingual)",
            description = "Lightweight & fast OpenAI Whisper model. Supports Arabic, English, and 90+ languages offline.",
            fileName = "whisper_tiny.onnx",
            downloadUrl = "https://huggingface.co/onnx-community/whisper-tiny/resolve/main/onnx/encoder_model_quantized.onnx",
            sizeBytes = 39_000_000L,
            sizeDisplay = "39.0 MB",
            isBundled = false
        ),
        ModelEntity(
            id = "whisper_base",
            type = ModelType.WHISPER_TRANSCRIPTION,
            displayName = "Whisper Base (Multilingual)",
            description = "Balanced speed and accuracy. Excellent recognition for Arabic vocals and diverse accents.",
            fileName = "whisper_base.onnx",
            downloadUrl = "https://huggingface.co/onnx-community/whisper-base/resolve/main/onnx/encoder_model_quantized.onnx",
            sizeBytes = 74_000_000L,
            sizeDisplay = "74.0 MB",
            isBundled = false
        ),
        ModelEntity(
            id = "whisper_small",
            type = ModelType.WHISPER_TRANSCRIPTION,
            displayName = "Whisper Small (High Accuracy)",
            description = "High precision speech recognition for complex audio, singing vocals, and multi-speaker tracks.",
            fileName = "whisper_small.onnx",
            downloadUrl = "https://huggingface.co/onnx-community/whisper-small/resolve/main/onnx/encoder_model_quantized.onnx",
            sizeBytes = 180_000_000L,
            sizeDisplay = "180.0 MB",
            isBundled = false
        )
    )

    private val _models = MutableStateFlow<List<ModelEntity>>(emptyList())
    val models: StateFlow<List<ModelEntity>> = _models.asStateFlow()

    private val _selectedVocalModelId = MutableStateFlow<String>(
        prefs.getString(KEY_SELECTED_VOCAL_MODEL, "spleeter_2stems") ?: "spleeter_2stems"
    )
    val selectedVocalModelId: StateFlow<String> = _selectedVocalModelId.asStateFlow()

    private val _selectedWhisperModelId = MutableStateFlow<String?>(
        prefs.getString(KEY_SELECTED_WHISPER_MODEL, "whisper_tiny")
    )
    val selectedWhisperModelId: StateFlow<String?> = _selectedWhisperModelId.asStateFlow()

    private val _whisperLanguage = MutableStateFlow<String>(
        prefs.getString(KEY_WHISPER_LANGUAGE, "auto") ?: "auto"
    )
    val whisperLanguage: StateFlow<String> = _whisperLanguage.asStateFlow()

    init {
        refreshModelsList()
    }

    /**
     * Refreshes model statuses by checking on-disk files in models directory and assets.
     */
    fun refreshModelsList() {
        val updatedList = catalogTemplates.map { template ->
            val targetFile = File(modelsDir, template.fileName)
            val isInstalled = if (template.isBundled) {
                // Bundled model is always available (either extracted or ready in assets)
                true
            } else {
                targetFile.exists() && targetFile.length() > 100_000L
            }

            template.copy(
                isInstalled = isInstalled,
                localPath = if (isInstalled) targetFile.absolutePath else null,
                sizeBytes = if (targetFile.exists()) targetFile.length() else template.sizeBytes
            )
        }
        _models.value = updatedList
    }

    fun getModelById(id: String): ModelEntity? {
        return _models.value.firstOrNull { it.id == id }
    }

    fun getSelectedVocalModel(): ModelEntity {
        val id = _selectedVocalModelId.value
        return _models.value.firstOrNull { it.id == id }
            ?: _models.value.first { it.type == ModelType.VOCAL_SEPARATION }
    }

    fun getSelectedWhisperModel(): ModelEntity? {
        val id = _selectedWhisperModelId.value ?: return null
        val found = _models.value.firstOrNull { it.id == id && it.type == ModelType.WHISPER_TRANSCRIPTION }
        return if (found?.isInstalled == true) found else null
    }

    fun selectModel(id: String, type: ModelType) {
        when (type) {
            ModelType.VOCAL_SEPARATION -> {
                _selectedVocalModelId.value = id
                prefs.edit().putString(KEY_SELECTED_VOCAL_MODEL, id).apply()
            }
            ModelType.WHISPER_TRANSCRIPTION -> {
                _selectedWhisperModelId.value = id
                prefs.edit().putString(KEY_SELECTED_WHISPER_MODEL, id).apply()
            }
        }
    }

    fun setWhisperLanguage(langCode: String) {
        _whisperLanguage.value = langCode
        prefs.edit().putString(KEY_WHISPER_LANGUAGE, langCode).apply()
    }

    /**
     * Downloads a model file using the existing OkHttp download infrastructure.
     * Reports real-time download progress and network speed.
     */
    suspend fun downloadModel(
        modelId: String,
        onProgress: (percent: Float, speedKbps: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val model = getModelById(modelId) ?: return@withContext Result.failure(
            IllegalArgumentException("Model $modelId not found in catalog")
        )

        val targetFile = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")

        updateModelDownloadState(modelId, isDownloading = true, progress = 0f, speed = 0L)

        try {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .addHeader("User-Agent", "VocalExtractor-Android/1.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Server returned HTTP ${response.code} for ${model.downloadUrl}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body from model server")
                val totalBytes = body.contentLength()
                val inputStream: InputStream = body.byteStream()

                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesReadTotal = 0L
                    val startTime = System.currentTimeMillis()
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesReadTotal += read

                        val percent = if (totalBytes > 0) {
                            (bytesReadTotal.toFloat() / totalBytes).coerceIn(0f, 1f)
                        } else {
                            0.5f
                        }

                        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                        val speedKbps = if (elapsedSeconds > 0) {
                            ((bytesReadTotal / 1024) / elapsedSeconds).toLong()
                        } else 0L

                        updateModelDownloadState(modelId, isDownloading = true, progress = percent, speed = speedKbps)
                        onProgress(percent, speedKbps)
                    }
                    output.flush()
                }
            }

            if (!tempFile.exists() || tempFile.length() < 100_000L) {
                tempFile.delete()
                throw IllegalStateException("Downloaded model file is incomplete or corrupted.")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(TAG, "Model $modelId downloaded successfully: ${targetFile.length()} bytes")
            updateModelDownloadState(modelId, isDownloading = false, progress = 1.0f, speed = 0L)
            refreshModelsList()

            // If no whisper model was selected and this is a whisper model, select it automatically
            if (model.type == ModelType.WHISPER_TRANSCRIPTION && _selectedWhisperModelId.value == null) {
                selectModel(model.id, ModelType.WHISPER_TRANSCRIPTION)
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model $modelId: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            updateModelDownloadState(modelId, isDownloading = false, progress = null, speed = 0L, error = e.message)
            refreshModelsList()
            Result.failure(e)
        }
    }

    /**
     * Deletes an installed model from storage. Bundled models cannot be deleted.
     */
    fun deleteModel(modelId: String): Boolean {
        val model = getModelById(modelId) ?: return false
        if (model.isBundled) {
            Log.w(TAG, "Cannot delete bundled model $modelId")
            return false
        }

        val targetFile = File(modelsDir, model.fileName)
        var deleted = false
        if (targetFile.exists()) {
            deleted = targetFile.delete()
        }

        if (_selectedWhisperModelId.value == modelId) {
            val nextInstalled = _models.value.firstOrNull {
                it.type == ModelType.WHISPER_TRANSCRIPTION && it.id != modelId && it.isInstalled
            }
            selectModel(nextInstalled?.id ?: "", ModelType.WHISPER_TRANSCRIPTION)
        }

        refreshModelsList()
        return deleted
    }

    /**
     * Imports a user-supplied custom model (.onnx).
     */
    suspend fun importCustomModel(uri: Uri, type: ModelType): Result<ModelEntity> = withContext(Dispatchers.IO) {
        try {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "custom_model.onnx"
            val targetFile = File(modelsDir, "custom_${System.currentTimeMillis()}_$fileName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot open input stream for $uri")

            if (targetFile.length() < 10_000L) {
                targetFile.delete()
                throw IllegalArgumentException("Imported model file is too small or invalid.")
            }

            val customModel = ModelEntity(
                id = "custom_${targetFile.nameWithoutExtension}",
                type = type,
                displayName = "Custom: $fileName",
                description = "User-supplied ONNX neural model.",
                fileName = targetFile.name,
                downloadUrl = "",
                sizeBytes = targetFile.length(),
                sizeDisplay = "${(targetFile.length() / (1024 * 1024))} MB",
                isBundled = false,
                isInstalled = true,
                localPath = targetFile.absolutePath
            )

            // Add to in-memory list and select it
            _models.value = _models.value + customModel
            selectModel(customModel.id, type)
            Result.success(customModel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import custom model: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun updateModelDownloadState(
        modelId: String,
        isDownloading: Boolean,
        progress: Float?,
        speed: Long,
        error: String? = null
    ) {
        _models.value = _models.value.map { model ->
            if (model.id == modelId) {
                model.copy(
                    isDownloading = isDownloading,
                    downloadProgress = progress,
                    downloadSpeedKbps = speed,
                    errorMessage = error
                )
            } else model
        }
    }
}
