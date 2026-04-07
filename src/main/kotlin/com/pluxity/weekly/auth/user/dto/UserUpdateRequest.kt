package com.pluxity.weekly.auth.user.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용자 수정 요청")
data class UserUpdateRequest(
    @field:Schema(description = "이름", example = "홍길동")
    val name: String? = null,
    @field:Schema(description = "코드", example = "EMP001")
    val code: String? = null,
    @field:Schema(description = "연락처", example = "010-1234-5678")
    val phoneNumber: String? = null,
    @field:Schema(description = "부서", example = "개발팀")
    val department: String? = null,
    @field:Schema(description = "이메일", example = "hong@pluxity.com")
    val email: String? = null,
    @field:Schema(description = "프로필 이미지 파일 ID", example = "1")
    val profileImageId: Long? = null,
    @field:Schema(description = "역할 ID 목록", example = "[1, 2]")
    val roleIds: List<Long>? = null,
)
