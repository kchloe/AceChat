package com.chloe.acechat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.chloe.acechat.data.tts.TtsManagerImpl
import com.chloe.acechat.di.AppContainer
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.LanguageMode
import com.chloe.acechat.presentation.chat.ChatScreen
import com.chloe.acechat.presentation.chat.ChatViewModel
import com.chloe.acechat.presentation.chat.ModelDownloadScreen
import com.chloe.acechat.presentation.chat.ModelDownloadViewModel
import com.chloe.acechat.presentation.conversationlist.ConversationListScreen
import com.chloe.acechat.presentation.conversationlist.ConversationListViewModel
import com.chloe.acechat.presentation.settings.SettingsScreen
import com.chloe.acechat.presentation.settings.SettingsViewModel

@Composable
fun AceChatNavGraph(appContainer: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ConversationList) {

        composable<ConversationList> {
            val vm = viewModel {
                ConversationListViewModel.Factory(
                    application = appContainer.application,
                    conversationRepository = appContainer.conversationRepository,
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                ).create(ConversationListViewModel::class.java)
            }
            ConversationListScreen(
                viewModel = vm,
                onOpenChat = { conversationId, engineMode, languageMode ->
                    navController.navigate(Chat(conversationId, engineMode.name, languageMode.name))
                },
                onOpenSettings = {
                    navController.navigate(Settings)
                },
                onNeedModelDownload = { conversationId, engineMode, languageMode ->
                    navController.navigate(ModelDownload(conversationId, engineMode.name, languageMode.name))
                },
            )
        }

        composable<Chat> { backStackEntry ->
            val route = backStackEntry.toRoute<Chat>()
            val mode = EngineMode.valueOf(route.engineMode)
            val language = LanguageMode.valueOf(route.languageMode)
            val vm = viewModel(key = route.conversationId) {
                val engine = appContainer.getLlmEngine(mode, language)
                ChatViewModel.Factory(
                    application = appContainer.application,
                    llmEngine = engine,
                    conversationId = route.conversationId,
                    conversationRepository = appContainer.conversationRepository,
                    ttsManager = TtsManagerImpl(appContainer.application),
                ).create(ChatViewModel::class.java)
            }
            ChatScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Settings> {
            val vm = viewModel {
                SettingsViewModel.Factory(
                    userPreferencesRepository = appContainer.userPreferencesRepository,
                ).create(SettingsViewModel::class.java)
            }
            SettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<ModelDownload> { backStackEntry ->
            val route = backStackEntry.toRoute<ModelDownload>()
            val vm = viewModel { ModelDownloadViewModel(appContainer.application) }
            ModelDownloadScreen(
                viewModel = vm,
                onDownloadCompleted = {
                    // 다운로드 완료 후 ModelDownload를 백스택에서 제거하고 Chat으로 이동.
                    // languageMode는 ModelDownload 라우트로부터 전달받은 값을 그대로 사용한다.
                    navController.navigate(
                        Chat(route.conversationId, route.engineMode, route.languageMode)
                    ) {
                        popUpTo<ModelDownload> { inclusive = true }
                    }
                },
            )
        }
    }
}
