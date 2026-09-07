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
import java.io.FileInputStream
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

        // Curated, verified working sample media streams for instant testing
        val SAMPLE_PRESETS = listOf(
            SamplePreset(
                title = "Big Buck Bunny (Direct MP4 Clip)",
                url = "https://www.w3schools.com/html/mov_bbb.mp4",
                platform = "Direct MP4 Stream"
            ),
            SamplePreset(
                title = "VidLii Showcase Video",
                url = "https://www.vidlii.com/watch?v=4fEcsaOOBGe",
                platform = "VidLii Web Link"
            ),
            SamplePreset(
                title = "Vocal Song Melody (Direct MP3)",
                url = "https://raw.githubusercontent.com/rafaelreis-hotmart/Audio-Sample-files/master/sample.mp3",
                platform = "Direct Audio Track"
            ),
            SamplePreset(
                title = "Flower Nature Clip (Direct MP4)",
                url = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
                platform = "Direct MP4 Stream"
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
        val lower = url.lowercase()
        return when {
            YOUTUBE_REGEX.matcher(url).find() -> {
                if (url.contains("/shorts/")) "YouTube Shorts" else "YouTube"
            }
            TIKTOK_REGEX.matcher(url).find() -> "TikTok"
            INSTAGRAM_REGEX.matcher(url).find() -> "Instagram Reel"
            TWITTER_REGEX.matcher(url).find() -> "X (Twitter)"
            lower.contains("vidlii.com") -> "VidLii"
            lower.contains("dailymotion.com") -> "Dailymotion"
            lower.contains("vimeo.com") -> "Vimeo"
            lower.contains("rumble.com") -> "Rumble"
            lower.contains("reddit.com") -> "Reddit"
            lower.contains("archive.org") -> "Internet Archive"
            isDirectVideoUrl(url) -> "Direct Video Link"
            else -> {
                try {
                    val host = Uri.parse(url).host?.removePrefix("www.")
                    if (!host.isNullOrBlank()) {
                        val simpleName = host.substringBefore('.').replaceFirstChar { it.uppercase() }
                        if (simpleName.isNotBlank() && simpleName.length > 2) simpleName else "Web Video"
                    } else {
                        "Web Video"
                    }
                } catch (e: Exception) {
                    "Web Video"
                }
            }
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
            val referer = streamResult.referer ?: cleanUrl

            Log.i(TAG, "Resolved stream URL: $streamUrl, Title: $titleCandidate, Referer: $referer")

            // 2. Download the video stream to app storage
            val downloadsDir = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }
            val videoId = UUID.randomUUID().toString().substring(0, 8)
            val cleanStream = streamUrl.substringBefore('?').lowercase()
            val fileExt = when {
                cleanStream.endsWith(".mp3") -> ".mp3"
                cleanStream.endsWith(".m4a") -> ".m4a"
                cleanStream.endsWith(".webm") -> ".webm"
                cleanStream.endsWith(".wav") -> ".wav"
                else -> ".mp4"
            }
            val cleanPlatformTag = platform.lowercase().replace(Regex("[^a-z0-9_]"), "_")
            val targetFile = File(downloadsDir, "media_${cleanPlatformTag}_$videoId$fileExt")

            onProgress(DownloadProgressState.Downloading(0.01f))
            downloadFile(streamUrl, targetFile, referer) { percent, speed ->
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
                retriever.setDataSource(targetFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (durStr != null) {
                    durationMs = durStr.toLongOrNull() ?: 30_000L
                }
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!metaTitle.isNullOrBlank()) {
                    resolvedTitle = "$platform - $metaTitle"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract metadata from downloaded media", e)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
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
        val title: String?,
        val referer: String? = null
    )

    private suspend fun resolveMediaStream(
        url: String,
        platform: String,
        onProgress: (DownloadProgressState) -> Unit
    ): StreamResolution {
        // 1. Direct media URL check (.mp4, .mkv, .webm, .mov, .m4a, .wav, .mp3)
        if (isDirectVideoUrl(url)) {
            return StreamResolution(url, extractFilenameFromUrl(url), referer = url)
        }

        // 2. YouTube & YouTube Shorts direct extraction
        val ytId = extractYouTubeId(url)
        if (ytId != null) {
            onProgress(DownloadProgressState.Resolving("Extracting YouTube stream ($ytId)…"))
            try {
                val directYt = queryYouTubeDirect(ytId)
                if (directYt != null) {
                    return directYt
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct YouTube extraction failed for $ytId", e)
            }

            // Fallback for YouTube: Invidious / Piped instances
            for (endpoint in INVIDIOUS_ENDPOINTS) {
                try {
                    onProgress(DownloadProgressState.Resolving("Checking alternative stream server…"))
                    val resolution = queryInvidiousApi(endpoint, ytId)
                    if (resolution != null) {
                        return resolution
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invidious endpoint failed: $endpoint", e)
                }
            }
        }

        // 3. TikTok extraction via TikWM API
        if (TIKTOK_REGEX.matcher(url).find()) {
            onProgress(DownloadProgressState.Resolving("Extracting TikTok video stream…"))
            try {
                val tiktokRes = queryTikWm(url)
                if (tiktokRes != null) {
                    return tiktokRes
                }
            } catch (e: Exception) {
                Log.w(TAG, "TikWM resolution failed for $url", e)
            }
        }

        // 4. Universal HTML5 Web Scraper & Stream Resolver
        // (Supports VidLii, Dailymotion, Vimeo, Rumble, Internet Archive, Reddit, blogs, news, and HTML5 video sites)
        try {
            val webMedia = extractWebPageMedia(url, platform, onProgress)
            if (webMedia != null) {
                return webMedia
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTML5 web media extraction failed for $url", e)
        }

        // 5. Cobalt API resolution for supported social networks (Instagram, Twitter, etc.)
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

        // 6. Check if URL itself is a direct media stream (strictly checking Content-Type via HEAD)
        try {
            val headRequest = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .head()
                .build()
            httpClient.newCall(headRequest).execute().use { response ->
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                if (contentType.startsWith("video/") || contentType.startsWith("audio/") || contentType.contains("octet-stream")) {
                    return StreamResolution(url, "$platform Video", referer = url)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Head request check failed for $url", e)
        }

        val lowerUrl = url.lowercase()
        val customHint = when {
            lowerUrl.contains("vidlii.com") && url.contains("watch?v=") && url.substringAfter("watch?v=").substringBefore('&').length < 8 ->
                " Note: VidLii video IDs are typically 11 characters (e.g. watch?v=4fEcsaOOBGe); the entered ID appears truncated."
            else -> ""
        }

        throw IllegalStateException(
            "Unable to extract a playable stream for this $platform link.$customHint " +
            "Please try one of the verified test presets below or import a media file directly."
        )
    }

    private fun queryYouTubeDirect(videoId: String): StreamResolution? {
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", "1.61.48")
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(payload.toString().toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val json = JSONObject(bodyStr)
            val title = json.optJSONObject("videoDetails")?.optString("title") ?: "YouTube Video"
            val streamingData = json.optJSONObject("streamingData") ?: return null

            // 1. Check direct combined formats (video + audio)
            val formats = streamingData.optJSONArray("formats")
            if (formats != null && formats.length() > 0) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val streamUrl = fmt.optString("url")
                    val mime = fmt.optString("mimeType")
                    if (streamUrl.isNotBlank() && mime.contains("mp4", ignoreCase = true)) {
                        return StreamResolution(streamUrl, "YouTube - $title")
                    }
                }
                val firstUrl = formats.getJSONObject(0).optString("url")
                if (firstUrl.isNotBlank()) {
                    return StreamResolution(firstUrl, "YouTube - $title")
                }
            }

            // 2. Check adaptive audio formats (AAC / MP4 audio stream)
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null && adaptive.length() > 0) {
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.getJSONObject(i)
                    val streamUrl = fmt.optString("url")
                    val mime = fmt.optString("mimeType")
                    if (streamUrl.isNotBlank() && mime.startsWith("audio/mp4")) {
                        return StreamResolution(streamUrl, "YouTube - $title")
                    }
                }
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.getJSONObject(i)
                    val streamUrl = fmt.optString("url")
                    val mime = fmt.optString("mimeType")
                    if (streamUrl.isNotBlank() && mime.startsWith("audio/")) {
                        return StreamResolution(streamUrl, "YouTube - $title")
                    }
                }
            }
        }
        return null
    }

    private fun queryTikWm(url: String): StreamResolution? {
        val encodedUrl = Uri.encode(url)
        val apiUrl = "https://www.tikwm.com/api/?url=$encodedUrl"
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val json = JSONObject(bodyStr)
            if (json.optInt("code", -1) == 0) {
                val data = json.optJSONObject("data") ?: return null
                val playUrl = data.optString("play")
                val title = data.optString("title", "TikTok Video")
                if (playUrl.isNotBlank()) {
                    val fullUrl = if (playUrl.startsWith("http")) playUrl else "https://www.tikwm.com$playUrl"
                    return StreamResolution(fullUrl, "TikTok - $title")
                }
            }
        }
        return null
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
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
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

    private fun extractWebPageMedia(
        pageUrl: String,
        platform: String,
        onProgress: (DownloadProgressState) -> Unit
    ): StreamResolution? {
        onProgress(DownloadProgressState.Resolving("Analyzing $platform web page for streams…"))
        val request = Request.Builder()
            .url(pageUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,video/*,*/*;q=0.8")
            .get()
            .build()

        val html: String
        val finalUrl: String
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                finalUrl = response.request.url.toString()

                // If direct stream returned or redirected to media
                if (contentType.startsWith("video/") || contentType.startsWith("audio/") || contentType.contains("octet-stream")) {
                    return StreamResolution(finalUrl, "$platform Video", referer = pageUrl)
                }

                val body = response.body ?: return null
                html = body.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load web page $pageUrl", e)
            return null
        }

        // 1. Extract title
        val title = extractTitleFromHtml(html) ?: "$platform Video"

        // 2. Gather stream candidates
        val candidates = mutableListOf<String>()

        // HTML5 <video src="...">
        val videoSrcPattern = Pattern.compile("""<video[^>]*\ssrc=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val vMatcher = videoSrcPattern.matcher(html)
        while (vMatcher.find()) {
            candidates.add(vMatcher.group(1))
        }

        // HTML5 <source src="...">
        val sourceSrcPattern = Pattern.compile("""<source[^>]*\ssrc=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val sMatcher = sourceSrcPattern.matcher(html)
        while (sMatcher.find()) {
            candidates.add(sMatcher.group(1))
        }

        // OpenGraph & Twitter player meta tags
        val ogPattern = Pattern.compile(
            """<meta[^>]+(?:property|name)=["'](?:og:video|og:video:url|og:video:secure_url|twitter:player:stream)["'][^>]+content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val ogMatcher = ogPattern.matcher(html)
        while (ogMatcher.find()) {
            candidates.add(ogMatcher.group(1))
        }

        // Reversed attribute order: content="..." property="og:video"
        val ogRevPattern = Pattern.compile(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["'](?:og:video|og:video:url|og:video:secure_url|twitter:player:stream)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val ogRevMatcher = ogRevPattern.matcher(html)
        while (ogRevMatcher.find()) {
            candidates.add(ogRevMatcher.group(1))
        }

        // JavaScript / JSON config parameters commonly found in video players:
        // contentUrl, videoUrl, video_url, file, videoSrc
        val jsonMediaPattern = Pattern.compile(
            """["'](?:contentUrl|videoUrl|video_url|file|videoSrc|src)["']\s*:\s*["']([^"']+\.(?:mp4|webm|mkv|m4a|mp3)[^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val jMatcher = jsonMediaPattern.matcher(html)
        while (jMatcher.find()) {
            candidates.add(jMatcher.group(1))
        }

        // Relative media paths: "/usfi/v/....mp4"
        val relMediaPattern = Pattern.compile(
            """["'](\/[^"'<>\s]+\.(?:mp4|webm|mkv|m4a|mp3)[^"'<>\s]*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val relMatcher = relMediaPattern.matcher(html)
        while (relMatcher.find()) {
            candidates.add(relMatcher.group(1))
        }

        // Absolute media URLs in page HTML: "https://...mp4"
        val absMediaPattern = Pattern.compile(
            """(https?:\/\/[^"'<>\s]+\.(?:mp4|webm|mkv|m4a|mp3)[^"'<>\s]*)""",
            Pattern.CASE_INSENSITIVE
        )
        val absMatcher = absMediaPattern.matcher(html)
        while (absMatcher.find()) {
            candidates.add(absMatcher.group(1))
        }

        // Resolve, clean, and deduplicate candidates
        val resolvedCandidates = candidates
            .map { it.trim().replace("&amp;", "&") }
            .filter { raw ->
                raw.isNotBlank() &&
                !raw.endsWith(".html", ignoreCase = true) &&
                !raw.endsWith(".php", ignoreCase = true) &&
                !raw.contains("/embed?", ignoreCase = true)
            }
            .map { raw ->
                resolveRelativeUrl(finalUrl, raw)
            }
            .distinct()

        Log.i(TAG, "Found ${resolvedCandidates.size} candidate streams on $pageUrl")

        // Probe candidates to find playable stream
        for (candidate in resolvedCandidates) {
            onProgress(DownloadProgressState.Resolving("Validating video stream…"))
            if (verifyCandidateStream(candidate, finalUrl)) {
                Log.i(TAG, "Successfully validated web stream: $candidate")
                return StreamResolution(candidate, title, referer = finalUrl)
            }
        }

        // If none probed with 200/206/video-type, fallback to direct .mp4 candidate if available
        val bestDirectCandidate = resolvedCandidates.firstOrNull {
            val clean = it.substringBefore('?').lowercase()
            clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".m4a")
        }
        if (bestDirectCandidate != null) {
            Log.i(TAG, "Falling back to best direct candidate: $bestDirectCandidate")
            return StreamResolution(bestDirectCandidate, title, referer = finalUrl)
        }

        return null
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return try {
            val baseUri = java.net.URI(baseUrl)
            baseUri.resolve(relativeOrAbsolute.trim()).toString()
        } catch (e: Exception) {
            if (relativeOrAbsolute.startsWith("/")) {
                try {
                    val baseUri = java.net.URI(baseUrl)
                    "${baseUri.scheme}://${baseUri.host}$relativeOrAbsolute"
                } catch (ex: Exception) {
                    relativeOrAbsolute
                }
            } else {
                relativeOrAbsolute
            }
        }
    }

    private fun extractTitleFromHtml(html: String): String? {
        val ogTitlePattern = Pattern.compile(
            """<meta[^>]+(?:property|name)=["']og:title["'][^>]+content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val ogM = ogTitlePattern.matcher(html)
        if (ogM.find()) {
            val t = cleanHtmlEntities(ogM.group(1).trim())
            if (t.isNotBlank()) return t
        }

        val titlePattern = Pattern.compile("""<title>([^<]+)</title>""", Pattern.CASE_INSENSITIVE)
        val tM = titlePattern.matcher(html)
        if (tM.find()) {
            val raw = cleanHtmlEntities(tM.group(1).trim())
            val cleaned = raw
                .replace(Regex("""\s*[-|–—]\s*(?:VidLii|YouTube|Vimeo|Dailymotion|TikTok).*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (cleaned.isNotBlank()) return cleaned
        }
        return null
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun verifyCandidateStream(streamUrl: String, referer: String): Boolean {
        val clean = streamUrl.substringBefore('?').lowercase()
        val isMediaExt = clean.endsWith(".mp4") || clean.endsWith(".webm") ||
                         clean.endsWith(".mkv") || clean.endsWith(".mov") ||
                         clean.endsWith(".m4a") || clean.endsWith(".wav") ||
                         clean.endsWith(".mp3")

        try {
            val rangeRequest = Request.Builder()
                .url(streamUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Referer", referer)
                .addHeader("Range", "bytes=0-1024")
                .get()
                .build()

            httpClient.newCall(rangeRequest).execute().use { response ->
                val ct = response.header("Content-Type")?.lowercase() ?: ""
                if (response.isSuccessful || response.code == 206) {
                    if (ct.startsWith("video/") || ct.startsWith("audio/") || ct.contains("octet-stream")) {
                        return true
                    }
                    val body = response.body ?: return isMediaExt
                    val buffer = ByteArray(32)
                    val readBytes = body.byteStream().read(buffer)
                    if (readBytes >= 8) {
                        val headerStr = String(buffer, 0, readBytes, Charsets.US_ASCII)
                        if (headerStr.contains("ftyp") || headerStr.contains("moov") || headerStr.contains("ID3")) {
                            return true
                        }
                    }
                    if (!ct.contains("html") && !ct.contains("json") && (response.code == 206 || isMediaExt)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Range probe failed for $streamUrl: ${e.message}")
        }

        try {
            val headRequest = Request.Builder()
                .url(streamUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Referer", referer)
                .head()
                .build()

            httpClient.newCall(headRequest).execute().use { response ->
                val ct = response.header("Content-Type")?.lowercase() ?: ""
                if (response.isSuccessful && (ct.startsWith("video/") || ct.startsWith("audio/") || ct.contains("octet-stream") || isMediaExt)) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Head probe failed for $streamUrl: ${e.message}")
        }

        return isMediaExt
    }

    private fun isDirectVideoUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".mp4") || clean.endsWith(".webm") ||
               clean.endsWith(".mkv") || clean.endsWith(".mov") ||
               clean.endsWith(".m4a") || clean.endsWith(".wav") ||
               clean.endsWith(".mp3")
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
        referer: String? = null,
        onProgress: (percent: Float, speedKbps: Long) -> Unit
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Accept", "*/*")

        if (!referer.isNullOrBlank()) {
            requestBuilder.addHeader("Referer", referer)
        }

        val request = requestBuilder.get().build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download video stream: HTTP ${response.code}")
            }

            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            if (contentType.startsWith("text/") || contentType.contains("html") || contentType.contains("application/json")) {
                throw IllegalStateException("Stream server returned an invalid web page ($contentType) instead of video data.")
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

        // Integrity check on downloaded file
        if (!targetFile.exists() || targetFile.length() < 1000L) {
            targetFile.delete()
            throw IllegalStateException("Downloaded file is empty or incomplete.")
        }

        // Verify file is not HTML/text error response
        val header = ByteArray(64)
        var bytesRead = 0
        FileInputStream(targetFile).use { fis ->
            bytesRead = fis.read(header)
        }
        if (bytesRead > 0) {
            val headerStr = String(header, 0, bytesRead, Charsets.US_ASCII).lowercase()
            if (headerStr.contains("<!doctype") || headerStr.contains("<html") ||
                headerStr.contains("{\"error") || headerStr.contains("{\"status")
            ) {
                targetFile.delete()
                throw IllegalStateException("Downloaded stream contained web error text instead of valid video data.")
            }
        }
    }
}
