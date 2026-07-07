# Chat v2 (Tool Calling) 설계 노트

> ⚠️ **살아있는 문서** — 여기 적힌 것은 확정이 아니다. PoC 검증 결과와 논의에 따라 언제든 바뀔 수 있으며, 바뀌면 이 문서를 갱신한다. (마지막 갱신: 2026-07-07)

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
3. **PoC는 순수 채팅형(즉시 실행)** — v1의 폼 확정 단계 없음. **이건 PoC 편의이지 제품 결정이 아님** — 폼 확정 UX 유지 여부는 미결(아래 5).
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

## 3. 앞으로 할 것 (우선순위 순 — 전부 변경 가능)

1. **조회 전용 E2E 검증**: not-found 변형 재검색("세이퍼스"), 동명/계층 간 후보 제시, id 추측 → 레지스트리 거부 → 재검색 자가 교정, 지어낸 인자 거부 → 재시도, 집계 질문("프로젝트별 진행률", "누가 태스크 많아")이 aggregate_items로 가는지, "지연된 태스크"(exclude_done+due_date_to), 상세/이력 조회, limit 초과 시 totals 안내, **CUD 요청("OO 완료", "삭제해줘", "주간보고 작성")이 실행 없이 보드/폼 안내로 빠지는지**, cachedTokens 실할인 확인
2. **v1 대체 이행**: FE가 v2로 전환하면 v1(/chat) 및 clarify/resolve 계약 폐기 일정 수립. CUD 진입점은 보드/폼
3. **FE 렌더링 채널**: `blocks`/attachments — tool 결과를 모델용(compact)과 FE용(full DTO)으로 분리, type 태그 블록으로 응답. v1의 ChatReadResponse(nullable 6종 union)를 대체하는 계약
4. **스트리밍(SSE)** — 루프 스텝별 중간 상태("검색 중…") 노출, 레이턴시 체감 완화
5. **운영 요소**: 동시요청 락, 루프 한도 초과 UX, chat_logs에 v2 구분(모델/스텝 수), Ollama 등 폴백 전략

## 4. v1 → v2 전환 판단 (트리거 — 트레이드오프 문서와 동일)

- context stuffing 토큰이 턴당 10k+ 상시화
- 프롬프트 규칙 추가로도 정확도 한계 반복
- 멀티스텝 필수 기능 요구 발생
- **+ 이번 PoC 실측이 위 트레이드오프 문서의 가설을 지지하는지** (지금까지는 지지)

## 5. 미결 사항 (결정 필요, 논의 대상)

- [x] ~~폼 확정 UX~~ / ~~mutating 확인 정책~~ — 2026-07-07 조회 전용 전환으로 소멸 (CUD는 보드/폼)
- [x] ~~v1 대체 여부~~ — **대체로 결정** (폐기 일정만 미정, 위 3-2)
- [ ] 모델 고정(gemini-2.5-flash) vs 폴백 체계
- [ ] steps trace의 프로덕션 노출 여부 (디버그 전용 헤더로 분리?)

## 6. 파일 맵

- `src/main/kotlin/com/pluxity/weekly/chat/v2/` — Controller / Service(루프) / Tools(조회 스키마 6개) / ToolExecutor / IdRegistry / LlmClient / HistoryStore / ItemNameMatcher / dto(wire + tool args)
- `src/main/resources/llm/chat-v2-prompt.txt`
- 테스트: `ChatV2ApiDtoTest`(wire 포맷), `ItemNameMatcherTest`
- 세션 인계 컨텍스트: 이 폴더 `CLAUDE.md`
