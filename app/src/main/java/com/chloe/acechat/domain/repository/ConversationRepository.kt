package com.chloe.acechat.domain.repository

import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getAllConversations(): Flow<List<Conversation>>
    suspend fun createConversation(engineMode: EngineMode): Conversation
    suspend fun updateConversationTitle(id: String, title: String)
    suspend fun deleteConversation(id: String)
    fun getMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun saveMessage(conversationId: String, message: ChatMessage)
}
