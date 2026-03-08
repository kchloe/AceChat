package com.chloe.acechat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloe.acechat.domain.preferences.UserPreferencesRepository
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.LanguageMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val engineMode: StateFlow<EngineMode> = userPreferencesRepository.engineMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EngineMode.ON_DEVICE,
        )

    fun setEngineMode(mode: EngineMode) {
        viewModelScope.launch {
            userPreferencesRepository.setEngineMode(mode)
        }
    }

    val languageMode: StateFlow<LanguageMode> = userPreferencesRepository.languageMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LanguageMode.ENGLISH,
        )

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            userPreferencesRepository.setLanguageMode(mode)
        }
    }

    class Factory(
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(userPreferencesRepository) as T
    }
}
