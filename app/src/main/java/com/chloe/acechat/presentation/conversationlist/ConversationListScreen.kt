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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.chloe.acechat.domain.model.LanguageMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateTimeFormatter = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onOpenChat: (conversationId: String, engineMode: EngineMode, languageMode: LanguageMode) -> Unit,
    onOpenSettings: () -> Unit,
    onNeedModelDownload: (conversationId: String, engineMode: EngineMode, languageMode: LanguageMode) -> Unit,
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val engineMode by viewModel.engineMode.collectAsStateWithLifecycle()
    val languageMode by viewModel.languageMode.collectAsStateWithLifecycle()
    val hasOtherLanguageConversations by viewModel.hasOtherLanguageConversations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "AceChat")
                        Text(
                            text = when (languageMode) {
                                LanguageMode.ENGLISH -> "Practicing English \uD83C\uDDFA\uD83C\uDDF8"
                                LanguageMode.KOREAN -> "한국어 연습 중 \uD83C\uDDF0\uD83C\uDDF7"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    EngineBadge(
                        engineMode = engineMode,
                        modifier = Modifier.padding(end = 4.dp),
                    )
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
                        // createNewConversation()이 반환하는 Conversation에서 engineMode/languageMode를
                        // 읽어 사용한다. StateFlow 스냅샷(.value)은 DataStore에서 실제로 읽은 값과
                        // 다를 수 있으므로 (초기값 vs 저장값), 생성 시 사용된 값을 그대로 전달해야
                        // DB에 저장된 engineMode/languageMode와 네비게이션 파라미터가 일치한다.
                        val conversation = viewModel.createNewConversation()
                        val mode = conversation.engineMode
                        val language = conversation.languageMode
                        if (mode == EngineMode.ON_DEVICE && !viewModel.isModelReady()) {
                            onNeedModelDownload(conversation.id, mode, language)
                        } else {
                            onOpenChat(conversation.id, mode, language)
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
                    languageMode = languageMode,
                    hasOtherLanguageConversations = hasOtherLanguageConversations,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                ConversationList(
                    conversations = conversations,
                    onOpenChat = onOpenChat,
                    onDeleteConversation = { id ->
                        scope.launch { viewModel.deleteConversation(id) }
                    },
                    onRenameConversation = { id, newTitle ->
                        scope.launch { viewModel.renameConversation(id, newTitle) }
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
    onOpenChat: (conversationId: String, engineMode: EngineMode, languageMode: LanguageMode) -> Unit,
    onDeleteConversation: (id: String) -> Unit,
    onRenameConversation: (id: String, newTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(
            items = conversations,
            key = { it.id },
        ) { conversation ->
            ConversationItem(
                conversation = conversation,
                onClick = { onOpenChat(conversation.id, conversation.engineMode, conversation.languageMode) },
                onDelete = { onDeleteConversation(conversation.id) },
                onRename = { newTitle -> onRenameConversation(conversation.id, newTitle) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (newTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 시트 닫힘 애니메이션 완료 후 후속 액션 실행
    val hideAndDo: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                showBottomSheet = false
                action()
            }
        }
    }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = dateTimeFormatter.format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = { showBottomSheet = true },
            modifier = Modifier.semantics {
                contentDescription = "More options for ${conversation.title}"
            },
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ── ModalBottomSheet ──────────────────────────────────────────────────────
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
        ) {
            // 헤더: 대화 제목 + 엔진 뱃지
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                EngineBadge(engineMode = conversation.engineMode)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Rename
            BottomSheetMenuItem(
                icon = Icons.Default.Edit,
                label = "Rename",
                onClick = { hideAndDo { showRenameDialog = true } },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Delete (파괴적 액션 — error 색상 강조)
            BottomSheetMenuItem(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = { hideAndDo { showDeleteDialog = true } },
                iconTint = MaterialTheme.colorScheme.error,
                labelColor = MaterialTheme.colorScheme.error,
            )

            // 내비게이션 바 여백
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    // Rename 다이얼로그
    if (showRenameDialog) {
        RenameDialog(
            currentTitle = conversation.title,
            onConfirm = { newTitle ->
                showRenameDialog = false
                onRename(newTitle)
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    // Delete 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete conversation") },
            text = { Text("Are you sure you want to delete \"${conversation.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BottomSheetMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = labelColor)
    }
}

@Composable
private fun RenameDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename conversation") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EmptyConversationState(
    languageMode: LanguageMode,
    hasOtherLanguageConversations: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No conversations yet.\nTap + to start.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (hasOtherLanguageConversations) {
            Text(
                text = when (languageMode) {
                    LanguageMode.ENGLISH -> "Your Korean conversations are saved.\nSwitch language in Settings to access them."
                    LanguageMode.KOREAN -> "영어 대화 내역이 저장되어 있어요.\n설정에서 언어를 변경하면 다시 볼 수 있어요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
