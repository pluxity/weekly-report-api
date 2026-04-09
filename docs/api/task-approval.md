# Task 승인 워크플로우 API

## 개요

Task(태스크)의 완료 검수 플로우.

```
[담당자] 검수 요청 → IN_REVIEW
                       │
                       ├─ [PM] 승인 → DONE
                       └─ [PM] 반려 → IN_PROGRESS (사유 필수)
```

- 모든 상태 전이는 `task_approval_logs` 에 append-only 로 기록된다
- 반려 → 재요청 반복도 전부 순차 보존
- 상태 전이 시 Teams 봇으로 당사자에게 알림 발송 (서버측 자동 처리, FE 구현 불필요)

---

## TaskStatus 변경

기존 `TODO / IN_PROGRESS / DONE / HOLD` 에 **`IN_REVIEW` 추가**.

```
TODO → IN_PROGRESS → IN_REVIEW → DONE
                        ↑  │
                        │  └─(반려)→ IN_PROGRESS
                        │
                      (검수 요청)
```

> **중요**: 일반 `PATCH /tasks/{id}` 로는 **IN_REVIEW / DONE 으로의 상태 전이가 차단**됩니다. 반드시 아래 승인 플로우 API 를 사용해야 합니다. 시도하면 `400 INVALID_TASK_STATUS_TRANSITION` 반환.

---

## 엔드포인트

### 1. 검수 요청 (담당자)

```
POST /tasks/{id}/review-request
```

- **권한**: 해당 태스크의 assignee (또는 ADMIN)
- **전이**: `TODO | IN_PROGRESS` → `IN_REVIEW`
- **부가 동작**: 로그 기록 (`REVIEW_REQUEST`) + 에픽의 프로젝트 PM 에게 Teams DM 알림

**응답**: `204 No Content`

**에러**
| status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_TASK_STATUS_TRANSITION` | 이미 IN_REVIEW / DONE / HOLD 상태 |
| 403 | `PERMISSION_DENIED` | 본인 태스크가 아님 |
| 404 | `NOT_FOUND_TASK` | 태스크 없음 |

---

### 2. 승인 (PM)

```
POST /tasks/approve/{taskId}
```

- **권한**: 해당 에픽이 속한 프로젝트의 PM (또는 ADMIN)
- **전이**: `IN_REVIEW` → `DONE`
- **부가 동작**: 로그 기록 (`APPROVE`) + 담당자에게 Teams DM 알림
- ⚠️ Epic / Project 자동 승격은 **하지 않음** (향후 추가 가능)

**응답**: `204 No Content`

**에러**
| status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_TASK_STATUS_TRANSITION` | IN_REVIEW 가 아님 |
| 403 | `PERMISSION_DENIED` | 해당 프로젝트 PM 이 아님 |
| 404 | `NOT_FOUND_TASK` | 태스크 없음 |

---

### 3. 반려 (PM)

```
POST /tasks/reject/{taskId}
Content-Type: application/json
```

**Body**
```json
{
  "reason": "요구사항 불충족"
}
```

또는 사유 없이:
```json
{}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| reason | string | ❌ | 최대 1000자. null / 공백 허용 (null 로 저장됨) |

- **권한**: 해당 에픽이 속한 프로젝트의 PM (또는 ADMIN)
- **전이**: `IN_REVIEW` → `IN_PROGRESS`
- **부가 동작**: 로그 기록 (`REJECT`, reason nullable) + 담당자에게 Teams DM 알림 (사유 있으면 포함)

**응답**: `204 No Content`

**에러**
| status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_TASK_STATUS_TRANSITION` | IN_REVIEW 가 아님 |
| 403 | `PERMISSION_DENIED` | 해당 프로젝트 PM 이 아님 |
| 404 | `NOT_FOUND_TASK` | 태스크 없음 |

---

### 4. 검수 대기 큐 조회 (PM 리뷰 목록)

```
GET /tasks/pending-reviews
```

현재 로그인한 사용자에게 들어온 **검수 대기(IN_REVIEW)** 태스크 목록.

- **권한**
  - `PM`: 본인이 PM 인 프로젝트의 IN_REVIEW 태스크만
  - `ADMIN`: 전체 IN_REVIEW 태스크
  - 그 외: `403 PERMISSION_DENIED`
- **정렬**: **오래 기다린 순** (마지막 `REVIEW_REQUEST` 로그의 `createdAt ASC`)
  - 반려 → 재요청 시 시각이 최신으로 갱신됨
- **페이징 없음** (현재 정책)

**응답**: `200 OK`
```json
{
  "data": [
    {
      "taskId": 42,
      "taskName": "로그인 API 개발",
      "description": "OAuth2 연동 포함",
      "projectId": 1,
      "projectName": "SAFERS 관제 시스템",
      "epicId": 10,
      "epicName": "기획",
      "assigneeId": 5,
      "assigneeName": "김담당",
      "dueDate": "2026-03-31",
      "reviewRequestedAt": "2026-04-08T10:00:00",
      "actions": {
        "approve": {
          "method": "POST",
          "url": "/tasks/approve/42"
        },
        "reject": {
          "method": "POST",
          "url": "/tasks/reject/42"
        }
      }
    }
  ]
}
```

#### PendingReviewResponse

| 필드 | 타입 | nullable | 설명 |
|------|------|----------|------|
| taskId | number (Long) | ❌ | 태스크 ID |
| taskName | string | ❌ | 태스크명 |
| description | string | ✅ | 설명 |
| projectId | number | ❌ | 프로젝트 ID |
| projectName | string | ❌ | 프로젝트명 |
| epicId | number | ❌ | 에픽 ID |
| epicName | string | ❌ | 에픽명 |
| assigneeId | number | ✅ | 담당자(검수 요청자) ID |
| assigneeName | string | ✅ | 담당자 이름 |
| dueDate | string (ISO date) | ✅ | 마감일 |
| reviewRequestedAt | string (ISO-8601) | ❌ | 마지막 REVIEW_REQUEST 로그 `createdAt` |
| actions | `PendingReviewActions` | ❌ | 승인/반려 액션 링크 |

#### PendingReviewActions

```
{
  approve: ActionLink,
  reject:  ActionLink
}
```

#### ActionLink

| 필드 | 타입 | 예시 |
|------|------|------|
| method | string | `"POST"` |
| url | string | `"/tasks/approve/42"` |

> FE 는 이 두 URL 을 버튼 `onClick` 에서 그대로 fetch 에 꽂아 쓰면 됩니다.
> 반려 버튼은 클릭 시 사유 입력 모달을 띄운 뒤 `POST {url}` body 로 `{ "reason": "..." }` 또는 `{}` 를 전송.

**에러**
| status | code | 상황 |
|--------|------|------|
| 403 | `PERMISSION_DENIED` | PM / ADMIN 이 아님 |

---

### 5. 승인 로그 조회

```
GET /tasks/{id}/approval-logs
```

해당 태스크의 전체 검수/승인/반려 이력을 **시간순(오래된 순)** 으로 반환.

- **권한**: 해당 에픽에 접근 가능한 사용자 (ADMIN / 프로젝트 PM / 에픽 배정자)

**응답**: `200 OK`
```json
{
  "data": [
    {
      "id": 1,
      "taskId": 42,
      "actorId": 5,
      "actorName": "김담당",
      "action": "REVIEW_REQUEST",
      "reason": null,
      "createdAt": "2026-04-08T10:00:00"
    },
    {
      "id": 2,
      "taskId": 42,
      "actorId": 3,
      "actorName": "박피엠",
      "action": "REJECT",
      "reason": "엣지케이스 처리 누락. a=0 일 때 NPE 발생.",
      "createdAt": "2026-04-08T14:30:00"
    },
    {
      "id": 3,
      "taskId": 42,
      "actorId": 5,
      "actorName": "김담당",
      "action": "REVIEW_REQUEST",
      "reason": null,
      "createdAt": "2026-04-09T11:20:00"
    },
    {
      "id": 4,
      "taskId": 42,
      "actorId": 3,
      "actorName": "박피엠",
      "action": "APPROVE",
      "reason": null,
      "createdAt": "2026-04-09T15:00:00"
    }
  ]
}
```

---

## 타입 정의

### TaskApprovalLogResponse

| 필드 | 타입 | nullable | 설명 |
|------|------|----------|------|
| id | number (Long) | ❌ | 로그 PK |
| taskId | number (Long) | ❌ | 대상 태스크 ID |
| actorId | number (Long) | ❌ | 액션 수행자 ID |
| actorName | string | ❌ | 액션 수행자 이름 |
| action | `"REVIEW_REQUEST" \| "APPROVE" \| "REJECT"` | ❌ | 액션 종류 |
| reason | string | ✅ | 반려 사유 (REJECT 에서만 채워짐) |
| createdAt | string (ISO-8601 LocalDateTime) | ❌ | 생성 시각 |

> **actor 는 "그 액션을 수행한 사람"** 입니다. `REVIEW_REQUEST` 면 담당자, `APPROVE / REJECT` 면 PM.

### TaskApprovalAction (enum)

```
REVIEW_REQUEST   // 담당자가 검수 요청
APPROVE          // PM 이 승인
REJECT           // PM 이 반려
```

### TaskStatus (enum, 기존 + IN_REVIEW 추가)

```
TODO | IN_PROGRESS | IN_REVIEW | DONE | HOLD
```

---

## FE 구현 가이드

### 화면 제안

1. **태스크 상세 / 목록에서**
   - 본인이 담당자이고 `TODO | IN_PROGRESS` 상태 → `[검수 요청]` 버튼 노출
   - 본인이 프로젝트 PM 이고 `IN_REVIEW` 상태 → `[승인]` `[반려]` 버튼 노출
   - `IN_REVIEW` 상태는 상태 배지 색을 다르게 (예: 주황/노랑)

2. **승인 이력 섹션**
   - 태스크 상세 하단에 `GET /tasks/{id}/approval-logs` 결과를 타임라인으로 표시
   - 각 항목: `[아이콘] actorName - action (createdAt)` / 반려면 reason 인용구

3. **반려 UX**
   - `[반려]` 클릭 → 사유 입력 모달 → `reason` 을 받아 `POST /tasks/reject/{taskId}` 호출
   - `reason` 은 선택값. 입력 안 하면 body `{}` 또는 `{"reason": null}` 로 호출하면 됨
   - 단, 사유 없는 반려는 담당자가 맥락을 모르니 FE 단에서 **입력 권장 문구** 정도는 띄워주는 게 UX 좋음

### 상태별 가능한 액션 매트릭스

| 현재 status | 담당자 | PM |
|-------------|--------|-----|
| TODO | 검수 요청 | - |
| IN_PROGRESS | 검수 요청 | - |
| IN_REVIEW | - | 승인 / 반려 |
| DONE | - | - |
| HOLD | - | - |

### 기존 API 와의 관계

- `GET /tasks`, `GET /tasks/{id}` 응답의 `status` 필드에 `"IN_REVIEW"` 가 추가될 수 있음 (기존 `TaskResponse` 스키마는 그대로, enum 값만 추가)
- `PATCH /tasks/{id}` 로 `status` 를 `IN_REVIEW` / `DONE` 으로 바꾸려 하면 400 에러. **반드시 이 문서의 승인 API 사용**.

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-04-08 | 초안 작성 (feat/task-approval-workflow 브랜치) |
