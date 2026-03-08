package com.chloe.acechat.domain.repository

import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.LanguageMode
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getAllConversations(): Flow<List<Conversation>>
    fun getConversationsByLanguage(languageMode: LanguageMode): Flow<List<Conversation>>
    suspend fun createConversation(engineMode: EngineMode, languageMode: LanguageMode = LanguageMode.ENGLISH): Conversation
    suspend fun updateConversationTitle(id: String, title: String)
    suspend fun deleteConversation(id: String)
    fun getMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun saveMessage(conversationId: String, message: ChatMessage)
}
