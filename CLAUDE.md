# AceChat — Android 영어 학습 Voice Chatbot

## 프로젝트 개요
온디바이스 AI를 활용한 Android 영어 학습 음성 챗봇 앱.
유저가 영어로 말하면 AI가 대화하면서 잘못된 표현을 교정해준다.

## 경로
- AceChat 프로젝트: /Users/user/AndroidStudioProjects/AceChat
- Gallery 참고 레포: /Users/user/AndroidStudioProjects/GoogleAiGallery

## 기술 스택
- **LLM**: LiteRT-LM Kotlin API + Gemma-3-1B-IT (.litertlm 포맷)
- **STT**: Android SpeechRecognizer (Push-to-talk, 영어)
- **TTS**: Android TextToSpeech (영어)
- **UI**: Jetpack Compose
- **아키텍처**: MVVM + Coroutines/Flow
- **언어**: Kotlin

## 패키지 구조
data/
llm/        # LiteRT-LM Engine 래퍼
stt/        # SpeechRecognizer 래퍼
tts/        # TextToSpeech 래퍼
domain/
model/      # ChatMessage, ConversationState 데이터 모델
presentation/
chat/       # ChatScreen, ChatViewModel
components/ # 공통 UI 컴포넌트

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
- [ ] M6: TTS 연동
- [ ] M7: 파이프라인 통합 + UX 완성
- [ ] M8: 테스트 + 마무리

## 주의사항
- Gallery 코드를 복사하지 말고 패턴을 참고해서 AceChat에 맞게 작성할 것
- LiteRT-LM은 아직 preview 단계이므로 API 변경 가능성 있음
- 모델 파일(.litertlm)은 기기 로컬 스토리지에 수동 배치 (테스트용)