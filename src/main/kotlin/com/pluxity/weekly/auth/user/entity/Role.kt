package com.pluxity.weekly.auth.user.entity

import com.pluxity.weekly.core.entity.IdentityIdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "roles")
class Role(
    @Column(name = "name", nullable = false, unique = true)
    var name: String,
    @Column(name = "description", length = 100)
    var description: String?,
    var auth: String? = RoleType.USER.name,
) : IdentityIdEntity() {
    @OneToMany(mappedBy = "role")
    var userRoles: MutableList<UserRole> = mutableListOf()

    fun getAuthority(): String = "ROLE_$auth"

    fun changeRoleName(name: String) {
        this.name = name
    }

    fun changeDescription(description: String?) {
        this.description = description
    }
}
