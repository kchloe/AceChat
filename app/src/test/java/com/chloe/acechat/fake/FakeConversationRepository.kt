package com.chloe.acechat.fake

import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * ConversationRepository의 Fake 구현체.
 * 인메모리 MutableStateFlow로 상태를 관리한다.
 *
 * @param initialConversations 초기 대화 목록
 * @param shouldThrow true이면 모든 suspend 함수 호출 시 예외를 던진다.
 */
class FakeConversationRepository(
    initialConversations: List<Conversation> = emptyList(),
    private val shouldThrow: Boolean = false,
) : ConversationRepository {

    private val conversationsFlow = MutableStateFlow(initialConversations)
    private val messagesFlow = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

    // 호출 검증용
    var createConversationCallCount = 0
    var deleteConversationCallCount = 0
    var updateTitleCallCount = 0
    var saveMessageCallCount = 0
    var lastSavedMessage: ChatMessage? = null
    var lastUpdatedTitle: String? = null

    override fun getAllConversations(): Flow<List<Conversation>> = conversationsFlow

    override suspend fun createConversation(engineMode: EngineMode): Conversation {
        if (shouldThrow) throw RuntimeException("FakeConversationRepository: createConversation failed")
        createConversationCallCount++
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            engineMode = engineMode,
            createdAt = now,
            updatedAt = now,
        )
        conversationsFlow.update { it + conversation }
        return conversation
    }

    override suspend fun updateConversationTitle(id: String, title: String) {
        if (shouldThrow) throw RuntimeException("FakeConversationRepository: updateConversationTitle failed")
        updateTitleCallCount++
        lastUpdatedTitle = title
        conversationsFlow.update { conversations ->
            conversations.map { conv ->
                if (conv.id == id) conv.copy(title = title) else conv
            }
        }
    }

    override suspend fun deleteConversation(id: String) {
        if (shouldThrow) throw RuntimeException("FakeConversationRepository: deleteConversation failed")
        deleteConversationCallCount++
        conversationsFlow.update { it.filter { conv -> conv.id != id } }
        messagesFlow.update { it - id }
    }

    override fun getMessages(conversationId: String): Flow<List<ChatMessage>> =
        messagesFlow.map { it[conversationId] ?: emptyList() }

    override suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        if (shouldThrow) throw RuntimeException("FakeConversationRepository: saveMessage failed")
        saveMessageCallCount++
        lastSavedMessage = message
        messagesFlow.update { map ->
            val updated = (map[conversationId] ?: emptyList()) + message
            map + (conversationId to updated)
        }
    }

    // 테스트 헬퍼: 특정 대화의 메시지를 직접 주입한다.
    fun setMessages(conversationId: String, messages: List<ChatMessage>) {
        messagesFlow.update { it + (conversationId to messages) }
    }

    // 테스트 헬퍼: 대화 목록을 직접 교체한다.
    fun setConversations(conversations: List<Conversation>) {
        conversationsFlow.value = conversations
    }
}
