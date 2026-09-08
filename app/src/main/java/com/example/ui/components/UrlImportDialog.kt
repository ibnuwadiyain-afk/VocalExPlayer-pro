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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import com.example.media.DownloadProgressState
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
    onDismiss: () -> Unit,
    onSubmitUrl: (String) -> Unit,
    onLoadOfflineDemo: (() -> Unit)? = null,
    onClearError: () -> Unit
) {
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
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
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Import Video from URL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "YouTube & Social Media Link",
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
                if (isDownloading) {
                    // Active Download Progress View
                    ActiveDownloadProgressView(downloadState, elapsedTime)
                } else {
                    // Input & Quick Paste View
                    Text(
                        text = "Paste a link from YouTube, TikTok, Instagram, X, or direct video:",
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
                                "https://youtu.be/... or video URL",
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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (urlText.isNotBlank()) {
                                    onSubmitUrl(urlText.trim())
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
                            .testTag("url_input_field")
                    )

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
                                    onSubmitUrl(preset.url)
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
                                        text = "${preset.platform} • Click to test",
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
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = {
                        if (urlText.isNotBlank()) {
                            onSubmitUrl(urlText.trim())
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
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Download & Separate",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel_url_dialog_button")
                ) {
                    Text("Cancel", color = TextSecondary)
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
    }
}
