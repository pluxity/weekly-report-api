package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.llm.dto.IntentResult
import com.pluxity.weekly.chat.llm.dto.Message
import com.pluxity.weekly.report.dto.ReportItem
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Component
class ChatPromptBuilder(
    private val objectMapper: ObjectMapper,
) {
    private val systemPrompt: String by lazy {
        ClassPathResource("llm/system-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    private val intentPrompt: String by lazy {
        ClassPathResource("llm/intent-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    private val answerPrompt: String by lazy {
        ClassPathResource("llm/answer-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    private val weeklyReportClassifyPrompt: String by lazy {
        ClassPathResource("llm/weekly-report-classify-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    private val weeklyReportMatchPrompt: String by lazy {
        ClassPathResource("llm/weekly-report-match-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    fun buildIntentMessages(
        message: String,
        history: List<Message>,
    ): List<Message> {
        val today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
        val dayKo = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val withToday = "$intentPrompt\n\n## 오늘 날짜\n$today ($dayKo)"
        val prompt = appendHistory(withToday, history)
        return listOf(
            Message(role = "system", content = prompt),
            Message(role = "user", content = message),
        )
    }

    fun buildAnswerMessages(
        message: String,
        history: List<Message>,
        userName: String,
        roleNames: List<String>,
    ): List<Message> {
        val roles = roleNames.ifEmpty { listOf("일반 팀원 (역할 없음)") }.joinToString(", ")
        val withUser = "$answerPrompt\n\n## 현재 사용자\n이름: $userName / 역할: $roles"
        return listOf(
            Message(role = "system", content = appendHistory(withUser, history)),
            Message(role = "user", content = message),
        )
    }

    fun buildActionMessages(
        message: String,
        intent: IntentResult,
        context: String,
    ): List<Message> {
        val target = ChatTarget.fromOrNull(intent.target)
        return if (target == ChatTarget.WEEKLY_REPORT) {
            listOf(
                Message(role = "system", content = weeklyReportClassifyPrompt),
                Message(role = "user", content = "[CONTEXT]\n$context\n[/CONTEXT]\n\n$message"),
            )
        } else {
            val intentJson = objectMapper.writeValueAsString(intent)
            val userMessage = "[INTENT]\n$intentJson\n[/INTENT]\n\n[CONTEXT]\n$context\n[/CONTEXT]\n\n$message"
            listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userMessage),
            )
        }
    }

    /**
     * 매칭용: numberItems로 한 번 부여된 id→항목 맵을 받아 "id [담당자] 내용"으로 출력.
     * LLM은 id 쌍만 반환하고, 같은 맵으로 enrichMatched가 복원한다 (번호 부여는 호출 측 1회).
     */
    fun buildMatchMessages(
        prevById: Map<String, ReportItem>,
        currById: Map<String, ReportItem>,
    ): List<Message> {
        val userMessage =
            buildString {
                appendLine("[지난주 예정]")
                prevById.forEach { (id, item) -> appendLine("$id [${item.assignee ?: "?"}] ${item.text}") }
                appendLine()
                appendLine("[이번주 진행]")
                currById.forEach { (id, item) -> appendLine("$id [${item.assignee ?: "?"}] ${item.text}") }
            }
        return listOf(
            Message(role = "system", content = weeklyReportMatchPrompt),
            Message(role = "user", content = userMessage.trimEnd()),
        )
    }

    private fun appendHistory(
        basePrompt: String,
        history: List<Message>,
    ): String {
        if (history.isEmpty()) return basePrompt

        return buildString {
            append(basePrompt)
            appendLine()
            appendLine()
            appendLine("## 대화 히스토리 (참조용, 이 형식으로 응답하지 마세요)")
            history.forEach { appendLine(it.content) }
        }
    }
}
