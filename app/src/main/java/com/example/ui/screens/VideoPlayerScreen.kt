package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.MainViewModel
import com.example.ui.components.ProcessingOverlay
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.InstrumentalTeal
import com.example.ui.theme.VocalPink

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun VideoPlayerScreen(
    videoId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val videoList by viewModel.videoList.collectAsState()
    val video = videoList.find { it.id == videoId }

    val isProcessing by viewModel.isProcessing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val activeTitle by viewModel.activeVideoTitle.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()

    var vocalVolume by remember { mutableFloatStateOf(1.0f) }
    var instVolume by remember { mutableFloatStateOf(1.0f) }

    // Video Player
    val videoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    // Secondary synchronized audio player for isolated vocals when separated
    val vocalPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    // Instrumental track player
    val instPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            vocalPlayer.release()
            instPlayer.release()
        }
    }

    LaunchedEffect(video) {
        if (video != null) {
            val videoMediaItem = MediaItem.fromUri(Uri.parse(video.videoUri))
            videoPlayer.setMediaItem(videoMediaItem)
            videoPlayer.prepare()

            if (video.isSeparated && video.vocalsUri != null && video.accompanimentUri != null) {
                // When separated, mute the raw video track so the separated tracks handle audio
                videoPlayer.volume = 0.0f

                vocalPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(video.vocalsUri)))
                vocalPlayer.prepare()
                vocalPlayer.volume = vocalVolume

                instPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(video.accompanimentUri)))
                instPlayer.prepare()
                instPlayer.volume = instVolume

                // Sync play/pause and seek
                videoPlayer.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            vocalPlayer.seekTo(videoPlayer.currentPosition)
                            instPlayer.seekTo(videoPlayer.currentPosition)
                            vocalPlayer.play()
                            instPlayer.play()
                        } else {
                            vocalPlayer.pause()
                            instPlayer.pause()
                        }
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        vocalPlayer.seekTo(newPosition.positionMs)
                        instPlayer.seekTo(newPosition.positionMs)
                    }
                })
            } else {
                videoPlayer.volume = 1.0f
            }
        }
    }

    LaunchedEffect(vocalVolume) {
        vocalPlayer.volume = vocalVolume
    }

    LaunchedEffect(instVolume) {
        instPlayer.volume = instVolume
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = video?.title ?: "Video Player",
                        maxLines = 1,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_player")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Video Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = videoPlayer
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize().testTag("exo_player_view")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (video == null) {
                    Text(
                        text = "Video not found",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!video.isSeparated) {
                    // Unseparated State: prompt user to extract vocals
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Extract Vocals & Karaoke (UVR AI)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Run UVR MDX-Net neural AI separation to isolate vocals and accompaniment with pristine quality.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.startSeparation(video) },
                                modifier = Modifier.testTag("btn_extract_vocals_in_player")
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Isolate with UVR Neural AI")
                            }
                        }
                    }
                } else {
                    // Separated Audio Mixing Console
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AI Audio Mixer Console",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "UVR MDX-Net Separated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InstrumentalTeal
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick Preset Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = (vocalVolume == 1f && instVolume == 1f),
                                    onClick = { vocalVolume = 1f; instVolume = 1f },
                                    label = { Text("Original Mix") },
                                    modifier = Modifier.testTag("preset_both")
                                )
                                FilterChip(
                                    selected = (vocalVolume == 1f && instVolume == 0f),
                                    onClick = { vocalVolume = 1f; instVolume = 0f },
                                    label = { Text("Vocals Solo") },
                                    modifier = Modifier.testTag("preset_vocals_solo")
                                )
                                FilterChip(
                                    selected = (vocalVolume == 0f && instVolume == 1f),
                                    onClick = { vocalVolume = 0f; instVolume = 1f },
                                    label = { Text("Karaoke (No Vocals)") },
                                    modifier = Modifier.testTag("preset_karaoke")
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Vocal Stem Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (vocalVolume > 0f) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = "Vocals",
                                    tint = VocalPink,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Vocals",
                                            fontWeight = FontWeight.SemiBold,
                                            color = VocalPink
                                        )
                                        Text(
                                            text = "${(vocalVolume * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = vocalVolume,
                                        onValueChange = { vocalVolume = it },
                                        modifier = Modifier.testTag("slider_vocal_volume"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = VocalPink,
                                            activeTrackColor = VocalPink
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Instrumental Stem Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Instrumental",
                                    tint = InstrumentalTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Instrumental / Accompaniment",
                                            fontWeight = FontWeight.SemiBold,
                                            color = InstrumentalTeal
                                        )
                                        Text(
                                            text = "${(instVolume * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = instVolume,
                                        onValueChange = { instVolume = it },
                                        modifier = Modifier.testTag("slider_inst_volume"),
                                        colors = SliderDefaults.colors(
                                            thumbColor = InstrumentalTeal,
                                            activeTrackColor = InstrumentalTeal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ProcessingOverlay(
                isProcessing = isProcessing,
                progress = progress,
                statusMessage = statusMessage,
                videoTitle = activeTitle,
                elapsedSeconds = elapsedSeconds,
                onCancel = { viewModel.cancelSeparation() }
            )
        }
    }
}
