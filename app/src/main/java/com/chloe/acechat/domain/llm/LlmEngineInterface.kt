package com.chloe.acechat.domain.llm

import kotlinx.coroutines.flow.Flow

interface LlmEngineInterface {
    suspend fun initialize()
    fun sendMessage(userInput: String): Flow<String>
    fun resetConversation()
    fun close()
}
