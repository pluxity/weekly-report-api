package com.pluxity.weekly.chat.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "채팅 폼 데이터", subTypes = [ProjectChatDto::class, EpicChatDto::class, TaskChatDto::class, TeamChatDto::class])
sealed interface ChatDto {
    val name: String?
}

@Schema(description = "프로젝트 폼 데이터")
data class ProjectChatDto(
    @field:Schema(description = "프로젝트명", example = "알파 프로젝트")
    override val name: String?,
    @field:Schema(description = "설명", example = "프로젝트 설명")
    val description: String?,
    @field:Schema(description = "상태", example = "TODO")
    val status: String?,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: String?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: String?,
    @field:Schema(description = "PM ID", example = "1")
    val pmId: Long?,
) : ChatDto

@Schema(description = "에픽 폼 데이터")
data class EpicChatDto(
    @field:Schema(description = "에픽명", example = "백엔드 구축")
    override val name: String?,
    @field:Schema(description = "프로젝트 ID", example = "1")
    val projectId: Long?,
    @field:Schema(description = "설명", example = "에픽 설명")
    val description: String?,
    @field:Schema(description = "상태", example = "TODO")
    val status: String?,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: String?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: String?,
    @field:Schema(description = "배정 사용자 ID 목록", example = "[1, 2]")
    val userIds: List<Long>?,
) : ChatDto

@Schema(description = "태스크 폼 데이터")
data class TaskChatDto(
    @field:Schema(description = "태스크명", example = "API 설계")
    override val name: String?,
    @field:Schema(description = "에픽 ID", example = "1")
    val epicId: Long?,
    @field:Schema(description = "설명", example = "태스크 설명")
    val description: String?,
    @field:Schema(description = "상태", example = "TODO")
    val status: String?,
    @field:Schema(description = "진행률 (0~100)", example = "0")
    val progress: Int?,
    @field:Schema(description = "시작일", example = "2026-01-01")
    val startDate: String?,
    @field:Schema(description = "마감일", example = "2026-03-31")
    val dueDate: String?,
    @field:Schema(description = "담당자 ID", example = "1")
    val assigneeId: Long?,
) : ChatDto

@Schema(description = "팀 폼 데이터")
data class TeamChatDto(
    @field:Schema(description = "팀명", example = "백엔드팀")
    override val name: String?,
    @field:Schema(description = "팀장 ID", example = "1")
    val leaderId: Long?,
) : ChatDto
