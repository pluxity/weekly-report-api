# chat v2 구현 스펙 (Fable 이관용)

> 이 문서는 chat v2를 **한 번에 구현**하기 위한 스펙이다. 목표와 제약을 명시하고, 단계별
> 마이크로 지시는 지양한다 — 구현 방식(클래스 분리, 함수 시그니처 등)은 기존 코드 컨벤션을
> 따라 재량껏 정하라.
> 근거·트레이드오프는 `chat-v2-tool-calling-and-structured-output.md`(결정 문서),
> 진행 노트는 `chat-v2-design.md`, 배경은 `chat-tool-calling-tradeoff.md` 참조.

## 0. 원칙

> **LLM은 경계에서 자연어를 번역만 하고, 상태·진실은 서버가 소유한다.**

- 조회 = LLM이 "뭘 조회할지" 제안 → 서버가 실행 (tool calling 루프)
- 생성 = LLM이 "이런 모양"을 채움 → 서버가 검증·저장 (structured output)
- id·집계·권한·무결성은 절대 LLM에 맡기지 않는다.

## 1. 범위

**포함**
- 조회: 태스크 검색·집계·상세·리뷰·이력·유저 조회, 주간보고 조회
- 생성: 주간보고 작성 (붙여넣기 → 정해진 포맷으로 정리 후 저장)

**비범위 (건드리지 않음 / 보드·폼으로)**
- 태스크 CUD(생성·수정·삭제·완료·할당), 주간보고 **삭제**, 주간보고 외 작성
- 스트리밍(SSE), 조회 경로의 Ollama/Gemini 폴백, 다중 팀 리더 동시 생성

## 2. 결정 매트릭스 (확정 계약)

| 항목 | 결정 |
|---|---|
| 라우팅 | 생성/조회 **분리 엔드포인트** (FE가 "붙여넣기" vs "질문" 구분, 서버 라우터 없음) |
| provider | **OpenRouter** 유지 (OpenAI-wire) |
| 모델 | **gemini-2.5-flash** |
| 조회 메커니즘 | tool calling 루프 (기존 `ChatV2Service` 발전), MAX_STEPS=8 |
| 생성 메커니즘 | structured output — OpenRouter `response_format: json_schema` (폴백: Gemini 네이티브 `responseSchema`) |
| 생성 위치 | v2 패키지에 신설, `upsertFromClassify`·도메인 서비스 재사용 |
| classify 스키마 | 강하게 — **단일 팀 오브젝트(배열 아님)** + category enum + progress 포맷 |
| 매칭(지난주) | 유지 + structured output |
| id 가드레일 | `ChatV2IdRegistry` 유지 |
| 조회 tool | 기존 6종 + 주간보고 조회 1종 = **7종** |
| 히스토리 | `chatv2:history` **조회 턴만** (24h TTL, 12개) |
| FE 조회 응답 | **단순 텍스트(자연어 `reply`)** — PoC |
| 스트리밍 / 락 | non-streaming / `chat:lock:{userId}` 유저당 직렬화 유지 |
| v1(/chat) | 당분간 병존 → v2 생성 나가면 v1 주간보고 생성 폐기 수순 |
| 테스트 | 단위(DTO·matcher) + 생성 E2E, 조회는 수동 |

## 3. 엔드포인트

### 3.1 조회 — `POST /chat/v2` (기존 유지·발전)

- 요청: `{ message }`
- 처리: tool calling 루프 (`tool_calls` 나오는 동안 실행→결과 첨부→재호출, content 나오면 종료).
  `MAX_STEPS=8`. `chat:lock:{userId}` 30s 락으로 유저당 직렬화. JWT 인증.
- 응답: `{ reply, steps[], inputTokens, outputTokens, cachedTokens }`
  - `reply`: 자연어 텍스트 (PoC — 구조화 블록 없음)
  - `steps`: tool 실행 trace
- 히스토리: `chatv2:history:{userId}` 에 조회 턴만 기록 (user/assistant 텍스트 12개, 24h TTL)

### 3.2 생성 — `POST /chat/v2/weekly-report` (신설)

- 요청: `{ message }` (명령 + 주간보고 본문 붙여넣기)
- 처리: 본문 검증 → classify(structured output) → match(structured output, best-effort) → upsert.
  **팀 리더만** 작성. tool 루프 아님(single-shot). 히스토리 미기록.
- 응답: `{ reply, weeklyReport, inputTokens, outputTokens }`
- FE가 "주간보고 붙여넣기" 입력을 이 엔드포인트로 보낸다 (조회와 명시적 분리).

## 4. 생성 파이프라인 (structured output)

**모델·전송**: gemini-2.5-flash. OpenRouter `response_format: { type: "json_schema", ... }`.
OpenRouter→Gemini 전달이 불안하면 Gemini 네이티브(`responseMimeType: "application/json"` +
`responseSchema`) — v1 `LlmService.callGemini` 에 이미 있는 transport를 재사용해 폴백.

**classify 스키마 (`FormattedReport`)**
- 최상위는 **단일 팀 오브젝트**(팀 배열 아님). → 요청자 범위를 넘는 "3팀 동시 생성" 환각을 구조적으로 차단.
- `category`: **enum** (기존 카테고리 집합).
- `progress`: 포맷 고정 (예: `"\d+%"` 문자열 또는 null).
- `thisWeek / nextWeek / issues / others`: 각 항목 배열, 항목 필드는 기존 `FormattedReport` 계약을 따른다.
- 스키마는 **얕게** 유지 (Gemini는 과대·과중첩 스키마를 거부할 수 있음).
- 지저분한 입력 대비(누락 필드 추론 등)는 스키마가 아니라 **프롬프트**로 처리.

**검증 (LLM 호출 전/후)**
- 본문 유효 줄 2개 미만 → 안내 반환 (기존 `requireReportBody` 규칙).
- classify 결과 항목 0 → 안내 반환 (기존 `requireClassifiedItems` 규칙).

**매칭·저장**
- match: 지난주 `nextWeek` ↔ 이번주 `thisWeek` 의미 매칭. structured output. **best-effort**(실패해도 저장 진행).
  매칭 결과에 붙는 id는 서버 소유(`enrichMatched` 계약 유지).
- upsert: `weeklyReportService.upsertFromClassify` 재사용. id·트랜잭션은 서버 소유.

**파싱**: json_schema/responseSchema라 fence·백틱·다중블록 실패가 없다.
생성 경로에서 `decodeJson`/`stripCodeFence` 류 텍스트 추출은 쓰지 않는다.

## 5. 조회 tool (7종) + 가드레일

**tool 목록**
1. `search_items` — 통합 검색 (+team, 상태/담당(me·id)/소속/기간/exclude_done, sort/limit)
2. `get_item_details`
3. `aggregate_items` — 그룹별 개수·평균 진행률
4. `search_users`
5. `list_pending_reviews`
6. `get_task_history` — 리뷰 이력·반려 사유
7. `get_weekly_report` — **신규**. 주간보고 조회 (week 기준, **팀 리더 게이트** — v1 `handleRead` 규칙 재사용)

**규칙**
- 가드레일(`ChatV2IdRegistry`): 이번 턴 검색 결과의 id만 필터(project_id/epic_id/assignee_id)·상세·이력 인자에
  허용. 추측 id는 error로 재검색 유도. 히스토리에 id를 안 남기므로 멀티턴은 턴마다 재검색(의도된 비용).
- 인자 검증: `FAIL_ON_UNKNOWN_PROPERTIES` — 스키마에 없는 인자(발명 인자)는 실패 → error로 재시도 유도.
- 실행부: 기존 서비스 재사용(search 스코프·권한 공짜). 실패는 `{"error":...}` 반환(agent 패턴).
  타입당 limit 캡(기본 10, 최대 30) + totals/truncated.
- 집계는 서버 인메모리 groupBy (숫자는 LLM에 맡기지 않음).
- 스키마 비용: 규칙은 프롬프트에, tool description은 최소로 (스텝마다 곱해지는 비용).

## 6. 재사용 / 불변 / 폐기

**재사용 (이미 옳은 분리)**
- 도메인 서비스: search 스코프, `weeklyReportService.upsertFromClassify`, `findPrevWeekNextItems`,
  approval `findApprovalLogs`(requireEpicAccess 권한) 등
- `matchAgainstPrev` 골격·`enrichMatched`, `ChatV2IdRegistry`, `ItemNameMatcher`
- OpenAI tool calling wire DTO(`ChatV2ApiDto`, cached_tokens 포함), `chatv2:history` 스토어, chat_logs 기록

**건드리지 말 것**
- `report`/`search`/approval 도메인 서비스 계약 — v2는 그 위의 "채팅 경계 계층"일 뿐.

**폐기 (생성 경로)**
- context stuffing(DB 스냅샷 프롬프트 주입), content JSON 추출(`decodeJson`/`stripCodeFence`).

**v1**: 병존 유지. v2 주간보고 생성이 나가면 v1의 주간보고 생성 브랜치는 폐기 수순.

## 7. 프롬프트

- 조회: 기존 `llm/chat-v2-prompt.txt` 유지·발전 — 조회 전용 선언, CUD는 보드/폼 안내, 검색 우선,
  id·인자 추측 시 서버 거부, 집계는 `aggregate_items`, totals/truncated 안내.
- 생성(classify): 붙여넣기 본문에서 `FormattedReport` 추출. **단일 팀**, category/진행률 규칙,
  스키마에 없는 것 발명 금지. structured output이라 "JSON만 출력/마크다운 금지" 지시는 불필요.

## 8. 비기능

- non-streaming. 동시요청 락 `chat:lock:{userId}` 유지. MAX_STEPS 초과 시 graceful 안내.
- `cachedTokens`(implicit caching 실할인) 로깅 유지.

## 9. 테스트

- 단위(유지): `ChatV2ApiDtoTest`(wire + cached_tokens), `ItemNameMatcherTest`.
- 신규 E2E(생성): 스키마 강제로 파싱 실패 없음 / **3팀 입력 → 단일 팀만 저장**(범위 이탈 차단) /
  매칭 동작 / 본문 없음·항목 0 → 안내.
- 조회는 수동 검증 (설계 노트 시나리오: not-found 재검색, 동명 후보, "내 태스크", 팀 조회,
  id 추측→레지스트리 거부→자가 교정, 집계 질문, CUD 요청→보드/폼 안내).

## 10. 착수 전 확인 2건

1. **OpenRouter → gemini-2.5-flash 의 `response_format: json_schema` 전달** 확인.
   미지원/불안정 시 Gemini 네이티브 `responseSchema`(v1 `callGemini` transport)로 폴백.
   → **확인 완료 (2026-07-13 실호출)**: strict json_schema 그대로 수용, 3팀 입력→단일 팀만 반환, 코드펜스 없음. 폴백 미구현.
2. **FE 라우팅 계약** — "붙여넣기"(→ `/chat/v2/weekly-report`) vs "질문"(→ `/chat/v2`) 구분 방식.
   → FE 논의 필요 (백엔드는 분리 엔드포인트로 구현 완료).
