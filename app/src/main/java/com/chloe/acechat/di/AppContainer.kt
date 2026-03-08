package com.chloe.acechat.di

import android.app.Application
import android.content.Context
import com.chloe.acechat.data.db.AceChatDatabase
import com.chloe.acechat.data.llm.GeminiLlmEngine
import com.chloe.acechat.data.llm.MODEL_FILE_NAME
import com.chloe.acechat.data.llm.OnDeviceLlmEngine
import com.chloe.acechat.data.llm.buildSystemPrompt
import com.chloe.acechat.data.preferences.UserPreferencesRepositoryImpl
import com.chloe.acechat.domain.preferences.UserPreferencesRepository
import com.chloe.acechat.data.repository.ConversationRepositoryImpl
import com.chloe.acechat.domain.llm.LlmEngineInterface
import com.chloe.acechat.domain.model.EngineMode
import com.chloe.acechat.domain.model.LanguageMode
import com.chloe.acechat.domain.repository.ConversationRepository
import java.io.File

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** NavGraph 등에서 Application 컨텍스트가 필요할 때 사용. */
    val application: Application get() = appContext as Application

    val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepositoryImpl(appContext)

    private val db = AceChatDatabase.getInstance(appContext)
    val conversationRepository: ConversationRepository = ConversationRepositoryImpl(db)

    /**
     * 언어 모드가 변경될 때마다 새 시스템 프롬프트가 적용되어야 하므로,
     * 언어 모드별로 새 인스턴스를 생성한다.
     *
     * OnDeviceLlmEngine은 초기화 비용이 크지만, 언어 모드 전환은 드문 이벤트이므로
     * 대화 시작 시점에 새 엔진을 생성하는 전략을 취한다.
     * (AppContainer는 엔진 캐싱 책임을 갖지 않음 — 필요 시 NavGraph 레벨에서 캐싱 가능)
     */
    fun getLlmEngine(mode: EngineMode, languageMode: LanguageMode = LanguageMode.ENGLISH): LlmEngineInterface {
        val systemPrompt = buildSystemPrompt(languageMode)
        return when (mode) {
            EngineMode.ON_DEVICE -> {
                val modelDir = File(appContext.getExternalFilesDir(null), "models")
                val modelPath = File(modelDir, MODEL_FILE_NAME).absolutePath
                val cacheDir = appContext.getExternalFilesDir(null)?.absolutePath
                    ?: appContext.cacheDir.absolutePath
                OnDeviceLlmEngine(
                    modelPath = modelPath,
                    cacheDir = cacheDir,
                    systemPrompt = systemPrompt,
                )
            }
            EngineMode.ONLINE -> GeminiLlmEngine(systemPrompt = systemPrompt)
        }
    }
}
