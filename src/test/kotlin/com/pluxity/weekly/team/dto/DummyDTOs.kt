package com.pluxity.weekly.team.dto

fun dummyTeamRequest(
    name: String = "테스트 팀",
    leaderId: Long? = null,
) = TeamRequest(
    name = name,
    leaderId = leaderId,
)

fun dummyTeamUpdateRequest(
    name: String? = null,
    leaderId: Long? = null,
) = TeamUpdateRequest(
    name = name,
    leaderId = leaderId,
)
