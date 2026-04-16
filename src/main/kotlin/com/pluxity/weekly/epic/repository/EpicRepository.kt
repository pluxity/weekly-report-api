package com.pluxity.weekly.epic.repository

import com.pluxity.weekly.epic.entity.Epic
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface EpicRepository :
    JpaRepository<Epic, Long>,
    EpicCustomRepository {
    @EntityGraph(attributePaths = ["project"])
    fun findAllWithProjectByIdIn(ids: Collection<Long>): List<Epic>

    fun findByAssignmentsUserId(userId: Long): List<Epic>

    fun existsByAssignmentsUserIdAndId(
        userId: Long,
        epicId: Long,
    ): Boolean

    fun existsByProjectId(projectId: Long): Boolean

    fun findByProjectId(projectId: Long): List<Epic>

    fun findByProjectIdIn(projectIds: List<Long>): List<Epic>
}
