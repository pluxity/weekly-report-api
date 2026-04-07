package com.pluxity.weekly.team.entity

import com.pluxity.weekly.core.entity.IdentityIdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "teams")
class Team(
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "leader_id")
    var leaderId: Long? = null,
) : IdentityIdEntity() {
    fun update(
        name: String? = null,
        leaderId: Long? = null,
    ) {
        name?.let { this.name = it }
        leaderId?.let { this.leaderId = it }
    }
}
