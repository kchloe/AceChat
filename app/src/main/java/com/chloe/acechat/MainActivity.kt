package com.chloe.acechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.presentation.chat.ChatScreen
import com.chloe.acechat.presentation.chat.ChatViewModel
import com.chloe.acechat.presentation.chat.ModelDownloadScreen
import com.chloe.acechat.presentation.chat.ModelDownloadViewModel
import com.chloe.acechat.ui.theme.AceChatTheme

class MainActivity : ComponentActivity() {

    private val modelDownloadViewModel: ModelDownloadViewModel by viewModels()

    // by viewModels {}로 ViewModelStore에 등록하여 구성 변경 시 동일 인스턴스를 재사용하고
    // onCleared()가 정상 호출되도록 보장한다.
    // M11에서 DataStore로 저장된 모드를 읽어 전달하도록 교체 예정.
    private val chatViewModel: ChatViewModel by viewModels {
        val engine = (application as AceChatApplication)
            .appContainer.getLlmEngine(EngineMode.ON_DEVICE)
        ChatViewModel.Factory(application, engine)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AceChatTheme {
                // isChatReady is the single source of truth for screen routing.
                // It is set to true only via onDownloadCompleted, which is called by
                // ModelDownloadScreen after the model is ready (immediately if already
                // existed, or after an 800ms "Setup Complete!" display if just downloaded).
                // rememberSaveable로 구성 변경(화면 회전) 시에도 상태를 유지한다.
                var isChatReady by rememberSaveable { mutableStateOf(false) }

                if (isChatReady) {
                    ChatScreen(viewModel = chatViewModel)
                } else {
                    ModelDownloadScreen(
                        viewModel = modelDownloadViewModel,
                        onDownloadCompleted = { isChatReady = true },
                    )
                }
            }
        }
    }
}
