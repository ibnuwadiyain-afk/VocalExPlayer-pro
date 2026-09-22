package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusNew
import com.example.ui.theme.StatusProcessing
import com.example.ui.theme.StatusReady
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LocalAppLanguage
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: VideoItemEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elapsedTime: String = "",
    isActiveProcessing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val lang = LocalAppLanguage.current
    val isAudio = remember(video) {
        val path = video.videoUri.lowercase()
        path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".m4a") ||
                path.endsWith(".flac") || path.endsWith(".ogg") || path.endsWith(".aac")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("video_item_${video.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media Thumbnail / Icon Placeholder
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isAudio) Color(0xFF1E293B) else Color(0xFF262626)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAudio) Icons.Default.Audiotrack else Icons.Default.Videocam,
                    contentDescription = if (isAudio) "Audio Track" else "Video",
                    tint = if (isAudio) Color(0xFF38BDF8) else AccentTeal.copy(alpha = 0.8f),
                    modifier = Modifier.size(32.dp)
                )

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Duration Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = if (isAudio) Color(0xFF38BDF8).copy(alpha = 0.15f) else AccentTeal.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isAudio) AppStrings.badgeAudio(lang) else AppStrings.badgeVideo(lang),
                            color = if (isAudio) Color(0xFF38BDF8) else AccentTeal,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatDuration(video.duration),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Status Badge with timer if processing
            StatusBadge(
                status = video.status,
                elapsedTime = if (isActiveProcessing || video.status == VideoStatus.PROCESSING) elapsedTime else ""
            )
        }
    }
}

@Composable
fun StatusBadge(
    status: VideoStatus,
    elapsedTime: String = ""
) {
    val lang = LocalAppLanguage.current
    val (bgColor, textColor, defaultText, icon) = when (status) {
        VideoStatus.READY -> Quadruple(
            StatusReady.copy(alpha = 0.15f),
            StatusReady,
            AppStrings.badgeReady(lang),
            Icons.Default.CheckCircle
        )
        VideoStatus.PROCESSING -> Quadruple(
            StatusProcessing.copy(alpha = 0.15f),
            StatusProcessing,
            AppStrings.badgeProcessing(lang, elapsedTime),
            Icons.Default.HourglassTop
        )
        VideoStatus.ERROR -> Quadruple(
            StatusError.copy(alpha = 0.15f),
            StatusError,
            AppStrings.badgeError(lang),
            Icons.Default.ErrorOutline
        )
        VideoStatus.NEW -> Quadruple(
            StatusNew.copy(alpha = 0.15f),
            StatusNew,
            AppStrings.badgeNew(lang),
            Icons.Default.PlayArrow
        )
    }

    val displayText = if (status == VideoStatus.PROCESSING && elapsedTime.isNotBlank()) {
        AppStrings.badgeProcessing(lang, elapsedTime)
    } else {
        defaultText
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier.testTag("status_badge_${status.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = displayText,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
