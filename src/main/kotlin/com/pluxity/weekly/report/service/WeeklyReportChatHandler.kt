package com.pluxity.weekly.report.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatReadResponse
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.exception.ChatClarifyException
import com.pluxity.weekly.chat.llm.LlmService
import com.pluxity.weekly.chat.llm.dto.IntentResult
import com.pluxity.weekly.chat.llm.dto.LlmResult
import com.pluxity.weekly.chat.llm.dto.WeeklyReportClassifyResult
import com.pluxity.weekly.chat.service.ChatPromptBuilder
import com.pluxity.weekly.report.dto.MatchedAgainstPrev
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.repository.TeamRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

private const val EMPTY_BODY_GUIDE =
    "주간보고 본문이 없습니다. 명령과 함께 보고 내용을 붙여서 보내주세요.\n" +
        "예)\n주간보고 작성해줘\n홍길동\n- 이번주: OO 기능 개발 완료\n- 다음주: OO 테스트 진행"

/**
 * weekly_report target 전용 chat 흐름 핸들러.
 * 일반 chat 흐름(LlmAction → ChatActionRouter)을 우회하고
 * classify LLM → 즉시 upsert. create 시 지난주 보고와 매칭(LLM)도 수행.
 */
@Component
class WeeklyReportChatHandler(
    private val authorizationService: AuthorizationService,
    private val teamRepository: TeamRepository,
    private val promptBuilder: ChatPromptBuilder,
    private val llmService: LlmService,
    private val weeklyReportService: WeeklyReportService,
) {
    fun handle(
        intent: IntentResult,
        message: String,
        context: String,
    ): LlmResult<List<ChatActionResponse>> {
        val action =
            intent.action
                ?.let { ChatActionType.fromOrNull(it) }
                ?: throw ChatClarifyException("주간보고 action을 결정할 수 없습니다.")

        return when (action) {
            ChatActionType.CREATE -> handleCreate(message, context, intent)
            ChatActionType.READ -> handleRead(intent)
            ChatActionType.DELETE -> handleDelete(intent)
            else -> throw ChatClarifyException("주간보고는 작성/조회/삭제만 가능합니다.")
        }
    }

    private fun handleRead(intent: IntentResult): LlmResult<List<ChatActionResponse>> {
        val user = authorizationService.currentUser()
        teamRepository.findByLeaderId(user.requiredId).firstOrNull()
            ?: throw ChatClarifyException("주간보고는 팀 리더만 조회할 수 있습니다.")

        val report = weeklyReportService.findForChat(intent.week)
        // 조회는 LLM 미사용 → usage 0
        return LlmResult(
            listOf(
                ChatActionResponse(
                    action = ChatActionType.READ.key,
                    target = ChatTarget.WEEKLY_REPORT.key,
                    message =
                        if (report != null) {
                            "요청하신 주간보고를 찾았어요."
                        } else {
                            "해당 주차에 작성된 주간보고가 없어요. '주간보고 작성해줘'와 함께 본문을 보내면 등록할 수 있어요."
                        },
                    readResult = ChatReadResponse(weeklyReport = report),
                ),
            ),
        )
    }

    private fun handleDelete(intent: IntentResult): LlmResult<List<ChatActionResponse>> {
        val user = authorizationService.currentUser()
        val team =
            teamRepository.findByLeaderId(user.requiredId).firstOrNull()
                ?: throw ChatClarifyException("주간보고는 팀 리더만 삭제할 수 있습니다.")

        val weekStart = resolveWeekStart(intent.week)
        val deletedId =
            weeklyReportService.delete(team, weekStart)
                ?: throw ChatClarifyException("해당 주차에 삭제할 주간보고가 없습니다.")

        // 삭제는 LLM 미사용 → usage 0
        return LlmResult(
            listOf(
                ChatActionResponse(
                    action = ChatActionType.DELETE.key,
                    target = ChatTarget.WEEKLY_REPORT.key,
                    message = "해당 주차 주간보고를 삭제했어요.",
                    id = deletedId,
                ),
            ),
        )
    }

    private fun handleCreate(
        message: String,
        context: String,
        intent: IntentResult,
    ): LlmResult<List<ChatActionResponse>> {
        // 본문 없이 "주간보고 작성해줘"만 온 경우 LLM 호출 전에 차단
        requireReportBody(message)

        // 2차 LLM: classify (system=classify-prompt, user=context + 본문)
        val messages = promptBuilder.buildActionMessages(message, intent, context)
        val classify = llmService.classifyWeeklyReport(messages)
        requireClassifiedItems(classify.value)

        // 작성자 leader 팀 (다중 팀 leader는 후속 — 일단 첫 팀)
        val user = authorizationService.currentUser()
        val teams = teamRepository.findByLeaderId(user.requiredId)
        if (teams.isEmpty()) {
            throw ChatClarifyException("주간보고는 팀 리더만 작성할 수 있습니다.")
        }
        val team = teams.first()

        // 매칭은 tx 밖에서 best-effort 계산 → upsert에 전달 (실패해도 보고 저장은 성공)
        val matched = matchAgainstPrev(team, classify.value)
        val response = weeklyReportService.upsertFromClassify(team, message, classify.value, matched.value)

        val responses =
            listOf(
                ChatActionResponse(
                    action = ChatActionType.CREATE.key,
                    target = ChatTarget.WEEKLY_REPORT.key,
                    message = "'${team.name}' 팀 주간보고를 저장했어요. 정리된 내용을 확인해주세요.",
                    id = response.id,
                    readResult = ChatReadResponse(weeklyReport = response),
                ),
            )
        // classify + match(조건부) 토큰 합산 → ChatLog action_* 으로 매핑됨
        return LlmResult(responses, classify.usage + matched.usage)
    }

    /** 명령 한 줄만 오면 classify LLM이 보고서를 지어내므로(환각) 본문 유무를 먼저 검증한다. */
    private fun requireReportBody(message: String) {
        if (message.lines().count { it.isNotBlank() } < 2) {
            throw ChatClarifyException(EMPTY_BODY_GUIDE)
        }
    }

    /** classify가 항목을 하나도 못 뽑았으면 빈 보고서 upsert 대신 본문 안내로 되돌린다. */
    private fun requireClassifiedItems(classify: WeeklyReportClassifyResult) {
        val f = classify.formatted
        if (f.thisWeek.isEmpty() && f.nextWeek.isEmpty() && f.issues.isEmpty() && f.others.isEmpty()) {
            throw ChatClarifyException("보고 항목을 인식하지 못했습니다. $EMPTY_BODY_GUIDE")
        }
    }

    /**
     * 지난주 보고의 nextWeek와 이번주 thisWeek를 LLM으로 매칭.
     * tx 밖에서 호출 (LLM 콜이 DB 트랜잭션을 점유하지 않도록).
     * 지난주 보고 없음 / 매칭 실패 → null (보고 저장은 그대로 진행, best-effort).
     */
    private fun matchAgainstPrev(
        team: Team,
        classify: WeeklyReportClassifyResult,
    ): LlmResult<MatchedAgainstPrev?> {
        val prevNextWeek = weeklyReportService.findPrevWeekNextItems(team, classify.weekStart)
        if (prevNextWeek.isEmpty()) return LlmResult(null)
        val prevById = numberItems(prevNextWeek, "P")
        val currById = numberItems(classify.formatted.thisWeek, "C")
        return try {
            val raw = llmService.matchWeeklyReport(promptBuilder.buildMatchMessages(prevById, currById))
            LlmResult(enrichMatched(raw.value, prevById, currById), raw.usage)
        } catch (e: Exception) {
            log.warn(e) { "주간보고 매칭 실패 (team=${team.requiredId}) — 매칭 없이 저장" }
            LlmResult(null)
        }
    }
}
