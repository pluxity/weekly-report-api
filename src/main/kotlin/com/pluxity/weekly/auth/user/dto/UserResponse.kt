package com.pluxity.weekly.auth.user.dto

import com.pluxity.weekly.auth.user.entity.User
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용자 응답")
data class UserResponse(
    @field:Schema(description = "사용자 ID", example = "1")
    val id: Long,
    @field:Schema(description = "로그인 ID", example = "admin")
    val username: String,
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
    @field:Schema(description = "코드", example = "EMP001")
    val code: String?,
    @field:Schema(description = "연락처", example = "010-1234-5678")
    val phoneNumber: String?,
    @field:Schema(description = "부서", example = "개발팀")
    val department: String?,
    @field:Schema(description = "이메일", example = "hong@pluxity.com")
    val email: String?,
    @field:Schema(description = "비밀번호 변경 필요 여부")
    val shouldChangePassword: Boolean,
    @field:Schema(description = "역할 목록")
    val roles: List<RoleResponse>,
)

fun User.toResponse(): UserResponse =
    UserResponse(
        id = this.requiredId,
        username = this.username,
        name = this.name,
        code = this.code,
        phoneNumber = this.phoneNumber,
        department = this.department,
        email = this.email,
        shouldChangePassword = this.isPasswordChangeRequired(),
        roles = this.userRoles.sortedByDescending { it.role.id }.map { it.role.toResponse() },
    )
