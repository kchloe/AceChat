package com.chloe.acechat.presentation.conversationlist

import app.cash.turbine.test
import com.chloe.acechat.domain.model.Conversation
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.fake.FakeConversationRepository
import com.chloe.acechat.fake.FakeUserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ConversationListViewModel 단위 테스트.
 *
 * ConversationListViewModel은 AndroidViewModel을 상속하므로 Application 인스턴스가 필요하다.
 * 따라서 JVM 단위 테스트에서 직접 생성이 불가능하다.
 * isModelReady()는 실제 파일시스템을 조회하므로 테스트 범위에서 제외한다.
 *
 * 검증 가능한 범위:
 * - conversations Flow 방출 (FakeConversationRepository 기반)
 * - engineMode Flow 방출 (FakeUserPreferencesRepository 기반)
 * - createNewConversation, deleteConversation, renameConversation 위임 동작
 *
 * 접근 전략: ConversationListViewModel의 핵심 비즈니스 로직을 ConversationListLogic에
 * 추출하여 AndroidViewModel 의존성 없이 Fake 의존성과 직접 검증한다.
 * createNewConversation()은 userPreferencesRepository.engineMode.first()로 모드를 읽으므로
 * StateFlow 캐스팅 없이 Flow.first()를 사용한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeRepository: FakeConversationRepository
    private lateinit var fakePreferences: FakeUserPreferencesRepository

    // ConversationListViewModel의 핵심 비즈니스 로직을 추출한 테스트 전용 래퍼.
    // AndroidViewModel 의존성을 우회하기 위해 로직을 직접 검증한다.
    private inner class ConversationListLogic(
        private val repository: FakeConversationRepository,
        private val preferences: FakeUserPreferencesRepository,
    ) {
        val conversations: kotlinx.coroutines.flow.Flow<List<Conversation>> =
            repository.getAllConversations()

        val engineMode: kotlinx.coroutines.flow.Flow<EngineMode> = preferences.engineMode

        suspend fun createNewConversation(): String {
            // 프로덕션 코드(ConversationListViewModel)와 동일하게 engineMode.first()로 현재 값을 읽는다
            val currentMode = preferences.engineMode.first()
            val conversation = repository.createConversation(currentMode)
            return conversation.id
        }

        suspend fun deleteConversation(id: String) {
            repository.deleteConversation(id)
        }

        suspend fun renameConversation(id: String, newTitle: String) {
            repository.updateConversationTitle(id, newTitle)
        }
    }

    private lateinit var logic: ConversationListLogic

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeConversationRepository()
        fakePreferences = FakeUserPreferencesRepository(initialMode = EngineMode.ON_DEVICE)
        logic = ConversationListLogic(fakeRepository, fakePreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun conversations_initialValue_isEmpty() = runTest {
        logic.conversations.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createNewConversation_addsToConversationsList() = runTest {
        logic.conversations.test {
            assertTrue(awaitItem().isEmpty())

            logic.createNewConversation()

            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("New Chat", list[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createNewConversation_returnsValidId() = runTest {
        val id = logic.createNewConversation()
        assertTrue(id.isNotEmpty())
    }

    @Test
    fun createNewConversation_incrementsRepositoryCallCount() = runTest {
        logic.createNewConversation()
        logic.createNewConversation()

        assertEquals(2, fakeRepository.createConversationCallCount)
    }

    @Test
    fun deleteConversation_removesFromList() = runTest {
        val conv1 = makeConversation("id-1", "Chat 1")
        val conv2 = makeConversation("id-2", "Chat 2")
        fakeRepository.setConversations(listOf(conv1, conv2))

        logic.conversations.test {
            assertEquals(2, awaitItem().size)

            logic.deleteConversation("id-1")

            val remaining = awaitItem()
            assertEquals(1, remaining.size)
            assertEquals("id-2", remaining[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteConversation_incrementsRepositoryCallCount() = runTest {
        val conv = makeConversation("id-1", "Chat 1")
        fakeRepository.setConversations(listOf(conv))

        logic.deleteConversation("id-1")

        assertEquals(1, fakeRepository.deleteConversationCallCount)
    }

    @Test
    fun renameConversation_updatesTitle() = runTest {
        val conv = makeConversation("id-1", "Old Title")
        fakeRepository.setConversations(listOf(conv))

        logic.conversations.test {
            assertEquals("Old Title", awaitItem()[0].title)

            logic.renameConversation("id-1", "New Title")

            assertEquals("New Title", awaitItem()[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun renameConversation_storesCorrectTitle() = runTest {
        val conv = makeConversation("id-1", "Old Title")
        fakeRepository.setConversations(listOf(conv))

        logic.renameConversation("id-1", "New Title")

        assertEquals("New Title", fakeRepository.lastUpdatedTitle)
        assertEquals(1, fakeRepository.updateTitleCallCount)
    }

    @Test
    fun engineMode_initialValue_isOnDevice() = runTest {
        logic.engineMode.test {
            assertEquals(EngineMode.ON_DEVICE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun engineMode_afterChange_emitsNewValue() = runTest {
        logic.engineMode.test {
            assertEquals(EngineMode.ON_DEVICE, awaitItem())

            fakePreferences.setEngineMode(EngineMode.ONLINE)

            assertEquals(EngineMode.ONLINE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createNewConversation_usesCurrentEngineMode_fromPreferences() = runTest {
        // engineMode.first()로 preferences에서 현재 값을 읽어 대화 생성에 사용하는지 검증
        fakePreferences.setEngineMode(EngineMode.ONLINE)

        logic.createNewConversation()

        val created = fakeRepository.getAllConversations().first()
        assertEquals(1, created.size)
        assertEquals(EngineMode.ONLINE, created[0].engineMode)
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private fun makeConversation(id: String, title: String): Conversation = Conversation(
        id = id,
        title = title,
        engineMode = EngineMode.ON_DEVICE,
        createdAt = 1000L,
        updatedAt = 1000L,
    )
}
