package com.chloe.acechat.di

import android.app.Application
import android.content.Context
import com.chloe.acechat.data.db.AceChatDatabase
import com.chloe.acechat.data.llm.GeminiLlmEngine
import com.chloe.acechat.data.llm.MODEL_FILE_NAME
import com.chloe.acechat.data.llm.OnDeviceLlmEngine
import com.chloe.acechat.data.preferences.UserPreferencesRepository
import com.chloe.acechat.data.repository.ConversationRepositoryImpl
import com.chloe.acechat.domain.llm.LlmEngineInterface
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.repository.ConversationRepository
import java.io.File

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** NavGraph 등에서 Application 컨텍스트가 필요할 때 사용. */
    val application: Application get() = appContext as Application

    val userPreferencesRepository = UserPreferencesRepository(appContext)

    private val db = AceChatDatabase.getInstance(appContext)
    val conversationRepository: ConversationRepository = ConversationRepositoryImpl(db)

    // OnDeviceLlmEngine은 초기화 비용이 크므로 AppContainer에서 싱글턴으로 관리.
    // ChatViewModel.onCleared()에서 close()를 호출하지 않고, 앱 프로세스 종료 시까지 유지.
    val onDeviceLlmEngine: OnDeviceLlmEngine by lazy {
        val modelDir = File(appContext.getExternalFilesDir(null), "models")
        val modelPath = File(modelDir, MODEL_FILE_NAME).absolutePath
        val cacheDir = appContext.getExternalFilesDir(null)?.absolutePath
            ?: appContext.cacheDir.absolutePath
        OnDeviceLlmEngine(modelPath = modelPath, cacheDir = cacheDir)
    }

    // GeminiLlmEngine은 상태가 가벼우므로 필요할 때마다 새로 생성해도 되지만,
    // 일관성을 위해 싱글턴으로 관리.
    val geminiLlmEngine: GeminiLlmEngine by lazy {
        GeminiLlmEngine()
    }

    fun getLlmEngine(mode: EngineMode): LlmEngineInterface = when (mode) {
        EngineMode.ON_DEVICE -> onDeviceLlmEngine
        EngineMode.ONLINE -> geminiLlmEngine
    }
}
