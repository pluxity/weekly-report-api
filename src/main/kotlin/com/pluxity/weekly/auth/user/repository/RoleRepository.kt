package com.pluxity.weekly.auth.user.repository

import com.pluxity.weekly.auth.user.entity.Role
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository : JpaRepository<Role, Long> {
    @EntityGraph(attributePaths = ["userRoles.user", "userRoles.role"])
    fun findWithInfoById(id: Long): Role?

    @EntityGraph(attributePaths = ["userRoles.user", "userRoles.role"])
    fun findAllByOrderByCreatedAtDesc(): List<Role>
}
