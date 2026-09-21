package com.example.onnx

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.example.audio.AudioExtractor
import com.example.data.dao.VideoDao
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioSeparationPipeline(
    private val context: Context,
    private val videoDao: VideoDao
) {
    companion object {
        private const val TAG = "AudioSeparation"
        private const val WAKELOCK_TAG = "AudioSeparationPipeline:CPU"
    }

    val spleeterEngine = DeezerSpleeterInferenceEngine(context)
    val uvrEngine = UvrMdxInferenceEngine(context)

    val activeHardwareBackend: String
        get() = spleeterEngine.activeHardwareBackend

    /**
     * Runs the real separation pipeline for the given video with CPU WakeLock stability.
     * Supports genuine Deezer Spleeter (2-stem vocals + accompaniment) and UVR-MDX-Net.
     */
    suspend fun processVideo(
        video: VideoItemEntity,
        engineType: SeparationEngineType = SeparationEngineType.DEEZER_SPLEETER,
        customModelPath: String? = null,
        accelerationMode: AccelerationMode = AccelerationMode.TURBO,
        forceRecompute: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        var localWakeLock: PowerManager.WakeLock? = null
        try {
            // Acquire partial wake lock to prevent background CPU throttling or sleep
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                localWakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.apply {
                    setReferenceCounted(false)
                    acquire(4 * 60 * 60 * 1000L) // 4-hour timeout safeguard for long videos
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not acquire pipeline WakeLock: ${e.message}")
            }

            // Check cache
            val vocalsDir = File(context.filesDir, "vocals")
            if (!vocalsDir.exists()) vocalsDir.mkdirs()
            val vocalWavFile = File(vocalsDir, "vocal_${video.id}.wav")
            val instWavFile = File(vocalsDir, "inst_${video.id}.wav")

            if (forceRecompute) {
                if (vocalWavFile.exists()) vocalWavFile.delete()
                if (instWavFile.exists()) instWavFile.delete()
                Log.d(TAG, "forceRecompute requested: deleted old cached separation files for ${video.id}")
            }

            if (!forceRecompute && vocalWavFile.exists() && vocalWavFile.length() > 44) {
                Log.d(TAG, "Cached vocal track found at ${vocalWavFile.absolutePath}")
                videoDao.updateSeparationStatus(
                    id = video.id,
                    status = VideoStatus.READY,
                    progress = 1.0f,
                    vocalUri = vocalWavFile.absolutePath,
                    instrumentalUri = if (instWavFile.exists() && instWavFile.length() > 44) instWavFile.absolutePath else null,
                    errorMessage = null
                )
                onProgress(1.0f)
                return@withContext Result.success(vocalWavFile)
            }

            // Update status to processing
            videoDao.updateSeparationStatus(
                id = video.id,
                status = VideoStatus.PROCESSING,
                progress = 0.05f,
                vocalUri = null,
                instrumentalUri = null,
                errorMessage = null
            )
            onProgress(0.05f)

            // Step 1: Extract Audio from Video
            val videoUri = Uri.parse(video.videoUri)
            val decodedAudio = AudioExtractor.extractAudioFromVideo(
                context = context,
                videoUri = videoUri,
                onProgress = { extProgress ->
                    val overall = 0.05f + (extProgress * 0.15f)
                    onProgress(overall)
                }
            )

            try {
                // Step 2: Run Real ONNX Separation with selected Engine
                val engine: SeparationEngine = when (engineType) {
                    SeparationEngineType.DEEZER_SPLEETER -> spleeterEngine
                    SeparationEngineType.UVR_MDXNET -> uvrEngine
                    SeparationEngineType.CUSTOM -> if (!customModelPath.isNullOrBlank()) uvrEngine else spleeterEngine
                }

                Log.i(TAG, "Executing separation using engine: ${engine.engineType.displayName}")
                engine.separate(
                    decodedAudio = decodedAudio,
                    outputVocalFile = vocalWavFile,
                    outputInstrumentalFile = instWavFile,
                    customModelPath = customModelPath,
                    accelerationMode = accelerationMode,
                    onProgress = { sepProgress ->
                        val overall = 0.20f + (sepProgress * 0.75f)
                        onProgress(overall)
                    }
                )
            } finally {
                decodedAudio.close()
            }

            // Step 3: Mark Ready in Database
            val finalInstUri = if (instWavFile.exists() && instWavFile.length() > 44) instWavFile.absolutePath else null
            videoDao.updateSeparationStatus(
                id = video.id,
                status = VideoStatus.READY,
                progress = 1.0f,
                vocalUri = vocalWavFile.absolutePath,
                instrumentalUri = finalInstUri,
                errorMessage = null
            )
            onProgress(1.0f)

            Result.success(vocalWavFile)
        } catch (e: Exception) {
            Log.e(TAG, "Vocal separation failed for video ${video.id}", e)
            val msg = e.localizedMessage ?: "Unknown separation error"
            videoDao.updateSeparationStatus(
                id = video.id,
                status = VideoStatus.ERROR,
                progress = 0f,
                vocalUri = null,
                instrumentalUri = null,
                errorMessage = msg
            )
            Result.failure(e)
        } finally {
            try {
                if (localWakeLock?.isHeld == true) {
                    localWakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing pipeline WakeLock: ${e.message}")
            }
        }
    }
}
