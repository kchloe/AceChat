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
    /** false인 동안 UI에 렌더링되지 않는다. BOT 메시지는 TTS 시작 직전까지 false로 유지된다. */
    val isVisible: Boolean = false,
)
