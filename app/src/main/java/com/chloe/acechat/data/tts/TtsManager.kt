package com.chloe.acechat.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "TtsManager"
private const val UTTERANCE_ID = "acechat_tts"

sealed class TtsState {
    /** 대기 중 */
    object Idle : TtsState()

    /** 음성 출력 중 */
    object Speaking : TtsState()

    /** 오류 발생 */
    data class Error(val message: String) : TtsState()
}

/**
 * Android [TextToSpeech]를 래핑하는 클래스.
 *
 * 사용 스레드: [TextToSpeech]는 메인 스레드에서 초기화해야 한다.
 * [speak], [stop], [destroy]도 메인 스레드에서 호출할 것.
 */
class TtsManager(context: Context) {

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Idle)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.ENGLISH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "English language not supported")
                    _ttsState.value = TtsState.Error("English language not supported")
                } else {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _ttsState.value = TtsState.Speaking
                        }

                        override fun onDone(utteranceId: String?) {
                            _ttsState.value = TtsState.Idle
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _ttsState.value = TtsState.Error("TTS playback error")
                        }
                    })
                    isInitialized = true
                    tts?.setSpeechRate(0.85f)   // 1.0 = 기본속도, 낮을수록 느림
                    tts?.setPitch(0.9f)          // 1.0 = 기본피치, 낮을수록 낮은 목소리
                    Log.d(TAG, "TTS initialized")
                }
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
                _ttsState.value = TtsState.Error("TTS initialization failed")
            }
        }
    }

    /**
     * 주어진 텍스트를 음성으로 출력한다.
     * 기존 재생 중인 음성은 중단 후 새로 재생한다 ([TextToSpeech.QUEUE_FLUSH]).
     */
    fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** 음성 출력을 중단하고 상태를 [TtsState.Idle]로 초기화한다. */
    fun stop() {
        tts?.stop()
        _ttsState.value = TtsState.Idle
    }

    /** TTS 리소스를 해제하고 상태를 [TtsState.Idle]로 초기화한다. */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _ttsState.value = TtsState.Idle
    }
}
