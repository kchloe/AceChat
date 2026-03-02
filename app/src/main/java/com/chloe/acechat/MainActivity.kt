package com.chloe.acechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chloe.acechat.presentation.navigation.AceChatNavGraph
import com.chloe.acechat.ui.theme.AceChatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AceChatTheme {
                AceChatNavGraph(
                    appContainer = (application as AceChatApplication).appContainer,
                )
            }
        }
    }
}
