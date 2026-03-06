package com.chloe.acechat.presentation.chat

import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.MessageRole
import com.chloe.acechat.domain.model.MessageType
import com.chloe.acechat.fake.FakeConversationRepository
import com.chloe.acechat.fake.FakeLlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ChatViewModel 비즈니스 로직 단위 테스트.
 *
 * [테스트 불가 이유]
 * ChatViewModel은 AndroidViewModel을 상속하며,
 * SpeechRecognizerManager와 TtsManager를 생성자가 아닌 내부 프로퍼티로 직접 생성한다:
 *   private val speechRecognizerManager = SpeechRecognizerManager(application)
 *   private val ttsManager = TtsManager(application)
 *
 * 이 두 클래스는 Android SpeechRecognizer / TextToSpeech를 직접 사용하므로
 * JVM 테스트 환경에서 인스턴스화가 불가능하다. 결과적으로 ChatViewModel을
 * JVM 단위 테스트에서 직접 생성할 수 없다.
 *
 * [테스트 전략]
 * ChatViewModel의 핵심 비즈니스 로직(sendMessage 파이프라인, 상태 전이, 제목 자동 생성)은
 * ViewModel 외부에서 동일한 의존성(FakeLlmEngine, FakeConversationRepository)을 이용해
 * 직접 검증한다. 이를 위해 ChatViewModel의 순수 로직을 반영하는 TestableChatLogic을
 * 로컬에 정의한다.
 *
 * [설계 개선 제안]
 * SpeechRecognizerManager와 TtsManager를 인터페이스로 추출하고 생성자로 주입하면
 * ChatViewModel 자체를 단위 테스트할 수 있게 된다.
 * 이 변경은 android-architect에 위임을 권장한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeLlmEngine: FakeLlmEngine
    private lateinit var fakeRepository: FakeConversationRepository

    private val conversationId = "test-conv-id"
    private val defaultTitle = "New Chat"

    // ChatViewModel의 핵심 sendMessage 파이프라인을 Android 의존성 없이 재현한 테스트 전용 로직.
    private inner class TestableChatLogic(
        private val llmEngine: FakeLlmEngine,
        private val repository: FakeConversationRepository,
        private val convId: String,
        private var currentTitle: String = defaultTitle,
    ) {
        private val correctionMarker = "✏️ Correction:"
        private val titleMaxLength = 30

        private val _messages = MutableStateFlow<List<com.chloe.acechat.domain.model.ChatMessage>>(emptyList())
        val messages = _messages.asStateFlow()

        private val _state = MutableStateFlow<com.chloe.acechat.domain.model.ConversationState>(
            com.chloe.acechat.domain.model.ConversationState.Idle
        )
        val state = _state.asStateFlow()

        var errorOccurred = false

        suspend fun sendMessage(userInput: String) {
            val trimmed = userInput.trim()
            if (trimmed.isEmpty()) return

            val userMessage = com.chloe.acechat.domain.model.ChatMessage(
                role = MessageRole.USER,
                content = trimmed,
                isVisible = true,
            )
            _messages.update { it + userMessage }
            _state.value = com.chloe.acechat.domain.model.ConversationState.Loading

            // USER 메시지 저장
            repository.saveMessage(convId, userMessage)

            // 제목 자동 업데이트
            if (currentTitle == defaultTitle) {
                try {
                    val newTitle = trimmed.take(titleMaxLength)
                    repository.updateConversationTitle(convId, newTitle)
                    currentTitle = newTitle
                } catch (e: Exception) {
                    // 제목 업데이트 실패는 치명적이지 않으므로 무시한다
                }
            }

            // LLM 스트리밍
            var accumulated = ""
            try {
                llmEngine.sendMessage(trimmed).collect { token ->
                    accumulated += token
                    _state.value = com.chloe.acechat.domain.model.ConversationState.Streaming
                }

                val finalContent = accumulated.replace("\\n", "\n")
                val messageType =
                    if (finalContent.contains(correctionMarker)) MessageType.CORRECTION
                    else MessageType.NORMAL

                val botMessage = com.chloe.acechat.domain.model.ChatMessage(
                    role = MessageRole.BOT,
                    content = finalContent,
                    type = messageType,
                    isVisible = true,
                )

                _messages.update { it + botMessage }
                repository.saveMessage(convId, botMessage)
                _state.value = com.chloe.acechat.domain.model.ConversationState.Idle

            } catch (e: Exception) {
                errorOccurred = true
                _state.value = com.chloe.acechat.domain.model.ConversationState.Error(
                    e.message ?: "Inference failed"
                )
            }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeLlmEngine = FakeLlmEngine(
            responses = listOf(listOf("Hello", " there!"))
        )
        fakeRepository = FakeConversationRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------------------------
    // sendMessage — 정상 흐름
    // -----------------------------------------------------------------------------------------

    @Test
    fun sendMessage_addsUserMessageToList() = runTest {
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("Hello")

        val messages = logic.messages.value
        assertTrue(messages.any { it.role == MessageRole.USER && it.content == "Hello" })
    }

    @Test
    fun sendMessage_addsBotMessageToList() = runTest {
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("Hello")

        val messages = logic.messages.value
        assertTrue(messages.any { it.role == MessageRole.BOT })
    }

    @Test
    fun sendMessage_botMessageContent_isAccumulatedTokens() = runTest {
        fakeLlmEngine = FakeLlmEngine(responses = listOf(listOf("Hi", " there", "!")))
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("Hello")

        val botMessage = logic.messages.value.first { it.role == MessageRole.BOT }
        assertEquals("Hi there!", botMessage.content)
    }

    /**
     * 정상 흐름에서 sendMessage() 완료 후 최종 상태가 Idle인지 검증한다.
     *
     * 중간 상태 전이(Idle → Loading → Streaming → Idle)는 UnconfinedTestDispatcher로 인해
     * 동기적으로 완료되므로 캡처가 불가능하다. 최종 상태만 검증한다.
     */
    @Test
    fun sendMessage_finalState_isIdle() = runTest {
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("Hello")

        assertTrue(logic.state.value is com.chloe.acechat.domain.model.ConversationState.Idle)
    }

    @Test
    fun sendMessage_savesBothMessages_userAndBot() = runTest {
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("Hello")

        assertEquals(2, fakeRepository.saveMessageCallCount)
        // 마지막으로 저장된 메시지는 BOT 메시지여야 한다
        assertEquals(MessageRole.BOT, fakeRepository.lastSavedMessage?.role)
    }

    // -----------------------------------------------------------------------------------------
    // sendMessage — 제목 자동 생성
    // -----------------------------------------------------------------------------------------

    @Test
    fun sendMessage_firstMessage_updatesConversationTitle() = runTest {
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("Let's practice English")

        assertEquals("Let's practice English", fakeRepository.lastUpdatedTitle)
        assertEquals(1, fakeRepository.updateTitleCallCount)
    }

    @Test
    fun sendMessage_titleTruncated_at30Chars() = runTest {
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)
        val longInput = "A".repeat(50)

        logic.sendMessage(longInput)

        assertEquals("A".repeat(30), fakeRepository.lastUpdatedTitle)
    }

    @Test
    fun sendMessage_secondMessage_doesNotUpdateTitle() = runTest {
        val logic = TestableChatLogic(
            fakeLlmEngine,
            fakeRepository,
            conversationId,
            currentTitle = "Existing Title",
        )

        logic.sendMessage("Hello again")

        assertEquals(0, fakeRepository.updateTitleCallCount)
    }

    // -----------------------------------------------------------------------------------------
    // sendMessage — 메시지 타입 분류
    // -----------------------------------------------------------------------------------------

    @Test
    fun sendMessage_responseWithCorrectionMarker_setsMessageTypeCorrection() = runTest {
        val correctionResponse = listOf("Great try! ✏️ Correction: use 'went' instead of 'go'")
        fakeLlmEngine = FakeLlmEngine(responses = listOf(correctionResponse))
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("I go to school yesterday")

        val botMessage = logic.messages.value.first { it.role == MessageRole.BOT }
        assertEquals(MessageType.CORRECTION, botMessage.type)
    }

    @Test
    fun sendMessage_responseWithoutCorrectionMarker_setsMessageTypeNormal() = runTest {
        fakeLlmEngine = FakeLlmEngine(responses = listOf(listOf("Sounds great!")))
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("I love learning English")

        val botMessage = logic.messages.value.first { it.role == MessageRole.BOT }
        assertEquals(MessageType.NORMAL, botMessage.type)
    }

    // -----------------------------------------------------------------------------------------
    // sendMessage — 에러 케이스
    // -----------------------------------------------------------------------------------------

    @Test
    fun sendMessage_whenEngineThrows_setsErrorState() = runTest {
        fakeLlmEngine = FakeLlmEngine(shouldThrowOnSend = true)
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("Hello")

        assertTrue(logic.errorOccurred)
        assertTrue(logic.state.value is com.chloe.acechat.domain.model.ConversationState.Error)
    }

    @Test
    fun sendMessage_emptyInput_doesNotAddMessage() = runTest {
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("   ")

        assertTrue(logic.messages.value.isEmpty())
        assertEquals(0, fakeLlmEngine.sendMessageCallCount)
    }

    @Test
    fun sendMessage_backslashN_convertedToNewline() = runTest {
        fakeLlmEngine = FakeLlmEngine(responses = listOf(listOf("Line1\\nLine2")))
        val logic = TestableChatLogic(fakeLlmEngine, fakeRepository, conversationId)

        logic.sendMessage("Hello")

        val botMessage = logic.messages.value.first { it.role == MessageRole.BOT }
        assertEquals("Line1\nLine2", botMessage.content)
    }
}
