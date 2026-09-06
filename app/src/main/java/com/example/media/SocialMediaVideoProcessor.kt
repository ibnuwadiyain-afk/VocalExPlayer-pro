package com.example.media

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ExtractedVideoInfo(
    val title: String,
    val localFile: File,
    val originalUrl: String,
    val platform: String,
    val durationMs: Long = 0
)

sealed class DownloadProgressState {
    data class Resolving(val message: String) : DownloadProgressState()
    data class Downloading(
        val progress: Float,
        val bytesRead: Long,
        val totalBytes: Long,
        val speedKbps: Float = 0f
    ) : DownloadProgressState()
    data class Completed(val file: File) : DownloadProgressState()
    data class Failed(val error: String) : DownloadProgressState()
}

class SocialMediaVideoProcessor(private val context: Context) {

    companion object {
        private const val TAG = "SocialVideoProc"

        private val URL_FINDER_REGEX = Pattern.compile(
            """https?:\/\/(?:www\.|[-a-zA-Z0-9@:%._\+~#=]{1,256}\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_\+.~#?&//=]*)"""
        )

        private val VIDEO_ID_REGEX = Pattern.compile(
            """(?:https?:\/\/)?(?:www\.|m\.)?(?:youtube\.com\/(?:watch\?v=|embed\/|v\/|shorts\/)|youtu\.be\/)([\w-]{6,})""",
            Pattern.CASE_INSENSITIVE
        )

        private val TIKTOK_REGEX = Pattern.compile("""tiktok\.com\/""", Pattern.CASE_INSENSITIVE)
        private val INSTAGRAM_REGEX = Pattern.compile("""instagram\.com\/(?:p|reel|tv)\/""", Pattern.CASE_INSENSITIVE)
        private val TWITTER_REGEX = Pattern.compile("""(?:twitter\.com|x\.com)\/""", Pattern.CASE_INSENSITIVE)
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun detectPlatform(url: String): String {
        return when {
            url.contains("/shorts/", ignoreCase = true) -> "Web Video"
            VIDEO_ID_REGEX.matcher(url).find() -> "Web Video"
            TIKTOK_REGEX.matcher(url).find() -> "TikTok"
            INSTAGRAM_REGEX.matcher(url).find() -> "Instagram Reel"
            TWITTER_REGEX.matcher(url).find() -> "X (Twitter)"
            isLikelyDirectVideoUrl(url) -> "Direct Video Link"
            else -> "Web Video"
        }
    }

    fun extractUrlFromText(text: String): String? {
        val matcher = URL_FINDER_REGEX.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun isLikelyDirectVideoUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".mp4") || clean.endsWith(".webm") ||
                clean.endsWith(".mkv") || clean.endsWith(".mov") ||
                clean.endsWith(".m4v") || clean.endsWith(".mp3") ||
                clean.endsWith(".wav") || clean.endsWith(".m4a")
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace(Regex("""(?i)\b(youtube|google|tiktok|instagram|twitter)\b"""), "")
            .replace(Regex("""[\\/*?:"<>|]"""), "_")
            .trim()
            .ifBlank { "Video_${System.currentTimeMillis()}" }
    }

    suspend fun processAndDownload(
        inputUrl: String,
        onProgress: (DownloadProgressState) -> Unit
    ): Result<ExtractedVideoInfo> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = inputUrl.trim()
            onProgress(DownloadProgressState.Resolving("Analyzing video link…"))

            val platform = detectPlatform(trimmedUrl)
            val videoDirectory = File(context.filesDir, "videos").apply { mkdirs() }
            val targetFile = File(videoDirectory, "web_video_${System.currentTimeMillis()}.mp4")

            // Case 1: Direct media stream
            if (isLikelyDirectVideoUrl(trimmedUrl)) {
                return@withContext downloadDirectStream(
                    streamUrl = trimmedUrl,
                    targetFile = targetFile,
                    title = "Media_Stream_${System.currentTimeMillis()}",
                    platform = platform,
                    onProgress = onProgress
                )
            }

            // Case 2: Multi-layer resolution
            onProgress(DownloadProgressState.Resolving("Connecting to media stream resolver…"))
            val streamUrl = resolveStreamUrl(trimmedUrl)
                ?: return@withContext Result.failure(Exception("Could not resolve media stream for URL: $trimmedUrl"))

            downloadDirectStream(
                streamUrl = streamUrl,
                targetFile = targetFile,
                title = cleanTitle("Extracted_Video_${System.currentTimeMillis()}"),
                platform = platform,
                onProgress = onProgress
            )
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            onProgress(DownloadProgressState.Failed(e.message ?: "Extraction failed"))
            Result.failure(e)
        }
    }

    private fun resolveStreamUrl(pageUrl: String): String? {
        // Probe Cobalt API public instances
        val cobaltApis = listOf(
            "https://cobalt-backend.canine.tools/",
            "https://api.cobalt.tools/"
        )
        for (api in cobaltApis) {
            try {
                val jsonBody = JSONObject().apply {
                    put("url", pageUrl)
                    put("videoQuality", "720")
                }
                val request = Request.Builder()
                    .url(api)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.has("url")) {
                            return json.getString("url")
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun downloadDirectStream(
        streamUrl: String,
        targetFile: File,
        title: String,
        platform: String,
        onProgress: (DownloadProgressState) -> Unit
    ): Result<ExtractedVideoInfo> {
        val request = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        val response: Response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            return Result.failure(Exception("HTTP download failed: code ${response.code}"))
        }

        val body = response.body ?: return Result.failure(Exception("Empty download response"))
        val contentLength = body.contentLength()
        val inputStream: InputStream = body.byteStream()

        FileOutputStream(targetFile).use { output ->
            val buffer = ByteArray(32 * 1024)
            var bytesReadTotal = 0L
            var read: Int
            val startTime = System.currentTimeMillis()

            while (inputStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                bytesReadTotal += read

                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                val speed = if (elapsed > 0) (bytesReadTotal / 1024f) / elapsed else 0f
                val progress = if (contentLength > 0) {
                    (bytesReadTotal.toFloat() / contentLength).coerceIn(0f, 1f)
                } else {
                    -1f
                }

                onProgress(
                    DownloadProgressState.Downloading(
                        progress = progress,
                        bytesRead = bytesReadTotal,
                        totalBytes = contentLength,
                        speedKbps = speed
                    )
                )
            }
            output.flush()
        }

        onProgress(DownloadProgressState.Completed(targetFile))
        return Result.success(
            ExtractedVideoInfo(
                title = title,
                localFile = targetFile,
                originalUrl = streamUrl,
                platform = platform
            )
        )
    }
}
