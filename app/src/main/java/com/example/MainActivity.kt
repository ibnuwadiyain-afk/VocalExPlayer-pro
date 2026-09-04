package com.example

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        enableEdgeToEdge()

        setContent {
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

                    val isExporting by viewModel.isExporting.collectAsState()
                    val exportProgress by viewModel.exportProgress.collectAsState()
                    val exportResult by viewModel.exportResult.collectAsState()
                    val exportError by viewModel.exportError.collectAsState()

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
                            onBack = { viewModel.clearSelectedVideo() },
                            onRetry = { viewModel.retrySeparation(selectedVideo!!) },
                            onReprocess = { viewModel.reprocessVideo(selectedVideo!!) },
                            onExportVideo = { viewModel.exportVideo(selectedVideo!!) }
                        )
                    } else {
                        VideoListScreen(
                            videos = videos,
                            activeModelName = modelName,
                            customModelPath = customModelPath,
                            modelInfoMessage = modelInfoMessage,
                            accelerationMode = accelerationMode,
                            hardwareBackend = hardwareBackend,
                            onVideoSelected = { video -> viewModel.selectVideo(video) },
                            onImportVideo = { uri -> viewModel.importVideoFromUri(uri) },
                            onImportSampleVideo = { viewModel.importSampleVideo() },
                            onDeleteVideo = { video -> viewModel.deleteVideo(video) },
                            onImportCustomModel = { uri -> viewModel.importCustomModel(uri) },
                            onResetToBuiltInModel = { viewModel.resetToBuiltInModel() },
                            onSelectAccelerationMode = { mode -> viewModel.setAccelerationMode(mode) },
                            onClearModelMessage = { viewModel.clearModelInfoMessage() },
                            onReprocessVideo = { video -> viewModel.reprocessVideo(video) },
                            onExportVideo = { video -> viewModel.exportVideo(video) }
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
