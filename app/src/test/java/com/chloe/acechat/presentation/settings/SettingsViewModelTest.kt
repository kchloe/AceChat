package com.chloe.acechat.presentation.settings

import app.cash.turbine.test
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.fake.FakeUserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * SettingsViewModel 단위 테스트.
 *
 * P-1(UserPreferencesRepository 인터페이스 추출) 완료로 FakeUserPreferencesRepository를
 * SettingsViewModel 생성자에 직접 주입해 완전한 격리 테스트가 가능하다.
 *
 * 검증 범위:
 * - engineMode StateFlow 초기값
 * - setEngineMode() 호출 후 engineMode StateFlow 방출 검증
 * - setEngineMode()가 FakeUserPreferencesRepository에 실제로 저장되는지 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakePreferences: FakeUserPreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePreferences = FakeUserPreferencesRepository(initialMode = EngineMode.ON_DEVICE)
        viewModel = SettingsViewModel(fakePreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun engineMode_initialValue_isOnDevice() = runTest {
        assertEquals(EngineMode.ON_DEVICE, viewModel.engineMode.value)
    }

    @Test
    fun setEngineMode_toOnline_updatesFlow() = runTest {
        viewModel.engineMode.test {
            assertEquals(EngineMode.ON_DEVICE, awaitItem())

            viewModel.setEngineMode(EngineMode.ONLINE)

            assertEquals(EngineMode.ONLINE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setEngineMode_toOnDevice_updatesFlow() = runTest {
        fakePreferences.setEngineMode(EngineMode.ONLINE)
        val vm = SettingsViewModel(
            FakeUserPreferencesRepository(initialMode = EngineMode.ONLINE)
        )

        vm.engineMode.test {
            assertEquals(EngineMode.ONLINE, awaitItem())

            vm.setEngineMode(EngineMode.ON_DEVICE)

            assertEquals(EngineMode.ON_DEVICE, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setEngineMode_storesInFakeRepository() = runTest {
        viewModel.setEngineMode(EngineMode.ONLINE)

        assertEquals(EngineMode.ONLINE, fakePreferences.lastSetMode)
        assertEquals(1, fakePreferences.setEngineModeCallCount)
    }

    @Test
    fun setEngineMode_calledMultipleTimes_incrementsCallCount() = runTest {
        viewModel.setEngineMode(EngineMode.ONLINE)
        viewModel.setEngineMode(EngineMode.ON_DEVICE)

        assertEquals(2, fakePreferences.setEngineModeCallCount)
        assertEquals(EngineMode.ON_DEVICE, fakePreferences.lastSetMode)
    }

    @Test
    fun setEngineMode_sameModeRepeatedly_doesNotEmitDuplicate() = runTest {
        viewModel.engineMode.test {
            assertEquals(EngineMode.ON_DEVICE, awaitItem())

            // StateFlow는 동일 값 방출 시 구독자에게 전달하지 않는다
            viewModel.setEngineMode(EngineMode.ON_DEVICE)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
