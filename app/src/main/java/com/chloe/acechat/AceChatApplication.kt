package com.chloe.acechat

import android.app.Application
import com.chloe.acechat.di.AppContainer

class AceChatApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
