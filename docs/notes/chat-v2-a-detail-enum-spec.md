# (a) get_item_details → search_items `detail` enum 리팩토링 스펙

> 브랜치: `feature/chat-v2-search-detail-enum` (baseline=`feature/chat-v2-tool-calling-poc`, 7-tool). 커밋 제외. (2026-07-20)

## 1. 목적

- **tool 7 → 6.** `get_item_details`를 `search_items`의 **verbosity 파라미터**로 흡수. 근거: Anthropic "writing tools for agents" (tool 수 최소화, 상세는 별도 tool 아니라 모드로) + 우리 "search vs detail 겹침" 관찰.
- 효과 가설: **스키마 슬림(스텝당 토큰↓)** + tool 선택 단순화(대상 축 하나 + 자세함 파라미터). promptfoo A/B로 검증(현 7-tool vs 이 6-tool).

## 2. 핵심 설계

`search_items`에 파라미터 추가:
```
detail: "concise" (기본) | "detailed"
```
(이름은 `response_format` 대신 `detail` — 우리 structured output의 `response_format`과 혼동 방지)

- **concise** = 현 `taskMap`/`epicMap`/`projectMap` 그대로 (얇음).
- **detailed** = concise + 무거운 필드. **재조회 불필요** — search 결과 DTO가 이미 다 들고 있음(코드 확인):
  | 타입 | detailed 추가 필드 | 출처 |
  |---|---|---|
  | task | `description`, `start_date` | TaskResponse (엔티티 컬럼, 이미 있음) |
  | epic | `description`, `start_date` | EpicResponse (members는 concise에 이미 포함) |
  | project | `description`, `start_date`, `progress`, `members` | **ProjectService.search가 이미 채움**(toResponse) |
  | team | (type=team 경로, 이미 members 포함) | 변화 없음 |

  → get_item_details가 findById로 다시 가져오던 건 **불필요했음.** detailed는 같은 search 결과를 더 많이 매핑만 함.

## 3. 토큰 가드 (detailed 폭발 방지)

- detailed는 "특정 항목 좁혀 조회"용 → **detailed일 때 limit을 낮게 캡**(예: max 5). 초과 시 `truncated=true`.
- 프롬프트: "기본 concise. 설명·구성원·시작일 등 상세가 필요하고 **대상이 좁혀졌을 때만** detailed."

## 4. 변경 파일

1. `ChatV2Tools` — search_items properties에 `detail` enum 추가. `GET_ITEM_DETAILS` 상수 + tool() 엔트리 제거.
2. `ChatV2ToolArgs` — `SearchItemsArgs`에 `detail: String?`. `GetItemDetailsArgs` 삭제.
3. `ChatV2ToolSupport` — `taskDetailMap`/`epicDetailMap`/`projectDetailMap` 추가 (concise map + 무거운 필드, 서비스 호출 없음).
4. `SearchItemsHandler` — `detail` 파싱 → detailed면 detail map + limit 캡(5).
5. `ChatV2ToolExecutor` — `GET_ITEM_DETAILS` dispatch·`getItemDetailsHandler` 의존성 제거.
6. `GetItemDetailsHandler.kt` **삭제**.
7. `chat-v2-prompt.txt` — get_item_details 언급 제거, "상세=search detail=detailed" 규칙 추가.

## 5. 가드레일 영향

- get_item_details의 `validateKnown`(IdRegistry: 이번 턴 검색 id만) 경로가 사라짐. **문제 없음** — detailed도 이름 기반 search라 id 환각 불가(오히려 더 안전). IdRegistry는 여전히 필터 id 검증에 유효.

## 6. 비범위

- **concise를 지금보다 더 얇게 만들지 않음** — 현 map 유지. A/B가 "tool 통합" 효과만 격리하도록. (필드 다이어트는 별도 변경)
- 멀티 결과 detailed의 완벽한 토큰 최적화(현재는 limit 캡으로만).

## 7. 검증

1. 컴파일 + 기존 테스트 통과 (`ChatV2ApiDtoTest` 등 — get_item_details 참조 없는지 확인).
2. 8081로 기동 → promptfoo v2 config B provider와 비교:
   - **정밀 "상세" 케이스**가 B에서 `search_items detail=detailed`로 가는지 (A는 get_item_details).
   - 정밀 정확도 **유지 이상**, 토큰 **감소**, 복잡 정확도 유지.
3. 결과를 설계 노트 §3 + eval-spec에 판정 기록.

## 8. 롤백

- 브랜치 격리라 baseline은 무영향. B가 열등하면 브랜치 폐기.
