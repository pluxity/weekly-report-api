# detail enum v2 — concise 린화 + detailed 계층 트리

> (a) `chat-v2-a-detail-enum-spec.md` 후속. 커밋 제외. (2026-07-20)

## 1. 배경

(a) detail enum 구현 후 실측: **concise가 이미 리치해서 detailed와 델타가 작다** → enum이 값을 못 함("별 차이 없음"). concise를 앙상하게(스캔용), detailed를 부모 맥락+자식 트리까지(드릴인용)로 벌려 enum을 의미 있게 만든다.

## 2. concise 린화

| type | concise (신) | 제거→detailed |
|---|---|---|
| task | id, name, status, progress, due_date, assignee | project, epic |
| epic | id, name, status, due_date | project, **members** |
| project | id, name, status, due_date, pm (유지) | — |
| team | 유지 | — |

- `id`는 유지 — `get_task_history(task_id)` 등 후속 tool용.
- epic concise의 `members`(무거운 리스트) 제거가 핵심 (project엔 원래 없어 일관성도 회복).

## 3. detailed = concise + 부모 맥락 + 자식 트리

| type detailed | flat 추가(부모·상세) | 자식 트리(fetch) |
|---|---|---|
| task | project, epic, description, start_date | — |
| epic | project, members, description, start_date | **하위 tasks** |
| project | description, start_date, progress, members | **하위 epics(+각 tasks)** |

### 자식 트리 — 토큰 가드
- **단건 detailed일 때만** 펼침(해당 type 매치 == 1). 다건이면 flat detail만(트리 없음) → "그 프로젝트 자세히" 류만 트리.
- 캡: **에픽 10, 에픽당 태스크 10**. `task_count`는 전체 수. 초과 시 truncated 표시.
- **응답 모양 (project 1건 detailed)**:
```json
{ ...project detail 필드...,
  "epics": [
    { "name": "관제 고도화", "status": "IN_PROGRESS", "task_count": 12,
      "tasks": [ {"name":"CCTV API","status":"DONE"}, ... (캡 10) ] },
    ... (에픽 캡 10)
  ]
}
```
- epic 1건 detailed: `+ "tasks": [{name,status}, ...(캡10)], "task_count": N`.

## 4. 변경 파일 / 위치

1. **`ChatV2ToolSupport`** — concise/detailed **flat 필드**(pure, DTO 기반). task/epic concise 축소, taskDetailMap(+project,epic), epicDetailMap(+project,members). project는 그대로.
2. **`SearchItemsHandler`** — **자식 트리**(서비스 fetch) + 단건 게이트.
   - `buildResponse`에서 detailed && matches.size==1 이면 자식 부착.
   - fetch: epic→`taskService.search(epicId=)`, project→`epicService.search(projectId=)` + `taskService.search(projectId=)`를 epicId로 groupBy (N+1 회피).
   - 캡 상수: EPIC_CHILD_CAP=10, TASK_CHILD_CAP=10.

## 5. 비범위

- eval 토큰 측정은 후순위(우선 구현).
- task 자식 없음(부모 맥락만).
- 다건 detailed의 자식(트리 폭발) — 단건만.

## 6. 검증

- 컴파일/테스트.
- E2E: "그 프로젝트 자세히"→epics+tasks 트리·캡 동작 / "그 에픽 자세히"→tasks / 다건 detailed→트리 없음 / 목록 concise가 실제로 얇아졌는지.
