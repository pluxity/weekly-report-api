# 사용자 삭제/퇴사 분리 — 설계 명세

> 브랜치 `refactor/soft-delete-consistency`. 도메인 견고화 1단계.
> 스키마 진단 결과는 이 문서 말미 참조. 작성 2026-08-18.

## 문제

`User` 에 `@SoftDelete(columnName = "deleted")` 가 걸려 있는데, **User는 삭제되는 주체가 아니라
참조당하는 대상**이다. `@SoftDelete` 는 "삭제되면 존재하지 않는다"는 강한 선언이라, User에 걸면
이런 상태가 만들어진다.

```
DB   : tasks.assignee_id → users(id) FK 만족 (row가 deleted=true 로 남아있음)
앱   : User 조회에 deleted=false 필터가 붙어 그 유저를 못 찾음
결과 : DB는 정합한데 앱은 참조를 해소하지 못함
```

`UserService.delete()` 는 PM·팀리더만 막고(`ensureNoActiveResponsibility`) **태스크 담당자·팀원·
업무 그룹 배정은 막지 않는다.** 그 상태로 삭제하면 위 조합이 실제로 만들어진다.

여기에 더해 `delete` 라는 이름이 실제로는 soft delete를 수행하는 **이름과 동작의 불일치**가 있다.

## 확정 설계 (결정 완료)

1. **`users.deleted` 를 없애고 `users.retired_at (date)` 로 대체한다.** 퇴사자는 조회에 전부
   나오고, `김인엽(퇴사)` 처럼 표시한다. 표기 주체는 채널에 따라 다르다(아래 "퇴사 표기를 누가 붙이나").

   상태를 boolean과 시각으로 나눠 갖지 않는다. nullable 날짜 하나가 상태와 시점을 동시에 표현한다.
   ```
   retiredAt == null  ⟺  재직 중
   retiredAt != null  ⟺  그 날짜에 퇴사
   ```
   두 필드가 어긋나는 상태를 만들 수 없고, `tasks.completed_at` 과 같은 방식이다.
2. **`User` 에서 `@SoftDelete` 를 제거**한다. 참조가 항상 해소되므로 위 문제가 구조적으로 사라진다.
3. **두 연산을 분리한다.**
   - `POST /users/{id}/retire` — 퇴사. `retiredAt` 을 채운다. 데이터는 그대로.
   - `DELETE /users/{id}` — **완전 삭제.** 이 사람의 흔적을 DB에서 제거.
4. **퇴사 시 소속은 유지한다.** 팀원 목록·업무 그룹 배정에 퇴사 표시와 함께 남는다.
   "조회는 전부 된다"는 원칙과 일관되고, `EpicAssignmentService.unassignAndNotify` 가
   배정 해제 시 담당 태스크를 삭제하는 부수효과도 밟지 않는다.
5. **감사 컬럼(`created_by`/`updated_by`)은 손대지 않는다.** username이 13개 테이블에
   문자열로 남지만, 시스템 감사 기록으로 보고 예외 처리한다.

## 참조 처리 규칙

완전 삭제 시 각 참조를 어떻게 할지는 **"그것이 사용자의 소유물인가, 조직의 자산인가"** 로 가른다.

| 참조 | 성격 | 규칙 |
|---|---|---|
| `user_role.user_id` | 소유물 — 유저 없으면 무의미 | `ON DELETE CASCADE` |
| `team_members.user_id` | 소유물 — 유저의 소속 기록 | `ON DELETE CASCADE` |
| `epic_assignments.user_id` | 소유물 — 유저의 배정 기록 | `ON DELETE CASCADE` |
| `tasks.assignee_id` | **자산** — 태스크는 프로젝트-업무 그룹 계층에 속하고 담당자는 속성 | `ON DELETE SET NULL` |
| `task_approval_logs.actor_id` | **자산** — 태스크의 이력. 수행자는 속성 | `NOT NULL` 해제 + `ON DELETE SET NULL` |
| `projects.pm_id`, `teams.leader_id` | 현재 책임 | **FK 신규 추가.** `ON DELETE` 없음(RESTRICT) |

태스크에 CASCADE를 걸면 **담당자 삭제가 다른 사람의 업무 이력까지 지운다.** 승인 로그도 마찬가지로
"언제 무엇이 승인됐는지"는 조직의 기록이므로 남기고 수행자 연결만 끊는다.

### PM / 팀 리더

`projects.pm_id` 와 `teams.leader_id` 는 지금 FK가 없는 생 `bigint` 다. 참조 무결성이 없고,
PM 이름을 얻으려고 `ProjectService.resolvePmNames()` 가 별도 조회를 한다.

**FK를 추가하고 `ON DELETE` 는 지정하지 않는다(RESTRICT).** PM·팀 리더인 사용자를 삭제하면
DB가 거부한다. 프로젝트가 PM 없이 남는 상태를 만들지 않기 위해서이며, 인계 후 삭제해야 한다.
앱의 `ensureNoActiveResponsibility` 가 이미 같은 규칙을 강제하므로 DB가 그것을 뒷받침하는 형태다.

**퇴사는 막지 않는다.** 퇴사한 PM은 PM 자리를 유지한 채 퇴사자로 표시된다.
그래야 "이 프로젝트 PM이 퇴사했으니 인계가 필요하다"가 화면에 드러난다.

### 완전 삭제가 실제로 가능한 범위

JPA `CascadeType.ALL` 로는 불가능하다. `User` 엔티티에 `tasks`/`approvalLogs` 컬렉션이 없어
(참조가 반대 방향) cascade가 닿지 않는다. **DB 레벨 `ON DELETE` 로 걸어야 한다.**

위 규칙 적용 후에는 `ensureNoActiveResponsibility` 가 막는 경우(PM·팀리더)를 빼면
대부분의 사용자가 삭제 가능해진다.

## API

```
POST   /users/{id}/retire     { "retiredAt": "2026-08-31" }  → 204
POST   /users/{id}/rejoin     복직 — retiredAt 을 null 로     → 204   (restoreAndSync 와 짝)
DELETE /users/{id}            완전 삭제                       → 204
                                                              → 409  PM·팀리더인 경우
```

**퇴사일은 요청에서 받는다.** 실제 퇴사일과 시스템 처리일이 다른 경우가 흔하므로 소급 입력을
허용한다. 미래 날짜 허용 여부는 구현 시 정한다.

`UserResponse` 에 `retiredAt: LocalDate?` 추가. 퇴사 여부는 클라이언트가 `retiredAt != null` 로
판단한다.

퇴사일을 요청에서 받을지(과거 날짜 소급 입력) 오늘로 고정할지는 미결. 아래 참조.

### 퇴사 표기를 누가 붙이나

**API 응답의 `name` 은 항상 순수 이름이다.** 서버가 `김인엽(퇴사)` 같은 문자열을 만들지 않는다.
이름만 필요한 자리(아바타 이니셜, 멘션, 검색 매칭, 정렬)에서 문자열을 다시 파싱해야 하고,
채널마다 표기가 달라질 수 있기 때문이다. `ProjectResponse.progress` 나
`UserResponse.effectiveRole` 처럼 파생값을 별도 필드로 주는 기존 패턴과도 일관된다.

**단, 서버가 최종 문자열을 조립해 내보내는 채널은 서버가 붙인다.**

| 채널 | 표기 주체 |
|---|---|
| 웹 화면 | FE — `retiredAt` 을 보고 배지·회색 처리 등으로 렌더링 |
| Teams 알림 (`teams/service`) | **서버** — 메시지를 조립하는 쪽에서 붙인다 |
| 주간보고 본문 | **서버** — 같은 이유 |

클라이언트가 렌더링할 여지가 없는 채널이므로 서버가 최종 문자열을 책임진다.
이는 API 응답의 `name` 을 오염시키는 것과는 다른 층의 결정이다.

Teams 재가입 시 자동 부활(`UserService.restoreAndSync`)은 기존대로 동작한다.
`@SoftDelete` 가 사라지면서 우회용 네이티브 쿼리 두 개
(`findByAadObjectIdIncludingDeleted`, `restoreById`)가 불필요해진다.

## 작업 단계

### 1. 마이그레이션 (2개)
- `V20260818_001__user_delete_reference_rules.sql` — FK 5개를 drop 후 `ON DELETE` 를 붙여
  재생성. `task_approval_logs.actor_id` 의 `NOT NULL` 해제
- `V20260818_002__replace_users_deleted_with_retired_at.sql` — `retired_at` 추가,
  `deleted = true` 인 사용자를 `updated_at` 으로 백필, `deleted` 삭제

### 2. 엔티티
- `User` — `@SoftDelete` 제거, `retiredAt: LocalDate?` 추가. 퇴사/복직 도메인 메서드
- `TaskApprovalLog.actor` — `User` → `User?` (`nullable = true`)

### 3. 조회 경로
`@SoftDelete` 가 자동으로 걸러주던 지점을 명시적으로 처리한다.
- **인증 경로에서 퇴사자 로그인 차단** ← 가장 중요. 아래 리스크 참조
- 그 외 목록 조회는 퇴사자를 포함한다(설계 1번)

### 4. 서비스
- `UserService.delete` — 진짜 하드 삭제. PM·팀리더면 409
- `UserService.retire` / `rejoin` 신설
- 네이티브 우회 쿼리 2개 제거

### 5. DTO
- `UserResponse.retiredAt` 추가
- `TaskApprovalLogResponse.actorId/actorName` 을 nullable로.
  `actor` 사용처는 이 한 곳뿐이라 영향 범위가 좁다
- **`ProjectResponse` 와 `TeamResponse` 에 책임자의 퇴사 여부 노출** — PM·팀 리더가 퇴사자면
  화면에 표시해야 한다. `ProjectService.resolvePmNames()` 가 이미 사용자를 조회하므로
  이름과 함께 `retiredAt` 을 같이 담아 오면 된다

`Project.pmId` / `Team.leaderId` 를 `@ManyToOne User?` 관계로 바꾸는 것은 **이 작업에 넣지 않는다.**
DB FK 추가와 엔티티 매핑 변경은 독립적이고(Hibernate `validate` 는 FK를 검사하지 않는다),
관계로 바꾸면 `ProjectService.search`·`ProjectSearchFilter`·응답 매핑까지 번지기 때문이다.
표시에 필요한 것은 응답에 `retiredAt` 을 싣는 것뿐이다.

### 6. 테스트
현재 이 저장소에 통합 테스트가 0개다(전부 MockK 단위 테스트). 아래 세 가지는 mock으로 검증할 수
없으므로 실제 DB에 붙는 테스트가 필요하다.
- 퇴사자 로그인 차단
- 완전 삭제 시 CASCADE/SET NULL 동작
- 퇴사자가 담당자인 태스크 조회

## 리스크

**퇴사자 로그인 차단이 이 작업의 유일한 보안 리스크다.** 지금은 `@SoftDelete` 가 인증 경로의
사용자 조회를 걸러주기 때문에 퇴사자가 로그인할 수 없다. 어노테이션을 걷어내는 순간
**퇴사자 로그인 가능이 기본 동작이 된다.** 명시적 차단을 넣고 테스트로 못 박아야 한다.

`ddl-auto: validate` 전환(`f65eb54`) 이후 첫 스키마 변경이므로, 마이그레이션과 엔티티를
같이 반영하지 않으면 앱이 기동하지 않는다. 이는 의도된 동작이다.

완전 삭제는 되돌릴 수 없다. 퇴사와 삭제를 UI에서 확실히 구분해야 한다.

**`pm_id`/`leader_id` FK 추가는 기존 데이터가 깨져 있으면 실패한다.** 존재하지 않는 사용자
id가 남아 있으면 `ALTER TABLE ... ADD CONSTRAINT` 가 거부되고 마이그레이션이 멈춰 앱이
기동하지 않는다. 적용 전에 확인한다.

```sql
SELECT count(*) FROM projects p
 WHERE p.pm_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.pm_id);
SELECT count(*) FROM teams t
 WHERE t.leader_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = t.leader_id);
```

## 미결

- **퇴사자를 Teams 알림 발송 대상에서 제외할지** — 조회에는 나오지만 알림은 무의미할 수 있다.
  Teams 관련 작업을 할 때 한 번에 처리한다. 이 작업에서는 손대지 않는다
- `retire` 요청의 미래 날짜 허용 여부 — 예정 퇴사일을 미리 입력하는 흐름이 필요한지에 달렸다

## 확인된 사항

- 운영에 `deleted = true` 인 사용자가 **5명** 있다. 마이그레이션이 이들의 `retired_at` 을
  `updated_at` 으로 백필하므로 **실제 퇴사일과 다를 수 있다.** 정확한 날짜가 필요하면
  배포 후 수동 보정한다 (5건이라 부담은 없다)

## 배경: 스키마 진단 (2026-08-18)

운영 덤프와 엔티티를 대조한 결과. 운영과 스테이지 스키마는 md5 동일.

- 유령 컬럼 없음. 13개 테이블이 엔티티와 1:1
- **DB 제약은 엔티티에 선언한 것만 존재한다.** `TeamMember` 는 `@Table(uniqueConstraints=...)` 를
  선언해 `uq_team_member` 가 있지만, 같은 성격의 `epic_assignments` 와 `tasks(epic_id, name)` 은
  선언이 없어 제약도 없다. 앱에서만 중복을 검사한다
- 도메인 테이블(`tasks`/`epics`/`projects`/`teams`)에 PK·UNIQUE 외 인덱스가 0개
- 위 두 항목은 이 작업과 별개 마이그레이션으로 처리한다
