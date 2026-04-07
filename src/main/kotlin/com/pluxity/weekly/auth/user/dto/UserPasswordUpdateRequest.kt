package com.pluxity.weekly.auth.user.dto

data class UserPasswordUpdateRequest(
    val currentPassword: String,
    val newPassword: String,
)
