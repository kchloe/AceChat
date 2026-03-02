package com.chloe.acechat.data.db.mapper

import com.chloe.acechat.data.db.entity.MessageEntity
import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.MessageRole
import com.chloe.acechat.domain.model.MessageType

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
    type = runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.NORMAL),
    content = content,
    timestamp = timestamp,
    isVisible = true,
)

fun ChatMessage.toEntity(conversationId: String): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    type = type.name,
    content = content,
    timestamp = timestamp,
)
