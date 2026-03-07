package com.chloe.acechat.data.tts

sealed class TtsState {
    /** 대기 중 */
    object Idle : TtsState()

    /** 음성 출력 중 */
    object Speaking : TtsState()

    /** 오류 발생 */
    data class Error(val message: String) : TtsState()
}

/** 하위 호환성을 위한 typealias. 새 코드에서는 [TtsManagerImpl]을 직접 사용하라. */
@Deprecated("Use TtsManagerImpl directly", ReplaceWith("TtsManagerImpl"))
typealias TtsManager = TtsManagerImpl
