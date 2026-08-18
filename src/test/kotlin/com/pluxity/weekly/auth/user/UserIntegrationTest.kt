package com.pluxity.weekly.auth.user

import com.pluxity.weekly.auth.authentication.security.JwtProvider
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.repository.TaskRepository
import com.pluxity.weekly.test.ContainerConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * 사용자 삭제/퇴사 분리에 대한 통합 테스트.
 * FK 의 ON DELETE 동작, 연관 해소, 인증 필터는 모두 mock 으로 검증할 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainerConfig::class)
class UserIntegrationTest(
    private val jdbc: JdbcTemplate,
    private val taskRepository: TaskRepository,
    private val jwtProvider: JwtProvider,
    private val mockMvc: MockMvc,
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

        fun exists(
            table: String,
            id: Long,
        ): Int = jdbc.queryForObject("SELECT count(*) FROM $table WHERE id = $id", Int::class.java)!!

        fun insertUser(
            username: String,
            name: String,
            retiredAt: String = "NULL",
        ): Long =
            insertId(
                "INSERT INTO users (username, password, name, retired_at, last_password_change_date, created_at, updated_at) " +
                    "VALUES ('$username', 'pw', '$name', $retiredAt, now(), now(), now())",
            )

        fun insertTaskFor(assigneeId: Long?): Pair<Long, Long> {
            val projectId =
                insertId("INSERT INTO projects (name, created_at, updated_at) VALUES ('프로젝트', now(), now())")
            val epicId =
                insertId(
                    "INSERT INTO epics (project_id, name, created_at, updated_at) " +
                        "VALUES ($projectId, '업무그룹', now(), now())",
                )
            val taskId =
                insertId(
                    "INSERT INTO tasks (epic_id, name, assignee_id, created_at, updated_at) " +
                        "VALUES ($epicId, '태스크', ${assigneeId ?: "NULL"}, now(), now())",
                )
            return epicId to taskId
        }

        Given("사용자가 역할·팀·업무그룹에 엮이고 태스크 담당자이자 승인자인 상태에서") {
            clean()
            val userId = insertUser("victim", "삭제대상")
            val roleId = insertId("INSERT INTO roles (name, created_at, updated_at) VALUES ('USER', now(), now())")
            val userRoleId =
                insertId(
                    "INSERT INTO user_role (user_id, role_id, created_at, updated_at) " +
                        "VALUES ($userId, $roleId, now(), now())",
                )
            val teamId = insertId("INSERT INTO teams (name, created_at, updated_at) VALUES ('팀', now(), now())")
            val teamMemberId =
                insertId(
                    "INSERT INTO team_members (team_id, user_id, created_at, updated_at) " +
                        "VALUES ($teamId, $userId, now(), now())",
                )
            val (epicId, taskId) = insertTaskFor(userId)
            val assignmentId =
                insertId(
                    "INSERT INTO epic_assignments (epic_id, user_id, created_at, updated_at) " +
                        "VALUES ($epicId, $userId, now(), now())",
                )
            val logId =
                insertId(
                    "INSERT INTO task_approval_logs (task_id, actor_id, action, created_at, updated_at) " +
                        "VALUES ($taskId, $userId, 'APPROVE', now(), now())",
                )

            When("완전 삭제하면") {
                jdbc.update("DELETE FROM users WHERE id = ?", userId)

                Then("소유물인 역할·소속·배정은 함께 삭제된다") {
                    exists("user_role", userRoleId) shouldBe 0
                    exists("team_members", teamMemberId) shouldBe 0
                    exists("epic_assignments", assignmentId) shouldBe 0
                }

                Then("태스크는 남고 담당자 연결만 끊긴다") {
                    exists("tasks", taskId) shouldBe 1
                    jdbc
                        .queryForObject("SELECT assignee_id FROM tasks WHERE id = $taskId", Long::class.java)
                        .shouldBeNull()
                }

                Then("승인 로그는 남고 수행자 연결만 끊긴다") {
                    exists("task_approval_logs", logId) shouldBe 1
                    jdbc
                        .queryForObject("SELECT actor_id FROM task_approval_logs WHERE id = $logId", Long::class.java)
                        .shouldBeNull()
                }
            }
        }

        Given("퇴사자가 태스크 담당자로 남아 있을 때") {
            clean()
            val userId = insertUser("retiree", "퇴사자", retiredAt = "DATE '2026-08-31'")
            val (_, taskId) = insertTaskFor(userId)

            When("담당자까지 fetch 하는 조회를 실행하면") {
                Then("연관이 정상 해소되고 퇴사 상태가 드러난다") {
                    val task = taskRepository.findByStatus(TaskStatus.TODO).firstOrNull { it.id == taskId }
                    task.shouldNotBeNull()
                    val assignee = task.assignee
                    assignee.shouldNotBeNull()
                    assignee.name shouldBe "퇴사자"
                    assignee.isRetired shouldBe true
                }
            }
        }

        Given("재직 중인 사용자가 액세스 토큰을 발급받은 뒤") {
            clean()
            insertUser("worker", "재직자")
            val cookie = Cookie("AccessToken", jwtProvider.generateAccessToken("worker"))

            When("아직 재직 중이면") {
                Then("인증이 통과한다") {
                    mockMvc
                        .get("/users/me") { cookie(cookie) }
                        .andReturn()
                        .response.status shouldBe 200
                }
            }

            When("퇴사 처리된 뒤 같은 토큰으로 접근하면") {
                jdbc.update("UPDATE users SET retired_at = DATE '2026-08-31' WHERE username = 'worker'")

                Then("토큰이 유효해도 차단된다") {
                    mockMvc
                        .get("/users/me") { cookie(cookie) }
                        .andReturn()
                        .response.status shouldBe 403
                }
            }
        }
    })
