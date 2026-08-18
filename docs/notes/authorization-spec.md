# 권한 구조 개편 — 설계 명세

> 도메인 견고화 2단계. 작성 2026-08-18. 브랜치 `refactor/authorization`.

## 문제

**1. 보안이 opt-in 이다.** 서비스가 `authorizationService.requireXxx()` 를 직접 골라 호출해야 하고,
빠뜨려도 컴파일·테스트가 통과한다. 실제로 다음이 무방비다.

| 구멍 | 내용 |
|---|---|
| `/admin/**`, `/roles/**` | `SecurityConfig` 가 `anyRequest().authenticated()` 뿐이고 `@PreAuthorize` 도 없다. `UserService`·`RoleService` 는 `AuthorizationService` 를 주입조차 받지 않는다. **일반 사용자가 자기 역할을 ADMIN 으로 변경 가능** |
| `findById` | `TaskService:49`, `EpicService:44`, `ProjectService:75` — 권한 검사 없음. 목록은 스코프되는데 단건은 뚫림 |
| `search` 필터 우회 | `filter.epicIds ?: visibleEpicIds(user)` — 호출자가 id 를 주면 스코프가 무시된다. `ChatReadHandler.buildEpicFilter`/`buildProjectFilter` 가 실제로 그렇게 넣는다 |

**2. `requireXxx` 가 12개다.** 계층 위임이 없어 층마다 같은 판정을 새로 쓴다.
"이 사용자가 그 프로젝트 PM인가"를 네 가지 구현으로 묻고, `ADMIN 이면 통과` 가 12번 복붙돼 있다.

**3. 조회 범위 계산이 권한 서비스에 섞여 있다.** `visibleEpicIds()` 는 인가 판정이 아니라 쿼리 필터인데
같은 클래스에 있고 ID 목록을 반환한다. 그래서 권한이 요청 필터에 주입되고 위 우회가 생겼다.

## 확정 설계

**정책은 거의 그대로 두고 구조만 바꾼다.** 대부분은 새 규칙이 아니라
"이미 있던 규칙을 실제로 강제하고 중복을 없애는 것"이다.

### 규칙 — 계층 사실 하나 + 리소스별 예외 셋

계층을 타고 내려오는 판정은 **하나뿐**이다.

```kotlin
// "이 리소스가 속한 프로젝트의 관리자인가"
private fun isManagerOf(user, project: Project) = user.isAdmin || project.pmId == user.requiredId
private fun isManagerOf(user, epic: Epic)       = isManagerOf(user, epic.project)
private fun isManagerOf(user, task: Task)       = isManagerOf(user, task.epic)
```

나머지는 여기에 예외를 OR 로 더할 뿐이다.

| 리소스 | 판정 |
|---|---|
| project VIEW | `isManagerOf` \| 하위에 내가 배정된 에픽 있음 |
| project EDIT / DELETE | `isManagerOf` |
| epic VIEW | `isManagerOf` \| 배정된 나 |
| epic EDIT / DELETE / ASSIGN | `isManagerOf` |
| task VIEW / EDIT / DELETE | `isManagerOf` \| 담당자 본인 |
| task APPROVE (승인·반려) | `isManagerOf` |

예외는 셋뿐이다: 프로젝트의 "배정된 에픽 보유", 에픽의 "배정된 나", 태스크의 "담당자 본인".

태스크는 VIEW 와 EDIT 규칙이 같다. 볼 수 있으면 고칠 수 있다.

### 생성은 부모에 대해 검사한다

아직 없는 인스턴스는 판정 대상이 될 수 없으므로 `can(resource, CREATE)` 로 표현하지 않는다.

```kotlin
fun canCreateProject(user)                = user.isAdmin
fun canCreateEpic(user, project: Project) = isManagerOf(user, project)
fun canCreateTask(user, epic: Epic)       = can(user, epic, VIEW)   // 배정된 워커도 생성
```

태스크 생성만 `epic.VIEW` 인 것은 의도다 — 배정된 워커가 본인 업무를 스스로 등록한다.
워커가 만든 태스크는 항상 본인 담당이 된다(`TaskService.create` 가 미지정 시 요청자로 채우고,
타인 지정은 `ensureAssigned` 가 PM/ADMIN 으로 제한).

### API 형태

```kotlin
enum class Action { VIEW, EDIT, DELETE, ASSIGN, APPROVE }   // CREATE 는 위 별도 메서드

fun can(user, resource, action): Boolean      // 조건부 로직, 응답에 "수정 가능" 플래그
fun require(user, resource, action)           // 실패 시 PERMISSION_DENIED
```

무의미한 조합(`can(project, APPROVE)`)은 `when` 의 `else -> false` 로 **기본 거부**한다.
enum 을 쓰는 이유는 위 표를 그대로 테스트 케이스로 순회할 수 있기 때문이다.

### 판정과 필터는 같은 규칙의 두 표현이다

목록 조회는 행마다 `can()` 을 부를 수 없으므로 **같은 규칙을 SQL 조건으로도** 표현해야 한다.

| | 용도 | 형태 |
|---|---|---|
| `can` / `require` | 엔티티 1건 | 위임 |
| `scope(user)` | 목록 쿼리 | **조건**. 요청 필터와 항상 AND |

**`scope` 는 ID 목록이 아니라 조건을 반환한다.** 지금 `visibleEpicIds(): List<Long>?` 이
`null`=전체 / `emptyList`=없음 두 의미를 타입 밖에 두면서 방어 코드와 우회를 낳았다.

```kotlin
// can(task, VIEW) = 담당자 || isManagerOf  의 SQL 번역
fun taskScope(user): Predicate? =
    if (user.isAdmin) null                      // null = 조건 없음, 의미 하나뿐
    else or(
        path(Task::assignee)(User::id).eq(user.requiredId),
        path(Task::epic)(Epic::project)(Project::pmId).eq(user.requiredId),
    )
```

```sql
WHERE (t.assignee_id = :me OR p.pm_id = :me)   -- scope
  AND (요청 필터 ...)                            -- 항상 AND
```

얻는 것: `IN (...)` 폭발 없음, `null` 의미 하나, 방어 코드 불필요, 요청 필터가 스코프를 덮을 수 없음.

`can()` 과 `scope()` 가 일치하는지는 테스트로 검증한다 — 같은 데이터에서 `can()` 로 거른 결과와
`scope()` 로 조회한 결과가 같아야 한다.

### 관리 API 는 경로에서 막는다

```
/admin/**, /roles/**   → hasRole(ADMIN)      SecurityConfig 에서 일괄
GET /users             → 신설. 인증 사용자면 조회 가능
```

담당자 선택 등 일반 화면이 사용자 목록을 필요로 하므로 조회만 `/users` 로 분리한다.
`/admin/**` 안에 예외(“GET 만 개방”)를 두지 않는다 — 그게 다시 구멍이 된다.

## 지금과 달라지는 정책 — 하나뿐

- **팀 리더의 에픽 배정 특례 제거.** `requireEpicAssign` 이 아무 팀 리더나 허용한다(남의 프로젝트 에픽도).
  앞으로는 PM/ADMIN 만. TEAM_LEADER 역할은 주간보고에서만 쓰인다.

나머지는 정책 변경이 아니라 구멍 메우기다.

## 작업 단계

### 1. 구멍 막기 — 이것만으로 실제 위험은 대부분 사라진다

- `SecurityConfig` — `/admin/**`, `/roles/**` 에 `hasRole("ADMIN")`
- `GET /users` 신설 (`UserController`). `AdminUserController` 의 목록 조회는 그대로 두되 ADMIN 전용이 됨

### 2. `AccessPolicy` 도입

새 컴포넌트. 위 규칙 표를 `isManagerOf` + `can`/`require` + `canCreateXxx` 로 구현.

### 3. `scope` 분리

`visibleProjectIds`·`visibleEpicIds`·`restrictedAssigneeId` 를 조건 반환으로 대체.
`*CustomRepositoryImpl.findByFilter` 가 `filter` 와 `scope` 를 각각 받아 AND 로 결합하도록 변경.

- `TaskCustomRepositoryImpl`, `EpicCustomRepositoryImpl`, `ProjectCustomRepositoryImpl`
- `TaskSearchFilter` 등에서 `epicIds`/`projectIds` 제거 (권한 주입 슬롯이었음)

### 4. 호출부 교체

- `AuthorizationService` 의 `requireXxx` 12개 제거
- `TaskService`·`EpicService`·`ProjectService`·`TeamService`·`TaskReviewService`·`EpicAssignmentService` 호출부 교체
- **`findById` 3곳에 `require(user, resource, VIEW)` 추가**
- `checkChatPermission` 제거 (chat 은 서비스 계층 판정에 의존)
- `AuthorizationService` 에 남는 것: `currentUser()` + 주간보고용 4개 (아래 "팀 축" 참조)

### 5. 통합 테스트

`UserIntegrationTest` 와 같은 방식(Testcontainers). 역할 × 리소스 × 액션 표를 순회.
`can()` 과 `scope()` 의 일치도 함께 검증.

## 리스크

**`GET /users` 신설로 FE 가 사용자 목록 호출 경로를 바꿔야 한다.** `/admin/users` 를 담당자
드롭다운 등에 쓰고 있으면 배포 순서를 맞춘다. `docs/refactor-log.md` 에 FE 전달사항으로 기록할 것.

**`hasRole("ADMIN")` 은 `ROLE_ADMIN` 권한을 찾는다.** `CustomUserDetails.getAuthorities()` 가
`role.getAuthority()` 로 무엇을 반환하는지 **1단계 시작 전에 확인**한다. 접두사가 없으면
`hasAuthority("ADMIN")` 을 쓰거나 권한 문자열을 맞춰야 한다. 안 맞으면 경로 차단이 조용히 통과된다.

**`AuthorizationService` 가 chat 패키지를 참조한다**(`ChatActionType`, `ChatTarget`).
`checkChatPermission` 제거로 이 의존이 사라진다.

## 팀 축(주간보고)은 이번 범위 밖

구조가 프로젝트 축과 동일해서 나중에 그대로 흡수할 수 있다. 이번엔 손대지 않고,
프로젝트 축이 안정되면 옮긴다.

```
루트 2개
  project.pmId   → epic → task     ← 이번 작업
  team.leaderId  → weeklyReport    ← 나중
```

```kotlin
isLeaderOf(user, team)   = user.isAdmin || team.leaderId == 나
isLeaderOf(user, report) = isLeaderOf(user, report.team)     // 같은 위임
```

| 리소스 | 판정 |
|---|---|
| team VIEW | `isLeaderOf` |
| team EDIT / DELETE / 멤버관리 | ADMIN (리더 예외 없음) |
| weeklyReport VIEW / CREATE | `isLeaderOf` |

흡수 시 주의: 지금 `requireAdminOrLeader` 가 목록 조회 진입에서 **403** 을 준다.
스코프 방식으로 바꾸면 리더가 아닌 사용자는 빈 목록(200)이 되므로, **403 을 유지하려면
별도 가드를 남겨야 한다.** 403 유지가 결정 사항이다.

따라서 이번 작업에서 `AuthorizationService` 에 남기는 것:
`currentUser()`, `requireAdminOrLeader()`, `requireTeamAccess()`, `visibleTeamIds()`.

## 남겨두는 것

**ADMIN 이 시스템 관리와 전사 업무 권한을 겸한다.** 계정·역할 관리와 프로젝트 생성·전체 조회가
한 역할에 있다. 해당 계정이 대표 1개뿐이라 분리 이득이 없다. 시스템 관리자(개발자)와 경영진이
다른 사람이 되면 그때 `DIRECTOR` 같은 업무 역할로 분리한다.

**PM 은 프로젝트당 1명(`projects.pm_id`)을 유지한다.** 공동 PM 이 필요해지면 조인 테이블로 바꾸되
권한 판정은 `project.pmId == 나` → `project.isPm(나)` 한 줄만 바뀐다. 위임 구조는 영향 없다.

**`EpicAssignmentService.unassign` 이 배정 해제 시 담당 태스크를 삭제**하는 부수효과는
권한과 무관하게 위험하다. 별건으로 다룬다.
