package com.pluxity.weekly.authorization

enum class UserType(
    val roleName: String,
) {
    ADMIN("ADMIN"),
    PM("PM"),
    TEAM_LEADER("TEAM_LEADER"),
}
