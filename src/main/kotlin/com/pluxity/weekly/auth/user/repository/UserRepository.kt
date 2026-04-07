package com.pluxity.weekly.auth.user.repository

import com.pluxity.weekly.auth.user.entity.User
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    @EntityGraph(
        attributePaths = [
            "userRoles", "userRoles.role",
        ],
    )
    fun findAllBy(sort: Sort): List<User>

    @EntityGraph(
        attributePaths = [
            "userRoles.user",
            "userRoles.role",
        ],
    )
    fun findWithGraphById(id: Long): User?

    @EntityGraph(
        attributePaths = [
            "userRoles", "userRoles.role",
        ],
    )
    fun findByUsername(username: String): User?

    @EntityGraph(
        attributePaths = [
            "userRoles", "userRoles.role",
        ],
    )
    fun findByEmail(email: String): User?

    @EntityGraph(
        attributePaths = [
            "userRoles", "userRoles.role",
        ],
    )
    fun findByName(name: String): User?
}
