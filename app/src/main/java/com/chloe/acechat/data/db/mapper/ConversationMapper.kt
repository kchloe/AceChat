package com.chloe.acechat.data.db.mapper

import com.chloe.acechat.data.db.entity.ConversationEntity
import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    engineMode = runCatching { EngineMode.valueOf(engineMode) }.getOrDefault(EngineMode.ON_DEVICE),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    engineMode = engineMode.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
