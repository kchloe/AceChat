package com.chloe.acechat.presentation.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloe.acechat.data.stt.SpeechRecognizerManager
import com.chloe.acechat.data.stt.SttState
import com.chloe.acechat.data.tts.TtsManager
import com.chloe.acechat.data.tts.TtsState
import com.chloe.acechat.domain.llm.LlmEngineInterface
import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.ConversationState
import com.chloe.acechat.domain.model.MessageRole
import com.chloe.acechat.domain.model.MessageType
import com.chloe.acechat.domain.repository.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "ChatViewModel"

// Marker that separates the conversational reply from the grammar correction.
private const val CORRECTION_MARKER = "✏️ Correction:"

// 첫 USER 메시지로 대화 제목을 자동 생성할 때 사용하는 최대 글자 수.
private const val TITLE_MAX_LENGTH = 30

// 대화 생성 직후 기본 제목. 이 값이 유지되는 동안 첫 메시지로 제목을 업데이트한다.
private const val DEFAULT_CONVERSATION_TITLE = "New Chat"

class ChatViewModel(
    application: Application,
    internal var llmEngine: LlmEngineInterface,
    private val conversationId: String,
    private val conversationRepository: ConversationRepository,
) : AndroidViewModel(application) {

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

    // 현재 대화의 제목. 첫 메시지 전송 시 업데이트 여부 판단에 사용.
    private var conversationTitle: String = DEFAULT_CONVERSATION_TITLE

    init {
        loadExistingMessages()
        initializeEngine()
        observeSttResults()
    }

    // -----------------------------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------------------------

    /**
     * DB에서 기존 메시지를 1회 로드한다.
     * Flow를 지속 구독하지 않고 first()로 스냅샷만 읽어 스트리밍 중 충돌을 방지한다.
     */
    private fun loadExistingMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conversation = conversationRepository.getAllConversations()
                    .first()
                    .find { it.id == conversationId }
                if (conversation != null) {
                    conversationTitle = conversation.title
                }

                val existing = conversationRepository.getMessages(conversationId).first()
                if (existing.isNotEmpty()) {
                    // 기존 메시지는 모두 isVisible=true로 표시 (DB의 toDomain()에서 고정)
                    _messages.update { existing }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load existing messages", e)
            }
        }
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
     *
     * 완료 후 USER 메시지 + BOT 메시지를 DB에 저장한다.
     * 첫 USER 메시지이고 현재 제목이 기본값이면 제목을 자동 업데이트한다.
     */
    fun sendMessage(userInput: String) {
        if (_conversationState.value !is ConversationState.Idle) return

        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) return

        val userMessage = ChatMessage(role = MessageRole.USER, content = trimmed, isVisible = true)

        // USER 메시지는 즉시 노출.
        _messages.update { it + userMessage }
        _conversationState.update { ConversationState.Loading }

        viewModelScope.launch(Dispatchers.Default) {
            // USER 메시지 DB 저장
            try {
                conversationRepository.saveMessage(conversationId, userMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save user message", e)
            }

            // 첫 USER 메시지로 대화 제목 자동 업데이트
            if (conversationTitle == DEFAULT_CONVERSATION_TITLE) {
                val newTitle = trimmed.take(TITLE_MAX_LENGTH)
                try {
                    conversationRepository.updateConversationTitle(conversationId, newTitle)
                    conversationTitle = newTitle
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update conversation title", e)
                }
            }

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

                val botMessage = ChatMessage(
                    id = botId,
                    role = MessageRole.BOT,
                    content = finalContent,
                    type = messageType,
                    isVisible = false,
                )

                // BOT 메시지를 invisible 상태로 추가.
                _messages.update { it + botMessage }

                // BOT 메시지 DB 저장 (isVisible 필드는 DB에 저장하지 않으므로 값 무관)
                try {
                    conversationRepository.saveMessage(conversationId, botMessage)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save bot message", e)
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
                // 추론 에러는 엔진 자체가 살아있으므로 잠시 후 Idle로 복귀하여 재시도를 허용한다.
                // (엔진 초기화 실패는 별도 경로이며 Error 상태를 유지한다.)
                delay(3000)
                _conversationState.update { ConversationState.Idle }
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
        // llmEngine.close()는 호출하지 않음 — 엔진 수명주기는 AppContainer가 담당.
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
        private val llmEngine: LlmEngineInterface,
        private val conversationId: String,
        private val conversationRepository: ConversationRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(application, llmEngine, conversationId, conversationRepository) as T
    }
}
