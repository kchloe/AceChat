package com.chloe.acechat.data.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class SttState {
    /** 대기 중 — 마이크 버튼을 탭할 수 있는 상태 */
    object Idle : SttState()

    /** SpeechRecognizer가 준비 완료, 녹음 및 인식 중 */
    object Listening : SttState()

    /** 실시간 부분 인식 결과 */
    data class PartialResult(val text: String) : SttState()

    /**
     * 인식 완료 결과.
     * ViewModel이 이 상태를 감지하면 sendMessage()를 호출하고, 이후 Idle로 복귀시킨다.
     */
    data class Result(val text: String) : SttState()

    /**
     * 오류 발생.
     * ViewModel이 이 상태를 감지하면 잠시 후 Idle로 복귀시킨다.
     */
    data class Error(val message: String) : SttState()
}

/**
 * Android [SpeechRecognizer]를 래핑하는 클래스.
 *
 * 사용 스레드: [SpeechRecognizer]는 메인 스레드에서만 사용해야 하며,
 * 콜백도 메인 스레드에서 수신된다. [startListening]/[destroy]를 메인 스레드에서 호출할 것.
 */
class SpeechRecognizerManager(private val context: Context) {

    private val _sttState = MutableStateFlow<SttState>(SttState.Idle)
    val sttState: StateFlow<SttState> = _sttState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _sttState.value = SttState.Listening
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            _sttState.value = SttState.Error(errorMessage(error))
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _sttState.value = SttState.Result(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotEmpty()) {
                _sttState.value = SttState.PartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * 음성 인식을 시작한다.
     * 이미 [SttState.Listening] 상태라면 무시한다.
     *
     * @param locale 인식 언어. 기본값은 [Locale.ENGLISH].
     */
    fun startListening(locale: Locale = Locale.ENGLISH) {
        if (_sttState.value is SttState.Listening) return

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        speechRecognizer?.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // 최소 발화 길이: 너무 짧은 소음을 음성으로 오인하지 않도록 100ms 유지
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 100L)
            // 발화 도중 일시 침묵 허용 시간: 학습자가 다음 단어를 떠올리는 동안의 포즈를 허용.
            // 이 값은 구글 STT 서비스에 대한 "hint"이며 기기별로 실제 동작이 다를 수 있음.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            // 발화 완료로 최종 판단하는 침묵 시간: 위 값보다 길거나 같게 설정해야 일관성 있음.
            // 이 역시 구글 STT에 대한 hint이며 기기별 동작 차이가 있을 수 있음.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            // 참고: 마이크 버튼을 누른 후 말을 시작하기까지의 초기 대기 시간(speech timeout)을
            // 직접 제어하는 표준 Extra는 존재하지 않음. 해당 timeout은 OS/기기 구현에 따라 결정됨.
        }
        speechRecognizer?.startListening(intent)
    }

    /** 상태를 [SttState.Idle]로 초기화한다. ViewModel에서 Result/Error 처리 후 호출한다. */
    fun resetToIdle() {
        _sttState.value = SttState.Idle
    }

    /** SpeechRecognizer 리소스를 해제하고 상태를 Idle로 초기화한다. */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _sttState.value = SttState.Idle
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Unknown error ($error)"
    }
}
