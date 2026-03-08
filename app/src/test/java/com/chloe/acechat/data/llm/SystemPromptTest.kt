package com.chloe.acechat.data.llm

import com.chloe.acechat.domain.model.LanguageMode
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD Red 단계 — buildSystemPrompt() 함수 검증.
 *
 * LanguageMode enum과 buildSystemPrompt() 함수가 아직 존재하지 않으므로
 * 이 파일은 컴파일 오류로 실패한다 (Red 상태).
 */
class SystemPromptTest {

    // -----------------------------------------------------------------------------------------
    // TC-SP-01. ENGLISH 모드 프롬프트는 영어 튜터 페르소나를 포함한다
    // -----------------------------------------------------------------------------------------

    @Test
    fun buildSystemPrompt_englishMode_containsEnglishPersona() {
        val prompt = buildSystemPrompt(LanguageMode.ENGLISH)

        assertTrue(
            "ENGLISH 모드 프롬프트에 'English' 또는 'english'가 포함되어야 한다",
            prompt.contains("English", ignoreCase = true),
        )
    }

    // -----------------------------------------------------------------------------------------
    // TC-SP-02. KOREAN 모드 프롬프트는 한국어 튜터 페르소나를 포함한다
    // -----------------------------------------------------------------------------------------

    @Test
    fun buildSystemPrompt_koreanMode_containsKoreanPersona() {
        val prompt = buildSystemPrompt(LanguageMode.KOREAN)

        assertTrue(
            "KOREAN 모드 프롬프트에 '한국어' 또는 'Korean'이 포함되어야 한다",
            prompt.contains("한국어") || prompt.contains("Korean", ignoreCase = true),
        )
    }

    // -----------------------------------------------------------------------------------------
    // TC-SP-03. ENGLISH 모드와 KOREAN 모드 프롬프트는 서로 다르다
    // -----------------------------------------------------------------------------------------

    @Test
    fun buildSystemPrompt_englishAndKorean_produceDifferentPrompts() {
        val englishPrompt = buildSystemPrompt(LanguageMode.ENGLISH)
        val koreanPrompt = buildSystemPrompt(LanguageMode.KOREAN)

        assertNotEquals(
            "ENGLISH와 KOREAN 모드의 프롬프트는 서로 달라야 한다",
            englishPrompt,
            koreanPrompt,
        )
    }

    // -----------------------------------------------------------------------------------------
    // TC-SP-04. 두 프롬프트 모두 Correction 마커(✏️ Correction:)를 포함한다
    // -----------------------------------------------------------------------------------------

    @Test
    fun buildSystemPrompt_bothModes_containCorrectionMarker() {
        val englishPrompt = buildSystemPrompt(LanguageMode.ENGLISH)
        val koreanPrompt = buildSystemPrompt(LanguageMode.KOREAN)

        assertTrue(
            "ENGLISH 모드 프롬프트에 '✏️ Correction:'이 포함되어야 한다",
            englishPrompt.contains("✏️ Correction:"),
        )
        assertTrue(
            "KOREAN 모드 프롬프트에 '✏️ Correction:'이 포함되어야 한다",
            koreanPrompt.contains("✏️ Correction:"),
        )
    }
}
