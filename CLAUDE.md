# AceChat — Android 영어 학습 Voice Chatbot

## 프로젝트 개요
온디바이스 AI를 활용한 Android 영어 학습 음성 챗봇 앱.
유저가 영어로 말하면 AI가 대화하면서 잘못된 표현을 교정해준다.

## 경로
- AceChat 프로젝트: /Users/user/AndroidStudioProjects/AceChat
- Gallery 참고 레포: /Users/user/AndroidStudioProjects/GoogleAiGallery

## 기술 스택
- **LLM**: LiteRT-LM Kotlin API + Gemma-3-1B-IT (.litertlm 포맷)
- **LLM(온라인)**: Gemini API (google-generativeai SDK) — Phase 2+
- **STT**: Android SpeechRecognizer (Push-to-talk, 영어)
- **TTS**: Android TextToSpeech (영어)
- **UI**: Jetpack Compose
- **아키텍처**: MVVM + Coroutines/Flow
- **언어**: Kotlin
- **DB**: Room (로컬 채팅 히스토리)
- **Preferences**: DataStore Preferences (엔진 모드 설정)
- **Navigation**: Compose Navigation (Typed Routes)

## 패키지 구조
```
data/
  llm/        # OnDeviceLlmEngine, GeminiLlmEngine
  db/         # Room DB, DAO, Entity, Mapper
  preferences/ # UserPreferencesRepository (DataStore)
  repository/ # ConversationRepositoryImpl
  stt/        # SpeechRecognizer 래퍼
  tts/        # TextToSpeech 래퍼
domain/
  llm/        # LlmEngineInterface
  model/      # ChatMessage, ConversationState, Conversation, EngineMode 등
  repository/ # ConversationRepository 인터페이스
di/           # AppContainer
presentation/
  navigation/ # AceChatNavGraph
  conversationlist/ # ConversationListScreen, ViewModel
  chat/       # ChatScreen, ChatViewModel, ModelDownloadScreen, ViewModel
  settings/   # SettingsScreen, SettingsViewModel
  components/ # 공통 UI 컴포넌트
```

## Gallery 참고 포인트
- LLM 연동: GoogleAiGallery/Android 내 LLM 관련 코드 참고
- Chat UI: GoogleAiGallery/Android 내 AI Chat UI 컴포넌트 참고
- Gallery 코드는 참고용이며, AceChat에 맞게 단순화해서 적용할 것

## 시스템 프롬프트 방향
- 영어 교사 페르소나
- 유저와 영어로만 대화
- 유저의 문법/표현 오류를 자연스럽게 교정
- 교정 시 일반 응답과 구분되는 포맷 사용 (예: ✏️ Correction 섹션)

## 마일스톤 진행 현황
- [x] M0: 프로젝트 생성, Gallery clone, Claude Code 설치
- [x] M1: 프로젝트 셋업 (의존성, 폴더구조, 퍼미션)
- [x] M2: LiteRT-LM 연동 + 시스템 프롬프트
- [x] M3: Chat UI
- [x] M4: 모델 다운로드 관련 구현
- [x] M5: STT 연동
- [x] M6: TTS 연동
- [x] M7: 파이프라인 통합 + UX 완성
- [x] M8: LlmEngine 인터페이스화 + Gemini 온라인 엔진 추가
  - domain/llm/LlmEngineInterface.kt 신설
  - LlmEngine.kt → OnDeviceLlmEngine.kt 개명 + 인터페이스 구현
  - GeminiLlmEngine.kt 구현 (google-generativeai SDK)
  - domain/model/EngineMode.kt 신설
  - ChatViewModel 필드 타입 교체
  - build.gradle.kts: generativeai 의존성 + GEMINI_API_KEY BuildConfig 추가
- [x] M9: Room DB + ConversationRepository
  - ksp 플러그인 + Room 의존성 추가
  - ConversationEntity, MessageEntity, DAO 2개, DB 클래스, Mapper 구현
  - ConversationRepository 인터페이스 + Impl
  - ChatViewModel에 대화 생성/메시지 저장 로직 추가
- [ ] M10: AppContainer + Application + DataStore
  - AceChatApplication.kt, AppContainer.kt 구현
  - UserPreferencesRepository.kt (DataStore Preferences)
  - AndroidManifest.xml android:name 등록
  - MainActivity에서 appContainer 통한 ViewModel Factory 구성
  - datastore-preferences 의존성 추가
- [ ] M11: 멀티스크린 + Compose Navigation
  - kotlin-serialization 플러그인 + navigation-compose 의존성 추가
  - AceChatNavGraph.kt (route 정의 + NavHost)
  - ConversationListScreen + ViewModel
  - SettingsScreen + ViewModel (엔진 모드 토글)
  - ChatScreen, ChatViewModel 수정 (conversationId 수신, 기존 메시지 로드)
  - MainActivity 단순화 / OnDeviceLlmEngine 수명주기 AppContainer로 이전
- [ ] M12: 테스트 + 마무리
  - FakeLlmEngine 작성 (테스트용)
  - ChatViewModelTest, ConversationRepositoryImplTest 등 단위 테스트
  - ProGuard 규칙 점검

## 주의사항
- Gallery 코드를 복사하지 말고 패턴을 참고해서 AceChat에 맞게 작성할 것
- LiteRT-LM은 아직 preview 단계이므로 API 변경 가능성 있음
- 모델 파일(.litertlm)은 기기 로컬 스토리지에 수동 배치 (테스트용)

## Agent 사용 규칙
- 코드 작성/수정/리팩토링이 포함된 태스크는 반드시 subagent를 사용한다.
- 단순 질문, 설명, 분석만 하는 경우는 메인 Claude가 직접 처리해도 된다.
- UX 결정사항을 CLAUDE.md에 반영할 때는 아래 순서로 subagent를 호출한다:
  1. `android-architect` — UX 결정을 기술 언어로 해석 + 마일스톤 분해
  2. `project-planner` — 의존성 분석·우선순위 결정 + CLAUDE.md 업데이트
