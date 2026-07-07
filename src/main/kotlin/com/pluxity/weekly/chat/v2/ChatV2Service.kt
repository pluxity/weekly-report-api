package com.pluxity.weekly.chat.v2

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.chat.llm.dto.TokenUsage
import com.pluxity.weekly.chat.service.ChatLogData
import com.pluxity.weekly.chat.service.ChatLogService
import com.pluxity.weekly.chat.v2.dto.ChatV2Response
import com.pluxity.weekly.chat.v2.dto.ChatV2Step
import com.pluxity.weekly.chat.v2.dto.ToolMessage
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val log = KotlinLogging.logger {}

/**
 * tool calling 루프 PoC.
 * 모델이 tool_calls를 반환하는 동안 실행→결과 첨부→재호출을 반복하고,
 * content(자연어)가 나오면 그것이 사용자에게 가는 최종 응답이다.
 */
@Service
class ChatV2Service(
    private val llmClient: ChatV2LlmClient,
    private val toolExecutor: ChatV2ToolExecutor,
    private val historyStore: ChatV2HistoryStore,
    private val authorizationService: AuthorizationService,
    private val chatLogService: ChatLogService,
) {
    private val systemPrompt: String by lazy {
        ClassPathResource("llm/chat-v2-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    fun chat(message: String): ChatV2Response {
        val user = authorizationService.currentUser()
        val userId = user.requiredId
        val logData = ChatLogData(userId = userId, requestMessage = message)

        try {
            val roleNames = user.getRoles().map { it.name }
            val response = runLoop(message, userId, user.name, roleNames, logData)
            logData.success = true
            return response
        } catch (e: Exception) {
            logData.errorMessage = e.message
            throw e
        } finally {
            chatLogService.record(logData)
        }
    }

    private fun runLoop(
        message: String,
        userId: Long,
        userName: String,
        roleNames: List<String>,
        logData: ChatLogData,
    ): ChatV2Response {
        val messages = mutableListOf(ToolMessage(role = "system", content = buildSystemPrompt(userName, roleNames)))
        messages += historyStore.load(userId)
        messages += ToolMessage(role = "user", content = message)

        val steps = mutableListOf<ChatV2Step>()
        var usage = TokenUsage()
        var cachedTokens = 0
        // 이번 턴에서 검색으로 확인된 id만 mutating tool에 허용 (모델의 id 추측 차단)
        val idRegistry = ChatV2IdRegistry(userId)

        repeat(MAX_STEPS) { step ->
            val result = llmClient.call(messages, ChatV2Tools.ALL)
            usage += result.usage
            cachedTokens += result.cachedTokens
            log.info {
                "chat/v2 llm 호출 ${step + 1} — in=${result.usage.promptTokens} (cached=${result.cachedTokens}), " +
                    "out=${result.usage.completionTokens}"
            }
            val assistant = result.message

            val toolCalls = assistant.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                val reply =
                    assistant.content?.trim()?.takeIf { it.isNotBlank() }
                        ?: throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
                historyStore.appendTurn(userId, message, reply)
                logData.recordAction(usage, reply)
                log.info { "chat/v2 완료 — steps=${steps.size}, tokens=${usage.totalTokens} (cached=$cachedTokens)" }
                return ChatV2Response(
                    reply = reply,
                    steps = steps,
                    inputTokens = usage.promptTokens,
                    outputTokens = usage.completionTokens,
                    cachedTokens = cachedTokens,
                )
            }

            messages += assistant
            toolCalls.forEach { call ->
                log.info { "chat/v2 step ${step + 1} — ${call.function.name}(${call.function.arguments})" }
                val toolResult = toolExecutor.execute(call.function.name, call.function.arguments, userId, idRegistry)
                steps += ChatV2Step(tool = call.function.name, arguments = call.function.arguments, result = toolResult)
                messages +=
                    ToolMessage(
                        role = "tool",
                        content = toolResult,
                        toolCallId = call.id,
                    )
            }
        }

        log.warn { "chat/v2 루프 한도($MAX_STEPS) 초과 — message=$message" }
        logData.recordAction(usage)
        throw CustomException(ErrorCode.LLM_INVALID_RESPONSE)
    }

    private fun buildSystemPrompt(
        userName: String,
        roleNames: List<String>,
    ): String {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val dayKo = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val roles = roleNames.ifEmpty { listOf("일반 팀원 (역할 없음)") }.joinToString(", ")
        return "$systemPrompt\n## 오늘\n$today ($dayKo) / 사용자: $userName / 역할: $roles"
    }

    companion object {
        // 검색→사용자 확인→실행→확인 등 멀티스텝 흐름을 감안한 한도
        private const val MAX_STEPS = 8
    }
}
