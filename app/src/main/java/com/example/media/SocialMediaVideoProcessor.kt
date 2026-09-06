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
import java.net.URI
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
        private const val TAG = "WebVideoProcessor"

        private val VIDEO_ID_REGEX = Pattern.compile(
            """(?:https?:\/\/)?(?:www\.|m\.)?(?:[a-zA-Z0-9-]+\.[a-zA-Z]{2,}\/(?:watch\?v=|embed\/|v\/|shorts\/)|youtu\.be\/)([\w-]{6,})""",
            Pattern.CASE_INSENSITIVE
        )
        private val TIKTOK_REGEX = Pattern.compile("""(?:tiktok\.com)""", Pattern.CASE_INSENSITIVE)
        private val INSTAGRAM_REGEX = Pattern.compile("""(?:instagram\.com\/(?:p|reel|tv))""", Pattern.CASE_INSENSITIVE)
        private val TWITTER_REGEX = Pattern.compile("""(?:twitter\.com|x\.com)""", Pattern.CASE_INSENSITIVE)
        private val URL_FINDER_REGEX = Pattern.compile("""https?:\/\/[^\s"'<>]+""", Pattern.CASE_INSENSITIVE)

        // Verified public stream & media resolvers
        private val API_ENDPOINTS = listOf(
            "https://cobalt.api.scav.top/",
            "https://cobalt-backend.canine.tools/api/json",
            "https://co.wuk.sh/api/json",
            "https://api.cobalt.tools/"
        )

        // Working public test video presets with clear audio/vocals for immediate parser testing
        val TEST_PRESETS = listOf(
            SamplePreset(
                title = "Tears of Steel (Dialogue & Vocals Test)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                platform = "Direct Video Link"
            ),
            SamplePreset(
                title = "For Bigger Blazes (Vocal & Acoustic Sample)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                platform = "Direct Video Link"
            ),
            SamplePreset(
                title = "Big Buck Bunny (Animation Audio)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                platform = "Direct Video Link"
            )
        )

        // Backward compatibility alias
        val SAMPLE_PRESETS = TEST_PRESETS
    }

    data class SamplePreset(
        val title: String,
        val url: String,
        val platform: String
    )

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

    fun extractVideoId(url: String): String? {
        val matcher = VIDEO_ID_REGEX.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Resolves the stream or download URL for web videos, social clips, or direct links,
     * then downloads the MP4 and extracts duration metadata.
     */
    suspend fun processAndDownload(
        inputUrl: String,
        onProgress: (DownloadProgressState) -> Unit
    ): Result<ExtractedVideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = extractUrlFromText(inputUrl) ?: inputUrl.trim()
        val platform = detectPlatform(cleanUrl)
        Log.i(TAG, "Processing video URL: $cleanUrl [Platform: $platform]")

        onProgress(DownloadProgressState.Resolving("Analyzing video stream…"))

        try {
            // 1. Resolve media stream URL
            val streamResult = resolveMediaStream(cleanUrl, platform, onProgress)
            val streamUrl = streamResult.url
            val titleCandidate = cleanTitle(streamResult.title ?: "Web Video")

            Log.i(TAG, "Resolved stream URL: $streamUrl, Title: $titleCandidate")

            // 2. Download the video stream to app storage
            val downloadsDir = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
            val videoId = UUID.randomUUID().toString().substring(0, 8)
            val targetFile = File(downloadsDir, "video_${videoId}.mp4")

            onProgress(DownloadProgressState.Downloading(0.01f))
            downloadFile(streamUrl, cleanUrl, targetFile) { percent, speed ->
                onProgress(DownloadProgressState.Downloading(percent, speed))
            }

            if (!targetFile.exists() || targetFile.length() < 1000) {
                return@withContext Result.failure(IllegalStateException("Downloaded media file is empty or missing."))
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
                    resolvedTitle = cleanTitle(metaTitle)
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
            val errorMsg = e.localizedMessage ?: "Failed to process video link"
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
        // 1. Direct media URL check by extension
        if (isLikelyDirectVideoUrl(url)) {
            Log.i(TAG, "Direct media extension detected: $url")
            return StreamResolution(url, extractFilenameFromUrl(url))
        }

        // 2. Probe URL with HTTP Range request to inspect headers and follow redirects
        try {
            val directStream = probeDirectMediaStream(url)
            if (directStream != null) {
                Log.i(TAG, "Probed direct media stream from URL: ${directStream.url}")
                return directStream
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct media probe skipped: ${e.message}")
        }

        // 3. Inspect HTML webpage for embedded HTML5 video, OpenGraph video, or JSON-LD
        try {
            onProgress(DownloadProgressState.Resolving("Inspecting webpage for video source…"))
            val scraped = scrapeHtmlForVideo(url)
            if (scraped != null) {
                Log.i(TAG, "Found embedded video stream via HTML parser: ${scraped.url}")
                return scraped
            }
        } catch (e: Exception) {
            Log.d(TAG, "HTML video scraper skipped: ${e.message}")
        }

        // 4. Try public stream API resolvers
        for (endpoint in API_ENDPOINTS) {
            try {
                onProgress(DownloadProgressState.Resolving("Connecting to media stream resolver…"))
                val resolution = queryResolverApi(endpoint, url)
                if (resolution != null) {
                    return resolution
                }
            } catch (e: Exception) {
                Log.d(TAG, "API resolver endpoint failed: $endpoint", e)
            }
        }

        // 5. Fallback: Check if raw URL responds with a video Content-Type or 200 OK
        try {
            val probeReq = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .addHeader("Accept", "video/*,*/*;q=0.8")
                .get()
                .build()
            httpClient.newCall(probeReq).execute().use { response ->
                val cType = response.header("Content-Type") ?: ""
                val finalUrl = response.request.url.toString()
                if (cType.startsWith("video/") || cType.startsWith("audio/") ||
                    cType.contains("octet-stream") || isLikelyDirectVideoUrl(finalUrl)
                ) {
                    return StreamResolution(finalUrl, extractFilenameFromUrl(finalUrl))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Final fallback probe failed for $url", e)
        }

        throw IllegalStateException("Unable to resolve a video stream from this link. Please paste a direct video link (.mp4/.webm) or tap one of the verified test links.")
    }

    private fun probeDirectMediaStream(url: String): StreamResolution? {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .addHeader("Range", "bytes=0-2048")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) return null
            val contentType = response.header("Content-Type") ?: ""
            val contentDisposition = response.header("Content-Disposition") ?: ""
            val finalUrl = response.request.url.toString()

            if (contentType.startsWith("video/") ||
                contentType.startsWith("audio/") ||
                contentType.contains("vnd.apple.mpegurl") ||
                contentType.contains("x-mpegurl") ||
                (contentType.contains("octet-stream") && (finalUrl.contains(".mp4") || contentDisposition.contains(".mp4")))
            ) {
                val filename = extractFilenameFromDisposition(contentDisposition) ?: extractFilenameFromUrl(finalUrl)
                return StreamResolution(finalUrl, filename)
            }
        }
        return null
    }

    /**
     * Inspects HTML for standard video meta tags and HTML5 video sources
     */
    private fun scrapeHtmlForVideo(pageUrl: String): StreamResolution? {
        val request = Request.Builder()
            .url(pageUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null

            // Extract page title
            val titleMatcher = Pattern.compile("""<title[^>]*>(.*?)</title>""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
            var extractedTitle: String? = if (titleMatcher.find()) {
                titleMatcher.group(1)?.replace("\n", " ")?.trim()
            } else null

            extractedTitle = extractedTitle?.let { cleanTitle(it) }

            // 1. OpenGraph video (og:video:secure_url or og:video:url or og:video)
            val ogVideoRegex = Pattern.compile("""<meta\s+property=["']og:video(?::secure_url|:url)?["']\s+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val ogMatcher = ogVideoRegex.matcher(html)
            if (ogMatcher.find()) {
                val candidate = ogMatcher.group(1)?.replace("&amp;", "&")
                val resolved = resolveAbsoluteUrl(pageUrl, candidate)
                if (resolved != null) {
                    return StreamResolution(resolved, extractedTitle)
                }
            }

            // 2. Twitter video player stream
            val twitterVideoRegex = Pattern.compile("""<meta\s+(?:name|property)=["']twitter:player:stream["']\s+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val twMatcher = twitterVideoRegex.matcher(html)
            if (twMatcher.find()) {
                val candidate = twMatcher.group(1)?.replace("&amp;", "&")
                val resolved = resolveAbsoluteUrl(pageUrl, candidate)
                if (resolved != null) {
                    return StreamResolution(resolved, extractedTitle)
                }
            }

            // 3. HTML5 <video src="..."> or <source src="...">
            val html5VideoRegex = Pattern.compile("""<(?:video|source)[^>]+src=["']([^"']+)["'](?:[^>]*type=["']video\/[^"']*["'])?""", Pattern.CASE_INSENSITIVE)
            val h5Matcher = html5VideoRegex.matcher(html)
            while (h5Matcher.find()) {
                val rawSrc = h5Matcher.group(1)?.replace("&amp;", "&")
                if (!rawSrc.isNullOrBlank() && !rawSrc.startsWith("blob:")) {
                    val resolved = resolveAbsoluteUrl(pageUrl, rawSrc)
                    if (resolved != null && isLikelyDirectVideoUrl(resolved)) {
                        return StreamResolution(resolved, extractedTitle)
                    }
                }
            }

            // 4. JSON-LD "contentUrl": "https://..."
            val jsonLdRegex = Pattern.compile(""""contentUrl"\s*:\s*"([^"]+)"""", Pattern.CASE_INSENSITIVE)
            val jsonMatcher = jsonLdRegex.matcher(html)
            if (jsonMatcher.find()) {
                val candidate = jsonMatcher.group(1)?.replace("\\/", "/")
                val resolved = resolveAbsoluteUrl(pageUrl, candidate)
                if (resolved != null) {
                    return StreamResolution(resolved, extractedTitle)
                }
            }
        }
        return null
    }

    private fun resolveAbsoluteUrl(baseUrl: String, candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        return try {
            val trimmed = candidate.trim()
            if (trimmed.startsWith("//")) {
                "https:$trimmed"
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                URI(baseUrl).resolve(trimmed).toString()
            }
        } catch (e: Exception) {
            if (candidate.startsWith("http")) candidate else null
        }
    }

    private fun queryResolverApi(apiEndpoint: String, targetUrl: String): StreamResolution? {
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
            .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .post(jsonPayload.toString().toRequestBody(mediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val json = JSONObject(bodyStr)

            val streamUrl = json.optString("url")
            val filename = if (json.has("filename")) json.optString("filename") else null

            if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                return StreamResolution(streamUrl, filename?.let { cleanTitle(it) })
            }

            val picker = json.optJSONArray("picker")
            if (picker != null && picker.length() > 0) {
                for (i in 0 until picker.length()) {
                    val item = picker.getJSONObject(i)
                    val pUrl = item.optString("url")
                    if (pUrl.isNotBlank() && pUrl.startsWith("http")) {
                        return StreamResolution(pUrl, filename?.let { cleanTitle(it) })
                    }
                }
            }
        }
        return null
    }

    private fun isLikelyDirectVideoUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".mp4") || clean.endsWith(".webm") ||
                clean.endsWith(".mkv") || clean.endsWith(".mov") ||
                clean.endsWith(".m4v") || clean.endsWith(".3gp") ||
                clean.endsWith(".ts") ||
                url.contains("format=mp4", ignoreCase = true) ||
                url.contains("mime=video", ignoreCase = true)
    }

    private fun cleanTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""(?i)\s*-\s*youtube.*"""), "")
            .replace(Regex("""(?i)\s*\|\s*youtube.*"""), "")
            .replace(Regex("""(?i)youtube""", RegexOption.IGNORE_CASE), "Web Video")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
            .ifBlank { "Web Video" }
    }

    private fun extractFilenameFromUrl(url: String): String {
        return try {
            val last = url.substringBefore('?').substringAfterLast('/')
            if (last.isNotBlank() && last.contains('.')) cleanTitle(last) else "Web Video"
        } catch (e: Exception) {
            "Web Video"
        }
    }

    private fun extractFilenameFromDisposition(disposition: String): String? {
        val pattern = Pattern.compile("""filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(disposition)
        return if (matcher.find()) cleanTitle(matcher.group(1) ?: "") else null
    }

    private fun downloadFile(
        url: String,
        refererUrl: String,
        targetFile: File,
        onProgress: (percent: Float, speedKbps: Long) -> Unit
    ) {
        // First attempt with Referer and standard browser User-Agent
        var downloaded = tryDownload(url, refererUrl, targetFile, onProgress)
        if (!downloaded) {
            // Second attempt without Referer (some hosts block foreign Referer)
            downloaded = tryDownload(url, null, targetFile, onProgress)
        }
        if (!downloaded || !targetFile.exists() || targetFile.length() == 0L) {
            throw IllegalStateException("Failed to download video stream.")
        }
    }

    private fun tryDownload(
        url: String,
        refererUrl: String?,
        targetFile: File,
        onProgress: (percent: Float, speedKbps: Long) -> Unit
    ): Boolean {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "video/webm,video/mp4,video/*,*/*;q=0.9")

        if (!refererUrl.isNullOrBlank()) {
            requestBuilder.addHeader("Referer", refererUrl)
        }

        return try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return false

                val body = response.body ?: return false
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
                            val simulatedProgress = ((bytesReadTotal.toFloat() / (4 * 1024 * 1024)).coerceIn(0.1f, 0.95f))
                            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                            val speedKbps = if (elapsedSeconds > 0) ((bytesReadTotal / 1024) / elapsedSeconds).toLong() else 0L
                            onProgress(simulatedProgress, speedKbps)
                        }
                    }
                    outputStream.flush()
                }
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download attempt failed for $url: ${e.message}")
            false
        }
    }
}
