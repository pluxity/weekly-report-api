package com.pluxity.weekly.core.constant

import org.springframework.http.HttpStatus

interface Code {
    fun getHttpStatus(): HttpStatus

    fun getMessage(): String

    fun getStatusName(): String

    fun getCodeName(): String
}
