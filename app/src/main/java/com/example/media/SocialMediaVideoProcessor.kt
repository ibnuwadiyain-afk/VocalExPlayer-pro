package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ExtractedVideoInfo(
    val file: File,
    val title: String,
    val durationMs: Long,
    val platform: String,
    val originalUrl: String
)

sealed class DownloadProgressState {
    data class Resolving(val message: String) : DownloadProgressState()
    data class Downloading(val progress: Float, val speedKbps: Long = 0) : DownloadProgressState()
    data class Completed(val info: ExtractedVideoInfo) : DownloadProgressState()
    data class Failed(val error: String) : DownloadProgressState()
}

class SocialMediaVideoProcessor(private val context: Context) {

    companion object {
        private const val TAG = "SocialMediaProcessor"

        private val YOUTUBE_REGEX = Pattern.compile(
            """(?:https?:\/\/)?(?:www\.|m\.)?(?:youtube\.com\/(?:watch\?v=|embed\/|v\/|shorts\/)|youtu\.be\/)([\w-]{6,})""",
            Pattern.CASE_INSENSITIVE
        )
        private val TIKTOK_REGEX = Pattern.compile("""(?:tiktok\.com)""", Pattern.CASE_INSENSITIVE)
        private val INSTAGRAM_REGEX = Pattern.compile("""(?:instagram\.com\/(?:p|reel|tv))""", Pattern.CASE_INSENSITIVE)
        private val TWITTER_REGEX = Pattern.compile("""(?:twitter\.com|x\.com)""", Pattern.CASE_INSENSITIVE)
        private val URL_FINDER_REGEX = Pattern.compile("""https?:\/\/[^\s]+""", Pattern.CASE_INSENSITIVE)

        // Known public Cobalt / Video extraction instances
        private val COBALT_ENDPOINTS = listOf(
            "https://api.cobalt.tools/",
            "https://cobalt.api.scav.top/",
            "https://cobalt-backend.canine.tools/api/json"
        )

        // Public Invidious / Piped instances for YouTube stream fallback
        private val INVIDIOUS_ENDPOINTS = listOf(
            "https://invidious.asir.dev",
            "https://inv.tux.pizza",
            "https://invidious.nerdvpn.de",
            "https://pipedapi.kavin.rocks"
        )

        // Curated royalty-free sample videos with rich vocal tracks for testing
        val SAMPLE_PRESETS = listOf(
            SamplePreset(
                title = "Acoustic Vocal Performance (Royalty-Free)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                platform = "Web Direct (Test Sample)"
            ),
            SamplePreset(
                title = "Royalty-Free Vocal & Melody Sample",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                platform = "Web Direct (Test Sample)"
            )
        )
    }

    data class SamplePreset(
        val title: String,
        val url: String,
        val platform: String
    )

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun detectPlatform(url: String): String {
        return when {
            YOUTUBE_REGEX.matcher(url).find() -> {
                if (url.contains("/shorts/")) "YouTube Shorts" else "YouTube"
            }
            TIKTOK_REGEX.matcher(url).find() -> "TikTok"
            INSTAGRAM_REGEX.matcher(url).find() -> "Instagram Reel"
            TWITTER_REGEX.matcher(url).find() -> "X (Twitter)"
            url.contains(".mp4", ignoreCase = true) || url.contains(".webm", ignoreCase = true) -> "Direct Video Link"
            else -> "Web Video"
        }
    }

    fun extractUrlFromText(text: String): String? {
        val matcher = URL_FINDER_REGEX.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    fun extractYouTubeId(url: String): String? {
        val matcher = YOUTUBE_REGEX.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Resolves the stream or download URL for YouTube, social media, or direct video link,
     * then downloads the MP4 and extracts duration metadata.
     */
    suspend fun processAndDownload(
        inputUrl: String,
        onProgress: (DownloadProgressState) -> Unit
    ): Result<ExtractedVideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = extractUrlFromText(inputUrl) ?: inputUrl.trim()
        val platform = detectPlatform(cleanUrl)
        Log.i(TAG, "Processing video URL: $cleanUrl [Detected: $platform]")

        onProgress(DownloadProgressState.Resolving("Analyzing $platform link…"))

        try {
            // 1. Resolve direct download stream URL
            val streamResult = resolveMediaStream(cleanUrl, platform, onProgress)
            val streamUrl = streamResult.url
            val titleCandidate = streamResult.title ?: "$platform Video"

            Log.i(TAG, "Resolved stream URL: $streamUrl, Title: $titleCandidate")

            // 2. Download the video stream to app storage
            val downloadsDir = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
            val videoId = UUID.randomUUID().toString().substring(0, 8)
            val targetFile = File(downloadsDir, "video_${platform.lowercase().replace(" ", "_")}_$videoId.mp4")

            onProgress(DownloadProgressState.Downloading(0.01f))
            downloadFile(streamUrl, targetFile) { percent, speed ->
                onProgress(DownloadProgressState.Downloading(percent, speed))
            }

            if (!targetFile.exists() || targetFile.length() < 1000) {
                return@withContext Result.failure(IllegalStateException("Downloaded file is empty or missing."))
            }

            // 3. Extract duration & metadata via MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            var durationMs = 30_000L
            var resolvedTitle = titleCandidate

            try {
                retriever.setDataSource(context, Uri.fromFile(targetFile))
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    durationMs = durStr.toLongOrNull() ?: 30_000L
                }
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!metaTitle.isNullOrBlank()) {
                    resolvedTitle = "$platform - $metaTitle"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract metadata from downloaded video", e)
            } finally {
                retriever.release()
            }

            val finalInfo = ExtractedVideoInfo(
                file = targetFile,
                title = resolvedTitle,
                durationMs = durationMs,
                platform = platform,
                originalUrl = cleanUrl
            )

            onProgress(DownloadProgressState.Completed(finalInfo))
            Result.success(finalInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and process video from $cleanUrl", e)
            val errorMsg = e.localizedMessage ?: "Failed to process video from $platform"
            onProgress(DownloadProgressState.Failed(errorMsg))
            Result.failure(e)
        }
    }

    private data class StreamResolution(
        val url: String,
        val title: String?
    )

    private suspend fun resolveMediaStream(
        url: String,
        platform: String,
        onProgress: (DownloadProgressState) -> Unit
    ): StreamResolution {
        // Direct media check
        if (isDirectVideoUrl(url)) {
            return StreamResolution(url, extractFilenameFromUrl(url))
        }

        // Try Cobalt API (supports YouTube, TikTok, Instagram, Twitter, etc.)
        for (endpoint in COBALT_ENDPOINTS) {
            try {
                onProgress(DownloadProgressState.Resolving("Connecting to stream resolver ($platform)…"))
                val resolution = queryCobaltApi(endpoint, url)
                if (resolution != null) {
                    return resolution
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cobalt endpoint failed: $endpoint", e)
            }
        }

        // Fallback for YouTube: Invidious / Piped public API
        val ytId = extractYouTubeId(url)
        if (ytId != null) {
            for (endpoint in INVIDIOUS_ENDPOINTS) {
                try {
                    onProgress(DownloadProgressState.Resolving("Fetching YouTube stream ($ytId)…"))
                    val resolution = queryInvidiousApi(endpoint, ytId)
                    if (resolution != null) {
                        return resolution
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invidious endpoint failed: $endpoint", e)
                }
            }
        }

        // If all resolvers fail and URL looks like a direct link or stream, attempt direct fetch
        try {
            val headRequest = Request.Builder().url(url).head().build()
            httpClient.newCall(headRequest).execute().use { response ->
                val contentType = response.header("Content-Type") ?: ""
                if (contentType.startsWith("video/") || response.isSuccessful) {
                    return StreamResolution(url, "$platform Video")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Head request failed for $url", e)
        }

        throw IllegalStateException("Unable to resolve direct stream for $platform video. Please check the URL or try another link.")
    }

    private fun queryCobaltApi(apiEndpoint: String, targetUrl: String): StreamResolution? {
        val jsonPayload = JSONObject().apply {
            put("url", targetUrl)
            put("videoQuality", "720")
            put("audioFormat", "best")
            put("downloadMode", "auto")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(apiEndpoint)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "VocalExtractor/1.0")
            .post(jsonPayload.toString().toRequestBody(mediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Cobalt API returned HTTP ${response.code} from $apiEndpoint")
                return null
            }

            val bodyStr = response.body?.string() ?: return null
            val json = JSONObject(bodyStr)

            val status = json.optString("status")
            val streamUrl = json.optString("url")
            val filename = if (json.has("filename")) json.optString("filename") else null

            if (streamUrl.isNotBlank() && (status == "redirect" || status == "tunnel" || status == "stream" || status == "picker")) {
                return StreamResolution(streamUrl, filename)
            }
        }
        return null
    }

    private fun queryInvidiousApi(endpoint: String, videoId: String): StreamResolution? {
        val apiUrl = if (endpoint.contains("piped")) {
            "$endpoint/streams/$videoId"
        } else {
            "$endpoint/api/v1/videos/$videoId"
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/119.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val json = JSONObject(bodyStr)

            val title = json.optString("title", "YouTube Video")

            // Format streams (video + audio combined)
            val formatStreams = json.optJSONArray("formatStreams")
            if (formatStreams != null && formatStreams.length() > 0) {
                for (i in 0 until formatStreams.length()) {
                    val streamObj = formatStreams.getJSONObject(i)
                    val streamUrl = streamObj.optString("url")
                    val container = streamObj.optString("container", "mp4")
                    if (streamUrl.isNotBlank() && container.contains("mp4", ignoreCase = true)) {
                        return StreamResolution(streamUrl, "YouTube - $title")
                    }
                }
                val firstUrl = formatStreams.getJSONObject(0).optString("url")
                if (firstUrl.isNotBlank()) {
                    return StreamResolution(firstUrl, "YouTube - $title")
                }
            }

            // Check Piped videoStreams
            val videoStreams = json.optJSONArray("videoStreams")
            if (videoStreams != null && videoStreams.length() > 0) {
                for (i in 0 until videoStreams.length()) {
                    val streamObj = videoStreams.getJSONObject(i)
                    val streamUrl = streamObj.optString("url")
                    val videoOnly = streamObj.optBoolean("videoOnly", false)
                    if (!videoOnly && streamUrl.isNotBlank()) {
                        return StreamResolution(streamUrl, "YouTube - $title")
                    }
                }
            }
        }
        return null
    }

    private fun isDirectVideoUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mkv") || clean.endsWith(".mov")
    }

    private fun extractFilenameFromUrl(url: String): String {
        return try {
            val last = url.substringBefore('?').substringAfterLast('/')
            if (last.isNotBlank() && last.contains('.')) last else "Downloaded Video"
        } catch (e: Exception) {
            "Downloaded Video"
        }
    }

    private fun downloadFile(
        url: String,
        targetFile: File,
        onProgress: (percent: Float, speedKbps: Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download video stream: HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body from media server")
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()

            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(64 * 1024)
                var bytesReadTotal = 0L
                val startTime = System.currentTimeMillis()
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesReadTotal += read

                    if (totalBytes > 0) {
                        val percent = (bytesReadTotal.toFloat() / totalBytes).coerceIn(0f, 1f)
                        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                        val speedKbps = if (elapsedSeconds > 0) ((bytesReadTotal / 1024) / elapsedSeconds).toLong() else 0L
                        onProgress(percent, speedKbps)
                    } else {
                        // Unknown length
                        onProgress(0.5f, 0L)
                    }
                }
                outputStream.flush()
            }
        }
    }
}
