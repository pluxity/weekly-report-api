package com.pluxity.weekly.authorization

enum class UserType(
    val roleName: String,
) {
    ADMIN("ADMIN"),
    PM("PM"),
    PO("PO"),
    TEAM_LEADER("TEAM_LEADER"),
}
