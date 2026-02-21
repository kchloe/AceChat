package com.chloe.acechat.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloe.acechat.domain.model.ModelDownloadState
import kotlinx.coroutines.delay

@Composable
fun ModelDownloadScreen(
    viewModel: ModelDownloadViewModel,
    onDownloadCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    // Track the previous state to determine if we just completed a download
    // (Downloading → Downloaded) vs. the file was already there (Checking → Downloaded).
    // Only the former needs the 500ms "Setup Complete!" display. (이슈 2 수정)
    var prevState by remember { mutableStateOf<ModelDownloadState>(ModelDownloadState.Checking) }

    LaunchedEffect(downloadState) {
        if (downloadState is ModelDownloadState.Downloaded) {
            val justFinishedDownloading = prevState is ModelDownloadState.Downloading
            if (justFinishedDownloading) {
                delay(800) // Show "Setup Complete!" briefly after an actual download
            }
            onDownloadCompleted() // (이슈 3 수정) 실제로 호출됨
        }
        prevState = downloadState
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (downloadState) {
                ModelDownloadState.Checking -> {
                    CheckingUI()
                }

                ModelDownloadState.NotDownloaded -> {
                    NotDownloadedUI(
                        onDownloadClick = { viewModel.startDownload() },
                    )
                }

                is ModelDownloadState.Downloading -> {
                    DownloadingUI(
                        progress = (downloadState as ModelDownloadState.Downloading).progress,
                        onCancelClick = { viewModel.cancelDownload() },
                    )
                }

                ModelDownloadState.Downloaded -> {
                    DownloadedUI()
                }

                is ModelDownloadState.Failed -> {
                    FailedUI(
                        errorMessage = (downloadState as ModelDownloadState.Failed).message,
                        onRetryClick = { viewModel.startDownload() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckingUI(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotDownloadedUI(
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AceChat",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        Text(
            text = "AI Model Required",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = "AceChat을 사용하려면 AI 모델(약 700MB)을 다운로드해야 합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        Text(
            text = "WiFi 연결을 권장합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 48.dp),
        )

        Button(onClick = onDownloadClick) {
            Text("Download Model")
        }
    }
}

@Composable
private fun DownloadingUI(
    progress: Int,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Downloading Model...",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "$progress%",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(onClick = onCancelClick) {
            Text("Cancel")
        }
    }
}

@Composable
private fun DownloadedUI(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.padding(bottom = 32.dp),
        )

        Text(
            text = "Setup Complete!",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = "Starting AceChat...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedUI(
    errorMessage: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Download Failed",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp),
        )

        Button(onClick = onRetryClick) {
            Text("Retry")
        }
    }
}
