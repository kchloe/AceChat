package com.chloe.acechat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.MessageRole
import com.chloe.acechat.domain.model.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

private const val CORRECTION_MARKER = "✏️ Correction:"

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onPlayTapped: (() -> Unit)? = null,
) {
    val isUser = message.role == MessageRole.USER

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            if (isUser) {
                UserBubble(message)
            } else {
                BotBubble(message = message, isPlaying = isPlaying, onPlayTapped = onPlayTapped)
            }
            Text(
                text = timeFormatter.format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun UserBubble(message: ChatMessage) {
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 4.dp,
    )
    Box(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = message.content,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, onPlayTapped: () -> Unit) {
    val description = if (isPlaying) "Stop playback" else "Play message"
    val tint = if (isPlaying) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(
        onClick = onPlayTapped,
        modifier = Modifier
            .size(32.dp)
            .semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun BotBubble(
    message: ChatMessage,
    isPlaying: Boolean = false,
    onPlayTapped: (() -> Unit)? = null,
) {
    val mainShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 4.dp,
        bottomEnd = 16.dp,
    )
    val correctionShape = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 16.dp,
        bottomStart = 4.dp,
        bottomEnd = 16.dp,
    )

    val showCorrection = message.type == MessageType.CORRECTION
            && message.content.contains(CORRECTION_MARKER)

    if (showCorrection) {
        val parts = message.content.split(CORRECTION_MARKER, limit = 2)
        val replyText = parts[0].trim()
        val correctionText = parts.getOrElse(1) { "" }.trim()

        Column {
            // Main conversational reply
            Box(
                modifier = Modifier
                    .clip(mainShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = replyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Grammar correction section
            Box(
                modifier = Modifier
                    .clip(correctionShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(
                        text = "✏️ Correction:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = correctionText,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (onPlayTapped != null) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                ) {
                    PlayButton(isPlaying = isPlaying, onPlayTapped = onPlayTapped)
                }
            }
        }
    } else {
        Column {
            Box(
                modifier = Modifier
                    .clip(mainShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (onPlayTapped != null) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                ) {
                    PlayButton(isPlaying = isPlaying, onPlayTapped = onPlayTapped)
                }
            }
        }
    }
}
