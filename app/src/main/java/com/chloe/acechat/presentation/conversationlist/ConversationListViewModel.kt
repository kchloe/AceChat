package com.chloe.acechat.presentation.conversationlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloe.acechat.data.llm.MODEL_FILE_NAME
import com.chloe.acechat.domain.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

class ConversationListViewModel(
    application: Application,
    private val conversationRepository: ConversationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : AndroidViewModel(application) {

    val conversations: StateFlow<List<Conversation>> = conversationRepository
        .getAllConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val engineMode: StateFlow<EngineMode> = userPreferencesRepository.engineMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EngineMode.ON_DEVICE,
        )

    suspend fun createNewConversation(): String {
        val mode = userPreferencesRepository.engineMode.first()
        val conversation = conversationRepository.createConversation(mode)
        return conversation.id
    }

    suspend fun deleteConversation(id: String) {
        conversationRepository.deleteConversation(id)
    }

    suspend fun renameConversation(id: String, newTitle: String) {
        conversationRepository.updateConversationTitle(id, newTitle)
    }

    fun isModelReady(): Boolean {
        val modelDir = File(getApplication<Application>().getExternalFilesDir(null), "models")
        val modelFile = File(modelDir, MODEL_FILE_NAME)
        return modelFile.exists() && modelFile.length() > 0
    }

    class Factory(
        private val application: Application,
        private val conversationRepository: ConversationRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            ConversationListViewModel(application, conversationRepository, userPreferencesRepository) as T
    }
}
