package com.pluxity.weekly.chat.v2.eval

import com.pluxity.weekly.auth.user.entity.Role
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.RoleRepository
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.auth.user.repository.UserRoleRepository
import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.epic.entity.EpicStatus
import com.pluxity.weekly.epic.repository.EpicRepository
import com.pluxity.weekly.project.entity.Project
import com.pluxity.weekly.project.entity.ProjectStatus
import com.pluxity.weekly.project.repository.ProjectRepository
import com.pluxity.weekly.report.dto.FormattedReport
import com.pluxity.weekly.report.dto.ReportItem
import com.pluxity.weekly.report.entity.WeeklyReport
import com.pluxity.weekly.report.repository.WeeklyReportRepository
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskApprovalAction
import com.pluxity.weekly.task.entity.TaskApprovalLog
import com.pluxity.weekly.task.entity.TaskStatus
import com.pluxity.weekly.task.repository.TaskApprovalLogRepository
import com.pluxity.weekly.task.repository.TaskRepository
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.entity.TeamMember
import com.pluxity.weekly.team.repository.TeamMemberRepository
import com.pluxity.weekly.team.repository.TeamRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 로컬 eval 전용 결정적 seed 데이터 주입기.
 *
 * - `eval` 프로파일에서만 동작한다(프로덕션 보호). 실행: `--spring.profiles.active=local,eval`
 * - 도메인 서비스가 아니라 **리포지토리를 직접** 사용한다. 서비스는 `authorizationService.currentUser()`로
 *   SecurityContext를 요구하는데 seeder에는 인증 컨텍스트가 없어 터지기 때문이다.
 * - 실행마다 seed 테이블을 FK 안전한 역순으로 **전부 물리 삭제 후 재삽입**한다(전용 eval DB 전제).
 *   User/Project/Epic/Task는 `@SoftDelete`라 JPA delete가 soft-delete(update)로 바뀌어 unique 제약을
 *   재삽입에서 깨뜨리므로, 해당 4개 테이블은 네이티브 `DELETE`로 물리 삭제한다. 나머지는 repository로 삭제.
 * - 날짜는 전부 오늘(Asia/Seoul) 기준 상대값이라 언제 돌려도 이번주/지연/다음주 의미가 유지된다.
 */
@Component
@Profile("eval")
class EvalDataSeeder(
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val projectRepository: ProjectRepository,
    private val epicRepository: EpicRepository,
    private val taskRepository: TaskRepository,
    private val taskApprovalLogRepository: TaskApprovalLogRepository,
    private val weeklyReportRepository: WeeklyReportRepository,
    private val passwordEncoder: PasswordEncoder,
    private val entityManager: EntityManager,
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(EvalDataSeeder::class.java)

    private val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val mondayThisWeek: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    @Transactional
    override fun run(vararg args: String) {
        log.info("[eval-seed] 시작 (today={}, weekStart={})", today, mondayThisWeek)

        wipe()
        val counts = seed()

        log.info(
            "[eval-seed] 완료 — roles={}, users={}, teams={}, teamMembers={}, projects={}, " +
                "epics={}, tasks={}, approvalLogs={}, weeklyReports={}",
            counts.roles,
            counts.users,
            counts.teams,
            counts.teamMembers,
            counts.projects,
            counts.epics,
            counts.tasks,
            counts.approvalLogs,
            counts.weeklyReports,
        )
    }

    /** FK 안전한 역순 물리 삭제. soft-delete 4개 테이블은 네이티브 DELETE. */
    private fun wipe() {
        taskApprovalLogRepository.deleteAllInBatch()
        weeklyReportRepository.deleteAllInBatch()
        hardDelete("tasks") // @SoftDelete
        hardDelete("epic_assignments") // 전용 repository 없음
        hardDelete("epics") // @SoftDelete
        hardDelete("projects") // @SoftDelete
        teamMemberRepository.deleteAllInBatch()
        teamRepository.deleteAllInBatch()
        userRoleRepository.deleteAllInBatch()
        hardDelete("users") // @SoftDelete
        roleRepository.deleteAllInBatch()
    }

    private fun hardDelete(table: String) {
        entityManager.createNativeQuery("DELETE FROM $table").executeUpdate()
    }

    private fun seed(): SeedCounts {
        // ── roles ─────────────────────────────────────────────
        val roleAdmin = roleRepository.save(Role(name = "ADMIN", description = "관리자", auth = "ADMIN"))
        val rolePm = roleRepository.save(Role(name = "PM", description = "프로젝트 매니저", auth = "USER"))
        roleRepository.save(Role(name = "PO", description = "프로덕트 오너", auth = "USER"))
        val roleLeader = roleRepository.save(Role(name = "LEADER", description = "팀 리더", auth = "USER"))
        roleRepository.save(Role(name = "WORKER", description = "작업자", auth = "USER"))
        roleRepository.save(Role(name = "USER", description = "기본 사용자", auth = "USER"))

        // ── users (비번 전부 evaltest123) ─────────────────────
        val encoded: String = requireNotNull(passwordEncoder.encode("evaltest123"))

        val admin = newUser("admin", "김관리", encoded, roles = listOf(roleAdmin))
        val leader = newUser("leader", "이도경", encoded, roles = listOf(roleLeader, rolePm))
        val member = newUser("member", "박서준", encoded, roles = emptyList())
        val yuna = newUser("yuna", "최유나", encoded, roles = emptyList())
        val minho = newUser("minho", "정민호", encoded, roles = emptyList())
        // 미제출 팀(데이터플랫폼팀)용 — admin "안 낸 팀" 조회 테스트용. 이 팀은 weekly_report를 만들지 않는다.
        val dataLeader = newUser("dataleader", "한지민", encoded, roles = listOf(roleLeader))
        val dataMember = newUser("datamember", "오세훈", encoded, roles = emptyList())

        // ── team + members ───────────────────────────────────
        val team = teamRepository.save(Team(name = "플랫폼개발팀", leaderId = leader.id))
        val members = listOf(member, yuna, minho)
        members.forEach { teamMemberRepository.save(TeamMember(team = team, user = it)) }

        // 2번째 팀 — 이번주 주간보고 미제출(의도적). admin이 "안 낸 팀"으로 잡아야 하는 대상.
        val dataTeam = teamRepository.save(Team(name = "데이터플랫폼팀", leaderId = dataLeader.id))
        val dataMembers = listOf(dataMember)
        dataMembers.forEach { teamMemberRepository.save(TeamMember(team = dataTeam, user = it)) }

        // ── projects (pm=이도경) ──────────────────────────────
        val singapore =
            projectRepository.save(
                Project(
                    name = "싱가포르 물류 플랫폼",
                    status = ProjectStatus.IN_PROGRESS,
                    startDate = today.minusDays(60),
                    dueDate = today.minusDays(10),
                    pmId = leader.id,
                ),
            )
        val cctv =
            projectRepository.save(
                Project(
                    name = "CCTV 통합관제 고도화",
                    status = ProjectStatus.IN_PROGRESS,
                    startDate = today.minusDays(30),
                    dueDate = today.plusDays(30),
                    pmId = leader.id,
                ),
            )

        // ── epics ─────────────────────────────────────────────
        val epicDelivery = newEpic(singapore, "배송 추적 API")
        val epicSettlement = newEpic(singapore, "정산 모듈")
        val epicStreaming = newEpic(cctv, "영상 스트리밍")
        val epicAlert = newEpic(cctv, "이벤트 알림")

        // ── tasks ─────────────────────────────────────────────
        val tasks = mutableListOf<Task>()
        fun task(
            epic: Epic,
            name: String,
            assignee: User,
            status: TaskStatus,
            dueOffset: Long,
            startOffset: Long,
            completedOffset: Long?,
            progress: Int,
            description: String? = null,
        ): Task {
            val t =
                Task(
                    epic = epic,
                    name = name,
                    description = description,
                    status = status,
                    progress = progress,
                    startDate = today.plusDays(startOffset),
                    dueDate = today.plusDays(dueOffset),
                    assignee = assignee,
                )
            completedOffset?.let { t.completedAt = today.plusDays(it) }
            tasks += t
            return t
        }

        task(epicDelivery, "배송 상태 조회 API", member, TaskStatus.IN_PROGRESS, 8, -5, null, 40)
        task(epicSettlement, "정산 리포트 엑셀", member, TaskStatus.DONE, -4, -14, -1, 100)
        task(epicDelivery, "운송장 번호 검증", yuna, TaskStatus.IN_PROGRESS, 1, -3, null, 30)
        task(epicDelivery, "배송 알림 톡 연동", yuna, TaskStatus.TODO, 2, -1, null, 0)
        task(epicSettlement, "정산 오차 검증 로직", yuna, TaskStatus.IN_PROGRESS, 3, -2, null, 20)
        task(epicSettlement, "월마감 정산 배치", yuna, TaskStatus.IN_PROGRESS, -3, -10, null, 50) // 지연
        task(epicStreaming, "실시간 스트리밍 버퍼링 개선", minho, TaskStatus.IN_PROGRESS, 2, -4, null, 60)
        task(epicStreaming, "코덱 업그레이드", minho, TaskStatus.DONE, -6, -16, -2, 100)
        val pushTask = task(epicAlert, "이벤트 푸시 발송", member, TaskStatus.IN_REVIEW, 5, -8, null, 100)
        val retryTask = task(epicDelivery, "배송 지연 리트라이", yuna, TaskStatus.IN_REVIEW, -1, -9, null, 100) // 지연+리뷰대기
        task(
            epicStreaming,
            "CCTV 연동 API",
            member,
            TaskStatus.IN_PROGRESS,
            20,
            -10,
            null,
            25,
            description = "CCTV 장비 RTSP 스트림을 관제 서버에 연동하는 API. 인증·재연결 처리 포함.",
        )

        taskRepository.saveAll(tasks)

        // ── epic_assignments (task 담당자를 상위 epic에도 배정) ──
        listOf(epicDelivery, epicSettlement, epicStreaming, epicAlert).forEach { epic ->
            tasks
                .filter { it.epic === epic }
                .mapNotNull { it.assignee }
                .distinctBy { it.id }
                .forEach { epic.assign(it) }
            epicRepository.save(epic)
        }

        // ── task_approval_logs (append-only, 이력 재현) ────────
        val approvalLogs =
            listOf(
                TaskApprovalLog(task = pushTask, actor = member, action = TaskApprovalAction.REVIEW_REQUEST),
                TaskApprovalLog(task = retryTask, actor = yuna, action = TaskApprovalAction.REVIEW_REQUEST),
                TaskApprovalLog(
                    task = retryTask,
                    actor = leader,
                    action = TaskApprovalAction.REJECT,
                    reason = "테스트 케이스 누락 — 경계값 처리 추가 필요",
                ),
                TaskApprovalLog(task = retryTask, actor = yuna, action = TaskApprovalAction.REVIEW_REQUEST),
            )
        taskApprovalLogRepository.saveAll(approvalLogs)

        // ── weekly_report (이번주) ────────────────────────────
        val formatted =
            FormattedReport(
                thisWeek =
                    listOf(
                        ReportItem(
                            assignee = "박서준",
                            category = "싱가포르 물류 플랫폼",
                            text = "정산 리포트 엑셀 완료",
                            progress = "100%",
                            dueDate = null,
                        ),
                        ReportItem(
                            assignee = "박서준",
                            category = "싱가포르 물류 플랫폼",
                            text = "배송 상태 조회 API 진행",
                            progress = "40%",
                            dueDate = null,
                        ),
                    ),
                nextWeek =
                    listOf(
                        ReportItem(
                            assignee = "최유나",
                            category = "싱가포르 물류 플랫폼",
                            text = "정산 모듈 마무리",
                            progress = null,
                            dueDate = null,
                        ),
                    ),
            )
        weeklyReportRepository.save(
            WeeklyReport(
                team = team,
                teamNameRaw = "플랫폼개발팀",
                weekStart = mondayThisWeek,
                rawContent = "이번 주 배송 추적 API 진행, 정산 리포트 완료. 다음 주 정산 모듈 마무리 예정.",
                formatted = formatted,
                matchedAgainstPrev = null,
            ),
        )

        return SeedCounts(
            roles = 6,
            users = listOf(admin, leader, member, yuna, minho, dataLeader, dataMember).size,
            teams = 2,
            teamMembers = members.size + dataMembers.size,
            projects = 2,
            epics = 4,
            tasks = tasks.size,
            approvalLogs = approvalLogs.size,
            weeklyReports = 1,
        )
    }

    private fun newUser(
        username: String,
        name: String,
        encodedPassword: String,
        roles: List<Role>,
    ): User {
        val user =
            User(
                username = username,
                password = encodedPassword,
                name = name,
                code = null,
                email = "$username@pluxity.com",
            )
        if (roles.isNotEmpty()) user.addRoles(roles)
        return userRepository.save(user)
    }

    private fun newEpic(
        project: Project,
        name: String,
    ): Epic =
        epicRepository.save(
            Epic(
                project = project,
                name = name,
                status = EpicStatus.IN_PROGRESS,
            ),
        )

    private data class SeedCounts(
        val roles: Int,
        val users: Int,
        val teams: Int,
        val teamMembers: Int,
        val projects: Int,
        val epics: Int,
        val tasks: Int,
        val approvalLogs: Int,
        val weeklyReports: Int,
    )
}
