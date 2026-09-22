package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoItemEntity
import com.example.export.ExportFormat
import com.example.export.ExportNameSanitizer
import com.example.export.ProgressiveVideoMerger
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExportConfirmDialog(
    video: VideoItemEntity,
    onDismiss: () -> Unit,
    onConfirmExport: (deleteOriginalAfterExport: Boolean) -> Unit
) {
    // Overload for backward compatibility, defaults to WAV or MP4
    ExportOptionsDialog(
        video = video,
        onDismiss = onDismiss,
        onConfirmExport = { _, deleteOriginal -> onConfirmExport(deleteOriginal) }
    )
}

@Composable
fun ExportOptionsDialog(
    video: VideoItemEntity,
    onDismiss: () -> Unit,
    onConfirmExport: (format: ExportFormat, deleteOriginalAfterExport: Boolean) -> Unit
) {
    val isAudioSource = remember(video) {
        val path = video.videoUri.lowercase()
        path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".m4a") ||
                path.endsWith(".aac") || path.endsWith(".flac") || path.endsWith(".ogg")
    }

    var selectedFormat by remember {
        mutableStateOf(if (isAudioSource) ExportFormat.AUDIO_VOCALS_WAV else ExportFormat.AUDIO_VOCALS_WAV)
    }
    var deleteOriginalAfterExport by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val hasPremergedVideoCache = remember(video) {
        ProgressiveVideoMerger.isCacheReadyForVideo(context, video.id)
    }
    val previewSanitizedTitle = remember(video.title) {
        ExportNameSanitizer.sanitizeTitle(video.title)
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AccentTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Export Media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Audio & Video Extraction Formats",
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
                    .verticalScroll(scrollState)
                    .testTag("export_options_dialog")
            ) {
                // Source item info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isAudioSource) "SOURCE AUDIO TRACK" else "SOURCE VIDEO",
                            color = AccentTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = video.title,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Output: ${previewSanitizedTitle}_vocals.${selectedFormat.extension}",
                            color = AccentTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = "✓ Arabic & Unicode characters fully preserved",
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "AUDIO EXPORT FORMATS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentTeal,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                FormatOptionCard(
                    format = ExportFormat.AUDIO_VOCALS_WAV,
                    isSelected = selectedFormat == ExportFormat.AUDIO_VOCALS_WAV,
                    onSelect = { selectedFormat = ExportFormat.AUDIO_VOCALS_WAV },
                    icon = Icons.Default.Mic
                )

                Spacer(modifier = Modifier.height(6.dp))

                FormatOptionCard(
                    format = ExportFormat.AUDIO_VOCALS_M4A,
                    isSelected = selectedFormat == ExportFormat.AUDIO_VOCALS_M4A,
                    onSelect = { selectedFormat = ExportFormat.AUDIO_VOCALS_M4A },
                    icon = Icons.Default.Audiotrack
                )

                Spacer(modifier = Modifier.height(6.dp))

                FormatOptionCard(
                    format = ExportFormat.AUDIO_INSTRUMENTAL_WAV,
                    isSelected = selectedFormat == ExportFormat.AUDIO_INSTRUMENTAL_WAV,
                    onSelect = { selectedFormat = ExportFormat.AUDIO_INSTRUMENTAL_WAV },
                    icon = Icons.Default.MusicNote
                )

                Spacer(modifier = Modifier.height(6.dp))

                FormatOptionCard(
                    format = ExportFormat.AUDIO_INSTRUMENTAL_M4A,
                    isSelected = selectedFormat == ExportFormat.AUDIO_INSTRUMENTAL_M4A,
                    onSelect = { selectedFormat = ExportFormat.AUDIO_INSTRUMENTAL_M4A },
                    icon = Icons.Default.MusicNote
                )

                // Video formats (if source is not strictly audio)
                if (!isAudioSource) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VIDEO EXPORT FORMATS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentTeal,
                            letterSpacing = 0.5.sp
                        )
                        if (hasPremergedVideoCache) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentTeal.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "⚡ Instant Cache Ready",
                                    color = AccentTeal,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    FormatOptionCard(
                        format = ExportFormat.VIDEO_VOCALS_MP4,
                        isSelected = selectedFormat == ExportFormat.VIDEO_VOCALS_MP4,
                        onSelect = { selectedFormat = ExportFormat.VIDEO_VOCALS_MP4 },
                        icon = Icons.Default.Movie
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    FormatOptionCard(
                        format = ExportFormat.VIDEO_INSTRUMENTAL_MP4,
                        isSelected = selectedFormat == ExportFormat.VIDEO_INSTRUMENTAL_MP4,
                        onSelect = { selectedFormat = ExportFormat.VIDEO_INSTRUMENTAL_MP4 },
                        icon = Icons.Default.Movie
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Delete original option
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (deleteOriginalAfterExport) DarkSurfaceVariant else DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { deleteOriginalAfterExport = !deleteOriginalAfterExport }
                        .testTag("delete_original_checkbox_container")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteOriginalAfterExport,
                            onCheckedChange = { deleteOriginalAfterExport = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentTeal,
                                checkmarkColor = Color.Black,
                                uncheckedColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("delete_original_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = if (deleteOriginalAfterExport) StatusError else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Delete original after export",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Safely deletes source only after export succeeds.",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmExport(selectedFormat, deleteOriginalAfterExport) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Export ${selectedFormat.extension.uppercase()}",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_export_button")
            ) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkBackground,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun FormatOptionCard(
    format: ExportFormat,
    isSelected: Boolean,
    onSelect: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) DarkSurfaceVariant else DarkSurface,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isSelected) Modifier.border(1.5.dp, AccentTeal, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onSelect)
            .testTag("format_option_${format.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AccentTeal,
                    unselectedColor = TextSecondary
                ),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) AccentTeal else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = format.title,
                    color = TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    text = format.subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
