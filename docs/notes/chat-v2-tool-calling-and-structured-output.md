# chat v2 — tool calling 채택 & structured output 도입 결정

> 브랜치 `feature/chat-v2-tool-calling-poc`의 살아있는 결정 문서.
> 배경/트레이드오프 상세는 `chat-tool-calling-tradeoff.md`, 진행 노트는 `chat-v2-design.md` 참고.

## 1. 문제 — v1의 구조적 비용

기존 `/chat`(v1)은 **intent LLM → DB 전체 스냅샷 주입(context stuffing) → content에서 JSON 텍스트 추출** 구조다.
v0.6.x에서 answer 액션·자연어 message·not-found 안내 등으로 개선했지만, 파싱 실패·환각·매칭 오류는
"**텍스트로 JSON을 받는다**"는 구조 자체의 비용이라 한계가 있다.

### 실패 사례 (2026-07-12 주간보고 생성)

같은 요청이 7/12에는 실패하고 7/13에는 성공했다. 원인은 두 겹이었다.

1. **다중 코드펜스 → 파싱 실패.** classify LLM이 팀별로 ```` ```json ... ``` ```` 블록 3개를 이어붙여 반환했고,
   `stripCodeFence`(단일 블록 가정) + 단일 `readValue`가 내부 백틱(`` ` ``)에서 죽었다
   (`Unexpected character ('`')`). 게다가 이 파싱 실패는 `CustomException`으로 **즉시 전파돼 재시도조차 안 됐다**
   (`callWithRetry`는 CustomException을 재던짐 — 3회 재시도는 네트워크 예외에만 적용).
2. **범위 이탈 환각.** 요청자(경영기획팀 1인)의 범위를 넘어 3개 팀 보고를 멋대로 생성했다.

7/13 성공은 순전히 생성 비결정성 — 같은 입력, 다른 출력. 이게 자유 텍스트에서 JSON을 긁어내는 구조의 본질이다.

## 2. 핵심 원칙 — LLM proposes, server disposes

> **LLM은 경계에서 자연어를 번역만 하고, 상태·진실은 서버가 소유한다.**

| LLM이 잘하는 것 (경계 번역) | 서버가 잘하는 것 (진실·상태) |
|---|---|
| 지저분한 입력 → 필드 추출/정규화 | 스키마·enum·필수값·단일 팀 강제 |
| 자연어 질문 → 검색 필터 선택 | 실제 검색·스코프/권한 적용 |
| 의미 매칭(지난주↔이번주) | id 소유·upsert·유일성·참조 무결성 |
| 애매한 지칭 해소 | 집계·카운트·평균 (숫자는 LLM에 안 맡김) |

v1의 사고는 전부 이 선을 넘어서 났다 — id 추측(무결성을 LLM에), context stuffing(스코프를 프롬프트에),
JSON-from-text(검증을 LLM 성실성에). **tool calling과 structured output은 이 원칙의 두 얼굴이다.**

- tool calling = LLM이 "뭘 조회할지" 제안 → 서버가 실제 조회
- structured output = LLM이 "이런 모양"을 채움 → 서버가 검증·저장

## 3. 조회 = tool calling 채택 이유

조회("내 태스크", "프로젝트별 진행률", "지연된 것")는 **모델이 무엇을 검색할지 스스로 정하고 결과를 보고 이어가는
multi-step** 작업이다. 이 shape에는 tool calling 루프가 맞는다.

- **파싱 실패 소멸** — tool_calls의 arguments는 전용 필드로 오고, 결과는 서버가 실행한다.
  content에서 JSON을 긁던 실패 모드가 사라진다.
- **context stuffing 폐기** — 서버가 기존 서비스로 실제 검색·스코프·권한을 실행한다(권한 공짜).
- **가드레일** — 이번 턴 검색 결과의 id만 필터/상세 인자에 허용(id 추측 차단), 스키마에 없는 인자는 거부
  (`FAIL_ON_UNKNOWN_PROPERTIES` — 발명 인자로 무필터 목록을 특정인 업무로 포장하던 환각 차단).
- **집계는 서버가** — 숫자(개수·평균 진행률)는 LLM에 맡기지 않고 서버가 groupBy로 계산한다.

## 4. 생성 = structured output 도입 이유

주간보고 작성(붙여넣기 → 정해진 포맷으로 정리)은 **외부 조회가 없는 single-shot 추출**이다.
이 shape에는 tool 루프가 아니라 structured output(`response_format: json_schema`)이 맞는다.

- **파싱 실패 원천 제거** — 스키마 강제로 fence/백틱/다중블록 실패 모드가 사라진다.
  7/12의 1차 원인(재시도조차 안 되던 하드 실패)을 직격한다.
- **스키마 = 계약** — `FormattedReport` 모양·`category` enum·`progress` 포맷을 프로바이더가 강제한다.
  **단일 팀으로 조여 7/12의 2차 문제(3팀 환각)까지 차단**한다.
- **프롬프트 다이어트** — "JSON만 출력/마크다운 금지" 류 지시와 `stripCodeFence` 전처리를 걷어낸다.
- **왜 tool 루프가 아닌가** — 붙여넣기 포맷팅은 단발이라 루프는 왕복·지연·비용만 늘고 얻는 게 없다.
  "3팀 환각"이나 "지난주 매칭"은 루프로 안 풀리고 스키마/기존 match 단계로 푼다.

## 5. 왜 메커니즘이 둘인가 (일관성)

조회와 생성은 태스크 shape가 실제로 다르다(multi-step vs single-shot). 그래서 메커니즘도 다르다.
하지만 둘 다 **"LLM 제안 / 서버 확정"의 사례**라 원칙은 하나로 수렴한다 — 억지로 하나로 합치는 것보다
각자 단순하다. (생성을 v2 루프 안의 write tool로 넣지 **않는** 이유이기도 하다: CUD를 v2에서 뺀 근거
— id 추측 사고, 스키마가 스텝마다 곱해지는 비용 — 를 그대로 지키려면 생성은 루프 밖 전용 호출이어야 한다.)

## 6. 구현 결정 (2026-07-13)

| 항목 | 결정 |
|---|---|
| v2 범위 | 조회 + 주간보고 생성 (태스크 CUD·주간보고 외 작성은 보드/폼) |
| v1(/chat) | 당분간 병존 → v2 생성 신설 후 v1 생성 폐기 수순 |
| 라우팅 | 생성/조회 **분리 엔드포인트** (FE가 "붙여넣기" vs "질문" 구분, 서버 라우터 불필요) |
| provider | OpenRouter 유지(OpenAI-wire), 생성은 `response_format: json_schema` |
| 모델 | **gemini-2.5-flash** — structured output 지원 확인 (enum·required·format·nested subset) |
| 생성 위치 | v2 패키지에 신설, `upsert`/도메인 서비스는 재사용 |
| 매칭(지난주) | 유지 + json_schema |
| classify 스키마 | 강하게 — 단일 팀 강제 + category enum + progress 포맷 (지저분한 입력 대비는 프롬프트) |
| 조회 tool | 기존 6종 + 주간보고 조회 1종 = 7종 (`ChatV2IdRegistry` id 추측 가드레일 유지) |
| 주간보고 조회/삭제 | 조회는 조회 tool로 편입(팀 리더 게이트), 삭제는 폼/보드 |
| 히스토리 | `chatv2:history` **조회 턴만** 기록 (24h TTL, user/assistant 12개) |
| FE 조회 계약 | **단순 텍스트(자연어 reply만)** — PoC. 구조화 블록은 후속 |
| 스트리밍 / 락 | non-streaming 유지 / `chat:lock:{userId}` 유저당 직렬화 유지 |
| 테스트 | 단위(DTO·matcher) + structured output **생성 E2E**, 조회는 수동 검증 |

## 7. 트레이드오프 / 남은 리스크

- **은탄환 아님** — arguments·structured output도 결국 JSON 문자열이라 파싱 코드는 남는다.
  다만 fence류 실패는 없어진다.
- **structured output 경로** — 모델은 gemini-2.5-flash로, 구조화 출력(enum·required·format subset)을 지원한다.
  ~~다만 OpenRouter 경유 `response_format: json_schema`가 Gemini로 제대로 전달되는지 확인이 필요하며~~
  → **2026-07-13 실호출 검증 완료**: strict json_schema($ref/$defs·nullable union·additionalProperties:false 포함)가
  그대로 수용됐고, 3팀 혼합 입력에서 단일 팀 오브젝트만 반환(코드펜스 없음). Gemini 네이티브 폴백 불필요 판단, 미구현.
  스키마는 깊게 중첩하지 말 것(Gemini는 과대·과중첩 스키마를 거부할 수 있음).
- **provider 락** — 이 경로는 OpenRouter(structured output/tools) 고정을 감수한다.
  v1의 Gemini/Ollama 폴백은 이 경로에서 못 쓴다.
- **스키마 경직 vs 지저분한 입력** — strict 스키마는 엣지 입력에서 모델이 스키마와 싸울 수 있다.
  필수 뼈대만 스키마로, 나머지 유연성은 프롬프트로 남긴다.
