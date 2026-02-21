package com.chloe.acechat.domain.model

import java.util.UUID

enum class MessageRole {
    USER,
    BOT,
}

enum class MessageType {
    NORMAL,
    CORRECTION,
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val type: MessageType = MessageType.NORMAL,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
)
