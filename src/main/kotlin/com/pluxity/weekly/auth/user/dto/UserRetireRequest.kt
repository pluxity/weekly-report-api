package com.pluxity.weekly.auth.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

@Schema(description = "퇴사 처리 요청")
data class UserRetireRequest(
    @field:Schema(description = "퇴사일 (미래 불가)", example = "2026-08-31", required = true)
    @field:NotNull(message = "퇴사일은 필수입니다")
    val retiredAt: LocalDate,
)
