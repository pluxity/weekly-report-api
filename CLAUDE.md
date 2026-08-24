# chat-v2 worktree — tool calling PoC 전용

> ⚠️ 이 CLAUDE.md는 세션 컨텍스트 인계용 로컬 파일이다. **커밋 금지.**

## 이 폴더의 정체

- `weekly-report-api`의 **git worktree** (본 저장소: `C:\Users\PLX-PC-N002\weekly-report-api`, 그쪽은 develop에서 지연일 리팩토링 진행 중)
- 브랜치: `feature/chat-v2-tool-calling-poc` (develop의 f3fbdd2에서 분기, PoC 커밋 8ba50be)
- 이 세션의 역할: **`/chat/v2` tool calling 루프 PoC 검증·발전**. 기존 `/chat` 파이프라인은 건드리지 않는다.
- **방향 결정 (2026-07-07)**: v2는 **조회 전용 채팅으로 v1을 대체**한다. CUD(생성/수정/삭제/완료/할당)는 보드·폼(웹)에서. 오전에 CUD 포함 15-tool을 이식했다가 같은 날 조회 전용 6-tool로 재편 (근거: id 추측 사고 2건 + 스키마 비용 + 폼 확정 UX 논의 소멸 — 설계 노트 참고)
- **방향 확장 (2026-07-13)**: 주간보고 **작성**만 v2로 복귀 — 단 tool 루프가 아니라 **별도 엔드포인트 + structured output**(`response_format: json_schema`). 조회 tool은 7종(get_weekly_report 추가). 스펙: `docs/notes/chat-v2-implementation-spec.md`, 결정 근거: `chat-v2-tool-calling-and-structured-output.md`. **구현 완료, E2E 미검증** (아래)

## 배경 (요약)

기존 `/chat`은 intent LLM → DB 전체 스냅샷 주입(context stuffing) → JSON 텍스트 추출 구조. 2026-07 v0.6.x에서 answer 액션·자연어 message·not-found 안내·target 정정 등으로 개선했지만(develop a14b0cb, 3abef68, f3fbdd2), 파싱 실패·환각·매칭 오류는 "텍스트로 JSON을 받는" 구조 비용이라 한계가 있음. 그 대안으로 tool calling(모델이 tool_calls로 검색/수정을 요청하고 서버가 실행하는 대화형 루프)을 검토했고, 이 브랜치가 그 검증용 프로토타입.

- 전환 트레이드오프 문서: 이 폴더 `docs/notes/chat-tool-calling-tradeoff.md` (브랜치에 커밋됨)
- v0.6.x 개선 배경: 같은 위치 `chat-ux-improvement.md`
- **v2 설계 노트 (지금까지 한 것 + 앞으로 할 것 + 미결 사항)**: 이 폴더 `docs/notes/chat-v2-design.md` — 살아있는 문서, 작업 진행 시 갱신할 것

## 구현 현황 (2026-07-13 생성 경로 추가, 미커밋)

`src/main/kotlin/com/pluxity/weekly/chat/v2/` — 자체 완결 패키지:

| 파일 | 역할 |
|---|---|
| `ChatV2Controller` | `POST /chat/v2` `{message}` → `{reply, steps[], inputTokens, outputTokens, cachedTokens}` (조회) + `POST /chat/v2/weekly-report` `{message}` → `{reply, weeklyReport, inputTokens, outputTokens}` (생성) |
| `ChatV2Service` | 조회 루프 본체: tool_calls 나오는 동안 실행→결과 첨부→재호출, content 나오면 종료. MAX_STEPS=8, **초과 시 graceful 안내 reply**(예외 아님). `ChatV2UserLock` 직렬화. chat_logs 기록 재사용. cachedTokens 합산 로깅 |
| `ChatV2WeeklyReportService` | **생성 파이프라인 (2026-07-13)**: 리더 게이트 → 본문 검증(유효 줄 2+) → classify(json_schema) → 항목 0 검증 → 지난주 매칭(json_schema, best-effort) → `upsertFromClassify`. 사용자 실수(리더 아님·본문 없음·항목 0)는 200 + 안내 reply(weeklyReport=null). 히스토리 미기록. `numberItems`/`enrichMatched`/`buildMatchMessages`/`WeeklyReportClassifyResult` 재사용 |
| `ChatV2WeeklyReportSchemas` | classify/match JSON 스키마 — **최상위 단일 팀 오브젝트**(3팀 환각 구조 차단), required 전 필드 + additionalProperties=false. **스펙 편차 2건 의도적**: category enum 불채택(고정 집합 없음), progress 포맷 고정 불채택(원문 보존 계약) — 설계 노트 참고 |
| `ChatV2Tools` | **조회 tool 스키마 7개** — `search_items`, `get_item_details`, `aggregate_items`, `search_users`, `list_pending_reviews`, `get_task_history`, **`get_weekly_report`**(팀 리더 게이트, week=this/last/날짜). 규칙은 프롬프트에, description은 최소로 (스텝마다 곱해지는 비용) |
| `ChatV2ToolExecutor` | 조회 실행부 — 기존 서비스 재사용(search 스코프·권한 공짜). 실패는 `{"error":...}` 반환(agent 패턴). limit 캡 + totals/truncated. 집계는 인메모리 groupBy. get_weekly_report는 rawContent 제외·matchedAgainstPrev 포함 |
| `ChatV2IdRegistry` | **id 추측 차단 가드레일** — 이번 턴 검색 결과의 id만 필터·상세·이력 인자에 허용 (실사례 2건). 멀티턴은 턴마다 재검색 강제(의도된 비용) |
| `ChatV2UserLock` | `chat:lock:{userId}` 30s — **v1과 같은 키**라 v1/v2 조회/생성이 유저 안에서 직렬화. release-lock.lua 값 비교 해제 |
| `ChatV2LlmClient` | OpenRouter 전용. `call`(tools) + **`callStructured`(response_format: json_schema, strict)**. Gemini/Ollama 폴백 없음. cached_tokens 추출 |
| `ChatV2HistoryStore` | Redis `chatv2:history:{userId}` — 조회 턴만, user/assistant 텍스트 12개, 24h TTL |
| `dto/ChatV2ApiDto` | OpenAI wire DTO (snake_case + NON_NULL) + ResponseFormat/JsonSchemaSpec + 생성 요청/응답 DTO |
| `dto/ChatV2ToolArgs` | tool arguments 역직렬화 DTO 6종 — 필수 누락·발명 인자(FAIL_ON_UNKNOWN_PROPERTIES) 실패 → error로 재시도 유도 |

- 프롬프트: `llm/chat-v2-prompt.txt`(조회 — CUD는 보드/폼, 주간보고 작성은 붙여넣기 입력 안내, get_weekly_report 규칙), `llm/chat-v2-weekly-report-prompt.txt`(생성 classify — 단일 팀, 원문 보존, "JSON만 출력" 류 없음. 런타임에 오늘+요청자 팀명 주입)
- 테스트: `ChatV2ApiDtoTest`(wire + response_format), `ChatV2WeeklyReportSchemasTest`(스키마↔DTO 계약), `ItemNameMatcherTest` — 전체 `./gradlew test` 통과 (2026-07-13)
- **OpenRouter json_schema 실호출 검증 완료 (2026-07-13)**: gemini-2.5-flash가 strict 스키마 수용, 3팀 입력→단일 팀만, 코드펜스 없음 → Gemini 네이티브 폴백 불필요·미구현

## 미검증 / 미구현 (다음 할 일 후보)

1. **E2E 미검증** — 서버 기동 후 검증이 최우선 (시나리오 상세: 설계 노트 §3-1):
   - 생성: 정상 저장 / 3팀 입력→요청자 팀만 / 매칭 / 본문 없음·항목 0·리더 아님 → 안내 / 재작성 UPSERT
   - 조회: 기존 시나리오 + get_weekly_report("주간보고 보여줘", "빠진 항목?"), "주간보고 작성해줘"→붙여넣기 입력 안내, 락 연타 429
2. 의도적 제외(스펙 비범위): 스트리밍(SSE), 조회 Ollama/Gemini 폴백, 다중 팀 리더 동시 생성, 생성 네트워크 재시도(backoff)
3. 다음 단계: FE 계약(조회 결과 블록) 논의, v1(/chat)·clarify/resolve 폐기 일정 (v2 생성이 나가면 v1 주간보고 생성 폐기 수순)

## 실행

- `.\gradlew.bat bootRun` (local 프로파일). `application-local.yml`은 메인에서 복사돼 있음(OpenRouter 키 기본값 포함) — **이 파일 절대 커밋 금지** (키 노출)
- 메인 폴더 서버와 동시에 띄우려면 `--server.port=8081` (Redis/DB 공유 OK, v2 히스토리 키는 `chatv2:*`로 분리)
- 인증 필요: 기존 /chat과 동일하게 JWT

## 작업 컨벤션 (이 프로젝트 사용자 규칙)

- 커밋은 사용자가 명시적으로 요청할 때만. 커밋 메시지에 Claude/Co-Authored-By trailer **절대 금지**
- 브랜치: develop 기반, `feature/`·`fix/` + Conventional Commits. develop은 보호 없음 — 사용자가 승인하면 ff-merge 직push 관례
- `application-local.yml`, `docs/` 안내 문서는 커밋 제외 대상
- promptfoo(`promptfoo/promptfooconfig.yaml`)는 기존 /chat 프롬프트 회귀용 — v2는 프롬프트가 얇아서 해당 없음, E2E가 주 검증 수단
