package com.chloe.acechat.presentation.conversationlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter = SimpleDateFormat("MMM dd", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onOpenChat: (conversationId: String, engineMode: EngineMode) -> Unit,
    onOpenSettings: () -> Unit,
    onNeedModelDownload: (conversationId: String, engineMode: EngineMode) -> Unit,
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val engineMode by viewModel.engineMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "AceChat")
                        EngineBadge(engineMode = engineMode)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.semantics {
                            contentDescription = "Open settings"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val id = viewModel.createNewConversation()
                        val mode = viewModel.engineMode.value
                        if (mode == EngineMode.ON_DEVICE && !viewModel.isModelReady()) {
                            onNeedModelDownload(id, mode)
                        } else {
                            onOpenChat(id, mode)
                        }
                    }
                },
                modifier = Modifier.semantics {
                    contentDescription = "Start new conversation"
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (conversations.isEmpty()) {
                EmptyConversationState(
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                ConversationList(
                    conversations = conversations,
                    onOpenChat = onOpenChat,
                    onDeleteConversation = { id ->
                        scope.launch { viewModel.deleteConversation(id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun EngineBadge(
    engineMode: EngineMode,
    modifier: Modifier = Modifier,
) {
    val label = when (engineMode) {
        EngineMode.ON_DEVICE -> "ON-DEVICE"
        EngineMode.ONLINE -> "ONLINE"
    }
    Badge(
        modifier = modifier,
        containerColor = when (engineMode) {
            EngineMode.ON_DEVICE -> MaterialTheme.colorScheme.secondaryContainer
            EngineMode.ONLINE -> MaterialTheme.colorScheme.tertiaryContainer
        },
        contentColor = when (engineMode) {
            EngineMode.ON_DEVICE -> MaterialTheme.colorScheme.onSecondaryContainer
            EngineMode.ONLINE -> MaterialTheme.colorScheme.onTertiaryContainer
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    onOpenChat: (conversationId: String, engineMode: EngineMode) -> Unit,
    onDeleteConversation: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(
            items = conversations,
            key = { it.id },
        ) { conversation ->
            ConversationItem(
                conversation = conversation,
                onClick = { onOpenChat(conversation.id, conversation.engineMode) },
                onDelete = { onDeleteConversation(conversation.id) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Open conversation ${conversation.title}",
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dateFormatter.format(Date(conversation.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics {
                contentDescription = "Delete conversation ${conversation.title}"
            },
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyConversationState(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "No conversations yet.\nTap + to start.",
        modifier = modifier.padding(horizontal = 32.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
