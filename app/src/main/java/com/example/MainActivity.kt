package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.ui.locale.AppLanguage
import com.example.ui.components.ExportProgressDialog
import com.example.ui.screens.VideoListScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()

        setContent {
            val currentLanguage by viewModel.currentLanguage.collectAsState()

            CompositionLocalProvider(
                LocalLayoutDirection provides currentLanguage.layoutDirection,
                com.example.ui.locale.LocalAppLanguage provides currentLanguage
            ) {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = DarkBackground
                    ) {
                    val videos by viewModel.videoList.collectAsState()
                    val selectedVideo by viewModel.selectedVideo.collectAsState()
                    val isProcessing by viewModel.isProcessing.collectAsState()
                    val progress by viewModel.processingProgress.collectAsState()
                    val modelName by viewModel.customModelName.collectAsState()
                    val customModelPath by viewModel.customModelPath.collectAsState()
                    val modelInfoMessage by viewModel.modelInfoMessage.collectAsState()
                    val accelerationMode by viewModel.accelerationMode.collectAsState()
                    val hardwareBackend = viewModel.hardwareBackend

                    val processingElapsedFormatted by viewModel.processingElapsedTimeFormatted.collectAsState()
                    val processingEstimatedRemaining by viewModel.processingEstimatedRemainingFormatted.collectAsState()
                    val isBackgroundProtected by viewModel.isBackgroundProtected.collectAsState()
                    val activeProcessingVideoId by viewModel.activeProcessingVideoId.collectAsState()
                    val activeProcessingVideoTitle by viewModel.activeProcessingVideoTitle.collectAsState()

                    val isExporting by viewModel.isExporting.collectAsState()
                    val exportProgress by viewModel.exportProgress.collectAsState()
                    val exportResult by viewModel.exportResult.collectAsState()
                    val exportError by viewModel.exportError.collectAsState()

                    val savedFolders by viewModel.savedFolders.collectAsState()
                    val selectedFolder by viewModel.selectedFolder.collectAsState()
                    val folderMediaItems by viewModel.activeFolderMediaItems.collectAsState()
                    val isScanningFolder by viewModel.isScanningFolder.collectAsState()

                    val downloadProgressState by viewModel.downloadProgressState.collectAsState()
                    val isDownloadingMedia by viewModel.isDownloadingMedia.collectAsState()
                    val downloadElapsedFormatted by viewModel.downloadElapsedFormatted.collectAsState()
                    val downloadErrorMessage by viewModel.downloadErrorMessage.collectAsState()
                    val sharedMediaUrl by viewModel.sharedMediaUrl.collectAsState()
                    val webImportResolutionState by viewModel.webImportResolutionState.collectAsState()

                    // Android 13+ Notification permission request for foreground service
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                        ) {}
                        LaunchedEffect(Unit) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    BackHandler(enabled = selectedVideo != null) {
                        viewModel.clearSelectedVideo()
                    }

                    if (selectedVideo != null) {
                        VideoPlayerScreen(
                            video = selectedVideo!!,
                            isProcessing = isProcessing,
                            processingProgress = progress,
                            activeModelName = modelName,
                            accelerationMode = accelerationMode,
                            hardwareBackend = hardwareBackend,
                            elapsedTime = processingElapsedFormatted,
                            estimatedRemaining = processingEstimatedRemaining,
                            isBackgroundProtected = isBackgroundProtected,
                            onBack = { viewModel.clearSelectedVideo() },
                            onRetry = { viewModel.retrySeparation(selectedVideo!!) },
                            onReprocess = { viewModel.reprocessVideo(selectedVideo!!) },
                            onExportVideo = { deleteOriginal -> viewModel.exportVideo(selectedVideo!!, deleteOriginal) },
                            onExportMedia = { format, deleteOriginal -> viewModel.exportMedia(selectedVideo!!, format, deleteOriginal) },
                            onCancelProcessing = { viewModel.cancelSeparation() }
                        )
                    } else {
                        VideoListScreen(
                            videos = videos,
                            activeModelName = modelName,
                            customModelPath = customModelPath,
                            modelInfoMessage = modelInfoMessage,
                            accelerationMode = accelerationMode,
                            hardwareBackend = hardwareBackend,
                            downloadProgressState = downloadProgressState,
                            isDownloadingMedia = isDownloadingMedia,
                            downloadErrorMessage = downloadErrorMessage,
                            sharedMediaUrl = sharedMediaUrl,
                            downloadElapsedFormatted = downloadElapsedFormatted,
                            isProcessing = isProcessing,
                            processingProgress = progress,
                            processingElapsedFormatted = processingElapsedFormatted,
                            processingEstimatedRemaining = processingEstimatedRemaining,
                            activeProcessingVideoId = activeProcessingVideoId,
                            activeProcessingVideoTitle = activeProcessingVideoTitle,
                            isBackgroundProtected = isBackgroundProtected,
                            savedFolders = savedFolders,
                            selectedFolder = selectedFolder,
                            folderMediaItems = folderMediaItems,
                            isScanningFolder = isScanningFolder,
                            onFolderGranted = { uri -> viewModel.registerAndScanFolder(uri) },
                            onFolderSelected = { folder -> viewModel.selectAndScanFolder(folder) },
                            onFolderRemoved = { uriStr -> viewModel.removeSavedFolder(uriStr) },
                            onRefreshFolder = { viewModel.refreshActiveFolder() },
                            onImportStorageItem = { item -> viewModel.importMediaFromUri(item.uri, item.name) },
                            onVideoSelected = { video -> viewModel.selectVideo(video) },
                            onImportVideo = { uri -> viewModel.importMediaFromUri(uri) },
                            onImportSampleVideo = { viewModel.importSampleVideo() },
                            onDeleteVideo = { video -> viewModel.deleteVideo(video) },
                            onImportCustomModel = { uri -> viewModel.importCustomModel(uri) },
                            onResetToBuiltInModel = { viewModel.resetToBuiltInModel() },
                            onSelectAccelerationMode = { mode -> viewModel.setAccelerationMode(mode) },
                            resolutionState = webImportResolutionState,
                            onSubmitMediaUrl = { url -> viewModel.processSocialMediaUrl(url) },
                            onProbeResolutions = { url -> viewModel.probeResolutionsForUrl(url) },
                            onSelectResolution = { option -> viewModel.selectResolutionOption(option) },
                            onConfirmResolutionDownload = { viewModel.confirmDownloadWithResolution() },
                            onBackToUrlInput = { viewModel.backToUrlInput() },
                            onCancelDownload = { viewModel.cancelMediaDownload() },
                            onClearDownloadError = { viewModel.clearDownloadError() },
                            onDismissDownloadDialog = { viewModel.dismissDownloadDialog() },
                            onClearModelMessage = { viewModel.clearModelInfoMessage() },
                            onReprocessVideo = { video -> viewModel.reprocessVideo(video) },
                            onExportVideo = { video, deleteOriginal -> viewModel.exportVideo(video, deleteOriginal) },
                            onExportMedia = { video, format, deleteOriginal -> viewModel.exportMedia(video, format, deleteOriginal) },
                            onCancelProcessing = { viewModel.cancelSeparation() },
                            currentLanguage = currentLanguage,
                            onSelectLanguage = { lang -> viewModel.setLanguage(lang) }
                        )
                    }

                    // Global Export Progress and Success Dialog
                    if (isExporting || exportResult != null || exportError != null) {
                        ExportProgressDialog(
                            isExporting = isExporting,
                            exportProgress = exportProgress,
                            exportResult = exportResult,
                            exportError = exportError,
                            onDismiss = { viewModel.dismissExportDialog() }
                        )
                    }
                }
            }
        }
    }
}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                viewModel.handleIncomingShareUrl(text)
                return
            }
            val clipData = intent.clipData
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString()
                if (!clipText.isNullOrBlank()) {
                    viewModel.handleIncomingShareUrl(clipText)
                    return
                }
            }

            // In case a video file was shared directly to the app
            @Suppress("DEPRECATION")
            val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) {
                viewModel.importVideoFromUri(streamUri)
            }
        }
    }
}
