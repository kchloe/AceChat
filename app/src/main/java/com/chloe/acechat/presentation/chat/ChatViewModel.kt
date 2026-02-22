package com.chloe.acechat.presentation.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloe.acechat.data.llm.LlmEngine
import com.chloe.acechat.data.llm.MODEL_FILE_NAME
import com.chloe.acechat.data.stt.SpeechRecognizerManager
import com.chloe.acechat.data.stt.SttState
import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.ConversationState
import com.chloe.acechat.domain.model.MessageRole
import com.chloe.acechat.domain.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val TAG = "ChatViewModel"

// Marker that separates the conversational reply from the grammar correction.
private const val CORRECTION_MARKER = "✏️ Correction:"

class ChatViewModel(
    application: Application,
    modelPathOverride: String? = null,
) : AndroidViewModel(application) {

    private val modelPath: String = modelPathOverride ?: File(
        application.getExternalFilesDir(null), "models/$MODEL_FILE_NAME"
    ).absolutePath

    // LiteRT-LM needs a writable cache directory for compiled model artifacts (XNNPack, etc).
    // We use the app's external files directory which is guaranteed to be writable.
    private val cacheDir: String? = application.getExternalFilesDir(null)?.absolutePath

    private val llmEngine = LlmEngine(modelPath = modelPath, cacheDir = cacheDir)
    private val speechRecognizerManager = SpeechRecognizerManager(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val uiState: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Loading)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    /** SpeechRecognizerManager의 상태를 그대로 노출 */
    val sttState: StateFlow<SttState> = speechRecognizerManager.sttState

    init {
        initializeEngine()
        observeSttResults()
    }

    // -----------------------------------------------------------------------------------------
    // Engine lifecycle
    // -----------------------------------------------------------------------------------------

    private fun initializeEngine() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                llmEngine.initialize()
                _conversationState.update { ConversationState.Idle }
                Log.d(TAG, "Engine ready")
            } catch (e: Exception) {
                Log.e(TAG, "Engine initialization failed", e)
                _conversationState.update {
                    ConversationState.Error(e.message ?: "Failed to initialize model")
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // STT
    // -----------------------------------------------------------------------------------------

    /**
     * SttState를 구독하여:
     * - [SttState.Result]: 텍스트가 있으면 sendMessage() 호출 후 Idle 복귀
     * - [SttState.Error]: 1.5초 후 Idle 복귀
     */
    private fun observeSttResults() {
        viewModelScope.launch {
            speechRecognizerManager.sttState.collect { state ->
                when (state) {
                    is SttState.Result -> {
                        if (state.text.isNotEmpty()) {
                            sendMessage(state.text)
                        }
                        speechRecognizerManager.resetToIdle()
                    }
                    is SttState.Error -> {
                        delay(1500)
                        speechRecognizerManager.resetToIdle()
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * 마이크 버튼 탭 핸들러.
     * - [ConversationState.Idle]일 때만 동작
     * - 이미 [SttState.Listening] 상태면 무시
     */
    fun onMicTapped() {
        if (_conversationState.value !is ConversationState.Idle) return
        if (speechRecognizerManager.sttState.value is SttState.Listening) return
        viewModelScope.launch(Dispatchers.Main) {
            speechRecognizerManager.startListening()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------------------------

    /**
     * Adds the user message to the list, sends it to the LLM, and streams the response
     * back token by token into a BOT message.
     *
     * After streaming completes the message type is set to:
     * - [MessageType.CORRECTION] when the response contains "✏️ Correction:"
     * - [MessageType.NORMAL] otherwise
     */
    fun sendMessage(userInput: String) {
        if (_conversationState.value !is ConversationState.Idle) return

        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) return

        // Add USER message immediately.
        _messages.update { it + ChatMessage(role = MessageRole.USER, content = trimmed) }
        _conversationState.update { ConversationState.Loading }

        viewModelScope.launch(Dispatchers.Default) {
            val botId = UUID.randomUUID().toString()
            var accumulated = ""
            var streamingStarted = false

            try {
                llmEngine.sendMessage(trimmed).collect { token ->
                    accumulated += token

                    if (!streamingStarted) {
                        // First token: add the BOT message and switch to STREAMING state.
                        streamingStarted = true
                        _messages.update {
                            it + ChatMessage(
                                id = botId,
                                role = MessageRole.BOT,
                                content = processResponse(accumulated),
                                isStreaming = true,
                            )
                        }
                        _conversationState.update { ConversationState.Streaming }
                    } else {
                        // Subsequent tokens: update content in place.
                        _messages.update { messages ->
                            messages.map { msg ->
                                if (msg.id == botId)
                                    msg.copy(content = processResponse(accumulated))
                                else
                                    msg
                            }
                        }
                    }
                }

                // Flow completed normally — finalize the BOT message.
                val finalContent = processResponse(accumulated)
                val messageType =
                    if (finalContent.contains(CORRECTION_MARKER)) MessageType.CORRECTION
                    else MessageType.NORMAL

                if (streamingStarted) {
                    _messages.update { messages ->
                        messages.map { msg ->
                            if (msg.id == botId)
                                msg.copy(content = finalContent, type = messageType, isStreaming = false)
                            else
                                msg
                        }
                    }
                }
                _conversationState.update { ConversationState.Idle }

            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                if (streamingStarted) {
                    _messages.update { messages ->
                        messages.map { msg ->
                            if (msg.id == botId) msg.copy(isStreaming = false) else msg
                        }
                    }
                }
                _conversationState.update {
                    ConversationState.Error(e.message ?: "Inference failed")
                }
            }
        }
    }

    /**
     * Clears all messages and resets the LLM conversation context.
     * No-op while LOADING or STREAMING.
     */
    fun clearConversation() {
        val state = _conversationState.value
        if (state is ConversationState.Loading || state is ConversationState.Streaming) return

        viewModelScope.launch(Dispatchers.Default) {
            _messages.update { emptyList() }
            llmEngine.resetConversation()
            _conversationState.update { ConversationState.Idle }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmEngine.close()
        speechRecognizerManager.destroy()
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /** Converts literal \n sequences that the LLM may output into real newlines. */
    private fun processResponse(text: String): String = text.replace("\\n", "\n")

    // -----------------------------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------------------------

    class Factory(
        private val application: Application,
        private val modelPathOverride: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(application, modelPathOverride) as T
    }
}
