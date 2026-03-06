package com.chloe.acechat.fake

import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeUserPreferencesRepository(
    initialMode: EngineMode = EngineMode.ON_DEVICE,
) : UserPreferencesRepository {
    private val _engineMode = MutableStateFlow(initialMode)
    override val engineMode: Flow<EngineMode> = _engineMode.asStateFlow()

    var setEngineModeCallCount = 0
    var lastSetMode: EngineMode? = null

    override suspend fun setEngineMode(mode: EngineMode) {
        setEngineModeCallCount++
        lastSetMode = mode
        _engineMode.value = mode
    }
}
