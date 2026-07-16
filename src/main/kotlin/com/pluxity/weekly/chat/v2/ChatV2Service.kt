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
    private val idRegistryStore: ChatV2IdRegistryStore,
    private val authorizationService: AuthorizationService,
    private val chatLogService: ChatLogService,
    private val userLock: ChatV2UserLock,
) {
    private val systemPrompt: String by lazy {
        ClassPathResource("llm/chat-v2-prompt.txt").getContentAsString(Charsets.UTF_8)
    }

    fun chat(message: String): ChatV2Response {
        val user = authorizationService.currentUser()
        val userId = user.requiredId
        return userLock.withLock(userId) {
            val logData = ChatLogData(userId = userId, requestMessage = message)
            try {
                val roleNames = user.getRoles().map { it.name }
                val response = runLoop(message, userId, user.name, roleNames, logData)
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
        // 검색으로 확인된 id만 조회 tool에 허용 (모델의 id 추측 차단). 세션 단위로 영속돼 이전 턴 검색 결과가 이어진다.
        val idRegistry = idRegistryStore.load(userId)
        var lastStepHadError = false
        var errorRetries = 0

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
                // 직전 스텝이 error인데 모델이 사과로 끝내려 하면, 종료 대신 한 번 더 유도 (상한으로 무한루프 차단).
                if (lastStepHadError && errorRetries < MAX_ERROR_RETRIES) {
                    errorRetries++
                    log.info { "chat/v2 error 후 사과 감지 → 재시도 유도 $errorRetries/$MAX_ERROR_RETRIES" }
                    messages += assistant
                    messages += ToolMessage(role = "user", content = ERROR_RETRY_NUDGE)
                    lastStepHadError = false
                    return@repeat
                }
                historyStore.appendTurn(userId, message, reply)
                idRegistryStore.save(userId, idRegistry)
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
            var stepError = false
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
                if (toolResult.trimStart().startsWith("{\"error\"")) stepError = true
            }
            lastStepHadError = stepError
        }

        // 루프 한도 초과 — 에러 대신 graceful 안내 (요청이 소화된 만큼의 trace는 응답에 남긴다)
        log.warn { "chat/v2 루프 한도($MAX_STEPS) 초과 — message=$message" }
        val reply = "요청을 처리하는 단계가 너무 많아 여기서 멈췄어요. 질문을 더 구체적으로 나눠서 다시 시도해주세요."
        historyStore.appendTurn(userId, message, reply)
        idRegistryStore.save(userId, idRegistry)
        logData.recordAction(usage, reply)
        return ChatV2Response(
            reply = reply,
            steps = steps,
            inputTokens = usage.promptTokens,
            outputTokens = usage.completionTokens,
            cachedTokens = cachedTokens,
        )
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

        // error 직후 모델이 사과로 끝내려 할 때 재시도를 유도하는 횟수 상한 (무한루프 방지)
        private const val MAX_ERROR_RETRIES = 1

        private const val ERROR_RETRY_NUDGE =
            "직전 도구 호출이 실패했습니다(위 오류 참고). 같은 인자로 반복하지 말 것 — " +
                "이름을 변형(음차↔영문, 핵심 단어만)해 다시 조회하거나 다른 도구를 쓰고, " +
                "정말 없거나 여러 개면 사과만 하지 말고 그 사실·후보를 사용자에게 알리세요."
    }
}
