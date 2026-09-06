package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.data.model.VideoItemEntity
import com.example.ui.components.ExportAudioChoice
import com.example.ui.components.ExportProgressDialog
import com.example.ui.components.ModelSettingsDialog
import com.example.ui.components.ProcessingOverlay
import com.example.ui.components.UrlImportDialog
import com.example.ui.components.VideoListItem
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.InstrumentalTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: MainViewModel,
    onNavigateToPlayer: (videoId: Long) -> Unit
) {
    val videos by viewModel.videoList.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val activeTitle by viewModel.activeVideoTitle.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val accelerationMode by viewModel.accelerationMode.collectAsState()
    val customModelPath by viewModel.customModelPath.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()

    var showUrlDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var videoToExport by remember { mutableStateOf<VideoItemEntity?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.importVideoFromUri(it) }
    }

    val onnxFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importCustomModel(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "VocalExtractor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "UVR MDX-Net Neural AI Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = InstrumentalTeal,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showUrlDialog = true },
                        modifier = Modifier.testTag("btn_top_import_url")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Import from Link",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("btn_top_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Video") },
                text = { Text("Import Video") },
                modifier = Modifier.testTag("fab_import_video"),
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (videos.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Videos in Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Import any video from your device or paste a web video link to isolate vocals with genuine UVR MDX-Net neural AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier.testTag("empty_state_import_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Video File")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showUrlDialog = true },
                        modifier = Modifier.testTag("empty_state_url_btn")
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import from URL")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    items(videos, key = { it.id }) { video ->
                        VideoListItem(
                            video = video,
                            onClick = { onNavigateToPlayer(video.id) },
                            onStartSeparation = { viewModel.startSeparation(video) },
                            onExport = { videoToExport = video },
                            onDelete = { viewModel.deleteVideo(video) }
                        )
                    }
                }
            }

            // Processing Overlay
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

    if (showUrlDialog) {
        UrlImportDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url ->
                showUrlDialog = false
                viewModel.processSocialMediaUrl(url)
            }
        )
    }

    if (showSettingsDialog) {
        ModelSettingsDialog(
            currentMode = accelerationMode,
            customModelName = customModelPath?.substringAfterLast("/"),
            onPickCustomModel = {
                onnxFilePickerLauncher.launch("*/*")
            },
            onResetToBundledModel = {
                viewModel.resetToBundledModel()
            },
            onSave = { newMode ->
                viewModel.updateAccelerationMode(newMode)
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (videoToExport != null) {
        ExportProgressDialog(
            isExporting = isExporting,
            progress = exportProgress,
            onStartExport = { choice ->
                val targetVideo = videoToExport
                if (targetVideo != null) {
                    viewModel.exportVideo(targetVideo, choice) {
                        videoToExport = null
                    }
                }
            },
            onDismiss = { videoToExport = null }
        )
    }
}
