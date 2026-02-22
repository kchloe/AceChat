package com.chloe.acechat.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloe.acechat.data.stt.SttState
import com.chloe.acechat.domain.model.ConversationState
import com.chloe.acechat.presentation.components.MessageBubble
import com.chloe.acechat.presentation.components.MicButton
import com.chloe.acechat.presentation.components.MicButtonState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.uiState.collectAsStateWithLifecycle()
    val conversationState by viewModel.conversationState.collectAsStateWithLifecycle()
    val sttState by viewModel.sttState.collectAsStateWithLifecycle()

    val isLoading = conversationState is ConversationState.Loading
    val isIdle = conversationState is ConversationState.Idle
    val isError = conversationState is ConversationState.Error

    // ── 런타임 권한 처리 ────────────────────────────────────────────────────────
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasAudioPermission = isGranted
    }
    // 화면 최초 진입 시 권한이 없으면 요청
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // ───────────────────────────────────────────────────────────────────────────

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when a new message is added or streaming content grows
    val lastMessageContent = messages.lastOrNull()?.content
    LaunchedEffect(messages.size, lastMessageContent) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("AceChat") },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearConversation() },
                        enabled = isIdle && messages.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear conversation",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                // Full-screen loading while the engine initialises (no messages yet)
                isLoading && messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading model...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Full-screen error when the engine failed to initialise
                isError && messages.isEmpty() -> {
                    val errorMsg = (conversationState as ConversationState.Error).message
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Failed to load model",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Main chat UI
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(message = message)
                            }
                        }

                        // ── STT 실시간 미리보기 ────────────────────────────────────
                        val previewText = when (val s = sttState) {
                            is SttState.Listening -> "Listening..."
                            is SttState.PartialResult -> s.text
                            else -> null
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewText != null) {
                                Text(
                                    text = previewText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        // ─────────────────────────────────────────────────────────

                        // ── 마이크 버튼 ───────────────────────────────────────────
                        val micButtonState = when {
                            sttState is SttState.Listening -> MicButtonState.LISTENING
                            isIdle -> MicButtonState.IDLE
                            else -> MicButtonState.DISABLED
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MicButton(
                                state = micButtonState,
                                hasPermission = hasAudioPermission,
                                onTap = { viewModel.onMicTapped() },
                            )
                        }

                        // 권한 없음 안내 문구
                        if (!hasAudioPermission) {
                            Text(
                                text = "Microphone permission is required to use voice input.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 12.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // ─────────────────────────────────────────────────────────
                    }
                }
            }
        }
    }
}
