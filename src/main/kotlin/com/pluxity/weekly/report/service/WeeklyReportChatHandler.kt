package com.pluxity.weekly.report.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.dto.ChatActionResponse
import com.pluxity.weekly.chat.dto.ChatActionType
import com.pluxity.weekly.chat.dto.ChatReadResponse
import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.exception.ChatClarifyException
import com.pluxity.weekly.chat.llm.LlmService
import com.pluxity.weekly.chat.llm.dto.IntentResult
import com.pluxity.weekly.chat.service.ChatPromptBuilder
import com.pluxity.weekly.team.repository.TeamRepository
import org.springframework.stereotype.Component

/**
 * weekly_report target 전용 chat 흐름 핸들러.
 * 일반 chat 흐름(LlmAction → ChatActionRouter)을 우회하고
 * classify LLM → WriteService 즉시 upsert.
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
    ): List<ChatActionResponse> {
        val action =
            intent.actions
                .firstOrNull()
                ?.let { ChatActionType.fromOrNull(it) }
                ?: throw ChatClarifyException("주간보고 action을 결정할 수 없습니다.")

        return when (action) {
            ChatActionType.CREATE -> handleCreate(message, context, intent)
            ChatActionType.READ -> handleRead(intent)
            ChatActionType.DELETE -> handleDelete(intent)
            else -> throw ChatClarifyException("주간보고는 작성/조회/삭제만 가능합니다.")
        }
    }

    private fun handleRead(intent: IntentResult): List<ChatActionResponse> {
        val report = weeklyReportService.findForChat(intent.week)
        return listOf(
            ChatActionResponse(
                action = ChatActionType.READ.key,
                target = ChatTarget.WEEKLY_REPORT.key,
                readResult = ChatReadResponse(weeklyReport = report),
            ),
        )
    }

    private fun handleDelete(intent: IntentResult): List<ChatActionResponse> {
        val user = authorizationService.currentUser()
        val team =
            teamRepository.findByLeaderId(user.requiredId).firstOrNull()
                ?: throw ChatClarifyException("주간보고는 팀 리더만 삭제할 수 있습니다.")

        val weekStart = resolveWeekStart(intent.week)
        val deletedId =
            weeklyReportService.delete(team, weekStart)
                ?: throw ChatClarifyException("해당 주차에 삭제할 주간보고가 없습니다.")

        return listOf(
            ChatActionResponse(
                action = ChatActionType.DELETE.key,
                target = ChatTarget.WEEKLY_REPORT.key,
                id = deletedId,
            ),
        )
    }

    private fun handleCreate(
        message: String,
        context: String,
        intent: IntentResult,
    ): List<ChatActionResponse> {
        // 2차 LLM: classify (system=classify-prompt, user=context + 본문)
        val messages = promptBuilder.buildActionMessages(message, intent, context)
        val classify = llmService.classifyWeeklyReport(messages)

        // 작성자 leader 팀 (다중 팀 leader는 후속 — 일단 첫 팀)
        val user = authorizationService.currentUser()
        val teams = teamRepository.findByLeaderId(user.requiredId)
        if (teams.isEmpty()) {
            throw ChatClarifyException("주간보고는 팀 리더만 작성할 수 있습니다.")
        }
        val team = teams.first()

        // UPSERT
        val response = weeklyReportService.upsertFromClassify(team, message, classify)

        return listOf(
            ChatActionResponse(
                action = ChatActionType.CREATE.key,
                target = ChatTarget.WEEKLY_REPORT.key,
                id = response.id,
                readResult = ChatReadResponse(weeklyReport = response),
            ),
        )
    }
}
