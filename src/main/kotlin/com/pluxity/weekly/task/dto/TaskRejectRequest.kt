package com.pluxity.weekly.task.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "태스크 반려 요청")
data class TaskRejectRequest(
    @field:Schema(description = "반려 사유 (선택)", example = "요구사항 불충족")
    @field:Size(max = 1000, message = "반려 사유는 최대 1000자까지 입력 가능합니다")
    val reason: String? = null,
)
