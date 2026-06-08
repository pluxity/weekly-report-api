package com.pluxity.weekly.chat.entity

import com.pluxity.weekly.core.entity.IdentityIdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "chat_logs")
class ChatLog(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "request_message", columnDefinition = "TEXT", nullable = false)
    val requestMessage: String,
    @Column(name = "success", nullable = false)
    val success: Boolean,
    // 1차 LLM(의도 추출) 결과 — 실패 시 null
    @Column(name = "intent_result", columnDefinition = "TEXT")
    val intentResult: String? = null,
    // 2차 LLM(액션 생성) 결과 — weekly_report 우회/실패 시 null
    @Column(name = "action_result", columnDefinition = "TEXT")
    val actionResult: String? = null,
    // 실패 시 예외 메시지 (success=false일 때 기록)
    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,
    // 토큰은 단가가 다른 input(prompt)/output(completion)을 분리 저장 (cost 산출 근거)
    @Column(name = "intent_input_tokens", nullable = false)
    val intentInputTokens: Int = 0,
    @Column(name = "intent_output_tokens", nullable = false)
    val intentOutputTokens: Int = 0,
    @Column(name = "action_input_tokens", nullable = false)
    val actionInputTokens: Int = 0,
    @Column(name = "action_output_tokens", nullable = false)
    val actionOutputTokens: Int = 0,
    // USD. input/output 토큰 × 단가로 산출 (저장 시점 단가 고정)
    @Column(name = "cost", nullable = false, precision = 16, scale = 8)
    val cost: BigDecimal = BigDecimal.ZERO,
) : IdentityIdEntity()
