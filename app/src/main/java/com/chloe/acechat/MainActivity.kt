package com.chloe.acechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chloe.acechat.presentation.chat.ChatScreen
import com.chloe.acechat.presentation.chat.ChatViewModel
import com.chloe.acechat.presentation.chat.ModelDownloadScreen
import com.chloe.acechat.presentation.chat.ModelDownloadViewModel
import com.chloe.acechat.ui.theme.AceChatTheme

class MainActivity : ComponentActivity() {

    private val modelDownloadViewModel: ModelDownloadViewModel by viewModels()

    // Lazily created so modelPath is always taken from the final downloaded location.
    private val chatViewModel: ChatViewModel by lazy {
        ChatViewModel(application, modelDownloadViewModel.modelPath)
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
                var isChatReady by remember { mutableStateOf(false) }

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
