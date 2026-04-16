package com.pluxity.weekly.auth.user.repository

import com.pluxity.weekly.auth.user.entity.User
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

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
    fun findByAadObjectId(aadObjectId: String): User?

    @Query(
        "SELECT DISTINCT u FROM User u JOIN u.userRoles ur " +
            "WHERE UPPER(ur.role.name) = UPPER(:roleName) " +
            "ORDER BY u.name",
    )
    fun findAllByRoleName(roleName: String): List<User>
}
