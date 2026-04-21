package com.pluxity.weekly.core.response

import com.pluxity.weekly.chat.dto.Candidate
import org.springframework.http.HttpStatus

class ClarifyErrorResponseBody(
    status: HttpStatus,
    message: String?,
    code: String,
    error: String,
    val clarifyId: String,
    val field: String,
    val candidates: List<Candidate>,
) : ErrorResponseBody(status, message, code, error)
