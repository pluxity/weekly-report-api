# history 오염 fix 스펙 — 옵션 D (턴 전체 role-tagged 저장·replay)

> 커밋 제외. (2026-07-20)

## 1. 문제

크로스턴 히스토리(`chatv2:history:{userId}`)를 **압축/재구성해서 저장**해 턴 경계가 무너진다. tool calling 한 턴의 실제 구조는:

```
user → assistant[tool_use] → tool[result] → assistant[최종답]   (4+ 메시지)
```

그런데 현 저장은 이 중 **user + 최종 assistant 텍스트 2개만** 남기고 tool_use/tool_result를 버린다 → 다음 턴 replay 시 role 경계가 뭉개져 **이전 턴 데이터가 새 답변에 새거나(재사용·날조), 이전 요청을 현재 요청으로 착각(블렌딩)**한다.

- **E2E 실증**: "지연 심한 프로젝트"→"그 프로젝트 자세히" = steps 없이 PM·구성원 **환각**.

## 2. 옵션 비교 (A~C 실패 → D)

| 안 | fabrication | blending | mimicry | 판정 |
|---|---|---|---|---|
| A 프롬프트 soft | — | — | — | ✗ 불신뢰 |
| 원본 (user+최종답 텍스트) | ❌ 발생 | ✅ | ✅ | 오염 |
| B user만 | ✅ | ❌ **발생** | ✅ | ✗ 실측 실패 |
| C user+고정 마커 | ✅ | ✅ | ❌ **발생** | ✗ 실측 실패 |
| **D 턴 전체 role-tagged 저장·replay** | ⚠️ 완화 | ✅ | ✅ | **채택** |

- **B 실패**: assistant를 빼니 턴 경계 소멸 → "프로젝트 목록 보여줘"가 직전 "지연 프로젝트" 쿼리를 재실행(블렌딩).
- **C 실패**: 가짜 고정 마커를 assistant로 넣으니 모델이 **그 마커를 그대로 앵무새**(mimicry) — "그 프로젝트 자세히"에 마커 텍스트를 reply로 반환.
- **근본 원인**: A~C 모두 대화를 **압축/재구성**해 저장 → 역할 경계 붕괴. → **압축하지 말고 실제 메시지 배열을 role 태그째 저장·replay** 해야 함 (표준. Spring AI ChatMemory가 하는 것).

## 3. 옵션 D 설계

**한 턴의 전 메시지를 role 태그(+tool_calls/tool_call_id) 유지해 저장하고, 다음 턴에 그대로 replay.**

### 저장 스키마
- Redis List `chatv2:history:{userId}` 유지.
- 각 원소 = 메시지 1개를 `ToolMessage` 통째로 직렬화 — `role`, `content`(nullable), `tool_calls`(nullable), `tool_call_id`(nullable). (지금은 role/content만 저장 → tool 필드 추가)

### 저장 시점/대상
- `runLoop`에서 **이번 턴에 새로 생긴 메시지들**만 저장: `[현재 user]` + 루프 중 append된 `assistant[tool_calls]`·`tool[result]`·`assistant[최종답]`. (system[0], 로드된 이전 history는 제외)
- 종료(정상/MAX_STEPS 초과) 시 `historyStore.appendMessages(userId, newMessages)`.

### trim — **턴 단위** (핵심)
- 메시지 개수로 자르면 tool_use만 남고 result가 잘려 **API가 거부**(모든 tool_call은 짝 result 필수).
- **완결된 턴 단위**로 자른다. 턴 경계 = `role=="user"` 시작점. 최근 **N턴**(예: 5)만 유지.
- 토큰: tool_result JSON이 커서 원소 수 아닌 **턴 수**로 윈도잉.

### replay
- `load()` → 저장 메시지들을 `ToolMessage` 리스트로 복원(tool 필드 포함).
- `runLoop`: `messages = [system] + load() + [현재 user]` (구조 동일, history가 full 턴).

## 4. 변경 파일

1. **`ChatV2HistoryStore`**
   - `toJson`/`parse` → `ToolMessage` 전체 직렬화/역직렬화 (objectMapper로 통째, NON_NULL).
   - `appendTurn(userId, msg, reply)` → **`appendMessages(userId, List<ToolMessage>)`**.
   - `trim` → 턴 경계(user) 기준 최근 N턴. `MAX_MESSAGES` → `MAX_TURNS`.
2. **`ChatV2Service.runLoop`**
   - 이번 턴 새 메시지 추적(현재 user index부터 끝까지) → 종료 시 `appendMessages`.

## 5. 주의

- `ToolMessage` 직렬화: assistant[tool_calls] 턴은 `content=null` → NON_NULL 필수. `ChatV2ApiDto`의 ToolMessage에 이미 @JsonInclude(NON_NULL) 있음(확인).
- tool_result content(원 데이터 JSON)가 history에 들어가 **토큰↑** → N턴 작게(3~5).
- 로드/파싱 실패 방어 유지.
- **fabrication은 완화지 보장 아님**: "그 프로젝트 자세히"는 history에 concise tool_result만 있어 상세가 없음 → 모델이 재조회하면 정답, concise로 지어내면 여전히 샘. 깨끗한 구조라 재조회 확률↑이지만 100%는 아님. 필요 시 프롬프트 보조 규칙("history의 tool_result는 지난 턴 값, 최신은 재조회") 병행 — soft, 선택.

## 6. 검증

1. 컴파일 + 기존 테스트.
2. **E2E (history 비운 뒤)** ①"지연 심한 프로젝트" ②"프로젝트 목록"(블렌딩 X) ③"그 프로젝트 자세히"(재조회·환각 X·마커 X).
3. 멀티턴에서 tool_call↔result 짝 유지되어 **API 400 안 나는지**(trim 턴 단위 검증).
4. A/B 전제: A(baseline)·B(a-branch) 양쪽에 있어야 tool 선택 측정 유효 → baseline에도 반영.

## 7. 롤백 / 대안

- 브랜치·uncommitted라 되돌리기 쉬움.
- 이 로직은 **Spring AI `MessageChatMemoryAdvisor`가 기본 제공** → 추후 Spring AI 이식 시 프레임워크로 흡수. 지금은 우리 코드로(이미 Redis List + ToolMessage DTO 보유).
