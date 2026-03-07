# AceChat Backlog

> 관련 UX 리서치 전문: [docs/ux-strategy-report.md](ux-strategy-report.md)
> 마지막 업데이트: 2026-03-07

---

## 1. 버그 / 기술 부채

### STT 버그

- [ ] **[BUG] STT silence timeout 무효화**
  - 원인: `SpeechRecognizerManager`의 10,000ms timeout 설정이 기기/제조사 구현에 따라 무시됨 → 말을 조금만 멈춰도 조기 종료
  - 해결 방향: `onEndOfSpeech()` 콜백에서 1.5초 커스텀 타이머 시작, PartialResult 수신 시 2.5초로 자동 연장
  - 우선순위: 임팩트 H / 난이도 3

- [ ] **[BUG] LISTENING 중 강제 종료 불가**
  - 원인: LISTENING 상태에서 마이크 버튼 disabled 처리 → 사용자가 STT를 중단할 수단 없음
  - 해결 방향: LISTENING 상태에서도 버튼 활성화, 탭 시 `stopListening()` 호출
  - 우선순위: 임팩트 H / 난이도 3

### 기술 부채 (테스트 가능성)

- [ ] **[TECH-DEBT] `SpeechRecognizerManager` 인터페이스 추출**
  - 현황: `ChatViewModel`이 내부 프로퍼티로 직접 생성 → JVM 단위 테스트 불가
  - 해결 방향: `SpeechRecognizerManager` 인터페이스 추출 + `ChatViewModel` 생성자 주입 전환
  - 참조: `.claude/agent-memory/android-test-engineer/testability-analysis.md`

- [ ] **[TECH-DEBT] `TtsManager` 인터페이스 추출**
  - 현황: 위와 동일 사유
  - 해결 방향: `TtsManager` 인터페이스 추출 + 생성자 주입 전환
  - 비고: 두 인터페이스 추출 완료 시 `ChatViewModel` 직접 단위 테스트 가능해짐

---

## 2. M13 기능 로드맵

우선순위 기준: 임팩트(H/M/L) × 구현 난이도(1~5, 낮을수록 쉬움)

### Phase 1 — Core UX (확정)

| # | 기능 | 임팩트 | 난이도 | 비고 |
|---|---|---|---|---|
| A1 | **다시 듣기 버튼** (TTS 말풍선 재생 버튼) | H | 2 | |
| A2 | **언어 설정** (영어/한국어 배우기 모드 전환) | H | 3 | 시스템 프롬프트 분기 포함 |
| A3 | **UI 개선** (Material3 Compose 전반 정비) | M | 2 | Empty state, 카드 레이아웃 등 |
| A4 | **대화 제목 자동 생성 개선** (대화 종료 시 전체 대화 요약 기반 생성) | M | 2 | |
| B1 | **대화 시작 주제 카드** (Topic Starter) | H | 2 | 신규 대화 진입 장벽 해소, 3개 카드 제시 |

> M11 기존 구현: 첫 번째 메시지만으로 제목 생성 (단순 fallback 수준).
> A4 신규 목표: 대화 종료 시점에 전체 대화를 LLM이 요약하여 제목 생성.

### Phase 2 — Engagement

| # | 기능 | 임팩트 | 난이도 | 비고 |
|---|---|---|---|---|
| B2 | **학습 세션 요약 리포트** | H | 3 | `ChatMessage.type=CORRECTION` 이미 DB 저장 → 집계 즉시 가능 |
| B3 | **연속 학습 스트릭** (Streak) | H | 3 | 7일+ 유지 시 재방문 3배 효과 (Duolingo 데이터) |
| B4 | **교정 표현 저장 및 복습** (Correction Bookmark) | H | 3 | `ChatMessage`에 bookmark 플래그 필드 추가만 필요 |

### Phase 3 — Personalization (중장기)

| # | 기능 | 임팩트 | 난이도 | 비고 |
|---|---|---|---|---|
| B5 | **대화 난이도/주제 설정** (레벨 셀렉터) | M | 2 | |
| C1 | **시나리오 롤플레이** (카페, 비즈니스 미팅 등) | H | 4 | |
| C2 | **AI 튜터 페르소나 선택** (이름, 스타일) | M | 4 | |
| C3 | **발음 점수 시각화** | M | 5 | STT 수준 개선 선행 필요 |
| C4 | **학습 목표 설정** (주간 목표 분 수) | M | 2 | |
| C5 | **다크 모드** | L | 2 | |

---

## 3. UI/UX 개선 포인트

### ChatScreen

- [ ] 교정 카드 우하단에 "저장하기" 버튼 추가 → B4(교정 복습)와 직접 연결
  - 기술 근거: `ChatMessage` 모델에 bookmark 플래그 필드 추가만으로 즉시 구현 가능
- [ ] 신규 대화 시작 시 Topic Starter 카드 3개 표시 → 탭 시 AI가 해당 주제로 대화 시작 (B1 구현)
- [ ] 세션 종료(뒤로가기) 시 "View Session Summary" 옵션 제공 (B2 연결)

### ConversationListScreen

- [ ] Empty state 강화: 일러스트(Vector Drawable) + FAB 펄스 애니메이션 1회
- [ ] 대화 아이템에 마지막 메시지 미리보기 1줄 추가 (말줄임 처리)
- [ ] 언어 모드 뱃지 추가 [EN] / [KR] → A2 구현 후 연동

### 온보딩 (신규)

- [ ] 첫 설치 시 4단계 온보딩 플로우
  - Step 1: 언어 모드 선택 (영어 배우기 / 한국어 배우기)
  - Step 2: 레벨 선택 (Beginner / Intermediate / Advanced)
  - Step 3: 학습 목표 설정 (5 / 10 / 15 / 30분+)
  - Step 4: 엔진 모드 안내 (온디바이스 / Gemini)

---

## 참고 문서

- UX 리서치 전문 (경쟁앱 분석, 트렌드, 포지셔닝 전략): [docs/ux-strategy-report.md](ux-strategy-report.md)
- 테스트 가능성 상세 분석: [.claude/agent-memory/android-test-engineer/testability-analysis.md](../.claude/agent-memory/android-test-engineer/testability-analysis.md)
