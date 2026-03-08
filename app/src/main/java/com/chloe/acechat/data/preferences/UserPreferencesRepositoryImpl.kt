package com.chloe.acechat.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.LanguageMode
import com.chloe.acechat.domain.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepositoryImpl(private val context: Context) : UserPreferencesRepository {

    private val engineModeKey = stringPreferencesKey("engine_mode")
    private val languageModeKey = stringPreferencesKey("language_mode")

    override val engineMode: Flow<EngineMode> = context.dataStore.data.map { prefs ->
        runCatching { EngineMode.valueOf(prefs[engineModeKey] ?: EngineMode.ON_DEVICE.name) }
            .getOrDefault(EngineMode.ON_DEVICE)
    }

    override suspend fun setEngineMode(mode: EngineMode) {
        context.dataStore.edit { prefs ->
            prefs[engineModeKey] = mode.name
        }
    }

    override val languageMode: Flow<LanguageMode> = context.dataStore.data.map { prefs ->
        runCatching { LanguageMode.valueOf(prefs[languageModeKey] ?: LanguageMode.ENGLISH.name) }
            .getOrDefault(LanguageMode.ENGLISH)
    }

    override suspend fun setLanguageMode(mode: LanguageMode) {
        context.dataStore.edit { prefs ->
            prefs[languageModeKey] = mode.name
        }
    }
}
