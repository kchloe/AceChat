package com.chloe.acechat.preferences

import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.LanguageMode
import com.chloe.acechat.domain.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD Red 단계 — UserPreferencesRepository.languageMode 검증.
 *
 * LanguageMode enum과 UserPreferencesRepository.languageMode / setLanguageMode()가
 * 아직 존재하지 않으므로 이 파일은 컴파일 오류로 실패한다 (Red 상태).
 */
class LanguageModePreferencesTest {

    // -----------------------------------------------------------------------------------------
    // 테스트 내부 Fake: UserPreferencesRepository에 languageMode가 추가되었을 때를 가정
    // -----------------------------------------------------------------------------------------

    /**
     * A2 기능을 위해 확장된 FakeUserPreferencesRepository.
     *
     * UserPreferencesRepository 인터페이스에 languageMode / setLanguageMode()가
     * 추가된 이후 컴파일 가능하다.
     */
    private class FakeUserPreferencesRepositoryWithLanguage(
        initialEngineMode: EngineMode = EngineMode.ON_DEVICE,
        initialLanguageMode: LanguageMode = LanguageMode.ENGLISH,
    ) : UserPreferencesRepository {

        private val _engineMode = MutableStateFlow(initialEngineMode)
        override val engineMode: Flow<EngineMode> = _engineMode.asStateFlow()

        override suspend fun setEngineMode(mode: EngineMode) {
            _engineMode.value = mode
        }

        private val _languageMode = MutableStateFlow(initialLanguageMode)
        override val languageMode: Flow<LanguageMode> = _languageMode.asStateFlow()

        var setLanguageModeCallCount = 0
        var lastSetLanguageMode: LanguageMode? = null

        override suspend fun setLanguageMode(mode: LanguageMode) {
            setLanguageModeCallCount++
            lastSetLanguageMode = mode
            _languageMode.value = mode
        }
    }

    // -----------------------------------------------------------------------------------------
    // TC-LP-01. 기본 languageMode는 ENGLISH이다
    // -----------------------------------------------------------------------------------------

    @Test
    fun languageMode_default_isEnglish() = runTest {
        val fake = FakeUserPreferencesRepositoryWithLanguage()

        val result = fake.languageMode.first()

        assertEquals(LanguageMode.ENGLISH, result)
    }

    // -----------------------------------------------------------------------------------------
    // TC-LP-02. setLanguageMode(KOREAN) 후 languageMode가 KOREAN으로 변경된다
    // -----------------------------------------------------------------------------------------

    @Test
    fun setLanguageMode_korean_updatesLanguageModeToKorean() = runTest {
        val fake = FakeUserPreferencesRepositoryWithLanguage()

        fake.setLanguageMode(LanguageMode.KOREAN)

        assertEquals(LanguageMode.KOREAN, fake.languageMode.first())
    }

    // -----------------------------------------------------------------------------------------
    // TC-LP-03. setLanguageMode를 여러 번 호출하면 마지막 값이 유지된다
    // -----------------------------------------------------------------------------------------

    @Test
    fun setLanguageMode_calledMultipleTimes_retainsLastValue() = runTest {
        val fake = FakeUserPreferencesRepositoryWithLanguage()

        fake.setLanguageMode(LanguageMode.KOREAN)
        fake.setLanguageMode(LanguageMode.ENGLISH)

        assertEquals(LanguageMode.ENGLISH, fake.languageMode.first())
    }
}
