package com.chloe.acechat.data.llm

import android.util.Log
import com.chloe.acechat.domain.llm.LlmEngineInterface
import com.chloe.acechat.domain.model.LanguageMode
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

private const val TAG = "OnDeviceLlmEngine"

@OptIn(ExperimentalApi::class)
class OnDeviceLlmEngine(
    private val modelPath: String,
    // Must be a writable directory for engine cache files (XNNPack, quantization cache, etc.).
    // When modelPath starts with /data/local/tmp, the app has no write access to that directory,
    // so the caller must supply a writable path (e.g. context.getExternalFilesDir(null)).
    private val cacheDir: String? = null,
    private val systemPrompt: String = buildSystemPrompt(LanguageMode.ENGLISH),
) : LlmEngineInterface {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    /**
     * Initializes the LiteRT-LM engine and creates the conversation with the system prompt.
     * Runs on Dispatchers.IO to avoid blocking the calling coroutine's thread.
     * Throws on failure.
     */
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Initializing engine: $modelPath  cacheDir: $cacheDir")

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU, // GPU 시도 후 실패하면 CPU로 폴백하도록 수정
                visionBackend = null,
                audioBackend = null,
                maxNumTokens = 1024,
                cacheDir = cacheDir,
            )

            val newEngine = Engine(engineConfig)
            newEngine.initialize()

            val systemInstruction = Contents.of(listOf(Content.Text(systemPrompt)))
            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 64,
                        topP = 0.95,
                        temperature = 1.0,
                    ),
                    systemInstruction = systemInstruction,
                    tools = emptyList(),
                )
            )

            engine = newEngine
            conversation = newConversation
            Log.d(TAG, "Engine initialized successfully")
        }
    }

    /**
     * Sends a user message and returns a Flow of token strings.
     * The flow completes when the model finishes generating.
     * Cancelling the collector triggers cancelProcess() via awaitClose.
     */
    override fun sendMessage(userInput: String): Flow<String> = callbackFlow {
        val conv = conversation ?: run {
            close(IllegalStateException("Engine not initialized"))
            return@callbackFlow
        }

        var completed = false
        val contents = Contents.of(listOf(Content.Text(userInput)))

        conv.sendMessageAsync(
            contents,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    trySend(message.toString())
                }

                override fun onDone() {
                    completed = true
                    close()
                }

                override fun onError(throwable: Throwable) {
                    completed = true
                    // CancellationException means cancelProcess() was called (user stopped
                    // generation or coroutine was cancelled). Treat as normal completion,
                    // consistent with Gallery's LlmChatModelHelper behaviour.
                    if (throwable is CancellationException) {
                        Log.i(TAG, "Inference cancelled (normal)")
                        close()
                    } else {
                        Log.e(TAG, "Inference error", throwable)
                        close(throwable)
                    }
                }
            }
        )

        // If the collector is cancelled before onDone, stop the generation.
        awaitClose {
            if (!completed) {
                try {
                    conv.cancelProcess()
                } catch (e: Exception) {
                    Log.w(TAG, "cancelProcess failed", e)
                }
            }
        }
    }

    /**
     * Resets the conversation history while keeping the engine loaded.
     * Use this to start a new conversation session.
     */
    override fun resetConversation() {
        val eng = engine ?: return
        try {
            conversation?.close()
            val systemInstruction = Contents.of(listOf(Content.Text(systemPrompt)))
            conversation = eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
                    systemInstruction = systemInstruction,
                    tools = emptyList(),
                )
            )
            Log.d(TAG, "Conversation reset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset conversation", e)
        }
    }

    /**
     * Releases all resources. Call this when the engine is no longer needed.
     */
    override fun close() {
        try {
            conversation?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close conversation", e)
        }
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close engine", e)
        }
        conversation = null
        engine = null
        Log.d(TAG, "Engine closed")
    }
}
