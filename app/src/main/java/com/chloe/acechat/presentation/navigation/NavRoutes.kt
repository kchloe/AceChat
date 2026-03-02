package com.chloe.acechat.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
object ConversationList

@Serializable
object Settings

@Serializable
data class ModelDownload(val conversationId: String, val engineMode: String)

@Serializable
data class Chat(val conversationId: String, val engineMode: String)
