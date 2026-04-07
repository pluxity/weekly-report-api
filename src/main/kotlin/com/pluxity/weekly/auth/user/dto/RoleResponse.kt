package com.pluxity.weekly.auth.user.dto

import com.pluxity.weekly.auth.user.entity.Role

data class RoleResponse(
    val id: Long,
    val name: String,
    val description: String?,
)

fun Role.toResponse() =
    RoleResponse(
        this.requiredId,
        this.name,
        this.description,
    )
