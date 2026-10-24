package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class MediaResolutionOption(
    val id: String, // "best", "1080p", "720p", "480p", "360p", "audio"
    val label: String, // e.g. "1080p (Full HD)", "720p (HD)"
    val height: Int = 0, // e.g. 1080, 720, 480, 360, 0
    val format: String = "mp4", // "mp4", "webm", "mp3"
    val sizeText: String? = null, // e.g. "45.2 MB"
    val isRecommended: Boolean = false,
    val internalKey: String? = null, // token key e.g. Y2Mate formatKey
    val directUrl: String? = null, // if already resolved
    val isFromSource: Boolean = false
) {
    companion object {
        val PRESET_OPTIONS = listOf(
            MediaResolutionOption("best", "Best (Auto)", 1080, "mp4", isRecommended = true),
            MediaResolutionOption("1080p", "1080p (Full HD)", 1080, "mp4"),
            MediaResolutionOption("720p", "720p (HD)", 720, "mp4"),
            MediaResolutionOption("480p", "480p (SD)", 480, "mp4"),
            MediaResolutionOption("360p", "360p (Fast)", 360, "mp4"),
            MediaResolutionOption("audio", "Audio Only (MP3)", 0, "mp3")
        )
    }
}

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

class SocialMediaVideoProcessor(private val context: Context? = null) {

    companion object {
        private const val TAG = "SocialMediaProcessor"

        /**
         * Convenience static method to fetch available resolutions for a URL without requiring Context.
         */
        suspend fun fetchAvailableResolutions(inputUrl: String): List<MediaResolutionOption> {
            return SocialMediaVideoProcessor().fetchAvailableResolutions(inputUrl)
        }

        private val YOUTUBE_REGEX = Pattern.compile(
            """(?:https?:\/\/)?(?:www\.|m\.)?(?:youtube\.com\/(?:watch\?v=|embed\/|v\/|shorts\/)|youtu\.be\/)([\w-]{6,})""",
            Pattern.CASE_INSENSITIVE
        )
        private val TIKTOK_REGEX = Pattern.compile("""(?:tiktok\.com)""", Pattern.CASE_INSENSITIVE)
        private val INSTAGRAM_REGEX = Pattern.compile("""(?:instagram\.com\/(?:p|reel|tv))""", Pattern.CASE_INSENSITIVE)
        private val TWITTER_REGEX = Pattern.compile("""(?:twitter\.com|x\.com)""", Pattern.CASE_INSENSITIVE)
        private val TWITTER_STATUS_REGEX = Pattern.compile(
            """(?:twitter\.com|x\.com)\/(?:[^\/\s?#]+\/status|i\/status|i\/web\/status)\/(\d+)""",
            Pattern.CASE_INSENSITIVE
        )
        private val FACEBOOK_REGEX = Pattern.compile("""(?:facebook\.com|fb\.watch)""", Pattern.CASE_INSENSITIVE)
        private val DAILYMOTION_REGEX = Pattern.compile(
            """(?:dailymotion\.com\/(?:video|embed\/video)\/|dai\.ly\/)([a-zA-Z0-9]+)""",
            Pattern.CASE_INSENSITIVE
        )
        private val VIMEO_REGEX = Pattern.compile(
            """(?:vimeo\.com\/(?:channels\/(?:\w+\/)?|groups\/[^\/]*\/videos\/|album\/(?:\d+\/)?video\/|video\/|))(\d+)""",
            Pattern.CASE_INSENSITIVE
        )
        private val URL_FINDER_REGEX = Pattern.compile("""https?:\/\/[^\s]+""", Pattern.CASE_INSENSITIVE)

        // Known public Cobalt / Video extraction instances
        private val COBALT_ENDPOINTS = listOf(
            "https://api.cobalt.tools/",
            "https://cobalt.api.scav.top/",
            "https://cobalt-backend.canine.tools/api/json",
            "https://api.kashalls.dev/",
            "https://cobalt.tools/api/json"
        )

        // Public Invidious / Piped instances for YouTube stream fallback
        private val INVIDIOUS_ENDPOINTS = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://inv.tux.pizza",
            "https://invidious.projectsegfau.lt",
            "https://invidious.private.coffee",
            "https://invidious.asir.dev",
            "https://pipedapi.kavin.rocks"
        )

        // Curated, verified working sample media streams for instant testing
        val SAMPLE_PRESETS = listOf(
            SamplePreset(
                title = "The Learning Pit (Vimeo)",
                url = "https://vimeo.com/792271600",
                platform = "Vimeo Video"
            ),
            SamplePreset(
                title = "Acoustic Guitar Track (YouTube)",
                url = "https://www.youtube.com/watch?v=HndV87XpkWg",
                platform = "YouTube Video"
            ),
            SamplePreset(
                title = "Big Buck Bunny (Direct MP4 Clip)",
                url = "https://www.w3schools.com/html/mov_bbb.mp4",
                platform = "Direct MP4 Stream"
            ),
            SamplePreset(
                title = "Vocal Song Melody (Direct MP3)",
                url = "https://raw.githubusercontent.com/rafaelreis-hotmart/Audio-Sample-files/master/sample.mp3",
                platform = "Direct Audio Track"
            ),
            SamplePreset(
                title = "Dailymotion Video Clip",
                url = "https://www.dailymotion.com/video/x8o0ocx",
                platform = "Dailymotion Link"
            ),
            SamplePreset(
                title = "ChatGPT Browsing Demo (X / Twitter)",
                url = "https://x.com/OpenAI/status/1707077710047216095",
                platform = "X (Twitter)"
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
            TWITTER_REGEX.matcher(url).find() || lower.contains("t.co/") -> "X (Twitter)"
            FACEBOOK_REGEX.matcher(url).find() -> "Facebook"
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
     * Inspects a video or stream URL and retrieves the list of available resolutions from the source
     * (such as 1080p, 720p, 480p, 360p, or audio), when available.
     */
    suspend fun fetchAvailableResolutions(inputUrl: String): List<MediaResolutionOption> = withContext(Dispatchers.IO) {
        val cleanUrl = extractUrlFromText(inputUrl) ?: inputUrl.trim()
        if (cleanUrl.isBlank()) return@withContext emptyList()

        try {
            // 1. YouTube & YouTube Shorts
            val ytId = extractYouTubeId(cleanUrl)
            if (ytId != null) {
                // Try Y2Mate formats first (accurate size, bitrates, format keys)
                val y2Formats = fetchY2MateFormats(ytId)
                if (y2Formats.isNotEmpty()) {
                    return@withContext y2Formats
                }

                // Fallback to InnerTube formats
                val directYtFormats = fetchYouTubeDirectFormats(ytId)
                if (directYtFormats.isNotEmpty()) {
                    return@withContext directYtFormats
                }

                // Standard YouTube resolutions if source inspection is throttled
                return@withContext listOf(
                    MediaResolutionOption("1080p", "1080p (Full HD)", 1080, "mp4", isRecommended = true, isFromSource = true),
                    MediaResolutionOption("720p", "720p (HD)", 720, "mp4", isFromSource = true),
                    MediaResolutionOption("480p", "480p (SD)", 480, "mp4", isFromSource = true),
                    MediaResolutionOption("360p", "360p (Fast)", 360, "mp4", isFromSource = true),
                    MediaResolutionOption("audio", "Audio Only (MP3)", 0, "mp3", isFromSource = true)
                )
            }

            // 2. Vimeo
            val vimeoMatcher = VIMEO_REGEX.matcher(cleanUrl)
            if (vimeoMatcher.find() || cleanUrl.lowercase().contains("vimeo.com/")) {
                val vimeoId = if (vimeoMatcher.find(0)) vimeoMatcher.group(1) else cleanUrl.substringAfterLast('/').substringBefore('?')
                if (!vimeoId.isNullOrBlank() && vimeoId.all { it.isDigit() }) {
                    val vimeoFormats = fetchVimeoFormats(vimeoId)
                    if (vimeoFormats.isNotEmpty()) {
                        return@withContext vimeoFormats
                    }
                }
            }

            // 3. Dailymotion
            val dmMatcher = DAILYMOTION_REGEX.matcher(cleanUrl)
            if (dmMatcher.find()) {
                val dmId = dmMatcher.group(1)
                if (!dmId.isNullOrBlank()) {
                    val dmFormats = fetchDailymotionFormats(dmId)
                    if (dmFormats.isNotEmpty()) {
                        return@withContext dmFormats
                    }
                }
            }

            // 4. X (formerly Twitter) format resolution
            val twitterUrl = if (cleanUrl.contains("t.co/")) resolveRedirect(cleanUrl) else cleanUrl
            val twitterStatusId = extractTwitterStatusId(twitterUrl)
            if (twitterStatusId != null) {
                val twitterFormats = fetchTwitterFormats(twitterStatusId)
                if (twitterFormats.isNotEmpty()) {
                    return@withContext twitterFormats
                }
            }

            // 5. HLS Stream (.m3u8)
            if (cleanUrl.contains(".m3u8")) {
                val hlsFormats = fetchHlsFormats(cleanUrl)
                if (hlsFormats.isNotEmpty()) {
                    return@withContext hlsFormats
                }
            }

            // 5. Direct media file link
            if (isDirectVideoUrl(cleanUrl)) {
                val ext = cleanUrl.substringBefore('?').substringAfterLast('.').lowercase()
                return@withContext listOf(
                    MediaResolutionOption("original", "Source Quality ($ext)", 1080, ext, directUrl = cleanUrl, isRecommended = true, isFromSource = true),
                    MediaResolutionOption("audio", "Audio Only (Extract Track)", 0, ext, directUrl = cleanUrl, isFromSource = true)
                )
            }

            // 6. Generic web / social fallback
            return@withContext listOf(
                MediaResolutionOption("best", "Best Available (Auto)", 1080, "mp4", isRecommended = true, isFromSource = false),
                MediaResolutionOption("1080p", "1080p (Full HD)", 1080, "mp4", isFromSource = false),
                MediaResolutionOption("720p", "720p (HD)", 720, "mp4", isFromSource = false),
                MediaResolutionOption("480p", "480p (SD)", 480, "mp4", isFromSource = false),
                MediaResolutionOption("360p", "360p (Fast)", 360, "mp4", isFromSource = false),
                MediaResolutionOption("audio", "Audio Only (MP3)", 0, "mp3", isFromSource = false)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect resolutions from $cleanUrl", e)
            MediaResolutionOption.PRESET_OPTIONS
        }
    }

    /**
     * Resolves the stream or download URL for YouTube, social media, or direct video link,
     * then downloads the MP4/media and extracts duration metadata.
     */
    suspend fun processAndDownload(
        inputUrl: String,
        targetResolution: MediaResolutionOption? = null,
        onProgress: (DownloadProgressState) -> Unit
    ): Result<ExtractedVideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = extractUrlFromText(inputUrl) ?: inputUrl.trim()
        val platform = detectPlatform(cleanUrl)
        val resTag = targetResolution?.let { " [${it.label}]" } ?: ""
        Log.i(TAG, "Processing video URL: $cleanUrl [Detected: $platform]$resTag")

        onProgress(DownloadProgressState.Resolving("Analyzing $platform link$resTag…"))

        try {
            // 1. Resolve direct download stream URL
            val streamResult = resolveMediaStream(cleanUrl, platform, targetResolution, onProgress)
            val streamUrl = streamResult.url
            val titleCandidate = streamResult.title ?: "$platform Video"
            val referer = streamResult.referer ?: cleanUrl

            Log.i(TAG, "Resolved stream URL: $streamUrl, Title: $titleCandidate, Referer: $referer")

            // 2. Download the video stream to app storage
            val baseDir = context?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "app_files")
            val downloadsDir = File(baseDir, "downloads").apply { if (!exists()) mkdirs() }
            val videoId = UUID.randomUUID().toString().substring(0, 8)
            val cleanStream = streamUrl.substringBefore('?').lowercase()
            val fileExt = when {
                cleanStream.endsWith(".mp3") || targetResolution?.id == "audio" -> ".mp3"
                cleanStream.endsWith(".m4a") -> ".m4a"
                cleanStream.endsWith(".webm") -> ".webm"
                cleanStream.endsWith(".wav") -> ".wav"
                else -> ".mp4"
            }
            val cleanPlatformTag = platform.lowercase().replace(Regex("[^a-z0-9_]"), "_")
            val targetFile = File(downloadsDir, "media_${cleanPlatformTag}_$videoId$fileExt")

            onProgress(DownloadProgressState.Downloading(0.01f))
            if (streamUrl.contains(".m3u8")) {
                downloadHlsStream(streamUrl, targetFile, referer, targetResolution) { percent ->
                    onProgress(DownloadProgressState.Downloading(percent))
                }
            } else {
                downloadFile(streamUrl, targetFile, referer) { percent, speed ->
                    onProgress(DownloadProgressState.Downloading(percent, speed))
                }
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
        targetResolution: MediaResolutionOption?,
        onProgress: (DownloadProgressState) -> Unit
    ): StreamResolution {
        // 0. If direct stream URL was already resolved during format inspection
        if (!targetResolution?.directUrl.isNullOrBlank()) {
            return StreamResolution(targetResolution!!.directUrl!!, "$platform Video", referer = url)
        }

        // 1. Direct media URL check (.mp4, .mkv, .webm, .mov, .m4a, .wav, .mp3)
        if (isDirectVideoUrl(url)) {
            return StreamResolution(url, extractFilenameFromUrl(url), referer = url)
        }

        // 2. YouTube & YouTube Shorts extraction
        val ytId = extractYouTubeId(url)
        if (ytId != null) {
            // Strategy A: Y2Mate high-speed stream converter with targeted format
            try {
                onProgress(DownloadProgressState.Resolving("Requesting stream converter for YouTube…"))
                val y2Res = queryY2Mate(ytId, targetResolution, onProgress)
                if (y2Res != null) {
                    return y2Res
                }
            } catch (e: Exception) {
                Log.w(TAG, "Y2Mate resolution failed for $ytId", e)
            }

            // Strategy B (Primary & Verified): loader.to stream converter
            try {
                onProgress(DownloadProgressState.Resolving("Requesting stream converter for YouTube…"))
                val loaderRes = queryLoaderTo(ytId, targetResolution, onProgress)
                if (loaderRes != null) {
                    return loaderRes
                }
            } catch (e: Exception) {
                Log.w(TAG, "Loader.to resolution failed for $ytId", e)
            }

            // Strategy C: Direct YouTube InnerTube client extraction
            try {
                onProgress(DownloadProgressState.Resolving("Extracting direct YouTube stream…"))
                val directYt = queryYouTubeDirect(ytId, targetResolution)
                if (directYt != null) {
                    return directYt
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct YouTube extraction failed for $ytId", e)
            }

            // Strategy D: Invidious / Piped public instances
            for (endpoint in INVIDIOUS_ENDPOINTS) {
                try {
                    onProgress(DownloadProgressState.Resolving("Checking alternative stream server…"))
                    val resolution = queryInvidiousApi(endpoint, ytId, targetResolution)
                    if (resolution != null) {
                        return resolution
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invidious endpoint failed: $endpoint", e)
                }
            }

            // Fallback for YouTube: Cobalt APIs
            for (endpoint in COBALT_ENDPOINTS) {
                try {
                    onProgress(DownloadProgressState.Resolving("Checking alternative stream converter…"))
                    val res = queryCobaltApi(endpoint, url, targetResolution)
                    if (res != null) {
                        return res
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cobalt failed for YouTube $ytId: $endpoint", e)
                }
            }
        }

        // 3. Vimeo video stream resolution (config JSON + player metadata)
        val vimeoMatcher = VIMEO_REGEX.matcher(url)
        if (vimeoMatcher.find() || url.lowercase().contains("vimeo.com/")) {
            val vimeoId = if (vimeoMatcher.find(0)) vimeoMatcher.group(1) else url.substringAfterLast('/').substringBefore('?')
            if (!vimeoId.isNullOrBlank() && vimeoId.all { it.isDigit() }) {
                onProgress(DownloadProgressState.Resolving("Resolving Vimeo video stream ($vimeoId)…"))
                try {
                    val vimeoRes = queryVimeoStream(vimeoId, targetResolution)
                    if (vimeoRes != null) {
                        return vimeoRes
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Vimeo stream resolution failed for $vimeoId", e)
                }
            }
        }

        // 4. Dailymotion metadata extraction (official HLS stream)
        val dmMatcher = DAILYMOTION_REGEX.matcher(url)
        if (dmMatcher.find()) {
            val dmId = dmMatcher.group(1)
            if (!dmId.isNullOrBlank()) {
                onProgress(DownloadProgressState.Resolving("Resolving Dailymotion video stream ($dmId)…"))
                try {
                    val dmRes = queryDailymotionHls(dmId, targetResolution)
                    if (dmRes != null) {
                        return dmRes
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Dailymotion HLS resolution failed for $dmId", e)
                }
            }
        }

        // 5. Facebook video resolution (embed player page scraper)
        if (FACEBOOK_REGEX.matcher(url).find()) {
            onProgress(DownloadProgressState.Resolving("Extracting Facebook video stream…"))
            try {
                val fbRes = queryFacebookEmbed(url)
                if (fbRes != null) {
                    return fbRes
                }
            } catch (e: Exception) {
                Log.w(TAG, "Facebook embed resolution failed for $url", e)
            }
        }

        // 6. TikTok extraction via TikWM API
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

        // 7. X (Twitter) video resolution via Syndication & FxTwitter APIs
        val resolvedTwitterUrl = if (url.contains("t.co/")) resolveRedirect(url) else url
        val twitterStatusId = extractTwitterStatusId(resolvedTwitterUrl)
        if (twitterStatusId != null) {
            onProgress(DownloadProgressState.Resolving("Extracting X (Twitter) video stream…"))
            try {
                val twitterRes = resolveTwitterVideo(twitterStatusId, targetResolution)
                if (twitterRes != null) {
                    return twitterRes
                }
            } catch (e: Exception) {
                Log.w(TAG, "Twitter video resolution failed for $resolvedTwitterUrl", e)
            }
        }

        // 8. Universal HTML5 Web Scraper & Stream Resolver
        // (Supports VidLii, Dailymotion, Vimeo, Rumble, Internet Archive, Reddit, blogs, news, and HTML5 video sites)
        try {
            val webMedia = extractWebPageMedia(url, platform, onProgress)
            if (webMedia != null) {
                return webMedia
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTML5 web media extraction failed for $url", e)
        }

        // 8. Cobalt API resolution for supported social networks (Instagram, Twitter, etc.)
        for (endpoint in COBALT_ENDPOINTS) {
            try {
                onProgress(DownloadProgressState.Resolving("Connecting to stream resolver ($platform)…"))
                val resolution = queryCobaltApi(endpoint, url, targetResolution)
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

    private fun fetchY2MateFormats(videoId: String): List<MediaResolutionOption> {
        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
        val analyzeEndpoints = listOf(
            "https://www.y2mate.com/mates/analyzeV2/ajax",
            "https://t-y2mate.com/mates/analyzeV2/ajax"
        )
        for (endpoint in analyzeEndpoints) {
            try {
                val formBody = okhttp3.FormBody.Builder()
                    .add("k_query", ytUrl)
                    .add("k_page", "home")
                    .add("hl", "en")
                    .add("q_auto", "0")
                    .build()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", "https://www.y2mate.com/en")
                    .post(formBody)
                    .build()

                val respStr = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: continue

                val json = JSONObject(respStr)
                if (json.optString("status") != "ok") continue
                val links = json.optJSONObject("links") ?: continue
                val result = mutableListOf<MediaResolutionOption>()

                val mp4Obj = links.optJSONObject("mp4")
                if (mp4Obj != null) {
                    val keys = mp4Obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val item = mp4Obj.optJSONObject(k) ?: continue
                        val q = item.optString("q")
                        val qText = item.optString("q_text")
                        val size = item.optString("size").takeIf { it.isNotBlank() && it != "null" }
                        val keyVal = item.optString("k")
                        if (keyVal.isBlank()) continue

                        val height = when {
                            q.contains("1080") || qText.contains("1080") -> 1080
                            q.contains("720") || qText.contains("720") -> 720
                            q.contains("480") || qText.contains("480") -> 480
                            q.contains("360") || qText.contains("360") -> 360
                            q.contains("240") || qText.contains("240") -> 240
                            q.contains("144") || qText.contains("144") -> 144
                            else -> 720
                        }
                        val label = when (height) {
                            1080 -> "1080p (Full HD)"
                            720 -> "720p (HD)"
                            480 -> "480p (SD)"
                            360 -> "360p (Fast)"
                            240 -> "240p"
                            144 -> "144p"
                            else -> "${height}p"
                        }

                        if (result.none { it.height == height }) {
                            result.add(
                                MediaResolutionOption(
                                    id = "${height}p",
                                    label = label,
                                    height = height,
                                    format = "mp4",
                                    sizeText = size,
                                    internalKey = keyVal,
                                    isRecommended = height == 720 || (height == 1080 && result.isEmpty()),
                                    isFromSource = true
                                )
                            )
                        }
                    }
                }

                // Check MP3 audio track
                val mp3Obj = links.optJSONObject("mp3")
                if (mp3Obj != null) {
                    val keys = mp3Obj.keys()
                    if (keys.hasNext()) {
                        val k = keys.next()
                        val item = mp3Obj.optJSONObject(k)
                        val size = item?.optString("size")?.takeIf { it.isNotBlank() && it != "null" }
                        val keyVal = item?.optString("k")
                        if (!keyVal.isNullOrBlank()) {
                            result.add(
                                MediaResolutionOption(
                                    id = "audio",
                                    label = "Audio Only (MP3)",
                                    height = 0,
                                    format = "mp3",
                                    sizeText = size,
                                    internalKey = keyVal,
                                    isFromSource = true
                                )
                            )
                        }
                    }
                }

                if (result.isNotEmpty()) {
                    return result.sortedByDescending { it.height }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Y2Mate format inspection failed for $videoId", e)
            }
        }
        return emptyList()
    }

    private fun fetchVimeoFormats(videoId: String): List<MediaResolutionOption> {
        val embedUrl = "https://player.vimeo.com/video/$videoId?app_id=122963"
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://vimeo.com/")
                .get()
                .build()
            val html = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            val configPrefix = when {
                html.contains("window.playerConfig = ") -> "window.playerConfig = "
                html.contains("var config = ") -> "var config = "
                else -> null
            }
            if (configPrefix != null) {
                val startIndex = html.indexOf(configPrefix) + configPrefix.length
                val endIndex = html.indexOf("</script>", startIndex)
                if (startIndex > 0 && endIndex > startIndex) {
                    val jsonStr = html.substring(startIndex, endIndex).trim().removeSuffix(";")
                    val json = JSONObject(jsonStr)
                    val progressive = json.optJSONObject("request")?.optJSONObject("files")?.optJSONArray("progressive")
                    if (progressive != null && progressive.length() > 0) {
                        val result = mutableListOf<MediaResolutionOption>()
                        for (i in 0 until progressive.length()) {
                            val item = progressive.getJSONObject(i)
                            val streamUrl = item.optString("url")
                            val width = item.optInt("width", 0)
                            val height = item.optInt("height", 0).takeIf { it > 0 } ?: (width * 9 / 16)
                            val label = when {
                                height >= 1080 || width >= 1920 -> "1080p (Full HD)"
                                height >= 720 || width >= 1280 -> "720p (HD)"
                                height >= 480 || width >= 850 -> "480p (SD)"
                                height >= 360 || width >= 600 -> "360p (Fast)"
                                else -> "${height}p"
                            }
                            if (streamUrl.isNotBlank() && result.none { it.height == height }) {
                                result.add(
                                    MediaResolutionOption(
                                        id = "${height}p",
                                        label = label,
                                        height = height,
                                        format = "mp4",
                                        directUrl = streamUrl,
                                        isRecommended = height == 720 || height == 1080,
                                        isFromSource = true
                                    )
                                )
                            }
                        }
                        if (result.isNotEmpty()) {
                            return result.sortedByDescending { it.height }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo format inspection failed for $videoId", e)
        }
        return emptyList()
    }

    private fun fetchDailymotionFormats(videoId: String): List<MediaResolutionOption> {
        try {
            val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val request = Request.Builder()
                .url(metaUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://www.dailymotion.com/video/$videoId")
                .get()
                .build()
            val bodyStr = httpClient.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
            val json = JSONObject(bodyStr)
            val qualities = json.optJSONObject("qualities") ?: return emptyList()
            val result = mutableListOf<MediaResolutionOption>()
            val keys = qualities.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k == "auto") continue
                val qArr = qualities.optJSONArray(k)
                val streamUrl = qArr?.optJSONObject(0)?.optString("url")
                val h = k.toIntOrNull() ?: 720
                val label = when (h) {
                    1080 -> "1080p (Full HD)"
                    720 -> "720p (HD)"
                    480 -> "480p (SD)"
                    380 -> "380p"
                    240 -> "240p"
                    else -> "${h}p"
                }
                if (!streamUrl.isNullOrBlank() && result.none { it.height == h }) {
                    result.add(
                        MediaResolutionOption(
                            id = "${h}p",
                            label = label,
                            height = h,
                            format = "mp4",
                            directUrl = streamUrl,
                            isRecommended = h == 720,
                            isFromSource = true
                        )
                    )
                }
            }
            return result.sortedByDescending { it.height }
        } catch (e: Exception) {
            Log.w(TAG, "Dailymotion format inspection failed for $videoId", e)
            return emptyList()
        }
    }

    private fun fetchYouTubeDirectFormats(videoId: String): List<MediaResolutionOption> {
        try {
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
            val bodyStr = httpClient.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
            val json = JSONObject(bodyStr)
            val streamingData = json.optJSONObject("streamingData") ?: return emptyList()
            val result = mutableListOf<MediaResolutionOption>()

            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(i)
                    val url = fmt.optString("url")
                    val height = fmt.optInt("height", 0)
                    val quality = fmt.optString("qualityLabel", "${height}p")
                    val contentLength = fmt.optLong("contentLength", 0L)
                    val sizeText = if (contentLength > 0) "%.1f MB".format(contentLength / (1024.0 * 1024.0)) else null
                    if (url.isNotBlank() && height > 0 && result.none { it.height == height }) {
                        result.add(
                            MediaResolutionOption(
                                id = "${height}p",
                                label = "$quality ${if (height >= 1080) "(Full HD)" else if (height >= 720) "(HD)" else ""}".trim(),
                                height = height,
                                format = "mp4",
                                sizeText = sizeText,
                                directUrl = url,
                                isRecommended = height == 720,
                                isFromSource = true
                            )
                        )
                    }
                }
            }

            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.getJSONObject(i)
                    val mime = fmt.optString("mimeType")
                    val url = fmt.optString("url")
                    val contentLength = fmt.optLong("contentLength", 0L)
                    val sizeText = if (contentLength > 0) "%.1f MB".format(contentLength / (1024.0 * 1024.0)) else null
                    if (mime.startsWith("audio/mp4") && url.isNotBlank()) {
                        result.add(
                            MediaResolutionOption(
                                id = "audio",
                                label = "Audio Only (M4A)",
                                height = 0,
                                format = "m4a",
                                sizeText = sizeText,
                                directUrl = url,
                                isFromSource = true
                            )
                        )
                        break
                    }
                }
            }
            return result.sortedByDescending { it.height }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube direct formats inspection failed for $videoId", e)
            return emptyList()
        }
    }

    private fun fetchHlsFormats(m3u8Url: String): List<MediaResolutionOption> {
        try {
            val req = Request.Builder().url(m3u8Url).build()
            val manifest = httpClient.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
            val result = mutableListOf<MediaResolutionOption>()
            val lines = manifest.lines()
            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val resMatcher = Pattern.compile("""RESOLUTION=(\d+)x(\d+)""").matcher(line)
                    val bwMatcher = Pattern.compile("""BANDWIDTH=(\d+)""").matcher(line)
                    if (resMatcher.find()) {
                        val height = resMatcher.group(2).toIntOrNull() ?: 720
                        val bw = if (bwMatcher.find()) bwMatcher.group(1).toLongOrNull() else null
                        val bwText = if (bw != null) "${bw / 1000} kbps" else null
                        val nextUrl = if (i + 1 < lines.size) resolveRelativeUrl(m3u8Url, lines[i + 1].trim()) else null
                        val label = when {
                            height >= 1080 -> "1080p (Full HD)"
                            height >= 720 -> "720p (HD)"
                            height >= 480 -> "480p (SD)"
                            height >= 360 -> "360p (Fast)"
                            else -> "${height}p"
                        }
                        if (result.none { it.height == height }) {
                            result.add(
                                MediaResolutionOption(
                                    id = "${height}p",
                                    label = label,
                                    height = height,
                                    format = "mp4",
                                    sizeText = bwText,
                                    directUrl = nextUrl,
                                    isRecommended = height == 720,
                                    isFromSource = true
                                )
                            )
                        }
                    }
                }
            }
            return result.sortedByDescending { it.height }
        } catch (e: Exception) {
            Log.w(TAG, "HLS format inspection failed for $m3u8Url", e)
            return emptyList()
        }
    }

    private fun queryYouTubeDirect(videoId: String, targetResolution: MediaResolutionOption? = null): StreamResolution? {
        val clientConfigs = listOf(
            Triple("ANDROID_VR", "1.61.48", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"),
            Triple("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0", "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1"),
            Triple("WEB_EMBEDDED_PLAYER", "1.20231009.01.00", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        )

        val targetHeight = targetResolution?.height ?: 720
        val isAudioOnly = targetResolution?.id == "audio" || targetResolution?.height == 0

        for ((cName, cVer, userAgent) in clientConfigs) {
            try {
                val payload = JSONObject().apply {
                    put("videoId", videoId)
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", cName)
                            put("clientVersion", cVer)
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
                    .addHeader("User-Agent", userAgent)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val json = JSONObject(bodyStr)
                    val title = json.optJSONObject("videoDetails")?.optString("title") ?: "YouTube Video"
                    val streamingData = json.optJSONObject("streamingData") ?: return@use

                    // 1. If not audio-only, check video formats with closest resolution match
                    if (!isAudioOnly) {
                        val formats = streamingData.optJSONArray("formats")
                        if (formats != null && formats.length() > 0) {
                            var bestUrl: String? = null
                            var bestDiff = Int.MAX_VALUE
                            for (i in 0 until formats.length()) {
                                val fmt = formats.getJSONObject(i)
                                val streamUrl = fmt.optString("url")
                                val h = fmt.optInt("height", 0)
                                val mime = fmt.optString("mimeType")
                                if (streamUrl.isNotBlank() && mime.contains("mp4", ignoreCase = true)) {
                                    val diff = kotlin.math.abs(h - targetHeight)
                                    if (diff < bestDiff) {
                                        bestDiff = diff
                                        bestUrl = streamUrl
                                    }
                                }
                            }
                            if (bestUrl != null) {
                                return StreamResolution(bestUrl, "YouTube - $title")
                            }
                            val firstUrl = formats.getJSONObject(0).optString("url")
                            if (firstUrl.isNotBlank()) {
                                return StreamResolution(firstUrl, "YouTube - $title")
                            }
                        }
                    }

                    // 2. Adaptive audio formats (AAC / MP4 audio stream)
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
            } catch (e: Exception) {
                Log.w(TAG, "InnerTube client $cName attempt failed for $videoId", e)
            }
        }
        return null
    }

    private suspend fun queryLoaderTo(
        videoId: String,
        targetResolution: MediaResolutionOption? = null,
        onProgress: (DownloadProgressState) -> Unit
    ): StreamResolution? = withContext(Dispatchers.IO) {
        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
        val encodedYtUrl = URLEncoder.encode(ytUrl, "UTF-8")
        val isAudio = targetResolution?.id == "audio" || targetResolution?.height == 0
        val targetFmt = when {
            isAudio -> "mp3"
            targetResolution?.height == 1080 -> "1080"
            targetResolution?.height == 720 -> "720"
            targetResolution?.height == 480 -> "480"
            targetResolution?.height == 360 -> "360"
            else -> "720"
        }
        val formats = if (isAudio) {
            listOf("mp3")
        } else {
            listOf(targetFmt, "720", "360", "1080", "480", "mp3").distinct()
        }

        for (fmt in formats) {
            try {
                val initUrl = "https://loader.to/ajax/download.php?format=$fmt&url=$encodedYtUrl"
                val initReq = Request.Builder()
                    .url(initUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .addHeader("Referer", "https://en.loader.to/")
                    .get()
                    .build()

                val initRespStr = httpClient.newCall(initReq).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: continue

                val initJson = JSONObject(initRespStr)
                if (!initJson.optBoolean("success", false)) continue

                val downloadId = initJson.optString("id")
                val title = initJson.optString("title").ifBlank { "YouTube Video" }
                if (downloadId.isBlank()) continue

                // Retrieve progress endpoint from init response or fallback to active domain
                val rawProgressUrl = initJson.optString("progress_url")
                val baseProgressUrl = if (rawProgressUrl.isNotBlank() && rawProgressUrl != "null") {
                    rawProgressUrl
                } else {
                    "https://lto2.affadaffa.com/api/progress?id=$downloadId"
                }

                // Poll progress URL up to 60 times (~90-120s max for long videos)
                var downloadUrl: String? = null
                for (pollCount in 1..60) {
                    val waitMs = if (pollCount <= 10) 1500L else 2000L
                    delay(waitMs)
                    val progReq = Request.Builder()
                        .url(baseProgressUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .addHeader("Referer", "https://en.loader.to/")
                        .get()
                        .build()

                    val progRespStr = httpClient.newCall(progReq).execute().use { resp ->
                        if (!resp.isSuccessful) null else resp.body?.string()
                    } ?: continue

                    val progJson = JSONObject(progRespStr)
                    val progressVal = (progJson.optInt("progress", 0) / 10).coerceIn(0, 100)
                    val textMsg = progJson.optString("text", "")
                    if (textMsg.isNotBlank()) {
                        onProgress(DownloadProgressState.Resolving("Converting long video stream: $textMsg ($progressVal%)…"))
                    }

                    val candidate = progJson.optString("download_url")
                    if (!candidate.isNullOrBlank() && candidate != "null") {
                        downloadUrl = candidate
                        break
                    }

                    if (progJson.optInt("success", 0) == 1 && candidate.isNotBlank()) {
                        downloadUrl = candidate
                        break
                    }
                }

                if (!downloadUrl.isNullOrBlank()) {
                    Log.i(TAG, "Loader.to successfully resolved YouTube download URL: $downloadUrl")
                    return@withContext StreamResolution(downloadUrl, "YouTube - $title", referer = "https://en.loader.to/")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Loader.to format $fmt failed for $videoId", e)
            }
        }
        null
    }

    private suspend fun queryY2Mate(
        videoId: String,
        targetResolution: MediaResolutionOption? = null,
        onProgress: (DownloadProgressState) -> Unit
    ): StreamResolution? = withContext(Dispatchers.IO) {
        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
        val isAudio = targetResolution?.id == "audio" || targetResolution?.height == 0
        val targetHeight = targetResolution?.height ?: 720

        val analyzeEndpoints = listOf(
            "https://www.y2mate.com/mates/analyzeV2/ajax",
            "https://t-y2mate.com/mates/analyzeV2/ajax"
        )

        for (endpoint in analyzeEndpoints) {
            try {
                onProgress(DownloadProgressState.Resolving("Analyzing YouTube stream format…"))
                val formBody = okhttp3.FormBody.Builder()
                    .add("k_query", ytUrl)
                    .add("k_page", "home")
                    .add("hl", "en")
                    .add("q_auto", "0")
                    .build()

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", "https://www.y2mate.com/en")
                    .post(formBody)
                    .build()

                val analyzeRespStr = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: continue

                val analyzeJson = JSONObject(analyzeRespStr)
                if (analyzeJson.optString("status") != "ok") continue

                val vidTitle = analyzeJson.optString("title").ifBlank { "YouTube Video" }
                val links = analyzeJson.optJSONObject("links") ?: continue

                var formatKey: String? = targetResolution?.internalKey

                if (formatKey.isNullOrBlank()) {
                    if (isAudio) {
                        val mp3Obj = links.optJSONObject("mp3")
                        if (mp3Obj != null) {
                            val keys = mp3Obj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val item = mp3Obj.optJSONObject(k)
                                val kVal = item?.optString("k")
                                if (!kVal.isNullOrBlank()) {
                                    formatKey = kVal
                                    break
                                }
                            }
                        }
                    } else {
                        val mp4Obj = links.optJSONObject("mp4")
                        if (mp4Obj != null) {
                            // Find closest match to targetHeight
                            var bestDiff = Int.MAX_VALUE
                            val keys = mp4Obj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val item = mp4Obj.optJSONObject(k)
                                val q = item?.optString("q") ?: ""
                                val qText = item?.optString("q_text") ?: ""
                                val kVal = item?.optString("k")
                                if (!kVal.isNullOrBlank()) {
                                    val h = when {
                                        q.contains("1080") || qText.contains("1080") -> 1080
                                        q.contains("720") || qText.contains("720") -> 720
                                        q.contains("480") || qText.contains("480") -> 480
                                        q.contains("360") || qText.contains("360") -> 360
                                        else -> 720
                                    }
                                    val diff = kotlin.math.abs(h - targetHeight)
                                    if (diff < bestDiff) {
                                        bestDiff = diff
                                        formatKey = kVal
                                    }
                                }
                            }
                        }
                    }
                }

                if (formatKey.isNullOrBlank()) continue

                // Step 2: Request conversion
                onProgress(DownloadProgressState.Resolving("Preparing YouTube media download link…"))
                val convEndpoint = endpoint.replace("analyzeV2/ajax", "convertV2/index")
                val convForm = okhttp3.FormBody.Builder()
                    .add("vid", videoId)
                    .add("k", formatKey)
                    .build()

                val convReq = Request.Builder()
                    .url(convEndpoint)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", "https://www.y2mate.com/en")
                    .post(convForm)
                    .build()

                val convRespStr = httpClient.newCall(convReq).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: continue

                val convJson = JSONObject(convRespStr)
                if (convJson.optString("status") == "ok") {
                    val dlink = convJson.optString("dlink")
                    if (dlink.isNotBlank()) {
                        Log.i(TAG, "Y2Mate resolved stream: $dlink")
                        return@withContext StreamResolution(dlink, "YouTube - $vidTitle", referer = "https://www.y2mate.com/")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Y2Mate extraction attempt failed for $videoId", e)
            }
        }
        null
    }

    private fun queryDailymotionHls(videoId: String, targetResolution: MediaResolutionOption? = null): StreamResolution? {
        val metaUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
        val request = Request.Builder()
            .url(metaUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Referer", "https://www.dailymotion.com/video/$videoId")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val json = JSONObject(bodyStr)
            val title = json.optString("title").ifBlank { "Dailymotion Video" }
            val qualities = json.optJSONObject("qualities") ?: return null

            // If target resolution height is specified, check that specific quality entry first
            if (targetResolution != null && targetResolution.height > 0) {
                val qArr = qualities.optJSONArray("${targetResolution.height}")
                if (qArr != null && qArr.length() > 0) {
                    val streamUrl = qArr.getJSONObject(0).optString("url")
                    if (streamUrl.isNotBlank()) {
                        return StreamResolution(streamUrl, "Dailymotion - $title", referer = "https://www.dailymotion.com/video/$videoId")
                    }
                }
            }

            val autoArr = qualities.optJSONArray("auto")
            if (autoArr != null && autoArr.length() > 0) {
                val streamUrl = autoArr.getJSONObject(0).optString("url")
                if (streamUrl.isNotBlank()) {
                    return StreamResolution(streamUrl, "Dailymotion - $title", referer = "https://www.dailymotion.com/video/$videoId")
                }
            }

            // Check specific quality entries (1080, 720, 480, 380, etc.)
            val keys = qualities.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val qArr = qualities.optJSONArray(k)
                if (qArr != null && qArr.length() > 0) {
                    val streamUrl = qArr.getJSONObject(0).optString("url")
                    if (streamUrl.isNotBlank()) {
                        return StreamResolution(streamUrl, "Dailymotion - $title", referer = "https://www.dailymotion.com/video/$videoId")
                    }
                }
            }
        }
        return null
    }

    private fun queryVimeoStream(videoId: String, targetResolution: MediaResolutionOption? = null): StreamResolution? {
        val embedUrl = "https://player.vimeo.com/video/$videoId?app_id=122963"
        val targetHeight = targetResolution?.height ?: 720

        // Strategy 1: Fetch title and thumbnail from public Vimeo oEmbed API
        var oembedTitle: String? = null
        try {
            val oembedReq = Request.Builder()
                .url("https://vimeo.com/api/oembed.json?url=https%3A%2F%2Fvimeo.com%2F$videoId")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()
            httpClient.newCall(oembedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val oembedBody = resp.body?.string()
                    if (!oembedBody.isNullOrBlank()) {
                        val oembedJson = JSONObject(oembedBody)
                        val t = oembedJson.optString("title")
                        if (t.isNotBlank()) oembedTitle = t
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo oembed query failed for $videoId", e)
        }

        // Strategy 2: Vimeo player embed page scraping with iframe navigation headers (Bypasses Cloudflare block)
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://vimeo.com/")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Sec-Fetch-Dest", "iframe")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-Site", "cross-site")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""

                    // Extract window.playerConfig = { ... }
                    val configPrefix = when {
                        html.contains("window.playerConfig = ") -> "window.playerConfig = "
                        html.contains("var config = ") -> "var config = "
                        else -> null
                    }

                    if (configPrefix != null) {
                        val startIndex = html.indexOf(configPrefix) + configPrefix.length
                        val endIndex = html.indexOf("</script>", startIndex)
                        if (startIndex > 0 && endIndex > startIndex) {
                            val configJsonStr = html.substring(startIndex, endIndex).trim().removeSuffix(";")
                            try {
                                val json = JSONObject(configJsonStr)
                                val videoObj = json.optJSONObject("video")
                                val title = oembedTitle ?: videoObj?.optString("title") ?: "Vimeo Video"
                                val files = json.optJSONObject("request")?.optJSONObject("files")

                                // 1. Check progressive MP4 files matching target quality
                                val progressive = files?.optJSONArray("progressive")
                                if (progressive != null && progressive.length() > 0) {
                                    var bestUrl: String? = null
                                    var bestDiff = Int.MAX_VALUE
                                    for (i in 0 until progressive.length()) {
                                        val item = progressive.getJSONObject(i)
                                        val streamUrl = item.optString("url")
                                        val width = item.optInt("width", 0)
                                        val h = item.optInt("height", 0).takeIf { it > 0 } ?: (width * 9 / 16)
                                        if (streamUrl.isNotBlank()) {
                                            val diff = kotlin.math.abs(h - targetHeight)
                                            if (diff < bestDiff) {
                                                bestDiff = diff
                                                bestUrl = streamUrl
                                            }
                                        }
                                    }
                                    if (bestUrl != null) {
                                        return StreamResolution(bestUrl, "Vimeo - $title", referer = "https://player.vimeo.com/")
                                    }
                                }

                                // 2. Check HLS stream
                                val hls = files?.optJSONObject("hls")
                                val defaultCdn = hls?.optString("default_cdn")
                                val cdns = hls?.optJSONObject("cdns")
                                var m3u8Url: String? = null
                                if (cdns != null) {
                                    if (!defaultCdn.isNullOrBlank() && cdns.has(defaultCdn)) {
                                        m3u8Url = cdns.optJSONObject(defaultCdn)?.optString("url")
                                    }
                                    if (m3u8Url.isNullOrBlank()) {
                                        val keys = cdns.keys()
                                        while (keys.hasNext()) {
                                            val cdn = cdns.optJSONObject(keys.next())
                                            val u = cdn?.optString("url")
                                            if (!u.isNullOrBlank()) {
                                                m3u8Url = u
                                                break
                                            }
                                        }
                                    }
                                }

                                if (!m3u8Url.isNullOrBlank()) {
                                    return StreamResolution(m3u8Url, "Vimeo - $title", referer = "https://player.vimeo.com/")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse playerConfig JSON for Vimeo $videoId", e)
                            }
                        }
                    }

                    // Fallback pattern matching on embed HTML
                    val title = oembedTitle ?: extractTitleFromHtml(html) ?: "Vimeo Video"
                    val mp4Matcher = Pattern.compile("""["'](https?:\/\/[^"']+\.mp4[^"']*)["']""").matcher(html)
                    while (mp4Matcher.find()) {
                        val candidate = mp4Matcher.group(1).replace("\\/", "/")
                        if (candidate.contains("vod-progressive") || candidate.contains("vimeo") || candidate.contains("akamaized")) {
                            return StreamResolution(candidate, "Vimeo - $title", referer = "https://player.vimeo.com/")
                        }
                    }

                    val hlsMatcher = Pattern.compile("""["'](https?:\/\/[^"']+\.m3u8[^"']*)["']""").matcher(html)
                    if (hlsMatcher.find()) {
                        val hlsUrl = hlsMatcher.group(1).replace("\\/", "/")
                        return StreamResolution(hlsUrl, "Vimeo - $title", referer = "https://player.vimeo.com/")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo embed scraper failed for $videoId", e)
        }

        return null
    }

    private fun queryFacebookEmbed(videoUrl: String): StreamResolution? {
        val encodedUrl = URLEncoder.encode(videoUrl, "UTF-8")
        val embedUrl = "https://www.facebook.com/plugins/video.php?href=$encodedUrl"
        val request = Request.Builder()
            .url(embedUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null
            val title = extractTitleFromHtml(html) ?: "Facebook Video"

            // Look for HD or SD playable URLs in embed markup / JSON scripts
            val patterns = listOf(
                Pattern.compile("""["'](?:playable_url_quality_hd|hd_src|hd_src_no_ratelimit)["']\s*:\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
                Pattern.compile("""["'](?:playable_url|sd_src|sd_src_no_ratelimit)["']\s*:\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
                Pattern.compile("""(https?:\\\/\\\/video[^"'<>\s]+\.mp4[^"'<>\s]*)""", Pattern.CASE_INSENSITIVE)
            )

            for (pattern in patterns) {
                val matcher = pattern.matcher(html)
                if (matcher.find()) {
                    val rawUrl = matcher.group(1).replace("\\/", "/")
                    val cleaned = cleanHtmlEntities(rawUrl)
                    if (cleaned.isNotBlank()) {
                        return StreamResolution(cleaned, "Facebook - $title", referer = "https://www.facebook.com/")
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

    data class TwitterMediaResult(
        val title: String?,
        val variants: List<Pair<String, Int>>
    )

    fun extractTwitterStatusId(url: String): String? {
        val resolved = if (url.contains("t.co/")) resolveRedirect(url) else url
        val matcher = TWITTER_STATUS_REGEX.matcher(resolved)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val fallbackMatch = Regex("""/status/(\d+)""").find(resolved)
        return fallbackMatch?.groupValues?.get(1)
    }

    private fun resolveRedirect(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            httpClient.newCall(req).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve redirect for $url: ${e.message}")
            url
        }
    }

    private fun fetchTwitterFormats(statusId: String): List<MediaResolutionOption> {
        try {
            val syndication = queryTwitterSyndication(statusId)
            if (syndication != null && syndication.variants.isNotEmpty()) {
                val formats = buildTwitterFormats(syndication.variants)
                if (formats.isNotEmpty()) return formats
            }
        } catch (e: Exception) {
            Log.w(TAG, "Twitter syndication format fetch failed: ${e.message}")
        }

        try {
            val fx = queryFxTwitterApi(statusId)
            if (fx != null && fx.variants.isNotEmpty()) {
                val formats = buildTwitterFormats(fx.variants)
                if (formats.isNotEmpty()) return formats
            }
        } catch (e: Exception) {
            Log.w(TAG, "FxTwitter format fetch failed: ${e.message}")
        }

        return emptyList()
    }

    private fun resolveTwitterVideo(statusId: String, targetResolution: MediaResolutionOption?): StreamResolution? {
        if (targetResolution != null && !targetResolution.directUrl.isNullOrBlank()) {
            return StreamResolution(
                url = targetResolution.directUrl,
                title = "X (Twitter) Video - ${targetResolution.label}",
                referer = "https://x.com"
            )
        }

        // 1. Try official Syndication API (fast, unauthenticated, official CDN)
        try {
            val syndication = queryTwitterSyndication(statusId)
            if (syndication != null && syndication.variants.isNotEmpty()) {
                val chosenUrl = selectTwitterVariant(syndication.variants, targetResolution)
                if (chosenUrl != null) {
                    val title = syndication.title?.take(60)?.let { "X - $it" } ?: "X (Twitter) Video"
                    return StreamResolution(url = chosenUrl, title = title, referer = "https://x.com")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Twitter syndication resolution failed for status $statusId", e)
        }

        // 2. Try FxTwitter API fallback
        try {
            val fxResult = queryFxTwitterApi(statusId)
            if (fxResult != null && fxResult.variants.isNotEmpty()) {
                val chosenUrl = selectTwitterVariant(fxResult.variants, targetResolution)
                if (chosenUrl != null) {
                    val title = fxResult.title?.take(60)?.let { "X - $it" } ?: "X (Twitter) Video"
                    return StreamResolution(url = chosenUrl, title = title, referer = "https://x.com")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FxTwitter resolution failed for status $statusId", e)
        }

        // 3. Fallback to Twitter HTML OpenGraph scraper
        try {
            val htmlResult = queryTwitterHtmlScraper(statusId)
            if (htmlResult != null && htmlResult.variants.isNotEmpty()) {
                val chosenUrl = htmlResult.variants.first().first
                val title = htmlResult.title?.take(60)?.let { "X - $it" } ?: "X (Twitter) Video"
                return StreamResolution(url = chosenUrl, title = title, referer = "https://x.com")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Twitter HTML scraper fallback failed for status $statusId", e)
        }

        return null
    }

    private fun selectTwitterVariant(
        variants: List<Pair<String, Int>>,
        targetResolution: MediaResolutionOption?
    ): String? {
        if (variants.isEmpty()) return null
        if (targetResolution == null || targetResolution.id == "best" || targetResolution.id == "audio") {
            return variants.maxByOrNull { it.second }?.first
        }
        val targetH = targetResolution.height
        return variants.minByOrNull { (url, bitrate) ->
            val h = extractHeightFromTwitterUrl(url, bitrate)
            kotlin.math.abs(h - targetH)
        }?.first ?: variants.first().first
    }

    private fun queryTwitterSyndication(statusId: String): TwitterMediaResult? {
        val apiUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$statusId&token=x"
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val title = root.optString("text").ifBlank { null }
            val variants = mutableListOf<Pair<String, Int>>()

            fun extractFromMediaDetails(mediaArray: JSONArray?) {
                if (mediaArray == null) return
                for (i in 0 until mediaArray.length()) {
                    val mediaObj = mediaArray.optJSONObject(i) ?: continue
                    val videoInfo = mediaObj.optJSONObject("video_info") ?: continue
                    val varList = videoInfo.optJSONArray("variants") ?: continue
                    for (v in 0 until varList.length()) {
                        val variant = varList.optJSONObject(v) ?: continue
                        val url = variant.optString("url")
                        val contentType = variant.optString("content_type", variant.optString("type"))
                        val bitrate = variant.optInt("bitrate", 0)
                        if (url.isNotBlank() && (contentType.contains("mp4") || url.contains(".mp4"))) {
                            variants.add(url to bitrate)
                        }
                    }
                }
            }

            fun extractFromVideoObj(videoObj: JSONObject?) {
                if (videoObj == null) return
                val varList = videoObj.optJSONArray("variants") ?: return
                for (v in 0 until varList.length()) {
                    val variant = varList.optJSONObject(v) ?: continue
                    val url = variant.optString("src", variant.optString("url"))
                    val type = variant.optString("type", variant.optString("contentType"))
                    val bitrate = variant.optInt("bitrate", 0)
                    if (url.isNotBlank() && (type.contains("mp4") || url.contains(".mp4"))) {
                        variants.add(url to bitrate)
                    }
                }
            }

            extractFromMediaDetails(root.optJSONArray("mediaDetails"))
            extractFromVideoObj(root.optJSONObject("video"))

            val quoted = root.optJSONObject("quoted_tweet")
            if (quoted != null) {
                extractFromMediaDetails(quoted.optJSONArray("mediaDetails"))
                extractFromVideoObj(quoted.optJSONObject("video"))
            }

            if (variants.isNotEmpty()) {
                return TwitterMediaResult(title, variants)
            }
        }
        return null
    }

    private fun queryFxTwitterApi(statusId: String): TwitterMediaResult? {
        val apiUrl = "https://api.fxtwitter.com/i/status/$statusId"
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val tweet = root.optJSONObject("tweet") ?: return null
            val title = tweet.optString("text").ifBlank { null }
            val variants = mutableListOf<Pair<String, Int>>()

            fun extractFromMediaObj(media: JSONObject?) {
                if (media == null) return
                val videos = media.optJSONArray("videos") ?: media.optJSONArray("all") ?: return
                for (i in 0 until videos.length()) {
                    val item = videos.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url")
                    val formats = item.optJSONArray("formats")
                    if (formats != null && formats.length() > 0) {
                        for (f in 0 until formats.length()) {
                            val fmt = formats.optJSONObject(f) ?: continue
                            val url = fmt.optString("url")
                            val bitrate = fmt.optInt("bitrate", 0)
                            if (url.isNotBlank()) {
                                variants.add(url to bitrate)
                            }
                        }
                    } else if (itemUrl.isNotBlank() && (itemUrl.contains(".mp4") || item.optString("type") == "video" || item.optString("type") == "gif")) {
                        variants.add(itemUrl to 0)
                    }
                }
            }

            extractFromMediaObj(tweet.optJSONObject("media"))
            val quote = tweet.optJSONObject("quote")
            if (quote != null) {
                extractFromMediaObj(quote.optJSONObject("media"))
            }

            if (variants.isNotEmpty()) {
                return TwitterMediaResult(title, variants)
            }
        }
        return null
    }

    private fun queryTwitterHtmlScraper(statusId: String): TwitterMediaResult? {
        val scraperUrls = listOf(
            "https://fxtwitter.com/i/status/$statusId",
            "https://vxtwitter.com/i/status/$statusId"
        )
        for (pageUrl in scraperUrls) {
            try {
                val request = Request.Builder()
                    .url(pageUrl)
                    .addHeader("User-Agent", "facebookexternalhit/1.1")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val html = response.body?.string() ?: return@use
                    val ogVideoMatch = Regex("""<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""<meta\s+content=["']([^"']+)["']\s+property=["']og:video(?::secure_url)?["']""", RegexOption.IGNORE_CASE).find(html)
                    val ogTitleMatch = Regex("""<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                    val videoUrl = ogVideoMatch?.groupValues?.get(1)?.replace("&amp;", "&")
                    if (!videoUrl.isNullOrBlank()) {
                        val title = ogTitleMatch?.groupValues?.get(1)?.replace("&amp;", "&")
                        return TwitterMediaResult(title, listOf(videoUrl to 0))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HTML scraper failed for $pageUrl: ${e.message}")
            }
        }
        return null
    }

    private fun buildTwitterFormats(mp4List: List<Pair<String, Int>>): List<MediaResolutionOption> {
        if (mp4List.isEmpty()) return emptyList()

        val uniqueVariants = mp4List
            .distinctBy { it.first }
            .sortedByDescending { it.second }

        val options = mutableListOf<MediaResolutionOption>()
        var isFirst = true

        for ((url, bitrate) in uniqueVariants) {
            val height = extractHeightFromTwitterUrl(url, bitrate)
            val label = when {
                height >= 1080 -> "1080p (Full HD)"
                height >= 720 -> "720p (HD)"
                height >= 480 -> "480p (SD)"
                height >= 360 -> "360p (Fast)"
                else -> "${height}p (Mobile)"
            }
            val id = "${height}p"
            if (options.none { it.id == id }) {
                options.add(
                    MediaResolutionOption(
                        id = id,
                        label = label,
                        height = height,
                        format = "mp4",
                        directUrl = url,
                        isRecommended = isFirst,
                        isFromSource = true
                    )
                )
                isFirst = false
            }
        }

        val bestStream = uniqueVariants.firstOrNull()?.first
        if (bestStream != null) {
            options.add(
                MediaResolutionOption(
                    id = "audio",
                    label = "Audio Only (MP3)",
                    height = 0,
                    format = "mp3",
                    directUrl = bestStream,
                    isFromSource = true
                )
            )
        }

        return options
    }

    private fun extractHeightFromTwitterUrl(url: String, bitrate: Int): Int {
        val dimensionMatch = Regex("""(?:vid/(?:avc1/)?|/)?(\d{2,4})x(\d{2,4})""").find(url)
        if (dimensionMatch != null) {
            val w = dimensionMatch.groupValues[1].toIntOrNull()
            val h = dimensionMatch.groupValues[2].toIntOrNull()
            if (h != null && w != null) {
                return minOf(h, w)
            }
            if (h != null && h > 100) return h
        }
        return when {
            bitrate > 2_000_000 -> 1080
            bitrate >= 800_000 -> 720
            bitrate >= 400_000 -> 480
            bitrate > 0 -> 360
            else -> 720
        }
    }

    private fun queryCobaltApi(apiEndpoint: String, targetUrl: String, targetResolution: MediaResolutionOption? = null): StreamResolution? {
        val isAudio = targetResolution?.id == "audio" || targetResolution?.height == 0
        val quality = when (targetResolution?.height) {
            1080 -> "1080"
            720 -> "720"
            480 -> "480"
            360 -> "360"
            else -> "720"
        }
        val jsonPayload = JSONObject().apply {
            put("url", targetUrl)
            put("videoQuality", quality)
            put("audioFormat", "best")
            put("downloadMode", if (isAudio) "audio" else "auto")
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

    private fun queryInvidiousApi(endpoint: String, videoId: String, targetResolution: MediaResolutionOption? = null): StreamResolution? {
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
            val targetHeight = targetResolution?.height ?: 720

            // Format streams (video + audio combined)
            val formatStreams = json.optJSONArray("formatStreams")
            if (formatStreams != null && formatStreams.length() > 0) {
                var bestUrl: String? = null
                var bestDiff = Int.MAX_VALUE
                for (i in 0 until formatStreams.length()) {
                    val streamObj = formatStreams.getJSONObject(i)
                    val streamUrl = streamObj.optString("url")
                    val container = streamObj.optString("container", "mp4")
                    val qualityLabel = streamObj.optString("qualityLabel", "")
                    val h = qualityLabel.filter { it.isDigit() }.toIntOrNull() ?: 720
                    if (streamUrl.isNotBlank() && container.contains("mp4", ignoreCase = true)) {
                        val diff = kotlin.math.abs(h - targetHeight)
                        if (diff < bestDiff) {
                            bestDiff = diff
                            bestUrl = streamUrl
                        }
                    }
                }
                if (bestUrl != null) {
                    return StreamResolution(bestUrl, "YouTube - $title")
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

    private fun downloadHlsStream(
        m3u8Url: String,
        targetFile: File,
        referer: String,
        targetResolution: MediaResolutionOption? = null,
        onProgress: (Float) -> Unit
    ) {
        val origin = if (referer.contains("vimeo")) "https://player.vimeo.com" else if (referer.contains("dailymotion")) "https://www.dailymotion.com" else null

        // Step 1: Fetch playlist/manifest
        val reqBuilder = Request.Builder()
            .url(m3u8Url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .addHeader("Referer", referer)
            .addHeader("Accept", "*/*")
        if (origin != null) reqBuilder.addHeader("Origin", origin)
        val req = reqBuilder.get().build()

        val manifestContent: String
        val manifestFinalUrl: String
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Failed to load HLS playlist: HTTP ${resp.code}")
            manifestFinalUrl = resp.request.url.toString()
            manifestContent = resp.body?.string() ?: throw IllegalStateException("Empty HLS playlist")
        }

        // Check if master playlist or media playlist
        var mediaPlaylistContent = manifestContent
        var mediaPlaylistUrl = manifestFinalUrl

        if (manifestContent.contains("#EXT-X-STREAM-INF") || manifestContent.contains("#EXT-X-MEDIA:TYPE=AUDIO")) {
            val isAudioOnly = targetResolution?.id == "audio" || targetResolution?.height == 0
            val targetHeight = targetResolution?.height ?: 720
            var chosenVariantUrl: String? = null

            if (isAudioOnly) {
                val audioMatcher = Pattern.compile("""#EXT-X-MEDIA:TYPE=AUDIO.*?URI=["']([^"']+)["']""").matcher(manifestContent)
                if (audioMatcher.find()) {
                    chosenVariantUrl = resolveRelativeUrl(manifestFinalUrl, audioMatcher.group(1))
                }
            }

            if (chosenVariantUrl == null) {
                val lines = manifestContent.lines()
                var bestDiff = Int.MAX_VALUE
                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                        val lineNext = lines[i + 1].trim()
                        if (!lineNext.startsWith("#") && lineNext.isNotBlank()) {
                            val nextUrl = resolveRelativeUrl(manifestFinalUrl, lineNext)
                            val resMatcher = Pattern.compile("""RESOLUTION=(\d+)x(\d+)""").matcher(line)
                            val h = if (resMatcher.find()) resMatcher.group(2).toIntOrNull() ?: 720 else 720
                            val diff = kotlin.math.abs(h - targetHeight)
                            if (diff < bestDiff) {
                                bestDiff = diff
                                chosenVariantUrl = nextUrl
                            }
                        }
                    }
                }
            }

            if (chosenVariantUrl != null) {
                mediaPlaylistUrl = chosenVariantUrl
                val variantReqBuilder = Request.Builder()
                    .url(chosenVariantUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", referer)
                    .addHeader("Accept", "*/*")
                if (origin != null) variantReqBuilder.addHeader("Origin", origin)
                val variantReq = variantReqBuilder.get().build()

                httpClient.newCall(variantReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        mediaPlaylistContent = resp.body?.string() ?: manifestContent
                    }
                }
            }
        }

        // Parse optional init segment (#EXT-X-MAP:URI="...",[BYTERANGE="..."])
        var initSegmentUrl: String? = null
        var initSegmentRange: String? = null
        val mapMatcher = Pattern.compile("""#EXT-X-MAP:URI=["']([^"']+)["'](?:,BYTERANGE=["']?(\d+)(?:@(\d+))?["']?)?""").matcher(mediaPlaylistContent)
        if (mapMatcher.find()) {
            initSegmentUrl = resolveRelativeUrl(mediaPlaylistUrl, mapMatcher.group(1))
            val lengthStr = mapMatcher.group(2)
            val offsetStr = mapMatcher.group(3)
            if (!lengthStr.isNullOrBlank()) {
                val len = lengthStr.toLong()
                val off = offsetStr?.toLongOrNull() ?: 0L
                initSegmentRange = "bytes=$off-${off + len - 1}"
            }
        }

        // Parse segment URLs and byte ranges (#EXT-X-BYTERANGE:len[@offset])
        data class HlsSegmentItem(val url: String, val rangeHeader: String?)
        val segments = mutableListOf<HlsSegmentItem>()
        val mediaLines = mediaPlaylistContent.lines()
        var lastByteOffset = 0L
        var pendingRange: String? = null

        for (line in mediaLines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-BYTERANGE:")) {
                val brStr = trimmed.substringAfter(":").trim()
                if (brStr.contains("@")) {
                    val parts = brStr.split("@")
                    val len = parts[0].toLongOrNull() ?: 0L
                    val off = parts[1].toLongOrNull() ?: 0L
                    lastByteOffset = off + len
                    pendingRange = "bytes=$off-${off + len - 1}"
                } else {
                    val len = brStr.toLongOrNull() ?: 0L
                    val off = lastByteOffset
                    lastByteOffset = off + len
                    pendingRange = "bytes=$off-${off + len - 1}"
                }
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val segUrl = resolveRelativeUrl(mediaPlaylistUrl, trimmed)
                segments.add(HlsSegmentItem(segUrl, pendingRange))
                pendingRange = null
            }
        }

        if (segments.isEmpty() && initSegmentUrl == null) {
            throw IllegalStateException("No media segments found in HLS playlist.")
        }

        // Download init segment (if present) + media segments sequentially
        val totalSegments = segments.size + (if (initSegmentUrl != null) 1 else 0)
        var downloadedCount = 0

        FileOutputStream(targetFile).use { out ->
            if (initSegmentUrl != null) {
                val initReqBuilder = Request.Builder()
                    .url(initSegmentUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", referer)
                    .addHeader("Accept", "*/*")
                if (origin != null) initReqBuilder.addHeader("Origin", origin)
                if (initSegmentRange != null) initReqBuilder.addHeader("Range", initSegmentRange)
                val initReq = initReqBuilder.get().build()

                httpClient.newCall(initReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.copyTo(out)
                    }
                }
                downloadedCount++
                onProgress((downloadedCount.toFloat() / totalSegments).coerceIn(0f, 1f))
            }

            for (seg in segments) {
                val segReqBuilder = Request.Builder()
                    .url(seg.url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", referer)
                    .addHeader("Accept", "*/*")
                if (origin != null) segReqBuilder.addHeader("Origin", origin)
                if (seg.rangeHeader != null) segReqBuilder.addHeader("Range", seg.rangeHeader)
                val segReq = segReqBuilder.get().build()

                var success = false
                for (retry in 0..1) {
                    try {
                        httpClient.newCall(segReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.copyTo(out)
                                success = true
                            }
                        }
                        if (success) break
                    } catch (e: Exception) {
                        if (retry == 1) Log.w(TAG, "Failed downloading segment ${seg.url}", e)
                    }
                }
                downloadedCount++
                onProgress((downloadedCount.toFloat() / totalSegments).coerceIn(0f, 1f))
            }
            out.flush()
        }

        if (!targetFile.exists() || targetFile.length() < 1000L) {
            targetFile.delete()
            throw IllegalStateException("HLS download produced empty file.")
        }
    }
}
