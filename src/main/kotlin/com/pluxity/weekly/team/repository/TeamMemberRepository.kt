package com.pluxity.weekly.team.repository

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.team.entity.Team
import com.pluxity.weekly.team.entity.TeamMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface TeamMemberRepository : JpaRepository<TeamMember, Long> {
    @Query("SELECT tm FROM TeamMember tm JOIN FETCH tm.user u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE tm.team = :team")
    fun findByTeam(team: Team): List<TeamMember>

    fun existsByTeamAndUser(
        team: Team,
        user: User,
    ): Boolean

    fun deleteByTeamAndUser(
        team: Team,
        user: User,
    )

    fun findByUserId(userId: Long): List<TeamMember>
}
