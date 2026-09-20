package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.media.ConcurrentDownloadTask
import com.example.media.DownloadTaskStatus
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel.WebImportResolutionState
import com.example.media.DownloadProgressState
import com.example.media.MediaResolutionOption
import com.example.media.SocialMediaVideoProcessor
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UrlImportDialog(
    initialUrl: String = "",
    downloadState: DownloadProgressState?,
    isDownloading: Boolean,
    errorMessage: String?,
    elapsedTime: String = "00:00",
    resolutionState: WebImportResolutionState? = null,
    onDismiss: () -> Unit,
    onSubmitUrl: (String) -> Unit,
    onProbeResolutions: (String) -> Unit = {},
    onSelectResolution: (MediaResolutionOption) -> Unit = {},
    onConfirmResolutionDownload: () -> Unit = {},
    onBackToUrlInput: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onLoadOfflineDemo: (() -> Unit)? = null,
    downloadTasks: List<ConcurrentDownloadTask> = emptyList(),
    onCancelDownloadTask: (String) -> Unit = {},
    onRetryDownloadTask: (String) -> Unit = {},
    onStartConcurrentDownloads: ((List<String>) -> Unit)? = null,
    onPlayLiveStream: ((String) -> Unit)? = null,
    onClearError: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Web Media / Multi-Link, 1: Live Stream
    var liveStreamUrl by remember { mutableStateOf("") }
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    val clipboardManager = LocalClipboardManager.current

    val detectedUrls = remember(urlText) {
        val regex = Regex("""https?://[^\s,]+|rtsp://[^\s,]+""")
        regex.findAll(urlText).map { it.value.trim() }.filter { it.isNotBlank() }.toList()
    }

    val isProbing = resolutionState?.isProbing == true
    val hasResolutions = resolutionState != null && resolutionState.options.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            hasResolutions -> Icons.Default.HighQuality
                            isProbing -> Icons.Default.Tune
                            else -> Icons.Default.CloudDownload
                        },
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            isDownloading -> "Downloading Media"
                            isProbing -> "Checking Resolutions…"
                            hasResolutions -> "Choose Video Resolution"
                            selectedTab == 1 -> "Live Stream Player"
                            detectedUrls.size > 1 -> "Concurrent Multi-Link Import"
                            else -> "Import Video from URL"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = when {
                            isDownloading -> "Stream & Audio Extraction"
                            isProbing -> "Detecting source quality options…"
                            hasResolutions -> "Select quality before download"
                            selectedTab == 1 -> "Play HLS / DASH / RTSP live streams"
                            detectedUrls.size > 1 -> "${detectedUrls.size} links detected for parallel download"
                            else -> "YouTube & Social Media Link"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_import_dialog_content")
            ) {
                // Tab Selection for Normal/Multi-Link vs Live Stream (only when not downloading or probing)
                if (!isDownloading && !isProbing && !hasResolutions) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = DarkSurface,
                        contentColor = AccentTeal,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = AccentTeal
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Web & Multi-Link", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Live Streams", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                when {
                    isDownloading -> {
                        // Active Download Progress View (Multi-task or Single)
                        if (downloadTasks.isNotEmpty()) {
                            ConcurrentDownloadTasksView(
                                tasks = downloadTasks,
                                elapsedTime = elapsedTime,
                                onCancelTask = onCancelDownloadTask,
                                onRetryTask = onRetryDownloadTask
                            )
                        } else {
                            ActiveDownloadProgressView(downloadState, elapsedTime)
                        }
                    }
                    isProbing -> {
                        // Resolution Probing Spinner
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = AccentTeal,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Probing available video qualities…",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Querying source streams for 1080p, 720p, 480p & audio…",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    hasResolutions -> {
                        // Resolution Selection List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                                .testTag("resolution_picker_container")
                        ) {
                            Text(
                                text = "Available video & audio streams from source:",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            resolutionState!!.options.forEach { option ->
                                val isSelected = option.id == resolutionState.selectedOption?.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentTeal.copy(alpha = 0.14f) else DarkSurface,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) AccentTeal else DarkSurfaceVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { onSelectResolution(option) }
                                        .testTag("resolution_option_${option.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) AccentTeal else TextSecondary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = option.label,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                if (option.isRecommended) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = AccentTeal
                                                    ) {
                                                        Text(
                                                            text = "RECOMMENDED",
                                                            color = Color.Black,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                if (option.id == "audio" || option.height == 0) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF2E7D32)
                                                    ) {
                                                        Text(
                                                            text = "AUDIO ONLY",
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = option.format.uppercase(),
                                                    color = AccentTeal,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (!option.sizeText.isNullOrBlank()) {
                                                    Text(
                                                        text = "• ${option.sizeText}",
                                                        color = TextSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                if (option.isFromSource) {
                                                    Text(
                                                        text = "• From Source",
                                                        color = TextSecondary.copy(alpha = 0.8f),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    selectedTab == 1 -> {
                        // LIVE STREAM PLAYER TAB
                        Text(
                            text = "Stream live HLS (.m3u8), DASH (.mpd), or RTSP streams directly in realtime without separation:",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = liveStreamUrl,
                            onValueChange = {
                                liveStreamUrl = it
                                if (errorMessage != null) onClearError()
                            },
                            placeholder = {
                                Text(
                                    "https://.../stream.m3u8 or rtsp://...",
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (liveStreamUrl.isNotBlank()) {
                                    IconButton(onClick = { liveStreamUrl = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrBlank()) {
                                                liveStreamUrl = clip.trim()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            tint = AccentTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (liveStreamUrl.isNotBlank() && onPlayLiveStream != null) {
                                        onDismiss()
                                        onPlayLiveStream(liveStreamUrl.trim())
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentTeal,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentTeal
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_stream_url_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "VERIFIED LIVE BROADCAST PRESETS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val livePresets = listOf(
                            Triple("NASA TV Official Live Broadcast", "HLS Live • Space Station & Mission Control", "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"),
                            Triple("Tears of Steel 4K HLS Stream", "Multi-Bitrate Adaptive HLS Demo", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"),
                            Triple("Apple BipBop Multi-Rate Stream", "Low-Latency Dynamic Bitrate Stream", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"),
                            Triple("Worldwide Radio Live Stream", "Continuous High-Fidelity Audio Stream", "https://stream.zeno.fm/f3wvbbqmdg8uv")
                        )

                        livePresets.forEach { (name, sub, url) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        liveStreamUrl = url
                                        if (onPlayLiveStream != null) {
                                            onDismiss()
                                            onPlayLiveStream(url)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF3B30))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(sub, color = TextSecondary, fontSize = 10.sp)
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Live",
                                        tint = AccentTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // Input & Quick Paste View (Supports Single & Multi-Link)
                        Text(
                            text = "Paste one or multiple video links (space, comma, or newline separated):",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = urlText,
                            onValueChange = {
                                urlText = it
                                if (errorMessage != null) onClearError()
                            },
                            placeholder = {
                                Text(
                                    "Paste 1 or multiple URLs to download concurrently…",
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (urlText.isNotBlank()) {
                                    IconButton(onClick = { urlText = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear text",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrBlank()) {
                                                urlText = clip.trim()
                                            }
                                        },
                                        modifier = Modifier.testTag("paste_clipboard_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste from clipboard",
                                            tint = AccentTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            minLines = if (detectedUrls.size > 1) 2 else 1,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentTeal,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = AccentTeal
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("url_input_field")
                        )

                        if (detectedUrls.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentTeal.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "⚡ ${detectedUrls.size} links detected for parallel concurrent download",
                                        color = AccentTeal,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusError.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = StatusError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = errorMessage,
                                        color = StatusError,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Supported Platforms Badges
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("YouTube", "Shorts", "Vimeo", "Facebook", "Dailymotion", "TikTok", "Instagram", "X (Twitter)", "VidLii", "Web Video").forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DarkSurface
                                ) {
                                    Text(
                                        text = tag,
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Instant Offline Demo (Guaranteed 100% Offline)
                        if (onLoadOfflineDemo != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AccentTeal.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onLoadOfflineDemo()
                                    }
                                    .testTag("offline_demo_preset_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "⚡ Instant Demo (100% Offline Guaranteed)",
                                            color = AccentTeal,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Test UVR AI separation instantly with built-in media (no network required).",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Quick Sample Links
                        Text(
                            text = "TEST PRESETS (VERIFIED WEB STREAMS)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        SocialMediaVideoProcessor.SAMPLE_PRESETS.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        urlText = preset.url
                                        onProbeResolutions(preset.url)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.title,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${preset.platform} • Click to choose resolution",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Share Sheet Tip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentTeal.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pro-tip: Tap \"Share\" in YouTube or TikTok and pick VocalExtractor!",
                                    color = AccentTeal,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                isDownloading -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("run_in_background_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Run in Background",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                isProbing -> {
                    // While probing, dismiss button allows cancelling
                }
                hasResolutions -> {
                    Button(
                        onClick = onConfirmResolutionDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_resolution_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download (${resolutionState?.selectedOption?.label ?: "Selected"})",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                selectedTab == 1 -> {
                    Button(
                        onClick = {
                            if (liveStreamUrl.isNotBlank() && onPlayLiveStream != null) {
                                onDismiss()
                                onPlayLiveStream(liveStreamUrl.trim())
                            }
                        },
                        enabled = liveStreamUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("play_live_stream_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play Live Stream",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                detectedUrls.size > 1 -> {
                    Button(
                        onClick = {
                            if (onStartConcurrentDownloads != null) {
                                onStartConcurrentDownloads(detectedUrls)
                            } else {
                                onSubmitUrl(detectedUrls.first())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("submit_concurrent_urls_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Download ${detectedUrls.size} Links Concurrently",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            if (urlText.isNotBlank()) {
                                onProbeResolutions(urlText.trim())
                            }
                        },
                        enabled = urlText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentTeal,
                            disabledContainerColor = AccentTeal.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("submit_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Select Quality & Download",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        dismissButton = {
            when {
                isDownloading -> {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.testTag("cancel_active_download_button")
                    ) {
                        Text("Cancel Download", color = StatusError)
                    }
                }
                isProbing -> {
                    TextButton(
                        onClick = onBackToUrlInput,
                        modifier = Modifier.testTag("cancel_probing_button")
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
                hasResolutions -> {
                    TextButton(
                        onClick = onBackToUrlInput,
                        modifier = Modifier.testTag("back_to_url_input_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change Link", color = TextSecondary)
                        }
                    }
                }
                else -> {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_url_dialog_button")
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        },
        containerColor = DarkBackground,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun ActiveDownloadProgressView(
    downloadState: DownloadProgressState?,
    elapsedTime: String = "00:00"
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (downloadState) {
            is DownloadProgressState.Resolving -> {
                CircularProgressIndicator(
                    color = AccentTeal,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = downloadState.message,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Resolving high-quality audio/video stream…",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                // Elapsed time pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "⏱ Elapsed: $elapsedTime",
                        color = AccentTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            is DownloadProgressState.Downloading -> {
                val percentInt = (downloadState.progress * 100).toInt()
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    color = AccentTeal,
                    trackColor = DarkSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloading video… $percentInt%",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (downloadState.speedKbps > 0) {
                        Text(
                            text = "${downloadState.speedKbps} KB/s",
                            color = AccentTeal,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Elapsed time badge during download
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant
                ) {
                    Text(
                        text = "⏱ Elapsed: $elapsedTime",
                        color = AccentTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Once finished, UVR AI vocal separation starts automatically.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            else -> {
                CircularProgressIndicator(
                    color = AccentTeal,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Preparing media…",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AccentTeal.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "💡 Tap \"Run in Background\" to continue using the app while downloading.",
                color = AccentTeal,
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun ConcurrentDownloadTasksView(
    tasks: List<ConcurrentDownloadTask>,
    elapsedTime: String,
    onCancelTask: (String) -> Unit,
    onRetryTask: (String) -> Unit
) {
    val completedCount = tasks.count { it.status == DownloadTaskStatus.COMPLETED }
    val failedCount = tasks.count { it.status == DownloadTaskStatus.FAILED }
    val activeCount = tasks.size - completedCount - failedCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Parallel Downloads ($completedCount/${tasks.size} Done)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (activeCount > 0) "$activeCount downloading in parallel" else "All tasks finished",
                    color = AccentTeal,
                    fontSize = 11.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DarkSurfaceVariant
            ) {
                Text(
                    text = "⏱ $elapsedTime",
                    color = AccentTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tasks.forEach { task ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, if (task.status == DownloadTaskStatus.DOWNLOADING) AccentTeal.copy(alpha = 0.4f) else DarkSurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                when (task.status) {
                                    DownloadTaskStatus.COMPLETED -> {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Done",
                                            tint = Color(0xFF34C759),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DownloadTaskStatus.FAILED -> {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = "Failed",
                                            tint = StatusError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DownloadTaskStatus.CANCELLED -> {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancelled",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    else -> {
                                        CircularProgressIndicator(
                                            color = AccentTeal,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = when (task.status) {
                                            DownloadTaskStatus.QUEUED -> "Queued…"
                                            DownloadTaskStatus.RESOLVING -> "Resolving stream…"
                                            DownloadTaskStatus.DOWNLOADING -> "${(task.progress * 100).toInt()}% • ${task.speedKbps} KB/s"
                                            DownloadTaskStatus.COMPLETED -> "Ready for playback"
                                            DownloadTaskStatus.FAILED -> task.errorMessage ?: "Failed"
                                            DownloadTaskStatus.CANCELLED -> "Cancelled"
                                        },
                                        color = when (task.status) {
                                            DownloadTaskStatus.COMPLETED -> Color(0xFF34C759)
                                            DownloadTaskStatus.FAILED -> StatusError
                                            else -> TextSecondary
                                        },
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            if (task.status == DownloadTaskStatus.DOWNLOADING || task.status == DownloadTaskStatus.RESOLVING || task.status == DownloadTaskStatus.QUEUED) {
                                IconButton(
                                    onClick = { onCancelTask(task.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Task",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else if (task.status == DownloadTaskStatus.FAILED) {
                                IconButton(
                                    onClick = { onRetryTask(task.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry Task",
                                        tint = AccentTeal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        if (task.status == DownloadTaskStatus.DOWNLOADING) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { task.progress },
                                color = AccentTeal,
                                trackColor = DarkSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AccentTeal.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "💡 Tap \"Run in Background\" to continue using the player while downloading.",
                color = AccentTeal,
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
