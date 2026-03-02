package com.chloe.acechat.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chloe.acechat.domain.model.EngineMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val engineModeKey = stringPreferencesKey("engine_mode")

    val engineMode: Flow<EngineMode> = context.dataStore.data.map { prefs ->
        runCatching { EngineMode.valueOf(prefs[engineModeKey] ?: EngineMode.ON_DEVICE.name) }
            .getOrDefault(EngineMode.ON_DEVICE)
    }

    suspend fun setEngineMode(mode: EngineMode) {
        context.dataStore.edit { prefs ->
            prefs[engineModeKey] = mode.name
        }
    }
}
