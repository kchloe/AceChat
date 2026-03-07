package com.chloe.acechat.domain.tts

import com.chloe.acechat.data.tts.TtsState
import kotlinx.coroutines.flow.StateFlow

interface TtsManagerInterface {
    val ttsState: StateFlow<TtsState>
    fun speak(text: String)
    fun stop()
    fun destroy()
}
