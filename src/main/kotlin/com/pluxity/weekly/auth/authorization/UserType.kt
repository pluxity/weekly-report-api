package com.pluxity.weekly.auth.authorization

enum class UserType(
    val roleName: String,
    val priority: Int,
) {
    ADMIN("ADMIN", 1),
    PO("PO", 2),
    PM("PM", 3),
    TEAM_LEADER("LEADER", 4),
    WORKER("WORKER", 5),
    ;

    companion object {
        fun effectiveRoleName(ownedRoleNames: Set<String>): String =
            entries
                .filter { it.roleName in ownedRoleNames }
                .minByOrNull { it.priority }
                ?.roleName
                ?: WORKER.roleName
    }
}
