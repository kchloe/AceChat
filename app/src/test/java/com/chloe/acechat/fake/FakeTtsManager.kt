package com.chloe.acechat.fake

import com.chloe.acechat.data.tts.TtsState
import com.chloe.acechat.domain.tts.TtsManagerInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TtsManagerInterface의 Fake 구현체.
 *
 * @param shouldThrow true이면 speak() 호출 시 예외를 던진다.
 */
class FakeTtsManager(
    private val shouldThrow: Boolean = false,
) : TtsManagerInterface {

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Idle)
    override val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    /** speak() 호출 횟수 */
    var speakCallCount = 0

    /** stop() 호출 횟수 */
    var stopCallCount = 0

    /** destroy() 호출 횟수 */
    var destroyCallCount = 0

    /** 마지막으로 speak()에 전달된 텍스트 */
    var lastSpokenText: String? = null

    override fun speak(text: String) {
        speakCallCount++
        lastSpokenText = text
        if (shouldThrow) throw RuntimeException("FakeTtsManager: speak failed")
        _ttsState.value = TtsState.Speaking
    }

    override fun stop() {
        stopCallCount++
        _ttsState.value = TtsState.Idle
    }

    override fun destroy() {
        destroyCallCount++
        _ttsState.value = TtsState.Idle
    }

    /** TTS 재생 완료를 시뮬레이션한다 — ttsState를 Idle로 전환한다. */
    fun simulateSpeakingComplete() {
        _ttsState.value = TtsState.Idle
    }

    /** TTS 오류를 시뮬레이션한다 — ttsState를 Error로 전환한다. */
    fun simulateError(message: String = "error") {
        _ttsState.value = TtsState.Error(message)
    }
}
