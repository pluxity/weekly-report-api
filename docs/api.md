# API 개요

전체 엔드포인트 목록과 역할 체계를 정리한다.

> 로컬에서 전체 명세 확인: `http://localhost:8080/swagger-ui.html`
> 운영 서버 context-path: `/api` (예: `https://<host>/api/swagger-ui.html`)

## 엔드포인트 목록

### 인증 (`/auth`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/auth/sign-up` | 회원가입 |
| POST | `/auth/sign-in` | 로그인 (JWT 쿠키 발급) |
| POST | `/auth/sign-out` | 로그아웃 |
| POST | `/auth/refresh-token` | 액세스 토큰 갱신 |

### 사용자 (`/users`, `/admin/users`, `/roles`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/users/me` | 내 정보 조회 |
| PATCH | `/users/me` | 내 정보 수정 |
| PATCH | `/users/me/password` | 내 비밀번호 변경 |
| DELETE | `/users/me/profile-image` | 프로필 이미지 삭제 |
| GET | `/admin/users` | 사용자 목록 조회 (ADMIN) |
| GET | `/admin/users/{id}` | 사용자 상세 조회 (ADMIN) |
| POST | `/admin/users` | 사용자 생성 (ADMIN) |
| PATCH | `/admin/users/{id}` | 사용자 정보 수정 (ADMIN) |
| PATCH | `/admin/users/{id}/roles` | 사용자 역할 수정 (ADMIN) |
| PATCH | `/admin/users/{id}/password` | 비밀번호 변경 (ADMIN) |
| PATCH | `/admin/users/{id}/password-init` | 비밀번호 초기화 (ADMIN) |
| DELETE | `/admin/users/{id}` | 사용자 삭제 (ADMIN) |
| DELETE | `/admin/users/{userId}/roles/{roleId}` | 사용자 역할 제거 (ADMIN) |
| GET | `/roles` | 역할 목록 조회 |
| GET | `/roles/{id}` | 역할 상세 조회 |
| POST | `/roles` | 역할 생성 |
| PATCH | `/roles/{id}` | 역할 수정 |
| DELETE | `/roles/{id}` | 역할 삭제 |

### 팀 (`/teams`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/teams` | 팀 목록 조회 |
| GET | `/teams/{id}` | 팀 단건 조회 |
| POST | `/teams` | 팀 생성 |
| PATCH | `/teams/{id}` | 팀 수정 |
| DELETE | `/teams/{id}` | 팀 삭제 |
| GET | `/teams/{teamId}/members` | 팀원 목록 조회 |
| POST | `/teams/{teamId}/members` | 팀원 추가 |
| DELETE | `/teams/{teamId}/members/{userId}` | 팀원 제거 |

### 프로젝트 (`/projects`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/projects` | 프로젝트 목록 조회 |
| GET | `/projects/{id}` | 프로젝트 단건 조회 |
| POST | `/projects` | 프로젝트 생성 |
| PATCH | `/projects/{id}` | 프로젝트 수정 |
| DELETE | `/projects/{id}` | 프로젝트 삭제 (소프트) |
| POST | `/projects/{id}/restore` | 프로젝트 복구 |

### 업무 그룹 — Epic (`/epics`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/epics` | 업무 그룹 목록 조회 |
| GET | `/epics/{id}` | 업무 그룹 단건 조회 |
| POST | `/epics` | 업무 그룹 생성 |
| PATCH | `/epics/{id}` | 업무 그룹 수정 |
| DELETE | `/epics/{id}` | 업무 그룹 삭제 (소프트) |
| POST | `/epics/{id}/restore` | 업무 그룹 복구 |
| GET | `/epics/{epicId}/assignments` | 배정 멤버 목록 조회 |
| POST | `/epics/{epicId}/assignments/{userId}` | 멤버 배정 |
| DELETE | `/epics/{epicId}/assignments/{userId}` | 멤버 배정 해제 |

### 태스크 (`/tasks`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/tasks` | 태스크 목록 조회 |
| GET | `/tasks/{id}` | 태스크 단건 조회 |
| POST | `/tasks` | 태스크 생성 |
| PATCH | `/tasks/{id}` | 태스크 수정 |
| DELETE | `/tasks/{id}` | 태스크 삭제 (소프트) |
| POST | `/tasks/{id}/restore` | 태스크 복구 |
| POST | `/tasks/{id}/review-request` | 리뷰 요청 (담당자) |
| POST | `/tasks/{id}/approve` | 태스크 승인 (PM) |
| POST | `/tasks/{id}/reject` | 태스크 반려 (PM) |
| GET | `/tasks/pending-reviews` | 리뷰 대기 목록 조회 (PM/ADMIN) |
| GET | `/tasks/{id}/approval-logs` | 승인/반려 이력 조회 |

### 주간보고 (`/weekly-reports`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/weekly-reports` | 주간보고 목록 조회 (팀·기간 필터) |
| GET | `/weekly-reports/{id}` | 주간보고 단건 조회 |
| GET | `/weekly-reports/summary` | 팀×주차 작성 상태 요약 |

> 주간보고 **작성·수정·삭제**는 `/chat` 엔드포인트를 통해 LLM이 처리합니다.

### 자연어 채팅 (`/chat`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/chat` | 자연어 메시지 전송 → 액션 JSON 반환 |
| POST | `/chat/resolve` | clarify 세션 누락 필드 보완 |

### 대시보드 (`/dashboard`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/dashboard/worker` | 작업자 대시보드 |
| GET | `/dashboard/pm/{projectId}` | PM 대시보드 |
| GET | `/dashboard/admin` | ADMIN 전체 대시보드 |
| GET | `/dashboard/person/{userId}` | 개인 KPI 상세 (ADMIN) |
| GET | `/dashboard/team/{teamId}/members` | 팀원 태스크 현황 |

### Teams 연동 (`/notifications`, `/teams/messages`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/notifications` | 내 Teams 알림 이력 조회 |
| GET | `/notifications/admin` | 전체 알림 이력 조회 (ADMIN) |
| POST | `/notifications/{id}/retry` | 실패 알림 재발사 (ADMIN) |
| POST | `/teams/messages` | Teams Bot Webhook 수신 |

## 역할 체계

| 역할 | 설명 | 우선순위 |
|------|------|----------|
| `ADMIN` | 시스템 전체 관리자 | 1 (최고) |
| `PO` | Product Owner | 2 |
| `PM` | Project Manager — 태스크 리뷰 승인/반려 | 3 |
| `LEADER` | 팀 리더 — 팀 주간보고 조회 권한 | 4 |
| `WORKER` | 일반 작업자 | 5 (기본) |

사용자가 여러 역할을 보유할 경우 우선순위가 가장 높은 역할이 `effectiveRole`로 사용된다.
