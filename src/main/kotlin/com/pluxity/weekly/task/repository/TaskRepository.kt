package com.pluxity.weekly.task.repository

import com.pluxity.weekly.epic.entity.Epic
import com.pluxity.weekly.task.entity.Task
import com.pluxity.weekly.task.entity.TaskStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TaskRepository :
    JpaRepository<Task, Long>,
    TaskCustomRepository {
    fun existsByEpicIdAndName(
        epicId: Long,
        name: String,
    ): Boolean

    fun existsByEpicId(epicId: Long): Boolean

    fun findByAssigneeId(assigneeId: Long): List<Task>

    fun findByAssigneeIdIn(assigneeIds: List<Long>): List<Task>

    fun findByEpicIn(epics: List<Epic>): List<Task>

    fun findByEpicId(epicId: Long): List<Task>

    @EntityGraph(attributePaths = ["epic", "epic.project"])
    fun findWithEpicAndProjectById(id: Long): Task?

    @EntityGraph(attributePaths = ["epic", "epic.project"])
    fun findAllWithEpicAndProjectByIdIn(ids: Collection<Long>): List<Task>

    @EntityGraph(attributePaths = ["epic", "epic.project", "assignee"])
    fun findByStatus(status: TaskStatus): List<Task>

    @EntityGraph(attributePaths = ["epic", "epic.project", "assignee"])
    fun findByStatusAndEpicProjectIdIn(
        status: TaskStatus,
        projectIds: List<Long>,
    ): List<Task>

    fun deleteByEpicIdAndAssigneeId(
        epicId: Long,
        assigneeId: Long,
    )

    @Query(value = "SELECT * FROM tasks WHERE id = :id", nativeQuery = true)
    fun findRawById(
        @Param("id") id: Long,
    ): Task?

    @Query(
        value = """
            SELECT EXISTS(
              SELECT 1 FROM tasks t
              JOIN epics e ON t.epic_id = e.id
              WHERE t.id = :id AND e.deleted = true
            )
        """,
        nativeQuery = true,
    )
    fun isParentEpicDeletedByTaskId(
        @Param("id") id: Long,
    ): Boolean

    @Query(
        value = """
            SELECT EXISTS(
              SELECT 1 FROM tasks t
              JOIN epics e ON t.epic_id = e.id
              JOIN projects p ON e.project_id = p.id
              WHERE t.id = :id AND p.deleted = true
            )
        """,
        nativeQuery = true,
    )
    fun isParentProjectDeletedByTaskId(
        @Param("id") id: Long,
    ): Boolean

    @Modifying
    @Query(value = "UPDATE tasks SET deleted = false WHERE id = :id", nativeQuery = true)
    fun restoreById(
        @Param("id") id: Long,
    ): Int

    @Modifying
    @Query(value = "UPDATE tasks SET deleted = false WHERE epic_id = :epicId", nativeQuery = true)
    fun restoreByEpicId(
        @Param("epicId") epicId: Long,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE tasks SET deleted = false
            WHERE epic_id IN (SELECT id FROM epics WHERE project_id = :projectId)
        """,
        nativeQuery = true,
    )
    fun restoreByProjectId(
        @Param("projectId") projectId: Long,
    ): Int
}
