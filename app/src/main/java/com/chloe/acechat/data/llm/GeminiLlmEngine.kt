package com.chloe.acechat.data.llm

import android.util.Log
import com.chloe.acechat.BuildConfig
import com.chloe.acechat.domain.llm.LlmEngineInterface
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "GeminiLlmEngine"
private const val MODEL_NAME = "gemini-2.0-flash"

class GeminiLlmEngine : LlmEngineInterface {

    private val model: GenerativeModel = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 1.0f
            topK = 64
            topP = 0.95f
        },
        systemInstruction = content { text(SYSTEM_PROMPT) },
    )

    // Mutable history of the current conversation session.
    // Each element is a Content object with role="user" or role="model".
    // historyLock guards all read/write access to history, which can be accessed
    // from different coroutine dispatchers (sendMessage on Default, resetConversation/close on Main).
    private val history = mutableListOf<Content>()
    private val historyLock = Any()

    /**
     * No-op: Gemini API requires no local initialization.
     */
    override suspend fun initialize() {
        Log.d(TAG, "GeminiLlmEngine ready (no local initialization needed)")
    }

    /**
     * Sends a user message and returns a Flow of token strings using Gemini streaming.
     * The conversation history is maintained across calls within the same session.
     */
    override fun sendMessage(userInput: String): Flow<String> = flow {
        Log.d(TAG, "Sending message to Gemini: ${userInput.take(80)}")

        // Snapshot history before starting the chat to avoid holding the lock during streaming.
        val historySnapshot = synchronized(historyLock) { history.toList() }
        val chat = model.startChat(history = historySnapshot)
        val accumulated = StringBuilder()

        try {
            chat.sendMessageStream(userInput).collect { chunk ->
                val text = chunk.text ?: return@collect
                accumulated.append(text)
                emit(text)
            }
        } catch (e: CancellationException) {
            // Coroutine cancellation must always be re-thrown so the cancellation signal propagates.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini streaming error", e)
            throw e
        }

        // Update history only after a successful response so that a failed
        // turn does not corrupt the conversation context.
        synchronized(historyLock) {
            history.add(content(role = "user") { text(userInput) })
            history.add(content(role = "model") { text(accumulated.toString()) })
        }

        Log.d(TAG, "Gemini response complete (${accumulated.length} chars)")
    }

    /**
     * Clears the in-memory conversation history, starting a fresh session.
     */
    override fun resetConversation() {
        synchronized(historyLock) { history.clear() }
        Log.d(TAG, "Conversation history cleared")
    }

    /**
     * No-op: Gemini API holds no local resources to release.
     */
    override fun close() {
        synchronized(historyLock) { history.clear() }
        Log.d(TAG, "GeminiLlmEngine closed")
    }
}
