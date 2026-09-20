package com.example.onnx

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.example.audio.AudioExtractor
import com.example.audio.InstantPhaseCancellationProcessor
import com.example.data.dao.VideoDao
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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

    private val inferenceEngine = UvrMdxInferenceEngine(context)
    private val streamingPipeline = StreamingNeuralSeparationPipeline(context)

    val activeHardwareBackend: String
        get() = inferenceEngine.activeHardwareBackend

    /**
     * Runs the real separation pipeline for the given video with CPU WakeLock stability.
     */
    suspend fun processVideo(
        video: VideoItemEntity,
        customModelPath: String? = null,
        accelerationMode: AccelerationMode = AccelerationMode.TURBO,
        separationMethod: SeparationMethod = SeparationMethod.STREAMING_NEURAL,
        streamChannel: Channel<ShortArray>? = null,
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
            val pcmCacheFile = File(vocalsDir, "vocal_${video.id}.pcm")

            if (forceRecompute) {
                if (vocalWavFile.exists()) vocalWavFile.delete()
                if (pcmCacheFile.exists()) pcmCacheFile.delete()
                Log.d(TAG, "forceRecompute requested: deleted old cached vocal file at ${vocalWavFile.absolutePath}")
            }

            if (!forceRecompute && vocalWavFile.exists() && vocalWavFile.length() > 44) {
                Log.d(TAG, "Cached vocal track found at ${vocalWavFile.absolutePath}")
                videoDao.updateSeparationStatus(
                    id = video.id,
                    status = VideoStatus.READY,
                    progress = 1.0f,
                    vocalUri = vocalWavFile.absolutePath,
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
                when (separationMethod) {
                    SeparationMethod.STREAMING_NEURAL -> {
                        streamingPipeline.executeStreamingPipeline(
                            decodedAudio = decodedAudio,
                            outputWavFile = vocalWavFile,
                            persistentPcmCacheFile = pcmCacheFile,
                            streamChannel = streamChannel,
                            customModelPath = customModelPath,
                            accelerationMode = accelerationMode,
                            onProgress = { sepProgress ->
                                onProgress(0.20f + (sepProgress * 0.80f))
                            }
                        ).getOrThrow()
                    }

                    SeparationMethod.INSTANT_PHASE_CANCELLATION -> {
                        InstantPhaseCancellationProcessor.processDecodedAudio(
                            decodedAudio = decodedAudio,
                            outputWavFile = vocalWavFile,
                            isVocalOnly = true,
                            onProgress = { dspProgress ->
                                onProgress(0.20f + (dspProgress * 0.80f))
                            }
                        )
                    }

                    SeparationMethod.UVR_MDX_FULL -> {
                        inferenceEngine.separateVocals(
                            decodedAudio = decodedAudio,
                            outputWavFile = vocalWavFile,
                            customModelPath = customModelPath,
                            accelerationMode = accelerationMode,
                            onProgress = { sepProgress ->
                                onProgress(0.20f + (sepProgress * 0.80f))
                            }
                        )
                    }
                }
            } finally {
                decodedAudio.close()
            }

            // Step 3: Mark Ready
            videoDao.updateSeparationStatus(
                id = video.id,
                status = VideoStatus.READY,
                progress = 1.0f,
                vocalUri = vocalWavFile.absolutePath,
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
