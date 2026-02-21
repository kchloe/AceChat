package com.chloe.acechat.domain.model

sealed class ConversationState {
    object Idle : ConversationState()
    object Loading : ConversationState()
    object Streaming : ConversationState()
    data class Error(val message: String) : ConversationState()
}
