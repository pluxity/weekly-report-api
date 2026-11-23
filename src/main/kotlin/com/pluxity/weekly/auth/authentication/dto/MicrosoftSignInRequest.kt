package com.pluxity.weekly.auth.authentication.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class MicrosoftSignInRequest(
    @field:Schema(description = "프론트(MSAL)에서 받은 Microsoft ID 토큰")
    @field:NotBlank(message = "ID 토큰은 공백이 될 수 없습니다.")
    val idToken: String,
)
