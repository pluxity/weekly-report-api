package com.pluxity.weekly.auth.user.dto

import com.pluxity.weekly.auth.authorization.UserType
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
    @field:Schema(description = "이메일", example = "hong@pluxity.com")
    val email: String?,
    @field:Schema(description = "비밀번호 변경 필요 여부")
    val shouldChangePassword: Boolean,
    @field:Schema(description = "역할 목록")
    val roles: List<RoleResponse>,
    @field:Schema(description = "대표 역할 (우선순위 ADMIN > PO > PM > LEADER, 없으면 WORKER)", example = "PM")
    val effectiveRole: String,
)

fun User.toResponse(): UserResponse =
    UserResponse(
        id = this.requiredId,
        username = this.username,
        name = this.name,
        code = this.code,
        phoneNumber = this.phoneNumber,
        email = this.email,
        shouldChangePassword = this.isPasswordChangeRequired(),
        roles = this.userRoles.sortedByDescending { it.role.id }.map { it.role.toResponse() },
        effectiveRole = UserType.effectiveRoleName(this.userRoles.map { it.role.name.uppercase() }.toSet()),
    )
