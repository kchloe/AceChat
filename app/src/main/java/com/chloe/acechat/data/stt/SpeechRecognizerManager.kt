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
     */
    fun startListening() {
        if (_sttState.value is SttState.Listening) return

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        speechRecognizer?.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 100L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
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
