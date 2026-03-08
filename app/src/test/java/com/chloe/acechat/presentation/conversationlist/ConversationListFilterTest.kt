package com.chloe.acechat.presentation.conversationlist

import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.LanguageMode
import com.chloe.acechat.domain.repository.ConversationRepository
import com.chloe.acechat.fake.FakeConversationRepository
import com.chloe.acechat.fake.FakeUserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TDD Red 단계 — 언어모드별 대화목록 필터링 테스트.
 *
 * 아래 항목이 아직 존재하지 않아 컴파일 오류 또는 런타임 실패가 발생한다:
 *   1. ConversationRepository.getConversationsByLanguage(LanguageMode) — 인터페이스 미정의
 *   2. ConversationListViewModel.hasOtherLanguageConversations (StateFlow) — ViewModel 미구현
 *
 * ConversationListViewModel은 AndroidViewModel 상속이므로 JVM에서 직접 생성 불가.
 * → TestableCl(Conversation List Logic)을 테스트 파일 내부에 정의해 핵심 필터링 로직을 격리 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationListFilterTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeConversationRepository: FakeConversationRepository
    private lateinit var fakeUserPreferencesRepository: FakeUserPreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeConversationRepository = FakeConversationRepository()
        fakeUserPreferencesRepository = FakeUserPreferencesRepository(
            initialLanguageMode = LanguageMode.ENGLISH,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------------------------
    // TestableConversationListLogic
    // ConversationListViewModel의 필터링/파생 상태 로직을 AndroidViewModel 없이 검증한다.
    // -----------------------------------------------------------------------------------------

    /**
     * ConversationListViewModel이 구현해야 할 언어 필터링 로직.
     *
     * - conversations: 현재 languageMode에 해당하는 대화만 반환
     *   → ConversationRepository.getConversationsByLanguage(LanguageMode) 사용 (Red: 미존재)
     * - hasOtherLanguageConversations: 현재 언어와 다른 대화가 1개 이상 존재하면 true
     *   → ConversationListViewModel.hasOtherLanguageConversations 에 대응 (Red: 미존재)
     */
    private inner class TestableConversationListLogic(
        private val conversationRepository: ConversationRepository,
        private val userPrefsRepository: FakeUserPreferencesRepository,
    ) {
        val languageMode: StateFlow<LanguageMode> = userPrefsRepository.languageMode
            .stateIn(
                scope = testScope,
                started = SharingStarted.Eagerly,
                initialValue = LanguageMode.ENGLISH,
            )

        /**
         * 현재 languageMode에 맞는 대화만 필터링해서 반환한다.
         *
         * GREEN: flatMapLatest로 languageMode 변경에 반응한다.
         */
        val conversations: StateFlow<List<Conversation>> =
            userPrefsRepository.languageMode
                .flatMapLatest { lang ->
                    conversationRepository.getConversationsByLanguage(lang)
                }
                .stateIn(
                    scope = testScope,
                    started = SharingStarted.Eagerly,
                    initialValue = emptyList(),
                )

        /**
         * 현재 languageMode와 다른 언어의 대화가 1개 이상 존재하면 true.
         *
         * GREEN: combine으로 languageMode 변경에 반응한다.
         */
        val hasOtherLanguageConversations: StateFlow<Boolean> =
            combine(
                conversationRepository.getAllConversations(),
                userPrefsRepository.languageMode,
            ) { all, lang -> all.any { it.languageMode != lang } }
                .stateIn(
                    scope = testScope,
                    started = SharingStarted.Eagerly,
                    initialValue = false,
                )

        suspend fun setLanguageMode(mode: LanguageMode) {
            userPrefsRepository.setLanguageMode(mode)
        }
    }

    // -----------------------------------------------------------------------------------------
    // 테스트용 Conversation 생성 헬퍼
    // -----------------------------------------------------------------------------------------

    private fun conversation(
        id: String,
        languageMode: LanguageMode,
        engineMode: EngineMode = EngineMode.ON_DEVICE,
    ) = Conversation(
        id = id,
        title = "Chat $id",
        engineMode = engineMode,
        createdAt = 0L,
        updatedAt = 0L,
        languageMode = languageMode,
    )

    // -----------------------------------------------------------------------------------------
    // TC-F01. ENGLISH 모드에서 KOREAN 대화는 목록에 나타나지 않는다
    // -----------------------------------------------------------------------------------------

    @Test
    fun conversations_whenEnglishMode_excludesKoreanConversations() = runTest {
        // Given: ENGLISH 대화 2개 + KOREAN 대화 1개
        fakeConversationRepository.setConversations(
            listOf(
                conversation("eng-1", LanguageMode.ENGLISH),
                conversation("eng-2", LanguageMode.ENGLISH),
                conversation("kor-1", LanguageMode.KOREAN),
            )
        )
        val logic = TestableConversationListLogic(
            conversationRepository = fakeConversationRepository,
            userPrefsRepository = fakeUserPreferencesRepository,
        )
        advanceUntilIdle()

        // When: conversations 수집
        val result = logic.conversations.value

        // Then: 크기 2, 모두 ENGLISH
        assertEquals(2, result.size)
        assertTrue(result.all { it.languageMode == LanguageMode.ENGLISH })
    }

    // -----------------------------------------------------------------------------------------
    // TC-F02. languageMode가 KOREAN으로 변경되면 conversations가 KOREAN 목록으로 교체된다
    // -----------------------------------------------------------------------------------------

    @Test
    fun conversations_whenLanguageModeChangedToKorean_returnsKoreanOnly() = runTest {
        // Given: ENGLISH 대화 1개 + KOREAN 대화 1개
        fakeConversationRepository.setConversations(
            listOf(
                conversation("eng-1", LanguageMode.ENGLISH),
                conversation("kor-1", LanguageMode.KOREAN),
            )
        )
        val logic = TestableConversationListLogic(
            conversationRepository = fakeConversationRepository,
            userPrefsRepository = fakeUserPreferencesRepository,
        )

        // When: KOREAN으로 변경
        logic.setLanguageMode(LanguageMode.KOREAN)
        advanceUntilIdle()

        // Then: KOREAN 대화 1개만 반환
        val result = logic.conversations.value
        assertEquals(1, result.size)
        assertEquals(LanguageMode.KOREAN, result[0].languageMode)
    }

    // -----------------------------------------------------------------------------------------
    // TC-F03. 동일 LanguageMode, 다른 EngineMode 대화는 같은 목록에 표시된다
    // -----------------------------------------------------------------------------------------

    @Test
    fun conversations_whenEnglishMode_includesBothEngineModes() = runTest {
        // Given: ENGLISH+ON_DEVICE, ENGLISH+ONLINE, KOREAN+ON_DEVICE
        fakeConversationRepository.setConversations(
            listOf(
                conversation("eng-ondevice", LanguageMode.ENGLISH, EngineMode.ON_DEVICE),
                conversation("eng-online", LanguageMode.ENGLISH, EngineMode.ONLINE),
                conversation("kor-ondevice", LanguageMode.KOREAN, EngineMode.ON_DEVICE),
            )
        )
        val logic = TestableConversationListLogic(
            conversationRepository = fakeConversationRepository,
            userPrefsRepository = fakeUserPreferencesRepository,
        )
        advanceUntilIdle()

        // When: conversations 수집
        val result = logic.conversations.value

        // Then: 크기 2, 두 항목 모두 ENGLISH
        assertEquals(2, result.size)
        assertTrue(result.all { it.languageMode == LanguageMode.ENGLISH })
    }

    // -----------------------------------------------------------------------------------------
    // TC-F04. 현재 모드와 다른 언어 대화가 존재하면 hasOtherLanguageConversations == true
    // -----------------------------------------------------------------------------------------

    @Test
    fun hasOtherLanguageConversations_whenKoreanExistsInEnglishMode_isTrue() = runTest {
        // Given: ENGLISH 모드, KOREAN 대화 1개 존재
        fakeConversationRepository.setConversations(
            listOf(
                conversation("eng-1", LanguageMode.ENGLISH),
                conversation("kor-1", LanguageMode.KOREAN),
            )
        )
        val logic = TestableConversationListLogic(
            conversationRepository = fakeConversationRepository,
            userPrefsRepository = fakeUserPreferencesRepository,
        )
        advanceUntilIdle()

        // Then: hasOtherLanguageConversations == true
        assertTrue(logic.hasOtherLanguageConversations.value)
    }

    // -----------------------------------------------------------------------------------------
    // TC-F05. 현재 모드와 다른 언어 대화가 없으면 hasOtherLanguageConversations == false
    // -----------------------------------------------------------------------------------------

    @Test
    fun hasOtherLanguageConversations_whenOnlyEnglishInEnglishMode_isFalse() = runTest {
        // Given: ENGLISH 모드, ENGLISH 대화만 존재
        fakeConversationRepository.setConversations(
            listOf(
                conversation("eng-1", LanguageMode.ENGLISH),
                conversation("eng-2", LanguageMode.ENGLISH),
            )
        )
        val logic = TestableConversationListLogic(
            conversationRepository = fakeConversationRepository,
            userPrefsRepository = fakeUserPreferencesRepository,
        )
        advanceUntilIdle()

        // Then: hasOtherLanguageConversations == false
        assertFalse(logic.hasOtherLanguageConversations.value)
    }
}
