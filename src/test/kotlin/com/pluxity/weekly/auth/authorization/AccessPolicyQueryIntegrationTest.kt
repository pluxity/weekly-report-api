package com.pluxity.weekly.auth.authorization

import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.test.ContainerConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

/**
 * [AccessPolicy] 가 쓰는 배정 조회 쿼리만 실제 DB 로 검증한다.
 *
 * 판정 규칙 자체는 [AccessPolicyTest] 에서 DB 없이 표로 검증한다. 여기서 보는 건 파생 쿼리가
 * 실제로 만들어지는지, 특히 `existsByAssignmentsUserIdAndProjectId` 가 epic → project 를 타고
 * 조인되는지다. 이 메서드는 이번 리팩토링에서 새로 추가돼 실행된 적이 없다.
 * soft delete(`epics.deleted`) 가 걸린 상태에서도 맞게 도는지도 함께 본다.
 */
@SpringBootTest
@Import(ContainerConfig::class)
class AccessPolicyQueryIntegrationTest(
    private val jdbc: JdbcTemplate,
    private val epicRepository: EpicRepository,
) : BehaviorSpec({
        extension(SpringExtension)

        fun clean() =
            jdbc.execute(
                """
                TRUNCATE task_approval_logs, tasks, epic_assignments, epics, projects,
                         team_members, teams, user_role, roles, users RESTART IDENTITY CASCADE
                """.trimIndent(),
            )

        fun insertId(sql: String): Long = jdbc.queryForObject("$sql RETURNING id", Long::class.java)!!

        fun insertUser(username: String): Long =
            insertId(
                "INSERT INTO users (username, password, name, last_password_change_date, created_at, updated_at) " +
                    "VALUES ('$username', 'pw', '$username', now(), now(), now())",
            )

        fun insertProject(name: String): Long =
            insertId(
                "INSERT INTO projects (name, status, deleted, created_at, updated_at) " +
                    "VALUES ('$name', 'TODO', false, now(), now())",
            )

        fun insertEpic(
            projectId: Long,
            name: String,
            deleted: Boolean = false,
        ): Long =
            insertId(
                "INSERT INTO epics (project_id, name, status, deleted, created_at, updated_at) " +
                    "VALUES ($projectId, '$name', 'TODO', $deleted, now(), now())",
            )

        fun assign(
            epicId: Long,
            userId: Long,
        ) = jdbc.update(
            "INSERT INTO epic_assignments (epic_id, user_id, created_at, updated_at) " +
                "VALUES ($epicId, $userId, now(), now())",
        )

        Given("워커가 프로젝트A 의 에픽 하나에만 배정돼 있다") {
            clean()
            val worker = insertUser("worker")
            val stranger = insertUser("stranger")
            val projectA = insertProject("A")
            val projectB = insertProject("B")
            val epicA = insertEpic(projectA, "A-1")
            insertEpic(projectB, "B-1")
            assign(epicA, worker)

            Then("에픽 단위 조회 — 배정된 에픽만 참") {
                epicRepository.existsByAssignmentsUserIdAndId(worker, epicA) shouldBe true
                epicRepository.existsByAssignmentsUserIdAndId(stranger, epicA) shouldBe false
            }

            Then("프로젝트 단위 조회 — epic → project 를 타고 조인된다") {
                epicRepository.existsByAssignmentsUserIdAndProjectId(worker, projectA) shouldBe true
                epicRepository.existsByAssignmentsUserIdAndProjectId(worker, projectB) shouldBe false
                epicRepository.existsByAssignmentsUserIdAndProjectId(stranger, projectA) shouldBe false
            }
        }

        Given("배정된 에픽이 soft delete 된 상태") {
            clean()
            val worker = insertUser("worker")
            val projectA = insertProject("A")
            val deletedEpic = insertEpic(projectA, "삭제됨", deleted = true)
            assign(deletedEpic, worker)

            Then("삭제된 에픽은 접근 근거가 되지 않는다") {
                epicRepository.existsByAssignmentsUserIdAndId(worker, deletedEpic) shouldBe false
                epicRepository.existsByAssignmentsUserIdAndProjectId(worker, projectA) shouldBe false
            }
        }

        Given("같은 프로젝트의 에픽 여러 개에 배정") {
            clean()
            val worker = insertUser("worker")
            val projectA = insertProject("A")
            val e1 = insertEpic(projectA, "A-1")
            val e2 = insertEpic(projectA, "A-2")
            assign(e1, worker)
            assign(e2, worker)

            Then("exists 라 중복 행이 있어도 참 하나로 떨어진다") {
                epicRepository.existsByAssignmentsUserIdAndProjectId(worker, projectA) shouldBe true
            }
        }
    })
