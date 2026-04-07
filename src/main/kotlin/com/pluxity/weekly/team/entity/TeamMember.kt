package com.pluxity.weekly.team.entity

import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.core.entity.IdentityIdEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "team_members",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_team_member",
            columnNames = ["team_id", "user_id"],
        ),
    ],
)
class TeamMember(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    val team: Team,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
) : IdentityIdEntity()
