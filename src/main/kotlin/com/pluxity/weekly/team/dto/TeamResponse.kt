package com.pluxity.weekly.team.dto

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.pluxity.weekly.auth.user.dto.UserResponse
import com.pluxity.weekly.core.response.BaseResponse
import com.pluxity.weekly.core.response.toBaseResponse
import com.pluxity.weekly.team.entity.Team
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "팀 응답")
data class TeamResponse(
    @field:Schema(description = "ID", example = "1")
    val id: Long,
    @field:Schema(description = "팀명", example = "개발팀")
    val name: String,
    @field:Schema(description = "팀장 사용자 ID", example = "1")
    val leaderId: Long?,
    @field:Schema(description = "팀장 이름", example = "나동규")
    val leaderName: String? = null,
    @field:Schema(description = "팀 멤버 목록")
    val members: List<UserResponse> = emptyList(),
    @field:JsonUnwrapped
    val baseResponse: BaseResponse,
)

fun Team.toResponse(
    leaderName: String? = null,
    members: List<UserResponse> = emptyList(),
): TeamResponse =
    TeamResponse(
        id = this.requiredId,
        name = this.name,
        leaderId = this.leaderId,
        leaderName = leaderName,
        members = members,
        baseResponse = this.toBaseResponse(),
    )
