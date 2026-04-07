package com.pluxity.weekly.auth.user.dto

import com.pluxity.weekly.auth.user.entity.User

data class UserLoggedInResponse(
    val id: Long,
    val username: String,
    val name: String,
    val code: String?,
    val phoneNumber: String?,
    val department: String?,
    val isLoggedIn: Boolean,
    val roles: List<RoleResponse>?,
)

fun User.toLoggedInResponse(isLoggedIn: Boolean): UserLoggedInResponse =
    UserLoggedInResponse(
        id = this.requiredId,
        username = this.username,
        name = this.name,
        code = this.code,
        phoneNumber = this.phoneNumber,
        department = this.department,
        isLoggedIn = isLoggedIn,
        roles = this.getRoles().map { it.toResponse() },
    )
