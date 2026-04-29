package com.pluxity.weekly.epic.repository

import com.pluxity.weekly.epic.entity.Epic
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    fun findByProjectIdIn(projectIds: List<Long>): List<Epic>

    @Query(value = "SELECT project_id FROM epics WHERE id = :id", nativeQuery = true)
    fun findProjectIdRawById(
        @Param("id") id: Long,
    ): Long?

    @Query(
        value = """
            SELECT EXISTS(
              SELECT 1 FROM epics e
              JOIN projects p ON e.project_id = p.id
              WHERE e.id = :id AND p.deleted = true
            )
        """,
        nativeQuery = true,
    )
    fun isParentProjectDeletedByEpicId(
        @Param("id") id: Long,
    ): Boolean

    @Modifying
    @Query(value = "UPDATE epics SET deleted = false WHERE id = :id", nativeQuery = true)
    fun restoreById(
        @Param("id") id: Long,
    ): Int

    @Modifying
    @Query(value = "UPDATE epics SET deleted = false WHERE project_id = :projectId", nativeQuery = true)
    fun restoreByProjectId(
        @Param("projectId") projectId: Long,
    ): Int
}
