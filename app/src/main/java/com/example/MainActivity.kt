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

                    BackHandler(enabled = selectedVideo != null) {
                        viewModel.clearSelectedVideo()
                    }

                    if (selectedVideo != null) {
                        VideoPlayerScreen(
                            video = selectedVideo!!,
                            isProcessing = isProcessing,
                            processingProgress = progress,
                            onBack = { viewModel.clearSelectedVideo() },
                            onRetry = { viewModel.retrySeparation(selectedVideo!!) }
                        )
                    } else {
                        VideoListScreen(
                            videos = videos,
                            onVideoSelected = { video -> viewModel.selectVideo(video) },
                            onImportVideo = { uri -> viewModel.importVideoFromUri(uri) },
                            onImportSampleVideo = { viewModel.importSampleVideo() },
                            onDeleteVideo = { video -> viewModel.deleteVideo(video) }
                        )
                    }
                }
            }
        }
    }
}
