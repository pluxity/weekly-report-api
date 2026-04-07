package com.pluxity.weekly.core.response

import org.springframework.http.HttpStatus

class ClarifyErrorResponseBody(
    status: HttpStatus,
    message: String?,
    code: String,
    error: String,
    val candidates: List<String>?,
) : ErrorResponseBody(status, message, code, error)
