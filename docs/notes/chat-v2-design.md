# Chat v2 (Tool Calling) 설계 노트

> ⚠️ **살아있는 문서** — 여기 적힌 것은 확정이 아니다. PoC 검증 결과와 논의에 따라 언제든 바뀔 수 있으며, 바뀌면 이 문서를 갱신한다. (마지막 갱신: 2026-07-06)

## 1. 배경 (요약)

기존 `/chat`(v1)은 intent 분류 → DB 전체 스냅샷 주입 → JSON 텍스트 추출 구조. 파싱 실패·환각·매칭 오류가 "텍스트로 JSON을 받는" 구조 비용으로 반복 발생해, tool calling(모델이 tool_calls로 검색/수정을 요청하고 서버가 실행하는 대화형 루프)으로의 전환을 검토 중.

- 상세 트레이드오프: 같은 폴더 [`chat-tool-calling-tradeoff.md`](chat-tool-calling-tradeoff.md)
- v1은 그대로 운영, v2는 `/chat/v2`로 병행 검증

## 2. 지금까지 한 것

### 구조 (커밋 8ba50be + 통합 검색 개선)

```
POST /chat/v2 {message}
 → ChatV2Service: while(tool_calls) { executor 실행 → 결과 첨부 → 재호출 }  (최대 6스텝)
 → content 나오면 종료 → {reply, steps[], inputTokens, outputTokens}
```

| 구성 | 내용 |
|---|---|
| tool 2개 | `search_items`(태스크·에픽·프로젝트 통합 검색), `update_task` |
| 실행부 | 기존 서비스 재사용 — 권한 공짜 (search 조회 스코프, update requireTaskOwner) |
| tool 오류 | 예외 대신 `{"error":...}`로 모델에 반환 → 모델이 자연어 안내 (agent 패턴) |
| 히스토리 | Redis `chatv2:*`, user/assistant 텍스트만 12개 — 멀티턴 지원 |
| 프롬프트 | 11줄 (v1 system-prompt 250줄 대비) |
| 가드레일 | update로 IN_REVIEW/DONE 차단, 검색 타입당 10건 캡, id는 검색 결과만 |

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

## 3. 앞으로 할 것 (우선순위 순 — 전부 변경 가능)

1. **E2E 시나리오 마저 검증**: 되물음→멀티턴 수정, not-found 변형 재검색("세이퍼스"), 동명/계층 간 후보 제시, DONE 차단 안내, tool 없는 요청(기능 발명 안 하는지), 권한 거부 전달
2. **tool 확장** (검증 결과 좋으면): `request_review`(완료 보고), `create_task` — 즉시 실행이 위험한 것부터 확인 단계 설계와 함께
3. **FE 렌더링 채널**: `blocks`/attachments — tool 결과를 모델용(compact)과 FE용(full DTO)으로 분리, type 태그 블록으로 응답. v1의 ChatReadResponse(nullable 6종 union)를 대체하는 계약
4. **스트리밍(SSE)** — 루프 스텝별 중간 상태("검색 중…") 노출, 레이턴시 체감 완화
5. **운영 요소**: 동시요청 락, 루프 한도 초과 UX, chat_logs에 v2 구분(모델/스텝 수), Ollama 등 폴백 전략

## 4. v1 → v2 전환 판단 (트리거 — 트레이드오프 문서와 동일)

- context stuffing 토큰이 턴당 10k+ 상시화
- 프롬프트 규칙 추가로도 정확도 한계 반복
- 멀티스텝 필수 기능 요구 발생
- **+ 이번 PoC 실측이 위 트레이드오프 문서의 가설을 지지하는지** (지금까지는 지지)

## 5. 미결 사항 (결정 필요, 논의 대상)

- [ ] **폼 확정 UX 유지 여부** — 순수 채팅(대화로 확인) vs 대화+폼 블록. FE 작업량과 실수 방지 장치가 걸림 → FE·팀 논의 필요
- [ ] update 외 mutating tool의 확인 정책 (즉시 실행 허용 범위)
- [ ] v2가 v1을 대체할지, 병행할지 (대체 시 clarify/resolve 계약 폐기 일정)
- [ ] 모델 고정(gemini-2.5-flash) vs 폴백 체계
- [ ] steps trace의 프로덕션 노출 여부 (디버그 전용 헤더로 분리?)

## 6. 파일 맵

- `src/main/kotlin/com/pluxity/weekly/chat/v2/` — Controller / Service(루프) / Tools(스키마) / ToolExecutor / LlmClient / HistoryStore / ItemNameMatcher / dto
- `src/main/resources/llm/chat-v2-prompt.txt`
- 테스트: `ChatV2ApiDtoTest`(wire 포맷), `ItemNameMatcherTest`
- 세션 인계 컨텍스트: 이 폴더 `CLAUDE.md`
