# Chat v2 (Tool Calling) 설계 노트

> ⚠️ **살아있는 문서** — 여기 적힌 것은 확정이 아니다. PoC 검증 결과와 논의에 따라 언제든 바뀔 수 있으며, 바뀌면 이 문서를 갱신한다. (마지막 갱신: 2026-07-20)

## 1. 배경 (요약)

기존 `/chat`(v1)은 intent 분류 → DB 전체 스냅샷 주입 → JSON 텍스트 추출 구조. 파싱 실패·환각·매칭 오류가 "텍스트로 JSON을 받는" 구조 비용으로 반복 발생해, tool calling(모델이 tool_calls로 검색/수정을 요청하고 서버가 실행하는 대화형 루프)으로의 전환을 검토 중.

- 상세 트레이드오프: 같은 폴더 [`chat-tool-calling-tradeoff.md`](chat-tool-calling-tradeoff.md)
- v1은 그대로 운영, v2는 `/chat/v2`로 병행 검증

## 2. 지금까지 한 것

### 구조 (2026-07-07 오후: **조회 전용 전환**)

> **방향 결정 (2026-07-07)**: v2는 **조회 전용 채팅으로 v1을 대체**한다. CUD(생성/수정/삭제/완료/할당)와 주간보고 작성은 전부 보드·폼(웹)에서 수행. 오전에 이식했던 CUD 15-tool 구성은 같은 날 제거.
> 근거: ① id 추측 사고 2건 — mutating이 있으면 피해가 데이터 훼손으로 직결 ② 15-tool 스키마의 스텝당 재전송 비용 ③ 폼 확정 UX·mutating 확인 정책 등 미결 논의가 통째로 불필요해짐.

```
POST /chat/v2 {message}
 → ChatV2Service: while(tool_calls) { executor 실행 → 결과 첨부 → 재호출 }  (최대 8스텝)
 → content 나오면 종료 → {reply, steps[], inputTokens, outputTokens, cachedTokens}
```

| 구성 | 내용 |
|---|---|
| tool 6개 (전부 read) | `search_items`(+team, 상태/담당(me·id)/소속/기간/exclude_done 필터, sort/limit, totals·truncated), `get_item_details`(설명·시작일·구성원), `aggregate_items`(그룹별 개수·평균 진행률 — search 전체 결과를 서버에서 groupBy), `search_users`, `list_pending_reviews`, `get_task_history`(리뷰 요청/승인/반려 이력+사유) |
| CUD 요청 처리 | 실행하지 않고 "변경은 보드/폼에서" 안내 (프롬프트). 대상 찾기·현재 값 확인은 조회로 지원 |
| 실행부 | 기존 서비스 재사용 — 조회 스코프 권한 공짜 (각 search의 AuthorizationService 스코프, findApprovalLogs의 requireEpicAccess) |
| tool 오류 | 예외 대신 `{"error":...}`로 모델에 반환 → 모델이 자연어 안내 (agent 패턴) |
| 히스토리 | Redis `chatv2:*`, user/assistant 텍스트만 12개 — 멀티턴 지원 |
| 프롬프트 | ~25줄 조회 전용 (v1 system-prompt 250줄 대비) |
| 가드레일 | **id 레지스트리**(이번 턴 검색 결과의 id만 검색 필터·상세·이력 인자에 허용 — 지어낸 필터로 빈 결과→"없다" 거짓 부정 방지. 실사례 2건, `ChatV2IdRegistry`), **unknown 인자 거부**(FAIL_ON_UNKNOWN_PROPERTIES — 지어낸 인자가 조용히 버려져 무필터 결과가 환각이 된 실사례), 타입당 limit 캡(기본 10, 최대 30) + totals/truncated로 잘림을 모델에 명시 |
| 토큰 관측 | 응답 `cachedTokens` — implicit caching 실할인 측정용 (chat_logs cost는 캐시 미반영 정가) |

### 설계 결정 기록 (왜 이렇게 했나 — 바뀔 수 있음)

1. **통합 검색 1개 > 타입별 검색 3개** — 실사용 로그상 사용자는 "태스크/프로젝트"를 안 붙이고 이름만 입력. 타입별 tool이면 모델이 찍어야 하고 왕복 증가. 통합 검색이면 계층 오인("safers 완료 처리" 사건)도 구조적으로 해결.
2. **이름 매칭은 서버 토큰 매칭 + 모델 변형 재시도 2단** — "cctv API"↔"CCTV 목록 API"는 서버(`ItemNameMatcher`: 토큰 분해, 대소문자·공백 무시), "세이퍼스"↔"SAFERS" 음차는 모델(프롬프트 규칙: 변형 재검색). context stuffing의 의미 매칭을 "항상 지불"에서 "필요할 때만"으로 전환.
3. **PoC는 순수 채팅형(즉시 실행)** — v1의 폼 확정 단계 없음. **이건 PoC 편의이지 제품 결정이 아님** — 폼 확정 UX 유지 여부는 미결(아래 6).
4. **steps trace를 응답에 포함** — 확률적 루프의 디버깅·평가 수단. 프로덕션에서 뺄지 미결.

### 실측 (2026-07-06, 첫 E2E)

"CCTV 목록 API 진행률 60으로 수정해" → search(1건 히트) → update(검색 id 그대로) → 자연어 확인. **2스텝, id 추측 없음.**

| | v1 | v2 |
|---|---|---|
| 입력 토큰/턴 | ~7,200 | **2,139** (-70%) |
| 턴당 비용 | ~$0.0021 | ~$0.0007 |
| 결과 | 폼 반환(저장 별도) | 즉시 반영 + 확인 문장 |

v1은 데이터 증가 시 토큰이 비례 증가, v2는 유지 — 격차는 벌어질 전망.

**⚠️ 정정 (2026-07-07, tool 15개 확장 후):** 위 -70%는 tool 2개 시절 수치다. 스키마는 루프 스텝마다 재전송되는 input이라 15개 확장으로 스텝당 ~4k가 바닥 비용이 됐고, 3스텝 턴 실측 14.7k($0.005) — v1의 2배를 넘었다. 대응:
- **스키마 다이어트** — 행동 규칙은 프롬프트에 한 번만, description은 최소로 (ChatV2Tools 주석에 원칙 명문화). 목표 스텝당 ~2k
- **cached_tokens 실측 로깅** — Gemini implicit caching은 스텝 간 동일 프리픽스(프롬프트+스키마+누적 메시지)에 자동 적용(75% 할인). 응답 `cachedTokens`·로그로 실할인 확인 후 비용 재평가. chat_logs의 cost는 캐시 미반영 정가 계산임에 유의
- 스텝별 tool 동적 축소는 **비채택**: 다음 스텝의 필요 tool을 예측하려면 v1 intent 분류가 도로 필요하고, tools 변경은 캐시 프리픽스를 깨서 실익이 없거나 역효과. 그래도 부족하면 턴 단위 서브셋(경량 라우터)을 미결 사항으로 검토

### 생성 경로 (2026-07-13: 주간보고 작성 = structured output — 구현 스펙 `chat-v2-implementation-spec.md` 이행)

```
POST /chat/v2/weekly-report {message: 명령+본문 붙여넣기}
 → 팀 리더 게이트 → 본문 검증(유효 줄 2+) → classify(response_format: json_schema, 단일 팀 스키마)
 → 항목 0 검증 → 지난주 매칭(json_schema, best-effort) → upsertFromClassify
 → {reply, weeklyReport, inputTokens, outputTokens}
```

| 구성 | 내용 |
|---|---|
| `ChatV2WeeklyReportService` | 파이프라인 본체. 사용자 실수(리더 아님·본문 없음·항목 0)는 예외가 아니라 **안내 reply(200, weeklyReport=null)** — v1의 ChatClarifyException(400) 계약 대체 |
| `ChatV2WeeklyReportSchemas` | classify/match JSON 스키마. 최상위 **단일 팀 오브젝트**(3팀 환각 구조 차단), 섹션·항목 필드 required + additionalProperties=false |
| `ChatV2LlmClient.callStructured` | `response_format: json_schema` (strict). **2026-07-13 실호출 검증**: OpenRouter→gemini-2.5-flash 그대로 수용, 3팀 입력→단일 팀만, 코드펜스 없음 → Gemini 네이티브 폴백 불필요 |
| 재사용 | `WeeklyReportClassifyResult`/`WeeklyReportMatchResult`(역직렬화 계약), `numberItems`/`enrichMatched`/`buildMatchMessages`(매칭 골격), `upsertFromClassify`(id·주차 정규화·tx 서버 소유), chat_logs |
| 프롬프트 | `chat-v2-weekly-report-prompt.txt` — v1 classify 프롬프트에서 "JSON만 출력" 류 제거, 단일 팀 규칙 추가, 런타임에 오늘+요청자 팀명만 주입 (**context stuffing 없음**) |
| 히스토리 | 생성 턴은 `chatv2:history` 미기록 (조회 턴만) |

**스펙 대비 의도적 편차 2건** (지저분한 입력 대비 — 결정 문서 §7 "필수 뼈대만 스키마로"):
- `category` enum 불채택 — 카테고리는 사업/프로젝트명 자유 텍스트라 고정 집합이 코드베이스에 없음. 자유 문자열(nullable) 유지.
- `progress` 포맷(`\d+%`) 고정 불채택 — FormattedReport 원문 보존 계약("완료"/"진행중"/"지연 대기 중" 표기 보존)과 충돌. 원문 그대로(nullable) 유지.

### 조회 경로 추가분 (2026-07-13)

- **tool 7종**: `get_weekly_report` 추가 — 내 팀 주간보고 조회(팀 리더 게이트, week=this/last/날짜는 서버 `resolveWeekStart` 해석, rawContent 제외·matchedAgainstPrev 포함)
- **동시요청 락**: `ChatV2UserLock` — v1과 같은 키 `chat:lock:{userId}`(30s, 값 비교 해제)로 조회·생성·v1이 유저 안에서 직렬화
- **MAX_STEPS(8) 초과 시 graceful 안내** — 예외(LLM_INVALID_RESPONSE) 대신 안내 reply + 소화된 steps 반환, 히스토리에도 기록

## 3. 조회 스코프 모델 — 페르소나 × 스코프 (2026-07-20 논의, 초안)

> 조회 전용 전환(§2) 이후 남은 질문: **"그럼 조회를 누구에게, 어디까지, 어느 정도로 보여주나."** CUD를 걷어낸 만큼 조회가 정말 잘 돼야 하고, 그 기준을 "역할별 잡(job-to-be-done)"으로 못박는다. 스코프 경계가 반만 그어져 있어(CUD↔조회만 나뉨) 필터 후보마다 맨손 판단하던 불안을 이 모델로 규칙화한다. **매트릭스 상세(관리자·ADMIN top 질문)는 채워가는 중.**

### 핵심 원리 2개

1. **누가 묻느냐가 기본 스코프를 정한다.** 챗은 "아무나 아무거나 묻는 범용 쿼리 엔진"이 아니라 **역할별로 기본 시야가 다른 도구**다. 스코프는 LLM이 추측할 게 아니라 **로그인 역할에서 서버가 소유·주입**한다 → **의도=LLM, 스코프=서버.** (LLM은 "지연된 거"·"리뷰 대기" 같은 의도만 다루고, "누구의"는 서버가 안다)
2. **챗은 덤프가 아니라 답을 준다.** 물어본 것 + (목록이면) 몇 줄 + 총계 + "보드에서 전체" 링크. 그 이상은 보드/대시보드 몫. (기존 `limit`/`totals`/`truncated` 철학의 연장)

### 페르소나 3 (역할이 아니라 잡으로 묶음)

| 페르소나 | 매핑 역할 | 잡 | 기본 스코프 |
|---|---|---|---|
| **팀원** | 역할 없음 | 내 태스크 쳐내기 | **나** (assignee=me) |
| **관리자** | 팀장 / PM / PO — 잡이 거의 동일 | 내 단위의 지연·리뷰·진행률·주간보고 | **팀 and/or 프로젝트** |
| **ADMIN** | ADMIN | 전체 or 특정 프로젝트 드릴다운 | **전체 / 임의 프로젝트** |

### 스코프 축 — 팀 ⊥ 프로젝트 (직교, 계층 아님)

- **프로젝트는 여러 팀을 가로지른다(확인됨).** 따라서 "우리 팀 지연"과 "내 프로젝트 지연"은 **서로 다른 태스크 집합** → 두 스코프를 하나로 합치지 못한다. 태스크는 (팀 × 프로젝트) 교차점에 앉는다.
- **팀장+PM 겸직자**는 새 페르소나가 아니라 **스코프 앵커를 둘 다 가진 관리자**다. 페르소나는 3개 유지, 겸직자만 팀·프로젝트 앵커를 동시 개방. (겸직 존재 자체가 팀≠프로젝트의 증거)
- 어느 앵커인지는 **질문이 결정**한다("우리 팀…"→팀, "OO 프로젝트 / 내 프로젝트…"→프로젝트). 겸직자가 애매하게 물으면 서버가 되묻거나 주 역할로 디폴트(프롬프트 규칙).

### 이 모델의 귀결 (설계에 미치는 영향)

- **"내 것" 스코프는 LLM 필터가 아니라 역할 디폴트**로 내려가는 게 맞다. 역할별 기본 스코프를 서버가 주입하므로, 관리자한텐 "내 프로젝트"가 디폴트 → LLM이 `pm_me`를 매번 켤 필요가 없다. → **2026-07-20 추가한 `search_items.pm_me`/`pm`은 이 관점에서 재검토 대상**(관리자 디폴트로 흡수 vs LLM 필터 유지, 미결 §6).
- LLM에 남는 필터 = **역할 스코프 안에서 더 좁히는 것**(상태·마감·특정 프로젝트명)만. 필터 surface가 줄어 "어디까지 만들지" 불안의 절반이 해소.
- **만들 tool/필터 목록 = 페르소나 3 × 대표 질문 3~5 매트릭스에서 파생.** 이 매트릭스가 곧 "어디까지"의 최종 답이자 v2 조회 스펙. (아직 미완)

### 미완 / 결정 필요

- **관리자·ADMIN의 "하루에 챗한테 물을 top 3~5 질문"** 목록 (팀원 예시는 나옴: 내 태스크·마감·리뷰 반려 사유)
- **역할 스코프의 서버 주입 지점** — 기존 검색 서비스에 이미 AuthorizationService 권한 스코프가 걸려 있음. 그 위에 "페르소나 기본 스코프"를 어떻게 얹을지 (권한 스코프 ⊇ 페르소나 스코프 관계 정리)
- 겸직자 스코프 애매 시 되묻기 vs 디폴트

### 진행 (2026-07-20) — 팀원 페르소나 PoC 여기까지

- **completedAt 도입**: `feature/delay-completed-at`(#92 `dc500c4`) merge — `completed_at` 컬럼+승인로그 백필+`Task.completedAt`. 새로 만들지 않고 지연일 리팩토링 브랜치 것 재사용 (마이그레이션 중복 회피)
- **Backward("이번주/저번주 뭐 했지")**: `search_items`에 `completed_from/to`(완료일 범위, 태스크 전용) 필터 추가 — TaskSearchFilter·repo·args·스키마·핸들러
- **Forward("남은 일")**: 기존 `assignee_me`+`exclude_done`로 동작 (신규 없음)
- **프롬프트**: 회고→completed 매핑 + 팀원 본인기준(assignee_me) 디폴트 안내 (soft — 서버 하드 강제는 미결)
- **PM 필터**(`pm_me`/`pm`)도 이 과정에 추가됨 — 단 위 귀결대로 역할 디폴트 흡수 검토 대상(미결 §6)
- **미구현(의도)**: approve() 완료시점 write-path(#92 후속) → 지금은 백필된 과거건만 조회, 스코프 하드 강제, 관리자/ADMIN 페르소나
- 다음: **관리자 페르소나**(팀 ⊥ 프로젝트 스코프) — 팀원에서 만든 필터 뼈대 재사용

### 진행 (2026-07-20 이어서) — 관리자 팀 스코프 + 아키텍처 발견

- **관리자 팀 스코프**: `search_items`에 `team_me`(내가 리더인 팀)/`team`(이름) 필터. Task↔Team은 **멤버 담당 경유만** 존재(project↔team 매핑 없음, 확인) → 팀→멤버 userId→`assignee IN`. `TaskSearchFilter.assigneeIds` + repo. 태스크 전용
- **"우리 팀원 누구" 버그 수정**: `search_items(type=team)`가 team_me 인식 → 팀+구성원 반환. 발견한 버그: `teamService.findById`가 members를 안 채움(toResponse 기본 emptyList) → searchTeams·get_item_details(team) 둘 다 멤버가 비어 있었음. `myLedTeams` 헬퍼로 `search()`(멤버 채움) 통합 + get_item_details는 findMembers 보강
- **아키텍처 발견 (PoC 핵심 산출물)**:
  1. **history 오염** — 크로스턴 assistant 답 텍스트(데이터 값)를 LLM이 재사용 → 교차귀속(프로젝트1 에픽을 2로), tool 미호출. 오늘 3회 재현. history는 CUD 확정 대화용이었으나 **v2 조회전용이 되며 명분 소멸.** load off 실험으로 오염 사라짐 확인(단 "그거" 지칭 깨짐) → **history 유지하되 값 빼고 지칭만** 저장이 방향(미결)
  2. **기능 커버리지 구멍 → 자신만만한 오답** — tool이 페르소나 잡을 못 덮으면 빈 결과가 아니라 틀린 tool을 확신하며 호출(search_users{role:LEADER} 사건)
  3. 가드레일(IdRegistry/FAIL_ON_UNKNOWN)은 **tool 인자만** 지킴 — stale history 재사용은 사각지대
- **다음**: (a) `get_item_details`를 `search_items`의 `response_format`(detailed/concise) enum으로 흡수 검토 — Anthropic "writing tools for agents" 권고(tool 수 최소화, detail은 모드로). (b) eval 셋으로 측정 기반 결정. (c) history 값 제거(지칭만)

## 4. 앞으로 할 것 (우선순위 순 — 전부 변경 가능)

1. **E2E 검증 (서버 기동 후)**:
   - 생성: 정상 붙여넣기 저장 / **3팀 입력 → 요청자 팀만 저장** / 지난주 매칭 동작 / 본문 없음·항목 0 → 안내 / 리더 아님 → 안내 / 재작성(UPSERT)
   - 조회: not-found 변형 재검색("세이퍼스"), 동명/계층 간 후보 제시, id 추측 → 레지스트리 거부 → 재검색 자가 교정, 지어낸 인자 거부 → 재시도, 집계 질문("프로젝트별 진행률", "누가 태스크 많아")이 aggregate_items로 가는지, "지연된 태스크"(exclude_done+due_date_to), 상세/이력 조회, **get_weekly_report("주간보고 보여줘", "지난주에 하기로 한 것 중 빠진 건?")**, limit 초과 시 totals 안내, **CUD 요청("OO 완료", "삭제해줘")이 실행 없이 보드/폼 안내로, "주간보고 작성해줘"가 붙여넣기 입력 안내로 빠지는지**, cachedTokens 실할인 확인, 락 동작(연타 시 429)
2. **v1 대체 이행**: FE가 v2로 전환하면 v1(/chat) 및 clarify/resolve 계약 폐기 일정 수립. CUD 진입점은 보드/폼
3. **FE 렌더링 채널**: `blocks`/attachments — tool 결과를 모델용(compact)과 FE용(full DTO)으로 분리, type 태그 블록으로 응답. v1의 ChatReadResponse(nullable 6종 union)를 대체하는 계약
4. **스트리밍(SSE)** — 루프 스텝별 중간 상태("검색 중…") 노출, 레이턴시 체감 완화
5. **운영 요소**: 동시요청 락, 루프 한도 초과 UX, chat_logs에 v2 구분(모델/스텝 수), Ollama 등 폴백 전략

## 5. v1 → v2 전환 판단 (트리거 — 트레이드오프 문서와 동일)

- context stuffing 토큰이 턴당 10k+ 상시화
- 프롬프트 규칙 추가로도 정확도 한계 반복
- 멀티스텝 필수 기능 요구 발생
- **+ 이번 PoC 실측이 위 트레이드오프 문서의 가설을 지지하는지** (지금까지는 지지)

## 6. 미결 사항 (결정 필요, 논의 대상)

- [x] ~~폼 확정 UX~~ / ~~mutating 확인 정책~~ — 2026-07-07 조회 전용 전환으로 소멸 (CUD는 보드/폼)
- [x] ~~v1 대체 여부~~ — **대체로 결정** (폐기 일정만 미정, 위 4-2)
- [ ] **`search_items.pm_me`/`pm` 필터 거취** (2026-07-20 추가) — 페르소나 모델(§3)상 "내 프로젝트"는 관리자 역할 디폴트로 흡수하는 게 정합적. LLM 필터로 유지할지 vs 서버 역할 스코프로 내릴지 결정 필요. 매트릭스 확정과 함께
- [ ] **필터 정의 중복** — 필터 1개 추가 = 스키마(ChatV2Tools)+DTO(ChatV2ToolArgs)+핸들러(resolveByName/적용) 3점 편집. 단일 필터 레지스트리에서 스키마·바인딩 파생하는 리팩토링 후보 (다음 필터 나오기 전)
- [ ] **history 오염 처리** (2026-07-20 발견) — 크로스턴 assistant 데이터 답을 LLM이 재사용해 교차귀속. 방향: 값 빼고 지칭 스레드만 저장(옵션 B=user 메시지만 / C=데이터 뺀 마커). 제거는 "그거" 지칭 깨져 불가. eval로 B/C 손익 측정 후 결정
- [ ] **get_item_details 거취** — 별도 tool 유지 vs `search_items`의 detailed/concise 모드로 흡수 (Anthropic writing-tools 권고). eval로 detail 사용 빈도 측정 후 결정
- [ ] 모델 고정(gemini-2.5-flash) vs 폴백 체계
- [ ] steps trace의 프로덕션 노출 여부 (디버그 전용 헤더로 분리?)
- [ ] 다중 팀 리더의 생성 — 현재 첫 팀 고정 (스펙 비범위)
- [ ] 생성 경로 네트워크 재시도 — 현재 단발 (v1 callWithRetry 같은 backoff 없음; json_schema라 파싱 재시도는 불필요)

## 7. 파일 맵

- `src/main/kotlin/com/pluxity/weekly/chat/v2/` — Controller(조회+생성) / Service(루프) / WeeklyReportService(생성 파이프라인) / WeeklyReportSchemas(classify·match json_schema) / Tools(조회 스키마 7개) / ToolExecutor / IdRegistry / UserLock / LlmClient(call + callStructured) / HistoryStore / ItemNameMatcher / dto(wire + tool args)
- `src/main/resources/llm/chat-v2-prompt.txt`(조회), `chat-v2-weekly-report-prompt.txt`(생성 classify)
- 테스트: `ChatV2ApiDtoTest`(wire 포맷 + response_format), `ChatV2WeeklyReportSchemasTest`(스키마↔DTO 계약), `ItemNameMatcherTest`
- 세션 인계 컨텍스트: 이 폴더 `CLAUDE.md`
