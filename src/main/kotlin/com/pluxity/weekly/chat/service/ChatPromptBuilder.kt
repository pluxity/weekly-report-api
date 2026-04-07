package com.pluxity.weekly.chat.service

import com.pluxity.weekly.chat.llm.dto.IntentResult
import com.pluxity.weekly.chat.llm.dto.Message
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

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

    fun buildIntentMessages(
        message: String,
        history: List<Message>,
    ): List<Message> {
        val prompt = appendHistory(intentPrompt, history)
        return listOf(
            Message(role = "system", content = prompt),
            Message(role = "user", content = message),
        )
    }

    fun buildActionMessages(
        message: String,
        intent: IntentResult,
        context: String,
        history: List<Message> = emptyList(),
    ): List<Message> {
        val intentJson = objectMapper.writeValueAsString(intent)
        val clarifyContext = buildClarifyContext(history)
        val userMessage = "$clarifyContext[INTENT]\n$intentJson\n[/INTENT]\n\n[CONTEXT]\n$context\n[/CONTEXT]\n\n$message"
        return listOf(
            Message(role = "system", content = systemPrompt),
            Message(role = "user", content = userMessage),
        )
    }

    /**
     * 직전 대화가 clarify로 끝났을 때만, 원래 질문을 2차 LLM에 전달
     */
    private fun buildClarifyContext(history: List<Message>): String {
        if (history.isEmpty()) return ""

        val lastEntry = history.last().content
        if (!lastEntry.contains("결과: clarify:")) return ""

        return buildString {
            appendLine("[CLARIFY_CONTEXT]")
            appendLine("현재 메시지는 이전 clarify에 대한 보충 답변이다.")
            appendLine("원래 질문: $lastEntry")
            appendLine("이전 질문의 의도를 유지하되, 현재 메시지의 정보로 대상을 특정하라.")
            appendLine("[/CLARIFY_CONTEXT]")
            appendLine()
        }
    }

    private fun appendHistory(
        basePrompt: String,
        history: List<Message>,
    ): String {
        if (history.isEmpty()) return basePrompt

        val lastEntry = history.last().content
        val isClarifyPending = lastEntry.contains("결과: clarify:")

        return buildString {
            append(basePrompt)
            appendLine()
            appendLine()
            appendLine("## 대화 히스토리 (참조용, 이 형식으로 응답하지 마세요)")
            history.forEach { appendLine(it.content) }
            if (isClarifyPending) {
                appendLine()
                appendLine("## 중요: 직전 대화가 clarify(미확정)로 끝났다. 현재 메시지는 이전 질문에 대한 보충 답변일 가능성이 높다. 이전 질문의 target과 actions를 이어받아라.")
            }
        }
    }
}
