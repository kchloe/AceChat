package com.chloe.acechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.chloe.acechat.data.llm.DEFAULT_MODEL_PATH
import com.chloe.acechat.presentation.chat.ChatScreen
import com.chloe.acechat.presentation.chat.ChatViewModel
import com.chloe.acechat.ui.theme.AceChatTheme

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(application, DEFAULT_MODEL_PATH)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AceChatTheme {
                ChatScreen(viewModel = chatViewModel)
            }
        }
    }
}
