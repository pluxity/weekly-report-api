package com.pluxity.weekly.auth.authorization

enum class UserType(
    val roleName: String,
) {
    ADMIN("ADMIN"),
    PM("PM"),
    PO("PO"),
    TEAM_LEADER("TEAM_LEADER"),
}
