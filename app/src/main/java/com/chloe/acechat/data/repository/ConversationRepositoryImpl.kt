package com.chloe.acechat.data.repository

import com.chloe.acechat.data.db.AceChatDatabase
import com.chloe.acechat.data.db.mapper.toDomain
import com.chloe.acechat.data.db.mapper.toEntity
import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.repository.ConversationRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ConversationRepositoryImpl(
    private val db: AceChatDatabase,
) : ConversationRepository {

    override fun getAllConversations(): Flow<List<Conversation>> =
        db.conversationDao().getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createConversation(engineMode: EngineMode): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            engineMode = engineMode,
            createdAt = now,
            updatedAt = now,
        )
        db.conversationDao().insertConversation(conversation.toEntity())
        return conversation
    }

    override suspend fun updateConversationTitle(id: String, title: String) {
        db.conversationDao().updateConversation(
            id = id,
            title = title,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun deleteConversation(id: String) {
        db.withTransaction {
            db.messageDao().deleteMessagesForConversation(id)
            db.conversationDao().deleteConversation(id)
        }
    }

    override fun getMessages(conversationId: String): Flow<List<ChatMessage>> =
        db.messageDao().getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        db.messageDao().insertMessage(message.toEntity(conversationId))
    }
}
