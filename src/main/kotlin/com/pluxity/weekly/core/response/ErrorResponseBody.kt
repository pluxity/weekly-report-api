package com.pluxity.weekly.core.response

import org.springframework.http.HttpStatus

open class ErrorResponseBody(
    status: HttpStatus,
    message: String?,
    val code: String,
    val error: String,
) : ResponseBody(status.value(), message)
