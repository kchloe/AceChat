# AceChat — Android 영어 학습 Voice Chatbot

## 프로젝트 개요
온디바이스 AI를 활용한 Android 영어 학습 음성 챗봇 앱.
유저가 영어로 말하면 AI가 대화하면서 잘못된 표현을 교정해준다.

## 경로
- AceChat 프로젝트: /Users/user/AndroidStudioProjects/AceChat
- Gallery 참고 레포: /Users/user/AndroidStudioProjects/GoogleAiGallery

## 기술 스택
- **LLM**: LiteRT-LM Kotlin API + Gemma-3-1B-IT (.litertlm 포맷)
- **LLM(온라인)**: Gemini API (google-generativeai SDK)
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
  preferences/ # UserPreferencesRepositoryImpl (DataStore)
  repository/ # ConversationRepositoryImpl
  stt/        # SpeechRecognizer 래퍼
  tts/        # TextToSpeech 래퍼
domain/
  llm/        # LlmEngineInterface
  model/      # ChatMessage, ConversationState, Conversation, EngineMode 등
  repository/ # ConversationRepository 인터페이스
  preferences/ # UserPreferencesRepository 인터페이스
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
- [x] M9: Room DB + ConversationRepository
- [x] M10: AppContainer + Application + DataStore
- [x] M11: 멀티스크린 + Compose Navigation
- [x] M12: 테스트 환경 구축 + 단위 테스트
- [ ] M13: 기능 고도화 — 세부 계획: `docs/backlog.md` 참조

## 참조 문서
- **기능 백로그 / 버그 / 기술 부채**: `docs/backlog.md`
- **UX 리서치** (경쟁앱 분석, 트렌드, 포지셔닝): `docs/ux-strategy-report.md`

## 주의사항
- Gallery 코드를 복사하지 말고 패턴을 참고해서 AceChat에 맞게 작성할 것
- LiteRT-LM은 아직 preview 단계이므로 API 변경 가능성 있음
- 모델 파일(.litertlm)은 기기 로컬 스토리지에 수동 배치 (테스트용)
- M13부터 새로 작성하는 코드는 TDD(Red → Green → Refactor) 사이클로 운영한다

## Agent 사용 규칙
- 코드 작성/수정/리팩토링이 포함된 태스크는 반드시 subagent를 사용한다.
- 단순 질문, 설명, 분석만 하는 경우는 메인 Claude가 직접 처리해도 된다.
- M13 기능 착수 전: `project-planner`로 backlog 항목 검토 + 마일스톤 분해 후 진행.
- **구현 중 새로운 버그·기술 부채·기능 아이디어를 발견하면 `docs/backlog.md`에 즉시 추가한다.**
- **backlog.md 항목 구현 완료 시 해당 항목을 `[ ]` → `[x]`로 업데이트한다.**
- UX 결정사항을 CLAUDE.md에 반영할 때는 아래 순서로 subagent를 호출한다:
  1. `android-architect` — UX 결정을 기술 언어로 해석 + 마일스톤 분해
  2. `project-planner` — 의존성 분석·우선순위 결정 + CLAUDE.md 업데이트
