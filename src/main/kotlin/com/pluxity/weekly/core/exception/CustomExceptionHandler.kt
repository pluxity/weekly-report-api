package com.pluxity.weekly.core.exception

import com.pluxity.weekly.chat.exception.ChatSelectRequiredException
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.response.ClarifyErrorResponseBody
import com.pluxity.weekly.core.response.ErrorResponseBody
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.sql.SQLException

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class CustomExceptionHandler {
    companion object {
        private val IGNORE_PATHS = listOf("favicon.ico")
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponseBody> =
        ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponseBody(
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    message = "서버 내부 오류가 발생했습니다.",
                    code = HttpStatus.INTERNAL_SERVER_ERROR.value().toString(),
                    error = HttpStatus.INTERNAL_SERVER_ERROR.name,
                ),
            ).also { log.error(e) { "Unhandled Exception" } }

    @ExceptionHandler(ChatSelectRequiredException::class)
    fun handleChatSelectRequiredException(e: ChatSelectRequiredException): ResponseEntity<ClarifyErrorResponseBody> =
        ResponseEntity
            .status(e.code.getHttpStatus())
            .body(
                ClarifyErrorResponseBody(
                    status = e.code.getHttpStatus(),
                    message = e.message,
                    code =
                        e.code
                            .getHttpStatus()
                            .value()
                            .toString(),
                    error = e.code.getCodeName(),
                    clarifyId = e.clarifyId,
                    field = e.field,
                    candidates = e.candidates,
                ),
            ).also {
                log.info { "ChatSelectRequiredException: clarifyId=${e.clarifyId}, field=${e.field}, candidates=${e.candidates.size}건" }
            }

    @ExceptionHandler(CustomException::class)
    fun handleCustomException(e: CustomException): ResponseEntity<ErrorResponseBody> =
        ResponseEntity
            .status(e.code.getHttpStatus())
            .body(
                ErrorResponseBody(
                    status = e.code.getHttpStatus(),
                    message = e.message,
                    code =
                        e.code
                            .getHttpStatus()
                            .value()
                            .toString(),
                    error = e.code.getCodeName(),
                ),
            ).also { log.error(e) { "CustomException: ${e.message}" } }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFoundException(e: EntityNotFoundException): ResponseEntity<ErrorResponseBody> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponseBody(
                    status = HttpStatus.NOT_FOUND,
                    message = e.message,
                    code = HttpStatus.NOT_FOUND.value().toString(),
                    error = HttpStatus.NOT_FOUND.name,
                ),
            ).also { log.error(e) { "EntityNotFoundException" } }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(
        e: NoResourceFoundException,
        request: HttpServletRequest?,
    ): ResponseEntity<ErrorResponseBody> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponseBody(
                    status = HttpStatus.NOT_FOUND,
                    message = "해당 경로를 찾지 못했습니다. url 을 확인해주세요",
                    code = HttpStatus.NOT_FOUND.value().toString(),
                    error = HttpStatus.NOT_FOUND.name,
                ),
            ).also {
                if (IGNORE_PATHS.none { request?.requestURI?.contains(it) == true }) {
                    log.error(e) { "NoResourceFoundException" }
                }
            }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponseBody> {
        log.error(e) { "Validation Error" }

        val fieldErrors = e.bindingResult.fieldErrors
        val errorMessage =
            fieldErrors.joinToString { error: FieldError -> "$error.field: $error.defaultMessage" }

        return ResponseEntity(
            ErrorResponseBody(
                status = HttpStatus.BAD_REQUEST,
                message = errorMessage,
                code = HttpStatus.BAD_REQUEST.value().toString(),
                error = HttpStatus.BAD_REQUEST.name,
            ),
            HttpStatus.BAD_REQUEST,
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponseBody> =
        ResponseEntity(
            ErrorResponseBody(
                status = HttpStatus.BAD_REQUEST,
                message =
                    e.message
                        ?.takeIf { "Required request body is missing" in it }
                        ?.let { "필수 요청 본문(Request Body)이 누락되었습니다." }
                        ?: "필수 요청 본문이 누락되었거나 형식이 잘못되었습니다.",
                code = HttpStatus.BAD_REQUEST.value().toString(),
                error = HttpStatus.BAD_REQUEST.name,
            ),
            HttpStatus.BAD_REQUEST,
        ).also { log.error(e) { "HttpMessageNotReadableException: ${e.message}" } }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponseBody> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponseBody(
                    status = HttpStatus.BAD_REQUEST,
                    message = "필수 요청 파라미터(${e.parameterName})가 누락되었습니다.",
                    code = HttpStatus.BAD_REQUEST.value().toString(),
                    error = HttpStatus.BAD_REQUEST.name,
                ),
            ).also { log.error(e) { "handleMissingServletRequestParameterException: ${e.message}" } }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(e: DataIntegrityViolationException): ResponseEntity<ErrorResponseBody> {
        val errorCode =
            when (extractSqlState(e)) {
                "23505" -> ErrorCode.DUPLICATE_RESOURCE_ID
                "23503" ->
                    if (isStillReferenced(e)) {
                        ErrorCode.RESOURCE_STILL_REFERENCED
                    } else {
                        ErrorCode.REFERENCED_RESOURCE_NOT_FOUND
                    }
                "23502" -> ErrorCode.MISSING_REQUIRED_VALUE
                else -> ErrorCode.DATA_INTEGRITY_VIOLATION
            }

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ErrorResponseBody(
                    status = errorCode.getHttpStatus(),
                    message = errorCode.getMessage(),
                    code = errorCode.getHttpStatus().value().toString(),
                    error = errorCode.getCodeName(),
                ),
            ).also { log.error(e) { "DataIntegrityViolationException [${extractSqlState(e)}]: ${e.message}" } }
    }

    private fun isStillReferenced(e: DataIntegrityViolationException): Boolean {
        val message = e.mostSpecificCause.message ?: return false
        return "is still referenced" in message || "여전히 참조" in message
    }

    private fun extractSqlState(e: DataIntegrityViolationException): String? {
        var cause: Throwable? = e.cause
        while (cause != null) {
            if (cause is SQLException) return cause.sqlState
            cause = cause.cause
        }
        return null
    }
}
