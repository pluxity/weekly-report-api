package com.pluxity.weekly.auth.user.dto

data class UserRoleUpdateRequest(
    val roleIds: List<Long> = emptyList(),
)
