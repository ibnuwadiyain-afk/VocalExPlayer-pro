package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import com.example.data.model.VideoItemEntity
import com.example.data.model.VideoStatus
import com.example.export.ExportFormat
import com.example.media.DownloadProgressState
import com.example.onnx.AccelerationMode
import com.example.storage.GrantedFolder
import com.example.storage.StorageMediaItem
import com.example.ui.components.ExportConfirmDialog
import com.example.ui.components.ExportOptionsDialog
import com.example.ui.components.ModelSettingsDialog
import com.example.ui.components.StorageFolderBrowserSheet
import com.example.ui.components.UrlImportDialog
import com.example.ui.components.VideoListItem
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageSelectionDialog
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusProcessing
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    videos: List<VideoItemEntity>,
    activeModelName: String,
    customModelPath: String?,
    modelInfoMessage: String?,
    accelerationMode: AccelerationMode = AccelerationMode.TURBO,
    hardwareBackend: String = "Multi-Core CPU",
    downloadProgressState: DownloadProgressState? = null,
    isDownloadingMedia: Boolean = false,
    downloadErrorMessage: String? = null,
    sharedMediaUrl: String? = null,
    downloadElapsedFormatted: String = "00:00",
    isProcessing: Boolean = false,
    processingProgress: Float = 0f,
    processingElapsedFormatted: String = "00:00",
    processingEstimatedRemaining: String? = null,
    activeProcessingVideoId: String? = null,
    activeProcessingVideoTitle: String? = null,
    isBackgroundProtected: Boolean = true,
    savedFolders: List<GrantedFolder> = emptyList(),
    selectedFolder: GrantedFolder? = null,
    folderMediaItems: List<StorageMediaItem> = emptyList(),
    isScanningFolder: Boolean = false,
    onFolderGranted: (Uri) -> Unit = {},
    onFolderSelected: (GrantedFolder) -> Unit = {},
    onFolderRemoved: (String) -> Unit = {},
    onRefreshFolder: () -> Unit = {},
    onImportStorageItem: (StorageMediaItem) -> Unit = {},
    onVideoSelected: (VideoItemEntity) -> Unit,
    onImportVideo: (Uri) -> Unit,
    onImportSampleVideo: () -> Unit,
    onDeleteVideo: (VideoItemEntity) -> Unit,
    onImportCustomModel: (Uri) -> Unit,
    onResetToBuiltInModel: () -> Unit,
    onSelectAccelerationMode: (AccelerationMode) -> Unit = {},
    models: List<com.example.model.ModelEntity> = emptyList(),
    selectedVocalModelId: String = "spleeter_2stems",
    selectedWhisperModelId: String? = null,
    whisperLanguage: String = "auto",
    onSelectModel: (String, com.example.model.ModelType) -> Unit = { _, _ -> },
    onDownloadModel: (String) -> Unit = {},
    onDeleteModel: (String) -> Unit = {},
    onSetWhisperLanguage: (String) -> Unit = {},
    onImportCustomModelWithType: (Uri, com.example.model.ModelType) -> Unit = { uri, _ -> onImportCustomModel(uri) },
    isBackupExporting: Boolean = false,
    backupExportProgress: Float = 0f,
    backupExportMessage: String? = null,
    onClearBackupExportMessage: () -> Unit = {},
    onExportModel: (String, Uri) -> Unit = { _, _ -> },
    onBackupAllModels: (Uri) -> Unit = {},
    onRestoreModels: (Uri) -> Unit = {},
    onShareModel: (String) -> Unit = {},
    onShareBackup: () -> Unit = {},
    resolutionState: com.example.MainViewModel.WebImportResolutionState? = null,
    onSubmitMediaUrl: (String) -> Unit = {},
    onProbeResolutions: (String) -> Unit = {},
    onSelectResolution: (com.example.media.MediaResolutionOption) -> Unit = {},
    onConfirmResolutionDownload: () -> Unit = {},
    onBackToUrlInput: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onClearDownloadError: () -> Unit = {},
    onDismissDownloadDialog: () -> Unit = {},
    onClearModelMessage: () -> Unit,
    onReprocessVideo: (VideoItemEntity) -> Unit,
    onExportVideo: (VideoItemEntity, Boolean) -> Unit,
    onExportMedia: ((VideoItemEntity, ExportFormat, Boolean) -> Unit)? = null,
    onCancelProcessing: (() -> Unit)? = null,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var videoForAction by remember { mutableStateOf<VideoItemEntity?>(null) }
    var videoToExport by remember { mutableStateOf<VideoItemEntity?>(null) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showStorageFolderBrowser by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sharedMediaUrl) {
        if (!sharedMediaUrl.isNullOrBlank()) {
            showUrlDialog = true
        }
    }

    // Video Picker via modern zero-permission Android Photo Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { onImportVideo(it) }
    }

    // Fallback OpenDocument launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportVideo(it) }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppStrings.appName(currentLanguage),
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showStorageFolderBrowser = true },
                        modifier = Modifier.testTag("storage_folders_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = AppStrings.storageFolders(currentLanguage),
                            tint = AccentTeal
                        )
                    }
                    IconButton(
                        onClick = { showUrlDialog = true },
                        modifier = Modifier.testTag("import_url_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = AppStrings.importUrl(currentLanguage),
                            tint = AccentTeal
                        )
                    }
                    IconButton(
                        onClick = { showModelDialog = true },
                        modifier = Modifier.testTag("model_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = AppStrings.aiModelSettings(currentLanguage),
                            tint = AccentTeal
                        )
                    }
                    IconButton(
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier.testTag("language_selector_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = AppStrings.languagePreferences(currentLanguage),
                            tint = AccentTeal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showStorageFolderBrowser = true },
                    containerColor = DarkSurfaceVariant,
                    contentColor = AccentTeal,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("storage_folders_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = AppStrings.fabFolders(currentLanguage),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.fabFolders(currentLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                ExtendedFloatingActionButton(
                    onClick = { showUrlDialog = true },
                    containerColor = DarkSurfaceVariant,
                    contentColor = AccentTeal,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("import_url_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = AppStrings.fabLink(currentLanguage),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.fabLink(currentLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                FloatingActionButton(
                    onClick = {
                        try {
                            documentPickerLauncher.launch(arrayOf("audio/*", "video/*"))
                        } catch (e: Exception) {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    },
                    containerColor = AccentTeal,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("import_video_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = AppStrings.fabImport(currentLanguage))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.fabImport(currentLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Model Message Toast / Notification Banner if present
                if (!modelInfoMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = modelInfoMessage,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onClearModelMessage,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // AI Model Status Bar Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { showModelDialog = true }
                        .testTag("ai_model_status_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentTeal.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = AppStrings.aiSeparationEngine(currentLanguage),
                                    color = AccentTeal,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentTeal.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = AppStrings.spleeterBadge(currentLanguage),
                                        color = AccentTeal,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = activeModelName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = AppStrings.changeModel(currentLanguage),
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Active Background Download Banner (when dialog is dismissed to background)
                if (isDownloadingMedia && !showUrlDialog) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF16253B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { showUrlDialog = true }
                            .testTag("active_background_download_banner")
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
                                    CircularProgressIndicator(
                                        color = AccentTeal,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = when (downloadProgressState) {
                                                is DownloadProgressState.Downloading -> "${AppStrings.downloadingWebMedia(currentLanguage)} (${(downloadProgressState.progress * 100).toInt()}%)"
                                                is DownloadProgressState.Resolving -> downloadProgressState.message
                                                else -> AppStrings.downloadingInBackground(currentLanguage)
                                            },
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "⏱ ${AppStrings.elapsedLabel(currentLanguage)}$downloadElapsedFormatted • ${AppStrings.tapToViewDetails(currentLanguage)}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { showUrlDialog = true },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(AppStrings.viewButton(currentLanguage), color = AccentTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = onCancelDownload,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = AppStrings.cancelDownload(currentLanguage),
                                            tint = StatusError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (downloadProgressState is DownloadProgressState.Downloading) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgressState.progress },
                                    color = AccentTeal,
                                    trackColor = DarkSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }

                // Active Processing Status Banner (survives background & shows timer/ETA)
                if (isProcessing) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF132620),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = videos.find { it.id == activeProcessingVideoId }
                                if (target != null) {
                                    onVideoSelected(target)
                                }
                            }
                            .testTag("active_processing_banner")
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
                                    CircularProgressIndicator(
                                        color = AccentTeal,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeProcessingVideoTitle?.let { AppStrings.extractingVocalsBanner(currentLanguage, it) } ?: AppStrings.aiVocalExtractionActive(currentLanguage),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "${(processingProgress * 100).toInt()}%",
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { processingProgress.coerceIn(0f, 1f) },
                                color = AccentTeal,
                                trackColor = Color(0xFF243B32),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = StatusProcessing,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${AppStrings.elapsedLabel(currentLanguage)}$processingElapsedFormatted",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                    if (!processingEstimatedRemaining.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val etaText = if (processingEstimatedRemaining == "FINISHING") {
                                            AppStrings.finishingUp(currentLanguage)
                                        } else {
                                            AppStrings.remainingTime(currentLanguage, processingEstimatedRemaining)
                                        }
                                        Text(
                                            text = "• $etaText",
                                            color = AccentTeal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = AppStrings.backgroundSafe(currentLanguage),
                                        color = AccentTeal,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                if (videos.isEmpty()) {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("empty_state_view"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = DarkSurface,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = AppStrings.emptyStateTitle(currentLanguage),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = AppStrings.emptyStateDescription(currentLanguage),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { showStorageFolderBrowser = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(50.dp)
                                .testTag("browse_storage_folders_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.browseStorageFolders(currentLanguage), color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                documentPickerLauncher.launch(arrayOf("audio/*", "video/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(50.dp)
                                .testTag("import_gallery_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentTeal)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.importAudioOrVideoFile(currentLanguage), color = TextPrimary, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { showUrlDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(50.dp)
                                .testTag("import_youtube_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentTeal)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.fromYoutubeOrLink(currentLanguage), color = TextPrimary, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onImportSampleVideo,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(50.dp)
                                .testTag("load_demo_track_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = AccentTeal)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.loadDemoMusicVideo(currentLanguage), fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("video_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = AppStrings.listTip(currentLanguage),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        items(videos, key = { it.id }) { video ->
                            val isActive = isProcessing && (video.id == activeProcessingVideoId)
                            VideoListItem(
                                video = video,
                                onClick = { onVideoSelected(video) },
                                onLongClick = { videoForAction = video },
                                elapsedTime = if (isActive) processingElapsedFormatted else "",
                                isActiveProcessing = isActive
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                        }
                    }
                }
            }
        }
    }

    // Video Action Options Dialog (Re-extract or Delete)
    videoForAction?.let { video ->
        AlertDialog(
            onDismissRequest = { videoForAction = null },
            title = { Text(video.title, color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = AppStrings.chooseActionTitle(currentLanguage),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (video.status == VideoStatus.READY) {
                        Button(
                            onClick = {
                                videoToExport = video
                                videoForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("dialog_export_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.exportVideoVocalsOnly(currentLanguage), color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            onReprocessVideo(video)
                            videoForAction = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_reprocess_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentTeal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppStrings.reextractVocalsWithAi(currentLanguage), color = AccentTeal, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            onDeleteVideo(video)
                            videoForAction = null
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = StatusError)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppStrings.deleteVideo(currentLanguage), color = StatusError, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { videoForAction = null }) {
                    Text(AppStrings.cancel(currentLanguage), color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // AI Model Settings & Custom Model Import Dialog
    if (showModelDialog) {
        ModelSettingsDialog(
            activeModelName = activeModelName,
            customModelPath = customModelPath,
            accelerationMode = accelerationMode,
            hardwareBackend = hardwareBackend,
            models = models,
            selectedVocalModelId = selectedVocalModelId,
            selectedWhisperModelId = selectedWhisperModelId,
            whisperLanguage = whisperLanguage,
            isBackupExporting = isBackupExporting,
            backupExportProgress = backupExportProgress,
            backupExportMessage = backupExportMessage,
            onClearBackupExportMessage = onClearBackupExportMessage,
            onDismiss = { showModelDialog = false },
            onSelectAccelerationMode = onSelectAccelerationMode,
            onSelectModel = onSelectModel,
            onDownloadModel = onDownloadModel,
            onDeleteModel = onDeleteModel,
            onSetWhisperLanguage = onSetWhisperLanguage,
            onImportCustomModel = { uri ->
                onImportCustomModel(uri)
                showModelDialog = false
            },
            onImportCustomModelWithType = { uri, type ->
                onImportCustomModelWithType(uri, type)
                showModelDialog = false
            },
            onExportModel = onExportModel,
            onBackupAllModels = onBackupAllModels,
            onRestoreModels = onRestoreModels,
            onShareModel = onShareModel,
            onShareBackup = onShareBackup,
            onResetToBuiltIn = {
                onResetToBuiltInModel()
                showModelDialog = false
            }
        )
    }

    // YouTube & Social Media URL Import Dialog
    if (showUrlDialog || (!sharedMediaUrl.isNullOrBlank() && !isDownloadingMedia) || resolutionState != null) {
        UrlImportDialog(
            initialUrl = sharedMediaUrl ?: "",
            downloadState = downloadProgressState,
            isDownloading = isDownloadingMedia,
            errorMessage = downloadErrorMessage,
            elapsedTime = downloadElapsedFormatted,
            resolutionState = resolutionState,
            onDismiss = {
                showUrlDialog = false
                onBackToUrlInput()
                onDismissDownloadDialog()
            },
            onSubmitUrl = { url ->
                onProbeResolutions(url)
            },
            onProbeResolutions = onProbeResolutions,
            onSelectResolution = onSelectResolution,
            onConfirmResolutionDownload = onConfirmResolutionDownload,
            onBackToUrlInput = onBackToUrlInput,
            onCancelDownload = {
                showUrlDialog = false
                onCancelDownload()
            },
            onLoadOfflineDemo = {
                onImportSampleVideo()
            },
            onClearError = onClearDownloadError
        )
    }

    // Storage Folder Browser Sheet
    StorageFolderBrowserSheet(
        isOpen = showStorageFolderBrowser,
        savedFolders = savedFolders,
        selectedFolder = selectedFolder,
        mediaItems = folderMediaItems,
        isScanning = isScanningFolder,
        onDismiss = { showStorageFolderBrowser = false },
        onFolderSelected = onFolderSelected,
        onFolderGranted = onFolderGranted,
        onFolderRemoved = onFolderRemoved,
        onRefreshFolder = onRefreshFolder,
        onMediaItemSelected = { item ->
            showStorageFolderBrowser = false
            onImportStorageItem(item)
        },
        onPickAnyFileDirectly = {
            showStorageFolderBrowser = false
            documentPickerLauncher.launch(arrayOf("audio/*", "video/*"))
        }
    )

    // Export Confirmation Dialog with Audio and Video options
    videoToExport?.let { video ->
        ExportOptionsDialog(
            video = video,
            onDismiss = { videoToExport = null },
            onConfirmExport = { format, deleteOriginal ->
                val target = videoToExport
                videoToExport = null
                if (target != null) {
                    if (onExportMedia != null) {
                        onExportMedia(target, format, deleteOriginal)
                    } else {
                        onExportVideo(target, deleteOriginal)
                    }
                }
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { lang ->
                onSelectLanguage(lang)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}
