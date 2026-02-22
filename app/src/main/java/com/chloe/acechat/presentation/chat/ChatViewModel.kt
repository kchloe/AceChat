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
import com.chloe.acechat.data.tts.TtsManager
import com.chloe.acechat.data.tts.TtsState
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
import kotlinx.coroutines.withContext
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
    // ViewModel은 메인 스레드에서 생성되므로 TtsManager 생성도 메인 스레드에서 실행된다.
    private val ttsManager = TtsManager(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val uiState: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Loading)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    /** SpeechRecognizerManager의 상태를 그대로 노출 */
    val sttState: StateFlow<SttState> = speechRecognizerManager.sttState

    /** TtsManager의 상태를 그대로 노출 */
    val ttsState: StateFlow<TtsState> = ttsManager.ttsState

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

        // USER 메시지는 즉시 노출.
        _messages.update { it + ChatMessage(role = MessageRole.USER, content = trimmed, isVisible = true) }
        _conversationState.update { ConversationState.Loading }

        viewModelScope.launch(Dispatchers.Default) {
            val botId = UUID.randomUUID().toString()
            var accumulated = ""
            var streamingStarted = false

            try {
                // 스트리밍 토큰 수신 — UI에 반영하지 않고 누적만 함.
                llmEngine.sendMessage(trimmed).collect { token ->
                    accumulated += token
                    if (!streamingStarted) {
                        streamingStarted = true
                        _conversationState.update { ConversationState.Streaming }
                    }
                }

                // 스트리밍 완료 — 최종 메시지 확정.
                val finalContent = processResponse(accumulated)
                val messageType =
                    if (finalContent.contains(CORRECTION_MARKER)) MessageType.CORRECTION
                    else MessageType.NORMAL

                // BOT 메시지를 invisible 상태로 추가.
                _messages.update {
                    it + ChatMessage(
                        id = botId,
                        role = MessageRole.BOT,
                        content = finalContent,
                        type = messageType,
                        isVisible = false,
                    )
                }

                // 메인 스레드에서 TTS 시작 → 말풍선 노출 → Idle 전환을 한 번에 처리.
                val ttsText = extractTtsText(finalContent, messageType)
                withContext(Dispatchers.Main) {
                    if (ttsText.isNotEmpty()) {
                        ttsManager.speak(ttsText)
                    }
                    // TTS 큐에 올라간 직후 말풍선을 노출하고 상태를 Idle로 전환.
                    _messages.update { messages ->
                        messages.map { if (it.id == botId) it.copy(isVisible = true) else it }
                    }
                    _conversationState.update { ConversationState.Idle }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
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

        ttsManager.stop()
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
        ttsManager.destroy()
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /** Converts literal \n sequences that the LLM may output into real newlines. */
    private fun processResponse(text: String): String = text.replace("\\n", "\n")

    /**
     * TTS로 읽을 텍스트를 반환한다.
     * - [MessageType.CORRECTION]: [CORRECTION_MARKER] 앞의 conversational reply 부분만 반환
     * - [MessageType.NORMAL]: 전체 텍스트 반환
     */
    private fun extractTtsText(content: String, type: MessageType): String =
        if (type == MessageType.CORRECTION) content.substringBefore(CORRECTION_MARKER).trim()
        else content

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
