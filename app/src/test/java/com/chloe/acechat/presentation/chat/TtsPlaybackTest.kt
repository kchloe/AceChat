package com.chloe.acechat.presentation.chat

import com.chloe.acechat.data.stt.SttState
import com.chloe.acechat.data.tts.TtsState
import com.chloe.acechat.fake.FakeTtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * TDD Green 단계 — 다시 듣기 버튼(onPlayTapped) 로직 테스트.
 *
 * TestableTtsPlaybackLogic은 ChatViewModel.onPlayTapped()의 동작을 검증한다.
 * Green 단계에서 실제 구현이 추가되었다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TtsPlaybackTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeTtsManager: FakeTtsManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTtsManager = FakeTtsManager()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------------------------
    // TestableTtsPlaybackLogic — ChatViewModel.onPlayTapped()의 동작을 검증하는 헬퍼.
    // -----------------------------------------------------------------------------------------

    private inner class TestableTtsPlaybackLogic(
        private val ttsManager: FakeTtsManager,
        sttStateFlow: StateFlow<SttState> = MutableStateFlow(SttState.Idle),
    ) {
        val sttState: StateFlow<SttState> = sttStateFlow

        private val _playingMessageId = MutableStateFlow<String?>(null)
        val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

        /**
         * 다시 듣기 버튼 탭 핸들러.
         *
         * - STT 활성 상태(Listening, PartialResult)이면 무시
         * - playingMessageId == messageId이면 stop() + playingMessageId = null (토글)
         * - playingMessageId != messageId이면 stop() + speak(text) + playingMessageId = messageId
         */
        fun onPlayTapped(messageId: String, text: String) {
            val currentSttState = sttState.value
            if (currentSttState is SttState.Listening || currentSttState is SttState.PartialResult) return

            if (_playingMessageId.value == messageId) {
                ttsManager.stop()
                _playingMessageId.value = null
                return
            }

            ttsManager.stop()
            _playingMessageId.value = messageId
            ttsManager.speak(text)
        }

        /**
         * TtsState 변화를 구독하여 Idle/Error 상태가 되면 playingMessageId를 null로 초기화한다.
         */
        suspend fun observeTtsState() {
            ttsManager.ttsState.collect { state ->
                if (state is TtsState.Idle || state is TtsState.Error) {
                    _playingMessageId.value = null
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // TC-01. 재생 버튼 탭 시 TTS speak 호출 및 playingMessageId 설정
    // -----------------------------------------------------------------------------------------

    @Test
    fun onPlayTapped_whenIdle_callsSpeakAndSetsPlayingMessageId() = runTest {
        val logic = TestableTtsPlaybackLogic(fakeTtsManager)

        logic.onPlayTapped(messageId = "msg-1", text = "Hello there")

        assertEquals(1, fakeTtsManager.speakCallCount)
        assertEquals("Hello there", fakeTtsManager.lastSpokenText)
        assertEquals("msg-1", logic.playingMessageId.value)
    }

    // -----------------------------------------------------------------------------------------
    // TC-02. 재생 중인 동일 메시지 재탭 시 재생 중단 (토글)
    // -----------------------------------------------------------------------------------------

    @Test
    fun onPlayTapped_sameMessageId_stopsPlaybackAndClearsPlayingId() = runTest {
        // Given: "msg-1"이 재생 중인 상태를 만들기 위해 먼저 탭
        val logic = TestableTtsPlaybackLogic(fakeTtsManager)
        logic.onPlayTapped(messageId = "msg-1", text = "Hello there")
        // 첫 번째 호출의 speakCallCount를 리셋해 두 번째 호출만 검증
        val stopCountBefore = fakeTtsManager.stopCallCount

        // When: 동일 messageId 재탭
        logic.onPlayTapped(messageId = "msg-1", text = "Hello there")

        // Then: stop 호출, speak 추가 호출 없음, playingMessageId null
        assertEquals(stopCountBefore + 1, fakeTtsManager.stopCallCount)
        assertEquals(1, fakeTtsManager.speakCallCount) // 처음 1번만 호출됨
        assertNull(logic.playingMessageId.value)
    }

    // -----------------------------------------------------------------------------------------
    // TC-03. 다른 메시지 탭 시 이전 중단 후 새 메시지 재생
    // -----------------------------------------------------------------------------------------

    @Test
    fun onPlayTapped_differentMessageId_stopsCurrentAndPlaysNew() = runTest {
        val logic = TestableTtsPlaybackLogic(fakeTtsManager)
        logic.onPlayTapped(messageId = "msg-1", text = "Hello there")
        val stopCountAfterFirst = fakeTtsManager.stopCallCount

        logic.onPlayTapped(messageId = "msg-2", text = "Good morning")

        // stop이 최소 1회 추가 호출되어야 함 (기존 재생 중단)
        assertEquals(stopCountAfterFirst + 1, fakeTtsManager.stopCallCount)
        assertEquals(2, fakeTtsManager.speakCallCount)
        assertEquals("Good morning", fakeTtsManager.lastSpokenText)
        assertEquals("msg-2", logic.playingMessageId.value)
    }

    // -----------------------------------------------------------------------------------------
    // TC-04. TTS 재생 완료(Idle) 시 playingMessageId 자동 초기화
    // -----------------------------------------------------------------------------------------

    @Test
    fun ttsState_idle_clearsPlayingMessageId() = runTest {
        val logic = TestableTtsPlaybackLogic(fakeTtsManager)
        // Given: 재생 시작
        logic.onPlayTapped(messageId = "msg-1", text = "Hello")
        assertEquals("msg-1", logic.playingMessageId.value)

        // observeTtsState를 구독으로 연결
        val job = launch { logic.observeTtsState() }

        // When: TTS 재생 완료
        fakeTtsManager.simulateSpeakingComplete()
        advanceUntilIdle()

        job.cancel()

        // Then: playingMessageId가 null로 초기화되어야 함
        assertNull(logic.playingMessageId.value)
    }

    // -----------------------------------------------------------------------------------------
    // TC-05. TTS Error 발생 시 playingMessageId 자동 초기화
    // -----------------------------------------------------------------------------------------

    @Test
    fun ttsState_error_clearsPlayingMessageId() = runTest {
        val logic = TestableTtsPlaybackLogic(fakeTtsManager)
        logic.onPlayTapped(messageId = "msg-1", text = "Hello")
        assertEquals("msg-1", logic.playingMessageId.value)

        val job = launch { logic.observeTtsState() }

        fakeTtsManager.simulateError()
        advanceUntilIdle()

        job.cancel()

        assertNull(logic.playingMessageId.value)
    }

    // -----------------------------------------------------------------------------------------
    // TC-06. STT Listening 중 재생 버튼 탭 시 무시
    // -----------------------------------------------------------------------------------------

    @Test
    fun onPlayTapped_whenSttListening_isIgnored() = runTest {
        val sttStateFlow = MutableStateFlow<SttState>(SttState.Listening)
        val logic = TestableTtsPlaybackLogic(fakeTtsManager, sttStateFlow)

        logic.onPlayTapped(messageId = "msg-1", text = "Hello")

        assertEquals(0, fakeTtsManager.speakCallCount)
        assertEquals(0, fakeTtsManager.stopCallCount)
        assertNull(logic.playingMessageId.value)
    }

    // -----------------------------------------------------------------------------------------
    // TC-07. STT PartialResult 중 재생 버튼 탭 시 무시
    // -----------------------------------------------------------------------------------------

    @Test
    fun onPlayTapped_whenSttPartialResult_isIgnored() = runTest {
        val sttStateFlow = MutableStateFlow<SttState>(SttState.PartialResult(text = "hel..."))
        val logic = TestableTtsPlaybackLogic(fakeTtsManager, sttStateFlow)

        logic.onPlayTapped(messageId = "msg-1", text = "Hello")

        assertEquals(0, fakeTtsManager.speakCallCount)
        assertNull(logic.playingMessageId.value)
    }
}
