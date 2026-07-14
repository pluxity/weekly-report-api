package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.llm.dto.TokenUsage
import com.pluxity.weekly.chat.llm.dto.WeeklyReportClassifyResult
import com.pluxity.weekly.chat.llm.dto.WeeklyReportMatchResult
import com.pluxity.weekly.chat.service.ChatLogData
import com.pluxity.weekly.chat.service.ChatLogService
import com.pluxity.weekly.chat.service.ChatPromptBuilder
import com.pluxity.weekly.chat.v2.dto.ChatV2WeeklyReportResponse
import com.pluxity.weekly.chat.v2.dto.ToolMessage
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.report.dto.MatchedAgainstPrev
import com.pluxity.weekly.report.service.WeeklyReportService
import com.pluxity.weekly.report.service.enrichMatched
import com.pluxity.weekly.report.service.numberItems
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.repository.TeamRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val log = KotlinLogging.logger {}

private const val EMPTY_BODY_GUIDE =
    "주간보고 본문이 없습니다. 명령과 함께 보고 내용을 붙여서 보내주세요.\n" +
        "예)\n주간보고 작성해줘\n홍길동\n- 이번주: OO 기능 개발 완료\n- 다음주: OO 테스트 진행"

/**
 * 주간보고 생성 — structured output 파이프라인 (조회 tool 루프와 별개의 single-shot 경로).
 * 본문 검증 → classify(json_schema) → 지난주 매칭(json_schema, best-effort) → upsert.
 *
 * v1과 달리 context stuffing(DB 스냅샷 주입)·content JSON 텍스트 추출이 없다 —
 * 스키마는 프로바이더가 강제하고(단일 팀·섹션 구조), id·주차 정규화·저장은 서버가 소유한다.
 * 사용자 실수(본문 없음·항목 0·리더 아님)는 예외가 아니라 안내 reply로 반환한다.
 */
@Service
class ChatV2WeeklyReportService(
    private val llmClient: ChatV2LlmClient,
    private val promptBuilder: ChatPromptBuilder,
    private val weeklyReportService: WeeklyReportService,
    private val teamRepository: TeamRepository,
    private val authorizationService: AuthorizationService,
    private val chatLogService: ChatLogService,
    private val userLock: ChatV2UserLock,
    private val objectMapper: ObjectMapper,
) {
    private val classifyPrompt: String by lazy {
        ClassPathResource("llm/chat-v2-weekly-report-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    fun create(message: String): ChatV2WeeklyReportResponse {
        val userId = authorizationService.currentUser().requiredId
        return userLock.withLock(userId) {
            val logData = ChatLogData(userId = userId, requestMessage = message)
            try {
                val response = process(userId, message, logData)
                logData.success = true
                response
            } catch (e: Exception) {
                logData.errorMessage = e.message
                throw e
            } finally {
                chatLogService.record(logData)
            }
        }
    }

    private fun process(
        userId: Long,
        message: String,
        logData: ChatLogData,
    ): ChatV2WeeklyReportResponse {
        // LLM 호출 전 차단 — 팀 리더만, 본문 없이 명령 한 줄이면 classify가 보고서를 지어낸다(환각)
        val team =
            teamRepository.findByLeaderId(userId).firstOrNull()
                ?: return ChatV2WeeklyReportResponse(reply = "주간보고 작성은 팀 리더만 할 수 있어요.")
        if (message.lines().count { it.isNotBlank() } < 2) {
            return ChatV2WeeklyReportResponse(reply = EMPTY_BODY_GUIDE)
        }

        val classifyResult =
            llmClient.callStructured(
                messages = buildClassifyMessages(team, message),
                schemaName = ChatV2WeeklyReportSchemas.CLASSIFY_NAME,
                schema = ChatV2WeeklyReportSchemas.CLASSIFY,
            )
        var usage = classifyResult.usage
        val classify = parseClassify(classifyResult.content)

        val f = classify.formatted
        if (f.thisWeek.isEmpty() && f.nextWeek.isEmpty() && f.issues.isEmpty() && f.others.isEmpty()) {
            logData.recordAction(usage)
            return ChatV2WeeklyReportResponse(
                reply = "보고 항목을 인식하지 못했습니다. $EMPTY_BODY_GUIDE",
                inputTokens = usage.promptTokens,
                outputTokens = usage.completionTokens,
            )
        }

        // 매칭은 tx 밖 best-effort — 실패해도 저장은 진행 (id 복원·missing/new 계산은 서버 소유)
        val (matched, matchUsage) = matchAgainstPrev(team, classify)
        usage += matchUsage
        val report = weeklyReportService.upsertFromClassify(team, message, classify, matched)

        logData.recordAction(usage)
        log.info { "chat/v2 주간보고 저장 — team=${team.requiredId}, week=${report.weekStart}, tokens=${usage.totalTokens}" }
        return ChatV2WeeklyReportResponse(
            reply = "'${team.name}' 팀 주간보고를 저장했어요. 정리된 내용을 확인해주세요.",
            weeklyReport = report,
            inputTokens = usage.promptTokens,
            outputTokens = usage.completionTokens,
        )
    }

    private fun buildClassifyMessages(
        team: Team,
        message: String,
    ): List<ToolMessage> {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val dayKo = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val system = "$classifyPrompt\n## 오늘\n$today ($dayKo)\n## 요청자 팀\n${team.name}"
        return listOf(
            ToolMessage(role = "system", content = system),
            ToolMessage(role = "user", content = message),
        )
    }

    /** json_schema가 구조를 강제하므로 여기 실패는 스키마-DTO 계약 불일치(버그)다 — fence 전처리 없이 바로 파싱 */
    private fun parseClassify(content: String): WeeklyReportClassifyResult =
        try {
            objectMapper.readValue(content, WeeklyReportClassifyResult::class.java)
        } catch (e: Exception) {
            log.error(e) { "chat/v2 classify 역직렬화 실패: $content" }
            throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
        }

    /**
     * 지난주 보고의 nextWeek ↔ 이번주 thisWeek 의미 매칭 (v1과 같은 numberItems/enrichMatched 골격).
     * LLM은 P/C 번호 쌍만 반환하고 항목 복원·1:1 보장·missing/new 계산은 서버가 한다.
     * 지난주 보고 없음·이번주 항목 없음·호출 실패 → null (best-effort).
     */
    private fun matchAgainstPrev(
        team: Team,
        classify: WeeklyReportClassifyResult,
    ): Pair<MatchedAgainstPrev?, TokenUsage> {
        val prevNextWeek = weeklyReportService.findPrevWeekNextItems(team, classify.weekStart)
        if (prevNextWeek.isEmpty() || classify.formatted.thisWeek.isEmpty()) return null to TokenUsage()
        val prevById = numberItems(prevNextWeek, "P")
        val currById = numberItems(classify.formatted.thisWeek, "C")
        return try {
            val messages =
                promptBuilder
                    .buildMatchMessages(prevById, currById)
                    .map { ToolMessage(role = it.role, content = it.content) }
            val result = llmClient.callStructured(messages, ChatV2WeeklyReportSchemas.MATCH_NAME, ChatV2WeeklyReportSchemas.MATCH)
            val raw = objectMapper.readValue(result.content, WeeklyReportMatchResult::class.java)
            enrichMatched(raw, prevById, currById) to result.usage
        } catch (e: Exception) {
            log.warn(e) { "chat/v2 주간보고 매칭 실패 (team=${team.requiredId}) — 매칭 없이 저장" }
            null to TokenUsage()
        }
    }
}
