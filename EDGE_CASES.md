# Task Approval Workflow — Edge Cases

> 작업 중 떠오르는 엣지케이스를 여기에 계속 적어두고 한 번에 훑기.
> 기준:
> - **Critical**: 안 하면 데이터 손상/버그/FK 오류. 지금 바로.
> - **필수**: MVP 전 필수. 이번 PR 안에 처리.
> - **Nice**: 드물거나 현재 권한 체크로 자연히 막힘. 별도 티켓.
> - **보류**: 이론상만 가능. 주석/로그만 남기고 패스.

---

## 📌 미처리

### Critical

- [ ] **Task 삭제 시 TaskApprovalLog FK 처리**
  - `TaskApprovalLog.task` 에 FK 달려있는데 삭제 정책 없음
  - 현재 상태에선 Task 삭제 시 FK 오류로 삭제 자체 실패할 가능성
  - 옵션 A: `@OnDelete(CASCADE)` — 로그까지 함께 삭제 (감사 로그 손실)
  - 옵션 B: Task.delete() 에서 명시적으로 로그 먼저 삭제
  - 옵션 C: Task 에 soft delete 도입 (deletedAt)
  - **결정 필요**: 팀 정책에 따라

- [ ] **Epic / Project 삭제 시 Task 전파**
  - Epic 삭제 → 하위 Task 삭제 → TaskApprovalLog 처리 (위 항목과 연결)
  - 현재 EpicService.delete() 동작 확인 필요

### 필수 (이번 PR 범위)

- [ ] **assignee 가 null 인 태스크에서 검수 요청**
  - `requestReview()` 에서 `requireTaskOwner` 는 통과 불가 (assignee?.id != user.id)
  - 다만 ADMIN 은 통과됨 → ADMIN 이 assignee null 태스크를 검수 요청하면? 알림 대상 PM 은 있음
  - **현재 동작 예상**: 정상 동작 (PM 알림만 감) — 확인 테스트 추가 필요

- [ ] **알림 대상이 본인일 때 (self-notify)**
  - PM 이 본인에게 할당된 태스크의 검수 요청을 본인이 받음 → 자기 자신에게 Teams DM
  - 그리고 본인이 승인하면 assignee(=본인)에게 또 알림
  - **결정 필요**: self-notify 허용 vs 스킵

### Nice (별도 티켓)

- [ ] **담당자 변경 후 이전 담당자의 검수 요청**
  - 권한 체크(`requireTaskOwner`)로 자연 차단됨. 추가 작업 불필요. 테스트로 문서화만.

- [ ] **PM 변경 직후 이전 PM 에게 알림이 이미 큐에 있으면?**
  - `TeamsNotificationEvent` 는 `AFTER_COMMIT + @Async` 라 이미 발송된 것은 되돌릴 수 없음
  - 치명적이지 않음 (알림 1회 잘못 가는 정도)

- [ ] **동일 태스크에 여러 명이 동시에 승인/반려**
  - DB 트랜잭션이 먼저 커밋된 쪽만 성공, 나머지는 status 체크에서 INVALID_TASK_STATUS_TRANSITION
  - 현재 구조로 자연 해결
  - 낙관적 락(`@Version`) 도입 고려는 나중에

- [ ] **IN_REVIEW 상태에서 assignee 변경 허용 여부**
  - 현재: `update()` 에서 IN_REVIEW 는 status 변경만 차단, 다른 필드(assignee 포함) 변경 가능
  - 결정: IN_REVIEW 중엔 assignee 변경 금지? 아니면 허용?
  - 허용 시: 검수 주체가 바뀌는 셈 (이전 담당자가 요청 → PM 반려 시 새 담당자에게 알림)

- [ ] **Task 반려되면 진행률 초기화할지?**
  - 현재: status 만 IN_PROGRESS 로, progress 는 그대로 (예: 100% 유지)
  - 100% 인데 IN_PROGRESS 라 UI 상 애매함
  - 결정: 반려 시 progress 를 이전 값으로 복구 vs 그대로 유지 vs 99 로 낮춤

- [ ] **GET /tasks/pending-reviews — PM 이 본인 프로젝트에 본인이 assignee 인 태스크도 포함?**
  - 현재 구현: 프로젝트 단위 필터라 포함됨
  - PM 이 본인 태스크를 검수 요청 → 본인 리뷰 큐에 뜸 → 본인이 승인 가능
  - 악용 가능하지만 드문 케이스. 정책 결정 필요.

- [ ] **승인 이력 조회 권한**
  - `findApprovalLogs()` 는 `requireEpicAccess` 체크 — 해당 에픽 접근 가능자 전부
  - 즉 에픽 배정자(Worker) 도 모든 이력을 볼 수 있음
  - 반려 사유가 민감하면 PM 전용으로 제한? 현재는 열람 허용.

### 보류 (로그/주석만)

- [ ] **알림 발송 실패 시**
  - 이미 `AFTER_COMMIT + @Async` 라 태스크 상태 전이는 보장됨
  - Teams 쪽 에러는 `TeamsNotificationService` 에서 warn 로그. 재시도 없음.
  - 재시도 큐 도입은 큰 작업이라 보류

- [ ] **Race: REVIEW_REQUEST 직후 PM 이 프로젝트에서 제거되면?**
  - 알림은 이미 발행됨. PM 권한 잃었으니 승인/반려 시도하면 403.
  - 태스크는 IN_REVIEW 상태로 고립됨 → 새 PM 배정 시 정상 처리 가능
  - 보류 OK

---

## ✅ 처리됨

- [x] **DONE 태스크는 update() 전체 차단** (status 변경뿐 아니라 모든 필드)
- [x] **IN_REVIEW 태스크 update() 상태 변경 차단**
- [x] **update() 로 status=IN_REVIEW / DONE 직접 지정 차단**
- [x] **반려 사유 nullable 허용** (REJECT_REASON_REQUIRED 제거)
- [x] **IN_PROGRESS 가 아닌 상태에서 검수 요청 시 예외** (TODO/IN_PROGRESS 만 허용)
- [x] **IN_REVIEW 가 아닌 상태에서 승인/반려 시 예외**
- [x] **반려 시 reason 을 TaskApprovalLog 에 기록** + Teams DM 에 사유 포함 (null 이면 suffix 생략)
- [x] **PM / ADMIN 이 아닌 사용자의 pending-reviews 조회 시 403**
- [x] **PM 이 담당 프로젝트 없을 때 pending-reviews 빈 목록 반환**
- [x] **chat 에서 "완료"/"끝냈어"/"100%" 를 review_request 로 매핑**

---

## 📝 작성 규칙

1. **떠오르는 즉시 한 줄만 적기** — 해결 시도 금지
2. **현재 작업 끊지 말기** — 흐름 유지가 우선
3. 기능 완성 후 이 파일 한 번에 훑고 분류/처리
4. 처리 완료한 건 "처리됨" 섹션으로 이동
5. PR 머지 전 "Critical"/"필수" 는 전부 해결했는지 체크

---

_생성: 2026-04-08 (feat/task-approval-workflow)_
