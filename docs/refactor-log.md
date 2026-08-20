# 리팩토링 변경 이력

브랜치별 변경 요약. 스캔용이며 상세는 `docs/notes/*-spec.md` 참조.

---

## refactor/authorization — 진행중

**Backend**

- `/admin/**`, `/roles/**` 에 `hasRole(ADMIN)` — 지금까지 로그인만 하면 역할 변경까지 가능했다
- `GET /users` 신설 — 담당자 선택 등 일반 화면용 사용자 목록
- **`AccessPolicy` 도입** — 리소스 판정 `requireXxx` 7개를 `can`/`require(user, resource, AccessAction)` 하나로.
  계층 사실은 `isManagerOf` 하나뿐이고 리소스별 예외 셋(프로젝트의 배정 에픽 보유 / 에픽의 배정된 나 /
  태스크의 담당자 본인)을 OR 로 더한다. ID 가 아니라 엔티티를 받아 `existsByIdAndPmId` 류 재조회가 사라짐
- **`AuthorizationService` 삭제** — 인증·인가·쿼리필터가 한 클래스에 섞여 있던 것을
  `AccessPolicy`(인가 전부) + `CurrentUserProvider`(요청자 조회)로 정리. 권한 판정 지점이 하나가 됨
- 역할 체크 3개(`requireAdmin`·`requireAdminOrPm`·`requireAdminOrLeader`) → `requireAnyRole(user, vararg UserType)`
- `checkChatPermission` 을 `ContextBuilder` 로 인라인 — 인가 패키지의 chat 의존 제거
- **`findById` 3곳에 권한 검사 추가** (`/projects/{id}`, `/epics/{id}`, `/tasks/{id}`) — 지금까지 무방비
- 테스트 — `AccessPolicyTest`(규칙 표 순회 25건, DB 불필요) + `AccessPolicyQueryIntegrationTest`(신규 쿼리 4건)
- 설계 `docs/notes/authorization-spec.md`

**정책 변경 2건**

- 팀 리더의 에픽 배정 특례 제거. 아무 팀 리더나 **남의 프로젝트 에픽까지** 배정할 수 있었다 → PM/ADMIN 만
- `isManagerOf` 에서 PM/PO 역할 조건 제거. `projects.pm_id` 로 지정됐지만 PM 역할이 없는 사용자가
  자기 프로젝트에서 **조용히 거부**되고 있었다 → `pm_id` 만 본다

**FE**

컨트롤러·요청·응답 스키마는 **변경 없음**. 목록 조회 결과도 동일. 실제로 해야 할 일은 아래 "필수" 한 줄뿐이다.

| 구분 | 내용 |
|---|---|
| 필수 | 사용자 목록 조회를 `GET /admin/users` → `GET /users` 로 변경. 기존 경로는 ADMIN 전용이 되어 담당자 드롭다운이 403. **응답 스키마 동일(`UserResponse`)이라 URL 문자열만 교체** |
| 확인 | `/admin/users` 를 쓰는 다른 호출부. `GET /admin/users/{id}`·`/with-is-logged-in` 은 `/users` 에 없다 — 일반 화면에서 쓰고 있으면 별도 논의 |
| 참고 | `GET /projects/{id}`·`/epics/{id}`·`/tasks/{id}` 가 권한 없는 사용자에게 403. 지금까지 아무 id 나 조회됐다. 정상 사용자(담당자·PM·ADMIN) 흐름은 영향 없으나 **딥링크·알림 클릭 진입의 403 처리** 확인 |
| 참고 | 존재하지 않는 리소스가 비관리자에게도 404 (기존 403). 에러를 상태 코드로 분기한다면 확인 |
| 참고 | 팀 리더의 에픽 배정이 403. 팀 리더에게 배정 버튼을 노출하는 화면이 있으면 숨김 |
| 참고 | 관리자 화면(사용자·역할 관리)은 ADMIN 계정에서만 동작 |

**미결**

- `/users` 응답이 `/admin/users` 와 동일한 `UserResponse` — 일반 사용자에게 전 직원의 `username`(로그인 ID),
  `shouldChangePassword` 까지 나간다. 취약점은 아니고 최소 노출 미달. **FE 가 실제로 쓰는 필드를 확인한 뒤 좁힌다**
- 조회 범위(`visible*Ids`)는 손대지 않음 — ID 목록을 요청 필터에 주입하는 구조라
  호출자가 id 를 직접 넣으면 스코프가 무시된다. 쿼리 조건 반환으로 바꾸는 건 다음 브랜치
- `TeamService.findAll`·`DashboardService` 의 스코프 누락, `Role.auth`/`Role.name` 이원화,
  `projects.pm_id` 제약 부재 — `authorization-spec.md` "나중에 볼 것" 참조

---

## refactor/soft-delete-consistency — PR #94

**Backend**

- `users.deleted` → `retired_at (date)`. `@SoftDelete` 제거, `retiredAt != null ⟺ 퇴사`
- 삭제/퇴사 분리 — `DELETE /admin/users/{id}` = 완전 삭제, `POST .../retire`·`/rejoin` 신설
- 사용자 삭제 시 참조 처리 — 역할·팀소속·에픽배정 `CASCADE` / 태스크·승인로그 `SET NULL`
- `projects.pm_id`·`teams.leader_id` FK 신규 (RESTRICT)
- 퇴사자 로그인·기존 토큰 차단 → `403 RETIRED_USER`
- 프로필 이미지 전면 제거 (미사용 기능)
- 마이그레이션 `V20260818_001~003` · 설계 `docs/notes/user-delete-retire-spec.md`

**FE**

| 구분 | 내용 |
|---|---|
| 필수 | 사용자 목록에 퇴사자가 나타남 (기존 5명). `retiredAt` 으로 필터하거나 퇴사 표시 |
| 필수 | 삭제 버튼과 퇴사 버튼 분리. `DELETE` 는 이제 영구 삭제 |
| 확인 | `DELETE /users/me/profile-image` 호출부 있으면 제거 (엔드포인트 삭제됨) |
| 추가 | `UserResponse.retiredAt: LocalDate?` — null 이면 재직 |
| 추가 | `POST /admin/users/{id}/retire` body `{"retiredAt":"2026-08-31"}`, `/rejoin` |
| 참고 | `TaskApprovalLogResponse.actorId/actorName` nullable (완전 삭제된 사용자) |
| 참고 | 퇴사 표기(`김인엽(퇴사)`)는 FE 렌더링. 서버는 `name` 을 가공하지 않음 |

**미결** — retire/rejoin 권한 체크는 다음 리팩토링에서 일괄 적용

---

## chore/flyway-baseline — develop 머지됨

**Backend**

- 2026-08-18 운영 스키마를 `V1__baseline.sql` 로 고정, 적용 완료 마이그레이션 13개 삭제
- Testcontainers 통합 테스트 환경 도입 (`MigrationChainTest`, `UserIntegrationTest`)

**배포 시**

- 기존 환경은 배포 전 `flyway_schema_history` 를 BASELINE 한 줄로 재설정해야 함
- 스테이지 적용 완료(2026-08-18) · **운영 미적용**

**FE** — 없음

---

## chore/ddl-auto-validate — develop 머지됨

**Backend**

- `ddl-auto: update` → `validate`. 스키마 변경 경로를 Flyway 마이그레이션으로 일원화
- 엔티티와 DB가 어긋나면 기동 실패 (의도된 동작)
- 빈 DB에서는 기동 불가 — 덤프 복원 또는 baseline 필요

**FE** — 없음
