package com.pluxity.weekly.team.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.auth.user.dto.UserResponse
import com.pluxity.weekly.auth.user.dto.toResponse
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.chat.dto.TeamSearchFilter
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.team.dto.TeamRequest
import com.pluxity.weekly.team.dto.TeamResponse
import com.pluxity.weekly.team.dto.TeamUpdateRequest
import com.pluxity.weekly.team.dto.toResponse
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.entity.TeamMember
import com.pluxity.weekly.team.repository.TeamMemberRepository
import com.pluxity.weekly.team.repository.TeamRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamService(
    private val teamRepository: TeamRepository,
    private val memberRepository: TeamMemberRepository,
    private val userRepository: UserRepository,
    private val authorizationService: AuthorizationService,
) {
    fun findAll(): List<TeamResponse> =
        teamRepository.findAll().map { team ->
            val members = memberRepository.findByTeam(team).map { it.user.toResponse() }
            val leaderName = team.leaderId?.let { getUserById(it).name }
            team.toResponse(leaderName = leaderName, members = members)
        }

    fun search(filter: TeamSearchFilter): List<TeamResponse> =
        teamRepository
            .findByFilter(filter)
            .map { team ->
                val members = memberRepository.findByTeam(team).map { it.user.toResponse() }
                val leaderName = team.leaderId?.let { getUserById(it).name }
                team.toResponse(leaderName = leaderName, members = members)
            }

    fun findById(id: Long): TeamResponse = getTeamById(id).toResponse()

    @Transactional
    fun create(request: TeamRequest): Long {
        val user = authorizationService.currentUser()
        authorizationService.requireAdmin(user)
        return teamRepository
            .save(
                Team(
                    name = request.name,
                    leaderId = request.leaderId,
                ),
            ).requiredId
    }

    @Transactional
    fun update(
        id: Long,
        request: TeamUpdateRequest,
    ) {
        val user = authorizationService.currentUser()
        authorizationService.requireAdmin(user)
        getTeamById(id).update(
            name = request.name,
            leaderId = request.leaderId,
        )
    }

    @Transactional
    fun delete(id: Long) {
        val user = authorizationService.currentUser()
        authorizationService.requireAdmin(user)
        teamRepository.deleteById(getTeamById(id).requiredId)
    }

    // ── TeamMember ──

    fun findMembers(teamId: Long): List<UserResponse> {
        val team = getTeamById(teamId)
        return memberRepository.findByTeam(team).map { it.user.toResponse() }
    }

    @Transactional
    fun addMember(
        teamId: Long,
        userId: Long,
    ): Long {
        val currentUser = authorizationService.currentUser()
        authorizationService.requireAdmin(currentUser)
        val team = getTeamById(teamId)
        val user = getUserById(userId)
        if (memberRepository.existsByTeamAndUser(team, user)) {
            throw CustomException(ErrorCode.DUPLICATE_TEAM_MEMBER, userId, teamId)
        }
        return memberRepository.save(TeamMember(team = team, user = user)).requiredId
    }

    @Transactional
    fun removeMember(
        teamId: Long,
        userId: Long,
    ) {
        val currentUser = authorizationService.currentUser()
        authorizationService.requireAdmin(currentUser)
        val team = getTeamById(teamId)
        val user = getUserById(userId)
        if (!memberRepository.existsByTeamAndUser(team, user)) {
            throw CustomException(ErrorCode.NOT_FOUND_TEAM_MEMBER, teamId, userId)
        }
        memberRepository.deleteByTeamAndUser(team, user)
    }

    private fun getTeamById(id: Long): Team =
        teamRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_TEAM, id)

    private fun getUserById(id: Long): User =
        userRepository.findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_USER, id)
}
