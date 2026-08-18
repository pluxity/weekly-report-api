# 리팩토링 변경 이력

브랜치별 변경 요약. 스캔용이며 상세는 `docs/notes/*-spec.md` 참조.

---

## refactor/authorization — 진행중

**Backend**

- `/admin/**`, `/roles/**` 에 `hasRole(ADMIN)` — 지금까지 로그인만 하면 역할 변경까지 가능했다
- `GET /users` 신설 — 담당자 선택 등 일반 화면용 사용자 목록
- 남은 작업은 `docs/notes/authorization-spec.md` 참조 (2단계부터)

**FE**

| 구분 | 내용 |
|---|---|
| 필수 | 사용자 목록 조회를 `GET /admin/users` → `GET /users` 로 변경. 기존 경로는 ADMIN 전용이 되어 담당자 드롭다운이 403 |
| 참고 | 관리자 화면(사용자·역할 관리)은 ADMIN 계정에서만 동작 |

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
