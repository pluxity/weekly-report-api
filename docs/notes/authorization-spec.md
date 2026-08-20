# 권한 판정 중복 제거 — 설계 명세

> 작성 2026-08-18, 개정 2026-08-20. 브랜치 `refactor/authorization`.
>
> **목표: 리소스 판정 `requireXxx` 7개를 `can`/`require` 하나로 접는다.** 그 이상 하지 않는다.
> `develop` 이 stage 에 올라가 있어 chat 동작과 공개 계약은 건드리지 않는다.

## 문제

계층 위임이 없어 층마다 같은 판정을 새로 쓴다.
"이 사용자가 그 프로젝트 PM인가" 를 네 가지 구현으로 묻고, `ADMIN 이면 통과` 가 복붙돼 있다.

```
requireProjectManager(user, projectId)   projectRepository.existsByIdAndPmId(...)
requireProjectManager(user, project)     project.pmId == user.requiredId
requireEpicManage(user, projectId)       projectRepository.existsByIdAndPmId(...)
requireEpicAssign(user, epicId)          projectRepository.existsByEpicIdAndPmId(...)
requireTaskReviewer(user, task)          task.epic.project.pmId == user.requiredId
```

같은 사실을 다섯 군데서 각자 조회한다. 리소스가 늘면 또 늘어난다.

## 설계 — 계층 사실 하나 + 리소스별 예외 셋

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

## API 형태

```kotlin
enum class Action { VIEW, EDIT, DELETE, ASSIGN, APPROVE }

fun can(user, resource, action): Boolean      // 조건부 로직, 응답에 "수정 가능" 플래그
fun require(user, resource, action)           // 실패 시 PERMISSION_DENIED
```

무의미한 조합(`can(project, APPROVE)`)은 `when` 의 `else -> false` 로 **기본 거부**한다.
enum 을 쓰는 이유는 위 표를 그대로 테스트 케이스로 순회할 수 있기 때문이다.

**ID 가 아니라 엔티티를 받는다.** 소프트 삭제된 프로젝트의 복구 흐름처럼 조회 필터를 우회해
로드한 엔티티에도 같은 판정을 걸 수 있어야 한다 (지금 `requireProjectManager` 가 ID 판과
엔티티 판 두 개인 이유가 그것이다). 부수로 `existsByIdAndPmId` 류 조회도 사라진다.

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

## 지금과 달라지는 정책 — 둘

- **팀 리더의 에픽 배정 특례 제거.** `requireEpicAssign` 이 아무 팀 리더나 허용했다(남의 프로젝트
  에픽도). 앞으로는 PM/ADMIN 만. TEAM_LEADER 역할은 주간보고에서만 쓰인다.

- **`isManagerOf` 에서 PM/PO 역할 조건을 뺐다.** 기존 판정은 `pmId 일치 && PM/PO 역할 보유`
  였는데, `ensurePmExists` 가 사용자 존재만 확인하고 역할은 안 보므로 **역할 없이 `pmId` 로
  지정된 사용자가 자기 프로젝트에서 조용히 거부**되고 있었다. 앞으로는 `pmId` 만 본다.

  판정 시점에 방어하면 깨진 데이터는 남고 증상만 숨는다. 검증은 쓰기 시점으로 옮기는 게 맞다
  (아래 "나중에 볼 것").

나머지는 정책 변경이 아니라 중복 제거다.

## 작업 단계

### 1. `AccessPolicy` 도입 — ✅ 완료

새 컴포넌트. 위 표를 `isManagerOf` + `can`/`require` + `canCreateXxx` 로 구현.
이 단계에서는 호출부를 바꾸지 않는다 — 만들어만 두고 테스트로 표를 순회한다.

### 2. 호출부 교체 — ✅ 완료

`TaskService`·`EpicService`·`ProjectService`·`TaskReviewService`·`EpicAssignmentService` 의
`requireXxx` 호출을 `require(user, resource, action)` 으로 교체하고, 아래 7개를 제거한다.

```
requireProjectManager(user, projectId)   requireEpicAssign
requireProjectManager(user, project)     requireTaskOwner
requireEpicAccess                        requireTaskReviewer
requireEpicManage
```

**`findById` 3곳에 `require(user, resource, VIEW)` 를 추가한다** (`TaskService:49`,
`EpicService:44`, `ProjectService:75`). 지금 권한 검사가 없고, 이번에 만든 `require` 를
그대로 쓰면 되므로 여기서 같이 메운다.

### 3. 테스트 — ✅ 완료

**판정 규칙은 DB 없이 검증한다.** `AccessPolicyTest` — 판정 대부분이 엔티티 필드를 읽는 순수
함수(`isManagerOf`, `isAssignee`, `isLeaderOf`, `isAdminRole`)라 컨테이너가 필요 없다.
리포지토리가 필요한 건 에픽 배정 조회 둘뿐이라 그것만 스텁한다.

행위자 8명(ADMIN / 해당 PM / 다른 PM / 배정워커 / 담당자 / 팀리더 / 무관 / 역할 없는 pmId 지정자)을
고정하고, **(리소스, 액션) 마다 "통과하는 행위자 집합" 하나로 단언**한다. 규칙 표가 그대로
코드가 되고, 실패하면 어느 행위자가 뒤집혔는지 바로 보인다. 25건.

특히 잡아두는 셀:

- 배정워커는 project VIEW 는 되지만 EDIT 는 안 된다
- 배정워커는 에픽을 봐도 **남의 태스크는 못 본다** (에픽 배정 ≠ 태스크 접근)
- 담당자는 자기 태스크를 **승인하지 못한다**
- 팀리더의 epic ASSIGN 거부 (정책 변경 ①)
- 역할 없는 pmId 지정자의 project EDIT 허용 (정책 변경 ②)
- 무의미한 조합(`project.APPROVE`, `task.ASSIGN` 등)의 기본 거부

**통합 테스트는 새 쿼리 하나만.** `AccessPolicyQueryIntegrationTest` — 이번에 추가한
`existsByAssignmentsUserIdAndProjectId` 가 epic → project 조인으로 실제 도는지, soft delete 된
에픽이 접근 근거가 되지 않는지, 중복 배정 행이 있어도 exists 로 떨어지는지. 4건.

`visible*Ids` 에는 통합 테스트를 쓰지 않았다 — 다음 리팩토링에서 쿼리 조건으로 갈아엎을
코드라 지금 씌우면 그대로 버려진다.

## 최종 구조

`AuthorizationService` 는 **삭제했다.** 인증·인가·쿼리필터가 한 클래스에 섞여 있던 게
원래 문제의 일부였고, 그중 인가만 빼내면 관리 포인트가 둘로 갈라지기 때문이다.

| 클래스 | 담당 |
|---|---|
| `AccessPolicy` | **인가 판정 전부.** `can`/`require`(project·epic·task·team), `canCreate*`, `requireAnyRole`, 조회 범위 |
| `CurrentUserProvider` | `get()` — 요청자 조회. 인증이지 인가가 아니라 분리 |
| `AccessAction` | `VIEW / EDIT / DELETE / ASSIGN / APPROVE` |

역할 체크 3개(`requireAdmin`·`requireAdminOrPm`·`requireAdminOrLeader`)는
`requireAnyRole(user, vararg UserType)` 하나로 접었다. 리소스가 아직 특정되지 않은
자리(팀 CUD, chat 사전 체크, 주간보고 목록 진입)에만 쓴다.

`checkChatPermission` 은 `ContextBuilder.requireChatMutationAllowed` 로 인라인했다.
내용이 역할 체크 두 줄뿐이고 chat 은 폐기 예정이라 클래스를 늘릴 이유가 없다.
이로써 인가 패키지의 chat 의존(`ChatActionType`, `ChatTarget`)도 사라졌다.

**조회 범위 5개(`visible*Ids`, `restrictedAssigneeId`, `pmScopedProjectIds`)를
`AccessPolicy` 안에 둔 이유:** 이건 같은 규칙의 쿼리 쪽 표현이다. 다음 리팩토링에서
ID 목록 → 쿼리 조건으로 바뀔 자리이고, 판정과 같은 클래스에 있어야 둘의 일치를
테스트로 검증할 수 있다.

### 권한 저장소(ACL) 를 쓰지 않는 이유

`resource_permission(subject, resource_type, resource_id, action)` 테이블로 대체하는 안을
검토했으나 채택하지 않았다.

- **권한 사실이 이미 도메인 데이터다.** `projects.pm_id`, `epic_assignments`,
  `tasks.assignee_id` 는 권한을 위해 있는 게 아니라 화면·알림·집계에 쓰이는 도메인 정보다
  (`assignee` 만 권한 코드 밖에서 89곳). ACL 로 대체하면 "담당자가 누구냐" 라는 개념이
  "권한 있는 사람" 으로 바뀌어 의미가 뒤집힌다
- **계층 위임이 사라진다.** 지금 `isManagerOf(task) = isManagerOf(task.epic.project)` 한 줄이
  ACL 에서는 태스크마다 행 발급(+PM 교체 시 전체 재발급)이거나, ACL 조회가 계층을 다시 아는 것이다
- **1:1 제약과 FK 를 잃는다.** `pm_id` 컬럼은 "프로젝트당 PM 1명" 을 구조로 보장한다.
  `resource_id bigint` 는 다형성 참조라 FK 제약을 걸 수 없다

임의 부여(특정 사용자에게만 공유)가 필요해지면 **테이블을 추가하고 `can()` 에 OR 한 줄**을
붙인다. 판정을 한 곳에 모아둔 진짜 이득이 그것이다.

## 나중에 볼 것

이번 작업 중 확인했지만 목표 밖이라 손대지 않는 것들. 별건으로 다룬다.

- **`search` 필터 우회** — `filter.epicIds ?: visibleEpicIds(user)` 라 호출자가 id 를 주면
  스코프가 무시된다. `ChatReadHandler:77,90` 이 실제로 그렇게 넣는다. 조회 범위를
  ID 목록이 아니라 쿼리 조건으로 바꾸면 구조적으로 막히지만, 그건 `search()` 리팩토링 몫
- **`TeamService.findAll`** — `teamRepository.findAll()` 직행. `visibleTeamIds()` 를 안 쓴다.
  담당자 드롭다운용 의도적 개방인지 먼저 판단 필요
- **`DashboardService`** — 리포지토리를 직접 호출한다(`:58`, `:146`, `:183`). 서비스 계층을
  우회하므로 서비스에 가드를 걸어도 안 걸린다
- **`Role.auth` 와 `Role.name` 이원화** — `getAuthority()` 는 `auth`, `hasRole` 은 `name` 을 본다.
  `POST /roles` 로 `name='SUPER', auth='ADMIN'` 같은 조합이 만들어진다. 통합하려면
  운영 데이터 확인과 마이그레이션이 필요해서 별건
- **필터 DTO 4개가 `chat/dto/` 에 있다** — 도메인이 chat 을 import 한다(`TaskCustomRepositoryImpl:6`).
  의존 방향이 거꾸로지만 패키지 이관은 chat 파일을 대량으로 건드린다
- **`projects.pm_id` 에 제약이 없다** — `V1__baseline.sql:127` 에 FK 조차 없고 `ensurePmExists` 는
  사용자 존재만 본다. "pmId 는 PM/PO 역할자여야 한다" 는 불변식을 쓰기 시점에 강제해야 한다.
  운영 데이터에 이미 위반 행이 있을 수 있어 확인이 선행돼야 하고, 프로젝트 생성/수정이 400 나기
  시작하는 동작 변경이라 별건
- **엔티티 판정으로 바뀌며 404/403 이 뒤바뀐 자리가 있다** — 존재하지 않는 리소스에 대해
  기존에는 비관리자가 403(존재 여부 비노출), 관리자가 404 였다. 이제 둘 다 404 다. 일관돼진
  쪽이지만 존재 여부가 드러난다. 사내 도구라 그대로 두되 기록해둔다
- **`EpicAssignmentService.unassign` 이 배정 해제 시 담당 태스크를 삭제**한다. 권한과 무관하게 위험

## 완료된 것

**`/admin/**`, `/roles/**` 경로 차단** — ✅ `864e4b6`. `SecurityConfig` 에 `hasRole(ADMIN)` 추가,
`GET /users` 신설. 일반 사용자 403 / ADMIN 200 확인함.

`GET /users` 신설로 FE 가 사용자 목록 호출 경로를 바꿔야 한다. `/admin/users` 를 담당자
드롭다운에 쓰고 있으면 배포 순서를 맞춘다. `docs/refactor-log.md` 에 FE 전달사항으로 기록할 것.

## 남겨두는 결정

**ADMIN 이 시스템 관리와 전사 업무 권한을 겸한다.** 해당 계정이 대표 1개뿐이라 분리 이득이 없다.
시스템 관리자와 경영진이 다른 사람이 되면 그때 `DIRECTOR` 같은 업무 역할로 분리한다.

**PM 은 프로젝트당 1명(`projects.pm_id`)을 유지한다.** 공동 PM 이 필요해지면 조인 테이블로 바꾸되
권한 판정은 `project.pmId == 나` → `project.isPm(나)` 한 줄만 바뀐다. 위임 구조는 영향 없다.
