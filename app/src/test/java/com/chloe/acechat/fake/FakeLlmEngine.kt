package com.chloe.acechat.fake

import com.chloe.acechat.domain.llm.LlmEngineInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * LlmEngineInterface의 Fake 구현체.
 *
 * @param responses sendMessage 호출 순서에 따라 반환할 토큰 목록 시퀀스.
 *                  비어 있으면 빈 Flow를 반환한다.
 * @param shouldThrowOnInitialize true이면 initialize() 호출 시 예외를 던진다.
 * @param shouldThrowOnSend true이면 sendMessage() 호출 시 예외를 던진다.
 */
class FakeLlmEngine(
    private val responses: List<List<String>> = listOf(listOf("Hello", " World")),
    private val shouldThrowOnInitialize: Boolean = false,
    private val shouldThrowOnSend: Boolean = false,
) : LlmEngineInterface {

    var initializeCallCount = 0
    var sendMessageCallCount = 0
    var resetCallCount = 0
    var closeCallCount = 0
    var lastInput: String? = null

    override suspend fun initialize() {
        initializeCallCount++
        if (shouldThrowOnInitialize) {
            throw RuntimeException("FakeLlmEngine: initialize failed")
        }
    }

    override fun sendMessage(userInput: String): Flow<String> {
        sendMessageCallCount++
        lastInput = userInput
        if (shouldThrowOnSend) {
            return flow { throw RuntimeException("FakeLlmEngine: sendMessage failed") }
        }
        val tokens = responses.getOrElse(sendMessageCallCount - 1) { emptyList() }
        return flow {
            tokens.forEach { token -> emit(token) }
        }
    }

    override fun resetConversation() {
        resetCallCount++
    }

    override fun close() {
        closeCallCount++
    }
}
