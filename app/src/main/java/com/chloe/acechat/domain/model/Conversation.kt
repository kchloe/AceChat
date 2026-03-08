package com.chloe.acechat.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val engineMode: EngineMode,
    val createdAt: Long,
    val updatedAt: Long,
    val languageMode: LanguageMode = LanguageMode.ENGLISH,
)
