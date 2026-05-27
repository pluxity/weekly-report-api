package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.dto.ChatTarget
import com.pluxity.weekly.chat.llm.dto.IntentResult
import com.pluxity.weekly.chat.llm.dto.Message
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

    private val weeklyReportClassifyPrompt: String by lazy {
        ClassPathResource("llm/weekly-report-classify-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    fun buildIntentMessages(
        message: String,
        history: List<Message>,
    ): List<Message> {
        val today = LocalDate.now()
        val dayKo = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val withToday = "$intentPrompt\n\n## 오늘 날짜\n$today ($dayKo)"
        val prompt = appendHistory(withToday, history)
        return listOf(
            Message(role = "system", content = prompt),
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
