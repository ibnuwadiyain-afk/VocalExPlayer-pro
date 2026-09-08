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
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
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
                title = "Big Buck Bunny (Direct MP4 Clip)",
                url = "https://www.w3schools.com/html/mov_bbb.mp4",
                platform = "Direct MP4 Stream"
            ),
            SamplePreset(
                title = "Vimeo Creative Showcase",
                url = "https://vimeo.com/76979871",
                platform = "Vimeo Video"
            ),
            SamplePreset(
                title = "VidLii Showcase Video",
                url = "https://www.vidlii.com/watch?v=4fEcsaOOBGe",
                platform = "VidLii Web Link"
            ),
            SamplePreset(
                title = "Dailymotion Video Clip",
                url = "https://www.dailymotion.com/video/x8o0ocx",
                platform = "Dailymotion Link"
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
            if (streamUrl.contains(".m3u8")) {
                downloadHlsStream(streamUrl, targetFile, referer) { percent ->
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

            // Fallback for YouTube: loader.to polling extractor
            try {
                onProgress(DownloadProgressState.Resolving("Requesting stream converter for YouTube…"))
                val loaderRes = queryLoaderTo(ytId, onProgress)
                if (loaderRes != null) {
                    return loaderRes
                }
            } catch (e: Exception) {
                Log.w(TAG, "Loader.to resolution failed for $ytId", e)
            }

            // Fallback for YouTube: Y2Mate high-speed stream converter
            try {
                onProgress(DownloadProgressState.Resolving("Requesting alternate stream converter for YouTube…"))
                val y2Res = queryY2Mate(ytId, onProgress)
                if (y2Res != null) {
                    return y2Res
                }
            } catch (e: Exception) {
                Log.w(TAG, "Y2Mate resolution failed for $ytId", e)
            }

            // Fallback for YouTube: Cobalt APIs
            for (endpoint in COBALT_ENDPOINTS) {
                try {
                    onProgress(DownloadProgressState.Resolving("Checking alternative stream converter…"))
                    val res = queryCobaltApi(endpoint, url)
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
                    val vimeoRes = queryVimeoStream(vimeoId)
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
                    val dmRes = queryDailymotionHls(dmId)
                    if (dmRes != null) {
                        return dmRes
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Dailymotion HLS resolution failed for $dmId", e)
                }
            }
        }

        // 4. Facebook video resolution (embed player page scraper)
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

        // 5. TikTok extraction via TikWM API
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
        val clientConfigs = listOf(
            Triple("ANDROID_VR", "1.61.48", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"),
            Triple("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0", "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1"),
            Triple("WEB_EMBEDDED_PLAYER", "1.20231009.01.00", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        )

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
            } catch (e: Exception) {
                Log.w(TAG, "InnerTube client $cName attempt failed for $videoId", e)
            }
        }
        return null
    }

    private suspend fun queryLoaderTo(
        videoId: String,
        onProgress: (DownloadProgressState) -> Unit
    ): StreamResolution? = withContext(Dispatchers.IO) {
        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
        val encodedYtUrl = URLEncoder.encode(ytUrl, "UTF-8")
        val formats = listOf("1080", "720", "480", "360", "mp3")

        for (fmt in formats) {
            try {
                val initUrl = "https://loader.to/ajax/download.php?format=$fmt&url=$encodedYtUrl"
                val initReq = Request.Builder()
                    .url(initUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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

                // Poll progress URL up to 20 times (total ~25-30s max)
                var downloadUrl: String? = null
                for (pollCount in 1..20) {
                    delay(1500)
                    val progressUrl = "https://p.oceansaver.net/ajax/progress.php?id=$downloadId"
                    val progReq = Request.Builder()
                        .url(progressUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .addHeader("Referer", "https://en.loader.to/")
                        .get()
                        .build()

                    val progRespStr = httpClient.newCall(progReq).execute().use { resp ->
                        if (!resp.isSuccessful) null else resp.body?.string()
                    } ?: continue

                    val progJson = JSONObject(progRespStr)
                    val progressVal = progJson.optInt("progress", 0)
                    val textMsg = progJson.optString("text", "")
                    if (textMsg.isNotBlank()) {
                        onProgress(DownloadProgressState.Resolving("Converting YouTube stream: $textMsg ($progressVal%)…"))
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
        onProgress: (DownloadProgressState) -> Unit
    ): StreamResolution? = withContext(Dispatchers.IO) {
        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
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

                // Find candidate format key: mp4 first (720p or 1080p or 360p), or mp3
                var formatKey: String? = null
                val mp4Obj = links.optJSONObject("mp4")
                if (mp4Obj != null) {
                    val keys = mp4Obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val item = mp4Obj.optJSONObject(k)
                        val kVal = item?.optString("k")
                        if (!kVal.isNullOrBlank()) {
                            formatKey = kVal
                            break
                        }
                    }
                }
                if (formatKey.isNullOrBlank()) {
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

    private fun queryDailymotionHls(videoId: String): StreamResolution? {
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

    private fun queryVimeoStream(videoId: String): StreamResolution? {
        val embedUrl = "https://player.vimeo.com/video/$videoId"

        // Strategy 1: Fetch title and thumbnail from public Vimeo oEmbed API
        var oembedTitle: String? = null
        try {
            val oembedReq = Request.Builder()
                .url("https://vimeo.com/api/oembed.json?url=https%3A%2F%2Fvimeo.com%2F$videoId")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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

        // Strategy 2: Vimeo player config endpoint with proper browser headers
        val configUrl = "https://player.vimeo.com/video/$videoId/config"
        try {
            val req = Request.Builder()
                .url(configUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Referer", embedUrl)
                .addHeader("Origin", "https://player.vimeo.com")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val videoObj = json.optJSONObject("video")
                        val title = oembedTitle ?: videoObj?.optString("title") ?: "Vimeo Video"
                        val files = json.optJSONObject("request")?.optJSONObject("files")

                        // 1. Check progressive MP4 files (highest quality first)
                        val progressive = files?.optJSONArray("progressive")
                        if (progressive != null && progressive.length() > 0) {
                            var bestUrl: String? = null
                            var bestWidth = 0
                            for (i in 0 until progressive.length()) {
                                val item = progressive.getJSONObject(i)
                                val streamUrl = item.optString("url")
                                val width = item.optInt("width", 0)
                                if (streamUrl.isNotBlank() && width >= bestWidth) {
                                    bestWidth = width
                                    bestUrl = streamUrl
                                }
                            }
                            if (bestUrl != null) {
                                return StreamResolution(bestUrl, "Vimeo - $title", referer = embedUrl)
                            }
                        }

                        // 2. Check HLS stream
                        val hls = files?.optJSONObject("hls")
                        val defaultCdn = hls?.optJSONObject("cdns")
                        if (defaultCdn != null) {
                            val cdnKeys = defaultCdn.keys()
                            while (cdnKeys.hasNext()) {
                                val cdnObj = defaultCdn.optJSONObject(cdnKeys.next())
                                val hlsUrl = cdnObj?.optString("url")
                                if (!hlsUrl.isNullOrBlank()) {
                                    return StreamResolution(hlsUrl, "Vimeo - $title", referer = embedUrl)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vimeo config endpoint query failed for $videoId", e)
        }

        // Strategy 3: Vimeo embed page scraping
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://vimeo.com/")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val title = oembedTitle ?: extractTitleFromHtml(html) ?: "Vimeo Video"

                    // Look for progressive MP4 links inside embedded script
                    val mp4Matcher = Pattern.compile("""["'](https?:\/\/[^"']+\.mp4[^"']*)["']""").matcher(html)
                    while (mp4Matcher.find()) {
                        val candidate = mp4Matcher.group(1).replace("\\/", "/")
                        if (candidate.contains("vod-progressive") || candidate.contains("vimeo") || candidate.contains("akamaized")) {
                            return StreamResolution(candidate, "Vimeo - $title", referer = embedUrl)
                        }
                    }

                    // Look for master.json or master.m3u8
                    val hlsMatcher = Pattern.compile("""["'](https?:\/\/[^"']+\.m3u8[^"']*)["']""").matcher(html)
                    if (hlsMatcher.find()) {
                        val hlsUrl = hlsMatcher.group(1).replace("\\/", "/")
                        return StreamResolution(hlsUrl, "Vimeo - $title", referer = embedUrl)
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

    private fun downloadHlsStream(
        m3u8Url: String,
        targetFile: File,
        referer: String,
        onProgress: (Float) -> Unit
    ) {
        val origin = if (referer.contains("vimeo")) "https://player.vimeo.com" else if (referer.contains("dailymotion")) "https://www.dailymotion.com" else null

        // Step 1: Fetch playlist/manifest
        val reqBuilder = Request.Builder()
            .url(m3u8Url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .addHeader("Referer", referer)
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

        if (manifestContent.contains("#EXT-X-STREAM-INF")) {
            // Master playlist: pick best variant URL
            val lines = manifestContent.lines()
            var bestVariantUrl: String? = null
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                    val lineNext = lines[i + 1].trim()
                    if (!lineNext.startsWith("#") && lineNext.isNotBlank()) {
                        bestVariantUrl = resolveRelativeUrl(manifestFinalUrl, lineNext)
                    }
                }
            }
            if (bestVariantUrl != null) {
                mediaPlaylistUrl = bestVariantUrl
                val variantReqBuilder = Request.Builder()
                    .url(bestVariantUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", referer)
                if (origin != null) variantReqBuilder.addHeader("Origin", origin)
                val variantReq = variantReqBuilder.get().build()

                httpClient.newCall(variantReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        mediaPlaylistContent = resp.body?.string() ?: manifestContent
                    }
                }
            }
        }

        // Parse segment URLs and optional init segment (#EXT-X-MAP:URI="...")
        var initSegmentUrl: String? = null
        val mapMatcher = Pattern.compile("""#EXT-X-MAP:URI=["']([^"']+)["']""").matcher(mediaPlaylistContent)
        if (mapMatcher.find()) {
            initSegmentUrl = resolveRelativeUrl(mediaPlaylistUrl, mapMatcher.group(1))
        }

        val segmentUrls = mutableListOf<String>()
        val mediaLines = mediaPlaylistContent.lines()
        for (line in mediaLines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                segmentUrls.add(resolveRelativeUrl(mediaPlaylistUrl, trimmed))
            }
        }

        if (segmentUrls.isEmpty()) {
            throw IllegalStateException("No media segments found in HLS playlist.")
        }

        // Download init segment (if present) + media segments sequentially
        val totalSegments = segmentUrls.size + (if (initSegmentUrl != null) 1 else 0)
        var downloadedCount = 0

        FileOutputStream(targetFile).use { out ->
            if (initSegmentUrl != null) {
                val initReqBuilder = Request.Builder()
                    .url(initSegmentUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", referer)
                if (origin != null) initReqBuilder.addHeader("Origin", origin)
                val initReq = initReqBuilder.get().build()

                httpClient.newCall(initReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.copyTo(out)
                    }
                }
                downloadedCount++
                onProgress((downloadedCount.toFloat() / totalSegments).coerceIn(0f, 1f))
            }

            for (segUrl in segmentUrls) {
                val segReqBuilder = Request.Builder()
                    .url(segUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Referer", referer)
                if (origin != null) segReqBuilder.addHeader("Origin", origin)
                val segReq = segReqBuilder.get().build()

                httpClient.newCall(segReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.copyTo(out)
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
