package com.chloe.acechat.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.chloe.acechat.data.db.AceChatDatabase
import com.chloe.acechat.domain.model.ChatMessage
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.MessageRole
import com.chloe.acechat.domain.model.MessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ConversationRepositoryImpl 계측 테스트.
 * Room in-memory DB를 사용하므로 실기기 또는 에뮬레이터에서 실행해야 한다.
 *
 * 검증 범위:
 * - createConversation: DB에 저장하고 도메인 모델을 반환한다
 * - getAllConversations: Flow로 목록을 방출한다
 * - updateConversationTitle: 제목을 업데이트한다
 * - deleteConversation: 대화와 관련 메시지를 모두 삭제한다
 * - saveMessage / getMessages: 메시지 저장 및 조회
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ConversationRepositoryImplTest {

    private lateinit var db: AceChatDatabase
    private lateinit var repository: ConversationRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AceChatDatabase::class.java,
        ).build()

        repository = ConversationRepositoryImpl(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -----------------------------------------------------------------------------------------
    // createConversation
    // -----------------------------------------------------------------------------------------

    @Test
    fun createConversation_returnsConversationWithId() = runTest {
        val conversation = repository.createConversation(EngineMode.ON_DEVICE)

        assertNotNull(conversation.id)
        assertTrue(conversation.id.isNotEmpty())
    }

    @Test
    fun createConversation_storesDefaultTitle() = runTest {
        val conversation = repository.createConversation(EngineMode.ON_DEVICE)

        assertEquals("New Chat", conversation.title)
    }

    @Test
    fun createConversation_storesCorrectEngineMode() = runTest {
        val conversation = repository.createConversation(EngineMode.ONLINE)

        assertEquals(EngineMode.ONLINE, conversation.engineMode)
    }

    // -----------------------------------------------------------------------------------------
    // getAllConversations
    // -----------------------------------------------------------------------------------------

    @Test
    fun getAllConversations_initiallyEmpty() = runTest {
        repository.getAllConversations().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAllConversations_afterCreate_emitsConversation() = runTest {
        repository.getAllConversations().test {
            assertTrue(awaitItem().isEmpty())

            repository.createConversation(EngineMode.ON_DEVICE)

            val list = awaitItem()
            assertEquals(1, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAllConversations_multipleCreate_emitsAllConversations() = runTest {
        repository.createConversation(EngineMode.ON_DEVICE)
        repository.createConversation(EngineMode.ONLINE)

        repository.getAllConversations().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------------------------
    // updateConversationTitle
    // -----------------------------------------------------------------------------------------

    @Test
    fun updateConversationTitle_updatesTitle() = runTest {
        val conversation = repository.createConversation(EngineMode.ON_DEVICE)

        repository.updateConversationTitle(conversation.id, "My Updated Title")

        repository.getAllConversations().test {
            val list = awaitItem()
            val updated = list.first { it.id == conversation.id }
            assertEquals("My Updated Title", updated.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateConversationTitle_doesNotAffectOtherConversations() = runTest {
        val conv1 = repository.createConversation(EngineMode.ON_DEVICE)
        val conv2 = repository.createConversation(EngineMode.ON_DEVICE)

        repository.updateConversationTitle(conv1.id, "Updated")

        repository.getAllConversations().test {
            val list = awaitItem()
            val conv2Updated = list.first { it.id == conv2.id }
            assertEquals("New Chat", conv2Updated.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------------------------
    // deleteConversation
    // -----------------------------------------------------------------------------------------

    @Test
    fun deleteConversation_removesFromList() = runTest {
        val conv = repository.createConversation(EngineMode.ON_DEVICE)

        repository.deleteConversation(conv.id)

        repository.getAllConversations().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteConversation_alsoDeletesToConversationMessages() = runTest {
        val conv = repository.createConversation(EngineMode.ON_DEVICE)
        val message = makeUserMessage("Hello")
        repository.saveMessage(conv.id, message)

        repository.deleteConversation(conv.id)

        repository.getMessages(conv.id).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteConversation_doesNotAffectOtherConversations() = runTest {
        val conv1 = repository.createConversation(EngineMode.ON_DEVICE)
        val conv2 = repository.createConversation(EngineMode.ON_DEVICE)

        repository.deleteConversation(conv1.id)

        repository.getAllConversations().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(conv2.id, list[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------------------------
    // saveMessage / getMessages
    // -----------------------------------------------------------------------------------------

    @Test
    fun saveMessage_andGetMessages_returnsMessage() = runTest {
        val conv = repository.createConversation(EngineMode.ON_DEVICE)
        val message = makeUserMessage("Hello World")

        repository.saveMessage(conv.id, message)

        repository.getMessages(conv.id).test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("Hello World", messages[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveMessage_multipleMessages_returnedInChronologicalOrder() = runTest {
        val conv = repository.createConversation(EngineMode.ON_DEVICE)
        val msg1 = makeUserMessage("First", timestamp = 1000L)
        val msg2 = makeUserMessage("Second", timestamp = 2000L)
        val msg3 = makeUserMessage("Third", timestamp = 3000L)

        repository.saveMessage(conv.id, msg1)
        repository.saveMessage(conv.id, msg2)
        repository.saveMessage(conv.id, msg3)

        repository.getMessages(conv.id).test {
            val messages = awaitItem()
            assertEquals(3, messages.size)
            assertEquals("First", messages[0].content)
            assertEquals("Second", messages[1].content)
            assertEquals("Third", messages[2].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getMessages_forDifferentConversation_returnsEmpty() = runTest {
        val conv = repository.createConversation(EngineMode.ON_DEVICE)
        val message = makeUserMessage("Hello")
        repository.saveMessage(conv.id, message)

        repository.getMessages("other-conv-id").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveMessage_botMessage_correctRoleStored() = runTest {
        val conv = repository.createConversation(EngineMode.ON_DEVICE)
        val botMessage = ChatMessage(
            role = MessageRole.BOT,
            content = "AI response",
            type = MessageType.NORMAL,
            isVisible = true,
        )

        repository.saveMessage(conv.id, botMessage)

        repository.getMessages(conv.id).test {
            val messages = awaitItem()
            assertEquals(MessageRole.BOT, messages[0].role)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveMessage_correctionType_typePreserved() = runTest {
        val conv = repository.createConversation(EngineMode.ON_DEVICE)
        val correctionMessage = ChatMessage(
            role = MessageRole.BOT,
            content = "Good try! ✏️ Correction: ...",
            type = MessageType.CORRECTION,
            isVisible = true,
        )

        repository.saveMessage(conv.id, correctionMessage)

        repository.getMessages(conv.id).test {
            val messages = awaitItem()
            assertEquals(MessageType.CORRECTION, messages[0].type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private fun makeUserMessage(
        content: String,
        timestamp: Long = System.currentTimeMillis(),
    ): ChatMessage = ChatMessage(
        role = MessageRole.USER,
        content = content,
        type = MessageType.NORMAL,
        isVisible = true,
        timestamp = timestamp,
    )
}
