package com.example.onnx

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.example.audio.AudioExtractor
import com.example.data.dao.VideoDao
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import com.example.export.ProgressiveVideoMerger
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

    private val inferenceEngine = SpleeterInferenceEngine(context)

    val activeHardwareBackend: String
        get() = inferenceEngine.activeHardwareBackend

    /**
     * Runs the real separation pipeline for the given video with CPU WakeLock stability.
     */
    suspend fun processVideo(
        video: VideoItemEntity,
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

            if (forceRecompute && vocalWavFile.exists()) {
                vocalWavFile.delete()
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
                    val overall = 0.05f + (extProgress * 0.20f)
                    onProgress(overall)
                }
            )

            // Step 2: Initialize Progressive Video Merger for zero-waiting video export
            val videoCacheFile = ProgressiveVideoMerger.getCacheFileForVideo(context, video.id)
            val progressiveMerger = ProgressiveVideoMerger(
                context = context,
                videoUriString = video.videoUri,
                sampleRate = decodedAudio.sampleRate,
                channels = 2,
                outputCacheFile = videoCacheFile
            )

            try {
                // Launch progressive video merger in decoupled background worker
                progressiveMerger.start()

                // Step 3: Run Real ONNX Separation & stream separated chunks simultaneously
                inferenceEngine.separateVocals(
                    decodedAudio = decodedAudio,
                    outputWavFile = vocalWavFile,
                    customModelPath = customModelPath,
                    accelerationMode = accelerationMode,
                    onChunkAvailable = { left, right, offset, count, isEos ->
                        progressiveMerger.feedSeparatedChunk(left, right, offset, count, isEos)
                    },
                    onProgress = { sepProgress ->
                        onProgress(sepProgress)
                    }
                )

                // Await background finalization of video cache (typically completes in < 200ms)
                progressiveMerger.awaitFinalization(timeoutMs = 3000L)
                if (progressiveMerger.isCacheReady) {
                    Log.i(TAG, "⚡ Progressive video cache ready for instant export: ${videoCacheFile.absolutePath}")
                }
            } catch (t: Throwable) {
                progressiveMerger.cancel()
                throw t
            } finally {
                decodedAudio.close()
            }

            // Step 4: Mark Ready
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
