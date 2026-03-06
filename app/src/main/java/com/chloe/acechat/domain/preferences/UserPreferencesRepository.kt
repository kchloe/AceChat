package com.chloe.acechat.domain.preferences

import com.chloe.acechat.domain.model.EngineMode
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val engineMode: Flow<EngineMode>
    suspend fun setEngineMode(mode: EngineMode)
}
