package com.example.onnx

import android.content.Context
import android.net.Uri
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
    }

    private val inferenceEngine = UvrMdxInferenceEngine(context)

    /**
     * Runs the real separation pipeline for the given video.
     */
    suspend fun processVideo(
        video: VideoItemEntity,
        customModelPath: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Check cache
            val vocalsDir = File(context.filesDir, "vocals")
            if (!vocalsDir.exists()) vocalsDir.mkdirs()
            val vocalWavFile = File(vocalsDir, "vocal_${video.id}.wav")

            if (vocalWavFile.exists() && vocalWavFile.length() > 44) {
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

            // Step 2: Run Real ONNX Separation
            inferenceEngine.separateVocals(
                decodedAudio = decodedAudio,
                outputWavFile = vocalWavFile,
                customModelPath = customModelPath,
                onProgress = { sepProgress ->
                    onProgress(sepProgress)
                }
            )

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
        }
    }
}
