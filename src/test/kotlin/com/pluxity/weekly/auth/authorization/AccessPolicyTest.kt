package com.pluxity.weekly.auth.authorization

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.dummyEpic
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.project.entity.dummyProject
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.dummyTask
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.entity.dummyTeam
import com.pluxity.weekly.team.repository.TeamRepository
import com.pluxity.weekly.test.entity.dummyRole
import com.pluxity.weekly.test.entity.dummyUser
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * 규칙 표를 그대로 순회한다. 각 (리소스, 액션) 마다 "통과하는 행위자 집합" 하나로 단언하므로
 * 어느 행위자가 뒤집혔는지 실패 메시지에서 바로 보인다.
 *
 * DB 를 띄우지 않는 이유: 판정 대부분이 엔티티 필드를 읽는 순수 함수다. 리포지토리가 필요한 건
 * 에픽 배정 조회 둘뿐이라 그것만 스텁한다. 실제 쿼리 동작은 AccessPolicyQueryIntegrationTest 에서 본다.
 */
class AccessPolicyTest :
    BehaviorSpec({

        val epicRepository: EpicRepository = mockk()
        val projectRepository: ProjectRepository = mockk()
        val teamRepository: TeamRepository = mockk()
        val policy = AccessPolicy(epicRepository, projectRepository, teamRepository)

        fun actor(
            id: Long,
            vararg roles: String,
        ): User =
            dummyUser(id = id, username = "user$id").apply {
                roles.forEachIndexed { i, r -> addRole(dummyRole(id = id * 10 + i, name = r)) }
            }

        val admin = actor(1L, "ADMIN")
        val pmA = actor(10L, "PM")
        val pmB = actor(20L, "PM")
        val worker = actor(30L) // projectA 의 epic1 에 배정된 워커
        val assigneeUser = actor(31L) // task1 담당자. 에픽에는 미배정
        val leader = actor(40L, "LEADER")
        val stranger = actor(50L)
        val roleless = actor(60L) // 역할 없이 pmId 로만 지정된 사용자

        val projectA = dummyProject(id = 100L, pmId = 10L)
        val projectB = dummyProject(id = 200L, pmId = 20L)
        val projectC = dummyProject(id = 300L, pmId = 60L) // PM 역할 없는 관리자
        val epic1 = dummyEpic(id = 110L, project = projectA)
        val task1 = dummyTask(id = 111L, epic = epic1).apply { assignee = assigneeUser }
        val teamX = dummyTeam(id = 400L, leaderId = 40L)

        // 에픽 배정은 worker 하나뿐
        every { epicRepository.existsByAssignmentsUserIdAndId(any(), any()) } returns false
        every { epicRepository.existsByAssignmentsUserIdAndProjectId(any(), any()) } returns false
        every { epicRepository.existsByAssignmentsUserIdAndId(30L, 110L) } returns true
        every { epicRepository.existsByAssignmentsUserIdAndProjectId(30L, 100L) } returns true

        val actors =
            linkedMapOf(
                "ADMIN" to admin,
                "PM-A" to pmA,
                "PM-B" to pmB,
                "배정워커" to worker,
                "담당자" to assigneeUser,
                "팀리더" to leader,
                "무관" to stranger,
            )

        fun onProject(
            p: Project,
            a: AccessAction,
        ) = actors.filterValues { policy.can(it, p, a) }.keys

        fun onEpic(
            e: Epic,
            a: AccessAction,
        ) = actors.filterValues { policy.can(it, e, a) }.keys

        fun onTask(
            t: Task,
            a: AccessAction,
        ) = actors.filterValues { policy.can(it, t, a) }.keys

        fun onTeam(
            t: Team,
            a: AccessAction,
        ) = actors.filterValues { policy.can(it, t, a) }.keys

        Given("프로젝트") {
            Then("VIEW — isManagerOf | 하위에 내가 배정된 에픽 있음") {
                onProject(projectA, AccessAction.VIEW) shouldBe setOf("ADMIN", "PM-A", "배정워커")
            }
            Then("EDIT — isManagerOf. 배정워커는 볼 수만 있고 못 고친다") {
                onProject(projectA, AccessAction.EDIT) shouldBe setOf("ADMIN", "PM-A")
            }
            Then("DELETE — isManagerOf") {
                onProject(projectA, AccessAction.DELETE) shouldBe setOf("ADMIN", "PM-A")
            }
            Then("남의 프로젝트는 ADMIN 만") {
                onProject(projectB, AccessAction.VIEW) shouldBe setOf("ADMIN", "PM-B")
                onProject(projectB, AccessAction.EDIT) shouldBe setOf("ADMIN", "PM-B")
            }
            Then("무의미한 조합은 기본 거부") {
                onProject(projectA, AccessAction.ASSIGN) shouldBe emptySet()
                onProject(projectA, AccessAction.APPROVE) shouldBe emptySet()
            }
        }

        Given("에픽") {
            Then("VIEW — isManagerOf | 배정된 나") {
                onEpic(epic1, AccessAction.VIEW) shouldBe setOf("ADMIN", "PM-A", "배정워커")
            }
            Then("EDIT / DELETE — isManagerOf") {
                onEpic(epic1, AccessAction.EDIT) shouldBe setOf("ADMIN", "PM-A")
                onEpic(epic1, AccessAction.DELETE) shouldBe setOf("ADMIN", "PM-A")
            }
            Then("ASSIGN — 팀 리더 특례가 제거됐다 (정책 변경)") {
                onEpic(epic1, AccessAction.ASSIGN) shouldBe setOf("ADMIN", "PM-A")
            }
            Then("무의미한 조합은 기본 거부") {
                onEpic(epic1, AccessAction.APPROVE) shouldBe emptySet()
            }
        }

        Given("태스크") {
            Then("VIEW / EDIT / DELETE — isManagerOf | 담당자 본인") {
                onTask(task1, AccessAction.VIEW) shouldBe setOf("ADMIN", "PM-A", "담당자")
                onTask(task1, AccessAction.EDIT) shouldBe setOf("ADMIN", "PM-A", "담당자")
                onTask(task1, AccessAction.DELETE) shouldBe setOf("ADMIN", "PM-A", "담당자")
            }
            Then("에픽에 배정됐다고 남의 태스크를 볼 수는 없다") {
                onTask(task1, AccessAction.VIEW).contains("배정워커") shouldBe false
            }
            Then("APPROVE — 담당자는 자기 태스크를 승인하지 못한다") {
                onTask(task1, AccessAction.APPROVE) shouldBe setOf("ADMIN", "PM-A")
            }
            Then("담당자 없는 태스크는 관리자만") {
                onTask(dummyTask(id = 112L, epic = epic1), AccessAction.VIEW) shouldBe setOf("ADMIN", "PM-A")
            }
            Then("무의미한 조합은 기본 거부") {
                onTask(task1, AccessAction.ASSIGN) shouldBe emptySet()
            }
        }

        Given("팀") {
            Then("VIEW — isLeaderOf") {
                onTeam(teamX, AccessAction.VIEW) shouldBe setOf("ADMIN", "팀리더")
            }
            Then("수정·삭제는 판정 대상이 아니다 — ADMIN 여부를 역할로 본다") {
                onTeam(teamX, AccessAction.EDIT) shouldBe emptySet()
                onTeam(teamX, AccessAction.DELETE) shouldBe emptySet()
            }
        }

        Given("생성 — 부모에 대해 검사한다") {
            Then("프로젝트 생성은 ADMIN 만") {
                actors.filterValues { policy.canCreateProject(it) }.keys shouldBe setOf("ADMIN")
            }
            Then("에픽 생성은 해당 프로젝트 관리자만") {
                actors.filterValues { policy.canCreateEpic(it, projectA) }.keys shouldBe setOf("ADMIN", "PM-A")
            }
            Then("태스크 생성은 에픽 VIEW 와 같다 — 배정된 워커도 본인 업무를 등록한다") {
                actors.filterValues { policy.canCreateTask(it, epic1) }.keys shouldBe setOf("ADMIN", "PM-A", "배정워커")
            }
        }

        Given("PM/PO 역할 없이 pmId 로만 지정된 사용자") {
            Then("자기 프로젝트를 관리할 수 있다 (정책 변경 — 예전엔 조용히 거부됐다)") {
                policy.can(roleless, projectC, AccessAction.EDIT) shouldBe true
                policy.can(roleless, projectC, AccessAction.VIEW) shouldBe true
            }
            Then("남의 프로젝트는 여전히 거부된다") {
                policy.can(roleless, projectA, AccessAction.EDIT) shouldBe false
            }
        }

        Given("역할 판정") {
            Then("hasAnyRole 은 나열한 역할 중 하나라도 있으면 참") {
                policy.hasAnyRole(pmA, UserType.ADMIN, UserType.PM, UserType.PO) shouldBe true
                policy.hasAnyRole(leader, UserType.ADMIN, UserType.PM, UserType.PO) shouldBe false
                policy.hasAnyRole(leader, UserType.ADMIN, UserType.TEAM_LEADER) shouldBe true
                policy.hasAnyRole(stranger, UserType.ADMIN) shouldBe false
            }
            Then("requireAnyRole 은 실패 시 PERMISSION_DENIED") {
                shouldNotThrowAny { policy.requireAnyRole(admin, UserType.ADMIN) }
                shouldThrow<CustomException> {
                    policy.requireAnyRole(stranger, UserType.ADMIN)
                }.code shouldBe ErrorCode.PERMISSION_DENIED
            }
        }

        Given("require 는 can 의 래퍼다") {
            Then("통과하면 예외 없음") {
                shouldNotThrowAny { policy.require(pmA, projectA, AccessAction.EDIT) }
            }
            Then("거부되면 PERMISSION_DENIED") {
                shouldThrow<CustomException> {
                    policy.require(stranger, projectA, AccessAction.VIEW)
                }.code shouldBe ErrorCode.PERMISSION_DENIED
                shouldThrow<CustomException> {
                    policy.require(assigneeUser, task1, AccessAction.APPROVE)
                }.code shouldBe ErrorCode.PERMISSION_DENIED
            }
        }
    })
