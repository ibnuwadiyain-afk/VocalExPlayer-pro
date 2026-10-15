package com.example.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Language
import com.example.onnx.AccelerationMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.ui.components.ExportConfirmDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import com.example.export.ExportFormat
import com.example.ui.components.ExportConfirmDialog
import com.example.ui.components.ExportOptionsDialog
import com.example.ui.components.ProcessingOverlay
import com.example.ui.components.TranscriptDialog
import com.example.ui.components.TtsDubbingDialog
import com.example.dubbing.DubbingConfig
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LocalAppLanguage
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.whisper.SrtGenerator
import com.example.whisper.TranscriptionSegment
import com.example.whisper.WhisperTokenizer
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun VideoPlayerScreen(
    video: VideoItemEntity,
    isProcessing: Boolean,
    processingProgress: Float,
    processingMessage: String = "",
    activeModelName: String,
    accelerationMode: AccelerationMode = AccelerationMode.TURBO,
    hardwareBackend: String = "Multi-Core CPU",
    elapsedTime: String = "00:00",
    estimatedRemaining: String? = null,
    isBackgroundProtected: Boolean = true,
    isTranscriptionEnabled: Boolean = false,
    hasWhisperModelInstalled: Boolean = false,
    whisperLanguage: String = "auto",
    onSetWhisperLanguage: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReprocess: () -> Unit,
    onExportVideo: (deleteOriginal: Boolean) -> Unit,
    onExportMedia: ((format: ExportFormat, deleteOriginal: Boolean) -> Unit)? = null,
    onCancelProcessing: (() -> Unit)? = null,
    onToggleTranscription: ((Boolean) -> Unit)? = null,
    onStartTranscription: (() -> Unit)? = null,
    onOpenModelSettings: (() -> Unit)? = null,
    onExportDubbed: ((video: VideoItemEntity, segments: List<TranscriptionSegment>, config: DubbingConfig) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(video.duration.coerceAtLeast(1L)) }
    var isSliderDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var useIsolatedVocals by remember { mutableStateOf(true) }
    var showExportConfirmDialog by remember { mutableStateOf(false) }
    var showTranscriptDialog by remember { mutableStateOf(false) }
    var showDubbingDialog by remember { mutableStateOf(false) }

    // Parse and observe subtitles if generated
    val srtFile = remember(video.subtitleUri, isProcessing) {
        val path = video.subtitleUri ?: File(context.filesDir, "subtitles/vocal_${video.id}.srt").absolutePath
        File(path).takeIf { it.exists() && it.length() > 0 }
    }
    val subtitleSegments = remember(srtFile, isProcessing) {
        srtFile?.let { SrtGenerator.parseSrtFile(it) } ?: emptyList()
    }
    val hasSubtitles = subtitleSegments.isNotEmpty()
    val activeSubtitle = remember(currentPosition, subtitleSegments) {
        subtitleSegments.firstOrNull { currentPosition in it.startMs..it.endMs }
    }

    val isAudioSource = remember(video) {
        val path = video.videoUri.lowercase()
        path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".m4a") ||
                path.endsWith(".flac") || path.endsWith(".ogg") || path.endsWith(".aac")
    }

    // Single unified ExoPlayer instance for frame-accurate, sample-synced video and audio playback
    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    fun selectTrack(vocalsOnly: Boolean) {
        val tracks = player.currentTracks
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.size >= 2) {
            // Audio Group 0: Original Audio from video file
            // Audio Group 1: AI Isolated Vocal Audio from separated WAV file
            val targetGroup = if (vocalsOnly) audioGroups[1] else audioGroups[0]
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, 0))
                .build()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    // Update media sources based on video status & vocal audio track
    LaunchedEffect(video.status, video.videoUri, video.vocalUri) {
        if (video.status == VideoStatus.READY) {
            val videoUri = Uri.fromFile(File(video.videoUri)).let {
                if (File(video.videoUri).exists()) it else Uri.parse(video.videoUri)
            }

            val hasVocalFile = !video.vocalUri.isNullOrBlank() && File(video.vocalUri).exists()
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUri))

            if (hasVocalFile) {
                // Merge video track and isolated vocal audio track into one hardware-clock synced stream
                val vocalUri = Uri.fromFile(File(video.vocalUri))
                val vocalSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(vocalUri))
                val mergedSource = MergingMediaSource(videoSource, vocalSource)
                player.setMediaSource(mergedSource)
            } else {
                player.setMediaSource(videoSource)
            }

            player.prepare()
        }
    }

    // Master Player listener
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = player.duration
                    if (d > 0) duration = d
                    selectTrack(useIsolatedVocals)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                selectTrack(useIsolatedVocals)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Switch between AI Vocals Only and Original Audio
    LaunchedEffect(useIsolatedVocals) {
        selectTrack(useIsolatedVocals)
    }

    // Sync position tracker loop
    LaunchedEffect(isPlaying, isSliderDragging) {
        while (isPlaying && !isSliderDragging) {
            currentPosition = player.currentPosition
            delay(150L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            TopAppBar(
                title = {
                    Text(
                        text = video.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (video.status == VideoStatus.READY) {
                        IconButton(
                            onClick = { showExportConfirmDialog = true },
                            modifier = Modifier.testTag("export_video_top_bar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export Video with Vocals Only",
                                tint = AccentTeal
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )

            // Video / Audio Player Surface at Top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(if (isAudioSource) Color(0xFF0F172A) else Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (video.status == VideoStatus.READY) {
                    if (isAudioSource) {
                        // Polished Audio Track Visualization Card
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(AccentTeal.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Audio Track",
                                    tint = AccentTeal,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = video.title,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (useIsolatedVocals) "Playing: AI Isolated Vocals" else "Playing: Original Audio",
                                color = AccentTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = false // Custom minimal controls below
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("video_player_view")
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (video.status == VideoStatus.ERROR) AppStrings.playbackUnavailable(lang) else AppStrings.preparingMedia(lang),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Real-time Subtitle Overlay on Video Surface
                if (hasSubtitles && activeSubtitle != null) {
                    val isArabic = activeSubtitle.text.any { c -> c.code in 0x0600..0x06FF || c.code in 0x0750..0x077F }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("floating_subtitle_box")
                    ) {
                        Text(
                            text = activeSubtitle.text,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = if (isArabic) androidx.compose.ui.text.style.TextDirection.Rtl else androidx.compose.ui.text.style.TextDirection.Ltr
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Minimal Controls & Audio Mode Below
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (video.status == VideoStatus.ERROR) {
                    // Error Card with Retry Button
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = StatusError,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = AppStrings.vocalExtractionIssue(lang),
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = video.errorMessage ?: "Inference error",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                            )
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(AppStrings.retryExtraction(lang), color = Color.Black)
                            }
                        }
                    }
                } else if (video.status == VideoStatus.READY) {
                    // Audio Mode Switcher: AI Isolated Vocals vs Original Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = useIsolatedVocals,
                            onClick = { useIsolatedVocals = true },
                            label = { Text(AppStrings.chipAiVocalsOnly(lang)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentTeal,
                                selectedLabelColor = Color.Black,
                                selectedLeadingIconColor = Color.Black,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("vocals_mode_chip")
                        )

                        FilterChip(
                            selected = !useIsolatedVocals,
                            onClick = { useIsolatedVocals = false },
                            label = { Text(AppStrings.chipOriginalAudio(lang)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentTeal,
                                selectedLabelColor = Color.Black,
                                selectedLeadingIconColor = Color.Black,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("original_audio_chip")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AppStrings.modelLabel(lang, activeModelName),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        TextButton(
                            onClick = onReprocess,
                            modifier = Modifier.testTag("reprocess_vocals_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = AppStrings.reextractVocals(lang),
                                color = AccentTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Slider
                    val effectivePosition = if (isSliderDragging) sliderPosition else (currentPosition.toFloat() / duration.coerceAtLeast(1L).toFloat())
                    Slider(
                        value = effectivePosition.coerceIn(0f, 1f),
                        onValueChange = {
                            isSliderDragging = true
                            sliderPosition = it
                        },
                        onValueChangeFinished = {
                            val seekTarget = (sliderPosition * duration).toLong()
                            player.seekTo(seekTarget)
                            isSliderDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = AccentTeal,
                            activeTrackColor = AccentTeal,
                            inactiveTrackColor = Color(0xFF333333)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_seek_slider")
                    )

                    // Timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Media Control Buttons (Play/Pause)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            color = AccentTeal,
                            onClick = {
                                if (isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("play_pause_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (video.status == VideoStatus.READY) {
                        // Optional Whisper Speech Recognition & Subtitles Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transcription_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Subtitles,
                                            contentDescription = null,
                                            tint = AccentTeal,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = AppStrings.transcribeVocalsToSubtitles(lang),
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (hasSubtitles) "SRT Ready (${subtitleSegments.size} segments)" else "Real Offline Whisper ASR",
                                                color = if (hasSubtitles) AccentTeal else TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isTranscriptionEnabled,
                                        onCheckedChange = { onToggleTranscription?.invoke(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = AccentTeal,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = DarkBackground
                                        ),
                                        modifier = Modifier.testTag("transcription_toggle_switch")
                                    )
                                }

                                if (isTranscriptionEnabled) {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (hasSubtitles) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { showTranscriptDialog = true },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("view_transcript_button")
                                            ) {
                                                Text(
                                                    text = AppStrings.viewTranscript(lang),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            Button(
                                                onClick = { showDubbingDialog = true },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("dub_video_tts_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.RecordVoiceOver,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Dub Video",
                                                    color = Color.Black,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        if (hasWhisperModelInstalled) {
                                            var whisperLangMenuExpanded by remember { mutableStateOf(false) }
                                            val currentWhisperLangObj = WhisperTokenizer.AVAILABLE_LANGUAGES.firstOrNull { it.code == whisperLanguage }
                                                ?: WhisperTokenizer.AVAILABLE_LANGUAGES.first()

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (lang == AppLanguage.ARABIC) "لغة الصوت للتفريغ:" else "Speech Language:",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )

                                                Box {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = DarkSurfaceVariant,
                                                        modifier = Modifier
                                                            .clickable { whisperLangMenuExpanded = true }
                                                            .testTag("player_whisper_language_chip")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Language,
                                                                contentDescription = null,
                                                                tint = AccentTeal,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "${currentWhisperLangObj.displayName} (${currentWhisperLangObj.nativeName})",
                                                                color = TextPrimary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowDropDown,
                                                                contentDescription = null,
                                                                tint = TextSecondary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    DropdownMenu(
                                                        expanded = whisperLangMenuExpanded,
                                                        onDismissRequest = { whisperLangMenuExpanded = false },
                                                        modifier = Modifier.background(DarkSurfaceVariant)
                                                    ) {
                                                        WhisperTokenizer.AVAILABLE_LANGUAGES.forEach { langItem ->
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Text(
                                                                            text = langItem.displayName,
                                                                            color = if (langItem.code == whisperLanguage) AccentTeal else TextPrimary,
                                                                            fontWeight = if (langItem.code == whisperLanguage) FontWeight.Bold else FontWeight.Normal,
                                                                            fontSize = 12.sp
                                                                        )
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text(
                                                                            text = "(${langItem.nativeName})",
                                                                            color = TextSecondary,
                                                                            fontSize = 11.sp
                                                                        )
                                                                    }
                                                                },
                                                                onClick = {
                                                                    onSetWhisperLanguage?.invoke(langItem.code)
                                                                    whisperLangMenuExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = { onStartTranscription?.invoke() },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("generate_subtitles_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Subtitles,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = AppStrings.generateSubtitles(lang),
                                                    color = Color.Black,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = AppStrings.whisperModelNotInstalled(lang),
                                                    color = StatusError,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(
                                                    onClick = { onOpenModelSettings?.invoke() },
                                                    modifier = Modifier.testTag("download_whisper_prompt_button")
                                                ) {
                                                    Text(
                                                        text = AppStrings.downloadWhisperModel(lang),
                                                        color = AccentTeal,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { showDubbingDialog = true },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("dub_with_imported_srt_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RecordVoiceOver,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Dub with Imported SRT",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { showExportConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("export_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.exportVideoVocalsOnly(lang),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Model & Hardware Acceleration Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Acceleration: ${accelerationMode.displayName} (${accelerationMode.speedBadge})",
                                    color = AccentTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hardware: $hardwareBackend • Spleeter 2-Stem Neural Engine",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        val isWhisperPhase = processingMessage.contains("Whisper", ignoreCase = true) ||
                processingMessage.contains("transcrib", ignoreCase = true) ||
                processingMessage.contains("subtitle", ignoreCase = true)

        val overlayMessage = if (processingMessage.isNotBlank()) {
            processingMessage
        } else {
            AppStrings.extractingVocalsWithSpleeter(lang, activeModelName)
        }

        // Centered Processing Overlay if video is NEW or PROCESSING
        ProcessingOverlay(
            isVisible = isProcessing || video.status == VideoStatus.PROCESSING || video.status == VideoStatus.NEW,
            progress = processingProgress,
            message = overlayMessage,
            elapsedTime = elapsedTime,
            estimatedRemaining = estimatedRemaining,
            isBackgroundProtected = isBackgroundProtected,
            lang = lang,
            onCancel = onCancelProcessing,
            cancelButtonText = if (isWhisperPhase) AppStrings.cancelTranscription(lang) else null
        )

        // Export Confirmation Dialog with Audio and Video options
        if (showExportConfirmDialog) {
            ExportOptionsDialog(
                video = video,
                onDismiss = { showExportConfirmDialog = false },
                onConfirmExport = { format, deleteOriginal ->
                    showExportConfirmDialog = false
                    if (onExportMedia != null) {
                        onExportMedia(format, deleteOriginal)
                    } else {
                        onExportVideo(deleteOriginal)
                    }
                }
            )
        }

        // Subtitles & Transcript Viewer Dialog with seek-on-tap and SRT export
        if (showTranscriptDialog) {
            TranscriptDialog(
                segments = subtitleSegments,
                currentPositionMs = currentPosition,
                srtFile = srtFile,
                onDismiss = { showTranscriptDialog = false },
                onSeekTo = { targetMs ->
                    player.seekTo(targetMs)
                    showTranscriptDialog = false
                },
                onOpenDubbing = {
                    showDubbingDialog = true
                }
            )
        }

        // Android TTS Voice Dubbing Dialog
        if (showDubbingDialog) {
            TtsDubbingDialog(
                video = video,
                segments = subtitleSegments,
                onDismiss = { showDubbingDialog = false },
                onConfirmDubbing = { config ->
                    showDubbingDialog = false
                    showTranscriptDialog = false
                    val cuesToDub = if (!config.preTranslatedSegments.isNullOrEmpty()) {
                        config.preTranslatedSegments
                    } else {
                        subtitleSegments
                    }
                    onExportDubbed?.invoke(video, cuesToDub, config)
                }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
