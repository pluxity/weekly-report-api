# chat v2 코드 가이드 — tool calling이 뭐고, 각 클래스가 뭘 하고, 어떤 사고를 어떻게 막았나

> 온보딩/학습용 문서. 계약이 바뀌면 갱신 대상은 `chat-v2-design.md`(설계 노트)이고,
> 이 문서는 "코드를 읽기 위한 지도"다. (작성: 2026-07-14, 대상 코드: `chat/v2` 패키지)

---

## 1. Tool calling이 뭔가

한 줄: **LLM이 도구를 실행하는 게 아니라, "이 함수를 이 인자로 불러달라"는 구조화된 요청(tool_calls)을 반환하면, 서버가 실행해서 결과를 되돌려주는 왕복 프로토콜.**

### 1-1. 왕복 한 사이클 ("내 태스크 보여줘")

```
[1] 서버 → LLM : system 프롬프트 + 히스토리 + user 메시지 + tools(도구 스키마 7개)
[2] LLM → 서버 : content 없음, tool_calls=[{name:"search_items", arguments:"{\"assignee_me\":true}"}]
[3] 서버       : ChatV2ToolExecutor가 taskService.search(...) 를 실제 실행
[4] 서버 → LLM : [1]의 메시지 전부 + [2]의 assistant 메시지 + role:"tool" 결과 JSON   ← 누적 재전송
[5] LLM → 서버 : content:"진행 중인 태스크가 3건 있어요: ..."  ← tool_calls 없음 = 루프 종료
```

- [2]는 **부탁이지 실행이 아니다.** 모델은 DB에 손댈 수 없다. 실행 주체는 항상 서버([3]).
- [2]~[4]가 반복될 수 있다(검색 → 없네 → 검색어 바꿔 재검색 → 상세 조회 → 답변). v2는 최대 8스텝.
- 매 스텝마다 [4]처럼 **메시지 누적분 + tools 스키마 전체가 다시 전송**된다. 스키마가 곧 반복 비용인 이유.

### 1-2. 와이어 포맷 (실제 JSON)

```jsonc
// [1] 요청
{ "model": "google/gemini-2.5-flash",
  "messages": [ {"role":"system","content":"...행동 규칙..."},
                {"role":"user","content":"내 태스크 보여줘"} ],
  "tools": [ {"type":"function","function":{
      "name":"search_items",
      "description":"태스크·업무 그룹·프로젝트·팀 통합 검색...",
      "parameters":{"type":"object","properties":{"assignee_me":{"type":"boolean"},...}}}}, ...6개 더 ] }

// [2] 응답 — arguments는 "JSON 문자열" (전용 필드!)
{ "choices": [{ "finish_reason": "tool_calls",
    "message": { "role":"assistant", "content": null,
      "tool_calls": [{ "id":"call_abc", "type":"function",
        "function": {"name":"search_items","arguments":"{\"assignee_me\":true}"} }] } }] }

// [4] 재요청에 추가되는 두 메시지
{"role":"assistant","tool_calls":[...]}                                      // 모델이 뭘 부탁했었는지
{"role":"tool","tool_call_id":"call_abc","content":"{\"tasks\":[...],\"totals\":...}"}  // 서버 실행 결과
```

`tool_call_id`로 부탁↔결과를 짝짓는다 (한 스텝에 tool_calls가 여러 개 올 수 있어서).

### 1-3. 스키마 vs 프롬프트 — 역할 분담

| | 역할 | v2에서의 위치 |
|---|---|---|
| `tools` 스키마 | **무엇이 존재하는가** — 함수명, 인자명/타입/enum. 프로바이더가 출력 형태를 유도 | `ChatV2Tools` |
| system 프롬프트 | **언제 어떻게 쓰는가** — 행동 규칙 ("되묻기 전에 검색", "'내'=assignee_me", "개수 질문은 aggregate_items") | `chat-v2-prompt.txt` |

[2]에서 모델이 `assignee_me:true`를 채운 것 = 스키마(그런 인자가 있다) + 프롬프트("내"→assignee_me)의 합작.
규칙을 스키마 description에 쓰지 않는 이유: **스키마는 스텝마다 재전송되는 반복 비용**이고 프롬프트는 한 번이다.

### 1-4. intent 분류는 어디 갔나

v1은 intent LLM이 먼저 `{"action":"read","target":"task"}`를 뽑고 서버가 분기했다.
tool calling에서는 "자연어 해석 → 무엇을 할지 결정"이 **도구 선택 + 인자 채우기 그 자체**로 흡수된다.
`search_items(assignee_me=true)`를 골랐다는 것이 곧 intent 분류 결과다. 그래서 v2에는 intent 단계·프롬프트가 없다.

### 1-5. 사촌: structured output

주간보고 생성처럼 **외부 조회가 필요 없는 단발 변환**은 루프가 낭비다(왕복·스키마 재전송만 늘어남).
이 shape에는 `response_format: json_schema` — "이 JSON 틀에 맞는 출력만 허용"을 **프로바이더가 강제**하는 기능 — 를 쓴다.
tool calling과 structured output은 같은 원칙의 두 얼굴이다:

> **LLM은 제안만 한다 (도구 호출 부탁 / JSON 틀 채우기). 확정(실행·검증·저장)은 전부 서버가 한다.**
> — "LLM proposes, server disposes"

---

## 2. 전체 구조 — 문이 2개

```
질문("내 태스크", "프로젝트별 진행률")     →  POST /chat/v2                 tool calling 루프 (멀티스텝 조회)
주간보고 붙여넣기("주간보고 작성해줘\n...")  →  POST /chat/v2/weekly-report   structured output (단발 생성)
```

FE가 입력 종류로 라우팅한다(서버에 라우터 없음). 태스크 생성/수정/삭제는 채팅이 안 한다 — 보드/폼 전담.

```
                    ┌─ 조회 ────────────────────────────────────────┐
ChatV2Controller ──►│ ChatV2Service (루프 반장)                      │
                    │   ├─ ChatV2LlmClient.call()        ← tools 전송 │
                    │   ├─ ChatV2Tools (도구 메뉴판 7개)              │
                    │   ├─ ChatV2ToolExecutor (실행 일꾼)             │
                    │   │    └─ 기존 도메인 Service들 (권한 공짜)      │
                    │   ├─ ChatV2IdRegistry (id 경비원, 턴마다 리셋)   │
                    │   └─ ChatV2HistoryStore (멀티턴 기억)           │
                    └───────────────────────────────────────────────┘
                    ┌─ 생성 ────────────────────────────────────────┐
                 ──►│ ChatV2WeeklyReportService (일직선 파이프라인)    │
                    │   ├─ ChatV2LlmClient.callStructured()          │
                    │   ├─ ChatV2WeeklyReportSchemas (JSON 틀)       │
                    │   └─ weeklyReportService.upsertFromClassify    │
                    └───────────────────────────────────────────────┘
                    공용: ChatV2UserLock (chat:lock:{userId} 직렬화), ChatLogService (chat_logs 기록)
```

---

## 3. 클래스별 — 뭘 하나 + 내부 동작

### ChatV2Service — 조회 루프의 반장

- `chat()`: 락 잡고 → 히스토리 로드 → `runLoop()` → chat_logs 기록 (성공/실패 무관 finally).
- `runLoop()`이 핵심. 의사코드:

```kotlin
messages = [system(프롬프트+오늘+사용자+역할)] + 히스토리 + [user(message)]
idRegistry = ChatV2IdRegistry(userId)          // ★ 턴마다 새로 만든다
repeat(8) {
    result = llmClient.call(messages, ChatV2Tools.ALL)
    if (result.toolCalls 없음) return 응답(reply=content, steps, 토큰)   // 대화 종료
    messages += assistant(tool_calls)
    for (call in toolCalls) {
        toolResult = toolExecutor.execute(call.name, call.arguments, userId, idRegistry)
        messages += tool(toolResult, call.id)   // 결과를 다음 스텝 입력으로
    }
}
return 응답(reply="단계가 너무 많아 멈췄어요...")   // 8스텝 초과: 예외 대신 graceful 안내
```

- 응답의 `steps[]` = 이번 턴의 (tool, arguments, result) 기록. 확률적 루프의 디버깅 창문.
- `cachedTokens`: 스텝 간 동일 프리픽스(프롬프트+스키마+누적 메시지)에 Gemini implicit caching이 걸리는지 실측용.

### ChatV2Tools — LLM에게 보여주는 메뉴판 (조회 7종)

`search_items`(통합 검색: 태스크·에픽·프로젝트·팀, 상태/담당/소속/기간 필터, sort/limit),
`get_item_details`, `aggregate_items`(그룹별 개수·평균 진행률), `search_users`,
`list_pending_reviews`, `get_task_history`(리뷰 이력·반려 사유), `get_weekly_report`(팀 리더 전용).

- 통합 검색 1개인 이유: 사용자는 "태스크/프로젝트"를 안 붙이고 이름만 말한다. 타입별 tool 3개면 모델이 찍어야 하고 왕복이 는다.
- description은 "무엇을 하는 도구인지"만 최소로 — 규칙은 프롬프트에 (1-3 참고).

### ChatV2ToolExecutor — 부탁을 실제 실행하는 일꾼

- `execute()` = `when(toolName)` 분기 + **catch 전부가 `{"error":"..."}` 반환으로 수렴** (예외를 안 터뜨림).
  에러를 모델에게 되돌리면 모델이 읽고 재검색/재시도로 자가 교정한다 — agent 패턴.
- 인자 파싱: `FAIL_ON_UNKNOWN_PROPERTIES` — 스키마에 없는 인자를 모델이 지어내면 **조용히 버리지 않고 실패**시킨다.
- 실행은 전부 기존 도메인 서비스 재사용 — `TaskService.search`의 권한 스코프, `findApprovalLogs`의
  `requireEpicAccess`가 공짜로 적용된다. 채팅용 권한 코드가 따로 없다.
- 결과 다이어트: 타입당 limit 캡(기본 10, 최대 30) + `totals`/`truncated`로 "잘렸음"을 모델에 명시.
  집계(`aggregate_items`)는 search 전체 결과를 서버가 groupBy — 숫자 계산을 LLM에 안 맡긴다.

### ChatV2IdRegistry — id 경비원 (40줄)

- 이번 턴에서 검색 결과로 나온 id만 `register()`. 필터(project_id/epic_id/assignee_id)·상세·이력 인자로
  들어온 id가 `isKnown()`이 아니면 실행 전에 거부 → 에러가 모델에게 돌아가 재검색 유도.
- 히스토리에 id를 안 남기므로 멀티턴은 턴마다 재검색이 강제된다 — 의도된 비용 (v1은 히스토리의 id를 믿다가 사고).

### ChatV2LlmClient — 전화기 (OpenRouter 전용)

- `call(messages, tools)`: tool calling용. 응답에서 message/usage/cached_tokens 추출.
- `callStructured(messages, schemaName, schema)`: `response_format: {type:"json_schema", json_schema:{name, strict:true, schema}}` 를 실어 보냄. content(JSON 문자열)를 그대로 반환 — fence 전처리 없음.
- Gemini/Ollama 폴백 없음. 이 경로는 OpenRouter 락을 감수한다 (결정 문서 §7).

### ChatV2HistoryStore — 멀티턴 기억 (조회 전용)

- Redis `chatv2:history:{userId}`에 **user/assistant 텍스트만** 12개, 24h TTL. tool 호출 내역·id는 저장 안 함.
- v1과의 차이: v1은 턴 요약에 id까지 남겨 intent가 이어받았다("그거 삭제해줘"→id=13). v2는 id를 안 남겨
  IdRegistry와 함께 "이번 턴 검색으로 확인한 id만 믿는" 체계를 완성한다.

### ChatV2UserLock — 동시요청 직렬화

- `chat:lock:{userId}` 30s. **v1과 같은 키**라 v1/v2 조회/생성이 한 유저 안에서 서로 직렬화된다.
- 해제는 release-lock.lua로 값 비교 후 삭제 (만료 뒤 남의 락을 지우는 것 방지). 중복이면 429.

### ChatV2WeeklyReportService — 생성 파이프라인 (일직선)

```
[검문1] 팀 리더인가?              아니면 → reply 안내 (200, weeklyReport=null)
[검문2] 본문 유효 줄 2개 이상?     아니면 → 본문 안내       ← LLM 호출 전 차단 (환각 방지 + 토큰 절약)
[LLM1]  classify: callStructured(단일 팀 스키마) → WeeklyReportClassifyResult로 역직렬화
[검문3] 4개 섹션 전부 비었나?      비었으면 → 안내
[LLM2]  match: 지난주 nextWeek ↔ 이번주 thisWeek 짝짓기. try-catch로 감싼 best-effort —
        실패해도 저장은 진행. LLM은 P/C 번호쌍만 반환, 항목 복원·1:1 보장·missing/new 계산은
        서버(numberItems/enrichMatched 재사용)
[저장]  weeklyReportService.upsertFromClassify — 같은 팀+주차면 덮어쓰기, 주차는 월요일로 정규화 (서버 소유)
```

- 사용자 실수는 예외(400)가 아니라 **안내 reply(200)** — FE는 항상 reply만 렌더하면 된다.
- 히스토리 미기록 (조회 턴만 기록). chat_logs에는 classify+match 토큰 합산 기록.

### ChatV2WeeklyReportSchemas — 생성용 JSON 틀

- `CLASSIFY`: 최상위 **단일 팀 오브젝트** + 섹션 4개·항목 필드 전부 required + `additionalProperties:false`.
- 조인 것: 구조 (팀 수·섹션·필드 존재). 푼 것: 값 (category·progress는 자유 문자열 — 카테고리는
  사업/프로젝트명이라 고정 집합이 없고, progress는 "완료"/"지연 대기 중" 원문 보존 계약 때문).
  → "필수 뼈대만 스키마로, 유연성은 프롬프트로" (스키마가 너무 조이면 지저분한 입력에서 모델이 스키마와 싸운다).

### dto/

- `ChatV2ApiDto`: OpenAI 호환 wire DTO (snake_case). `ToolMessage`(tool_calls/tool_call_id),
  `ResponseFormat`/`JsonSchemaSpec`, 요청/응답 DTO. arguments가 "JSON 문자열"인 것에 주의.
- `ChatV2ToolArgs`: tool arguments 역직렬화 6종. 필수 누락·미지 인자 → 실패 → 에러로 재시도 유도.

### 프롬프트 2개 (resources/llm/)

- `chat-v2-prompt.txt` (조회, ~22줄): 조회 전용 선언, CUD는 보드/폼 안내, 검색 우선(되묻기 전에),
  id·인자 추측 시 서버 거부 예고, 집계는 aggregate_items, totals/truncated 안내. 런타임에 오늘 날짜+사용자+역할 주입.
- `chat-v2-weekly-report-prompt.txt` (생성 classify): 원문 보존, 단일 팀, 섹션 분류 규칙, 지어내기 금지.
  "JSON만 출력/마크다운 금지" 류 지시가 **없다** — 스키마가 강제하므로 불필요. 런타임에 오늘+요청자 팀명 주입.

---

## 4. 어떤 문제를 어떻게 해결했나 (사고 → 장치 매핑)

전부 실제 발생했던 사고다. v1의 구조 비용("텍스트로 JSON을 받는다")이 원인이었고, v2 장치는 각각을 구조적으로 막는다.

| # | 실제 사고 (v1) | 원인 | v2 장치 |
|---|---|---|---|
| 1 | 7/12 주간보고 파싱 실패 — 모델이 ```json 블록 3개를 이어 붙여 반환, `stripCodeFence`가 내부 백틱에서 사망, 재시도조차 안 됨 | content 자유 텍스트에서 JSON 긁기 | **structured output** — 스키마를 프로바이더가 강제, fence/다중블록이 출력 자체에서 불가능. 조회 쪽은 tool_calls의 **전용 arguments 필드**로 동일 효과 |
| 2 | 7/12 범위 이탈 환각 — 경영기획팀 1인 요청인데 3개 팀 보고를 멋대로 생성 | 출력 형태를 프롬프트로만 제약 | classify 스키마 **최상위 = 단일 팀 오브젝트** (배열 아님). 3팀짜리 출력이 스키마 위반이라 생성 불가. 실호출 검증: 3팀 입력 → 요청자 팀만 반환 |
| 3 | id 추측 사고 2건 — epic_id=1 찍어서 엉뚱한 곳에 생성(CUD 시절) / project_id=3 찍어 빈 검색 → "없다" 거짓 부정 | 프롬프트 "id 추측 금지"는 안 지켜짐 | **ChatV2IdRegistry** — 이번 턴 검색 결과의 id만 허용, 위반은 실행 전 거부 후 에러 반환 → 모델이 재검색으로 자가 교정 (서버측 강제) |
| 4 | 발명 인자 환각 — search_items에 없던 assignee_id를 지어냄 → ignoreUnknown이 조용히 버림 → 무필터 전체 목록을 "특정인 업무"로 포장해 답변 | 관대한 역직렬화가 오류를 은폐 | **FAIL_ON_UNKNOWN_PROPERTIES** — 미지 인자는 실패시키고 "사용 가능한 인자 목록"을 에러로 반환 → 재시도 유도 |
| 5 | context stuffing 비용 — DB 전체 스냅샷을 매 턴 주입, 턴당 ~7.2k 토큰, 데이터 증가에 비례 | "모델이 다 알아야 답한다"는 구조 | 모델이 **필요한 것만 검색** (tool calling). 의미 매칭 비용을 "항상 지불"에서 "필요할 때만"으로. 서버 토큰 매칭(ItemNameMatcher) + 모델 변형 재검색("세이퍼스"→"safers") 2단 |
| 6 | 계층 오인 — "safers 완료 처리"에서 프로젝트를 태스크로 착각 | 타입별 검색을 모델이 선택 | **통합 검색 1개** — 3계층을 한 번에 뒤지고 결과의 타입을 보고 판단 |
| 7 | 집계 오답 위험 — 개수·평균을 모델이 셈 | 숫자를 LLM에 맡김 | **aggregate_items** — 서버가 전체 결과를 groupBy, 모델은 결과 숫자를 전달만 |
| 8 | 루프/동시성 운영 리스크 | — | MAX_STEPS=8 초과 시 graceful 안내(예외 아님), `chat:lock:{userId}` 유저당 직렬화, 실패 tool 결과도 응답 `steps[]`에 남아 디버깅 가능 |

---

## 5. v1 → v2 — 뭐가 어떻게 변했나

### 5-1. 파이프라인 비교

```
v1 (/chat) — 항상 고정 2단 LLM:
  user 메시지
   → [LLM 1] intent 분류 {action, target, id?}          ← 히스토리(턴 요약+id)로 후속 지칭 해소
   → 서버가 target별 DB 전체 스냅샷 생성 (context stuffing)
   → [LLM 2] 스냅샷+메시지 → JSON 액션 텍스트 생성
   → stripCodeFence로 코드펜스 벗기고 JSON 파싱          ← 여기가 상습 사고 지점
   → ChatActionRouter가 실행 / clarify·select는 예외로 FE 전달

v2 조회 (/chat/v2) — 가변 N단 (스텝 수를 모델이 결정):
  user 메시지
   → [LLM ①] tools 보고 도구 선택 (intent 분류가 여기 흡수됨)
   → 서버 실행 (권한·id 검증) → 결과 첨부 → [LLM ②] ... 반복 (≤8)
   → content 나오면 그게 답. 파싱할 JSON 자체가 없음 (arguments는 전용 필드)

v2 생성 (/chat/v2/weekly-report) — 고정 1~2단:
  본문 → [LLM 1] classify (json_schema 강제) → [LLM 2] match (best-effort) → upsert
```

### 5-2. 항목별 변화

| | v1 | v2 |
|---|---|---|
| 의도 파악 | 별도 intent LLM + 키워드 규칙 프롬프트 (~130줄) | 없음 — 도구 선택 자체가 intent |
| 모델에 주는 데이터 | DB 전체 스냅샷 선주입 (안 쓸 것까지) | 모델이 필요한 것만 검색해서 받음 |
| 출력 수신 | content 자유 텍스트에서 JSON 추출 | tool_calls 전용 필드 / json_schema 강제 |
| 권한 | ContextBuilder가 스냅샷 생성 시 별도 체크 | 기존 도메인 서비스 재사용 — 검색 스코프·requireEpicAccess 공짜 |
| id 신뢰 | 히스토리 턴 요약의 id를 intent가 이어받음 | 이번 턴 검색 결과의 id만 (IdRegistry), 히스토리에 id 안 남김 |
| 모호할 때 | clarify/select **예외**(400) → 별도 resolve API로 후속 | 모델이 검색 먼저 해보고 자연어로 되물음 — 같은 엔드포인트에서 대화로 해소 |
| 사용자 실수 (생성) | ChatClarifyException(400) | 안내 reply(200, weeklyReport=null) |
| 실패 시 | 파싱 실패 = CustomException 즉시 전파 (재시도 없음, 7/12) | 조회: 에러를 모델에 되돌려 자가 교정. 생성: 파싱 실패 모드 자체가 소멸 |
| 프롬프트 | system-prompt ~250줄 + intent ~130줄 | 조회 ~22줄 + 생성 classify 1장 |
| FE 응답 계약 | ChatActionResponse (nullable union) + clarify/resolve 계약 | 조회 {reply, steps[]}, 생성 {reply, weeklyReport} — 항상 reply만 렌더하면 됨 |
| 폴백 | OpenRouter→Gemini→Ollama 체인 | 없음 (OpenRouter 고정) |

### 5-3. 트레이드오프 — 얻은 것 vs 내준 것

**얻은 것**
- **사고 모드의 구조적 제거** — §4의 1~7이 프롬프트 설득이 아니라 스키마/서버 강제로 막힘. "같은 입력이 어제는 실패, 오늘은 성공"류의 비결정성 사고가 계약 위반으로 바뀜.
- **토큰이 데이터 크기와 분리** — v1은 DB가 크면 스냅샷도 커짐(첫 실측 턴당 ~7.2k, 증가 추세). v2는 검색 결과만 오감(같은 시나리오 2.1k, -70%). 데이터가 늘어도 턴 비용이 그대로.
- **프롬프트 유지보수 축소** — 규칙 380줄 → 22줄. 기능 추가가 "프롬프트 규칙 추가"가 아니라 "tool 추가"가 됨.
- **멀티스텝 능력** — "검색 → 없네 → 변형 재검색 → 상세 조회 → 답"이 한 턴에서. v1은 구조상 불가능(스냅샷에 있는 것만).

**내준 것**
- **스텝당 스키마 재전송 비용** — tools는 매 스텝 다시 감. tool 15개 시절 실측: 스텝당 ~4k가 바닥, 3스텝 턴 14.7k로 **v1의 2배**까지 역전됐던 적 있음. 대응이 7-tool 다이어트 + description 최소화이고, 스텝 간 동일 프리픽스에 implicit caching(75% 할인)이 걸리는지 `cachedTokens`로 실측 중. 스텝별 tool 동적 축소는 비채택 — 캐시 프리픽스를 깨고 intent 분류를 도로 수입하게 됨.
- **레이턴시 가변** — v1은 항상 LLM 2회, v2 조회는 1~8회. 복잡한 질문은 더 느리고, 스트리밍(SSE) 없인 체감이 나쁠 수 있음 (미결).
- **provider 락** — tools/json_schema에 기대므로 OpenRouter 고정. v1의 Gemini/Ollama 폴백 체인 포기. 장애 시 대안 없음 (미결: 폴백 체계).
- **멀티턴 재검색 비용** — 히스토리에 id를 안 남기는 대가로, "아까 그거"도 다음 턴에선 재검색. 안전(id 신선도)과 맞바꾼 의도된 비용.
- **비결정성의 이동** — 파싱은 안 깨지지만 "어떤 도구를 몇 번 부를지"는 여전히 확률적. 그래서 `steps[]` trace·E2E 시나리오가 회귀 수단 (promptfoo 같은 텍스트 회귀가 안 맞음).
- **파싱 코드 완전 소멸은 아님** — arguments/structured output도 결국 JSON 문자열. fence류 실패 모드만 사라진 것.

---

## 6. 빠른 파악용 읽기 순서

1. `ChatV2Service.runLoop()` — 루프 (심장)
2. `ChatV2Tools` — 상단 주석 + search_items 정의만
3. `ChatV2ToolExecutor.execute()` + `searchItems()` — 에러 수렴 패턴, validateKnown, totals/truncated
4. `ChatV2IdRegistry` — 전체 (1분)
5. `chat-v2-prompt.txt` — 규칙이 코드 가드레일과 1:1 대응하는지 보며
6. `ChatV2WeeklyReportService.process()` — 생성 파이프라인 (직선)
7. `ChatV2WeeklyReportSchemas` — 주석 + CLASSIFY 구조

배경 결정이 궁금해지면 그때 `chat-v2-tool-calling-and-structured-output.md`(결정 문서),
진행 상황·미결은 `chat-v2-design.md`(설계 노트).
