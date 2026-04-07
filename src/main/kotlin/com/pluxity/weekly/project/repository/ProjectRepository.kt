package com.pluxity.weekly.project.repository

import com.pluxity.weekly.project.dto.ProjectMemberResponse
import com.pluxity.weekly.project.entity.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProjectRepository :
    JpaRepository<Project, Long>,
    ProjectCustomRepository {
    fun findByPmId(pmId: Long): List<Project>

    fun existsByIdAndPmId(
        id: Long,
        pmId: Long,
    ): Boolean

    @Query(
        """
        SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
        FROM Project p
        JOIN Epic e ON e.project = p
        WHERE e.id = :epicId AND p.pmId = :pmId
        """,
    )
    fun existsByEpicIdAndPmId(
        epicId: Long,
        pmId: Long,
    ): Boolean

    @Query(
        """
        SELECT new com.pluxity.weekly.project.dto.ProjectMemberResponse(
            ea.epic.project.id, u.id, u.name, t.id, t.name
        )
        FROM EpicAssignment ea
        JOIN ea.user u
        LEFT JOIN TeamMember tm ON tm.user = u
        LEFT JOIN tm.team t
        WHERE ea.epic.project.id = :projectId
        GROUP BY ea.epic.project.id, u.id, u.name, t.id, t.name
        """,
    )
    fun findMembersByProjectId(projectId: Long): List<ProjectMemberResponse>

    @Query(
        """
        SELECT new com.pluxity.weekly.project.dto.ProjectMemberResponse(
            ea.epic.project.id, u.id, u.name, t.id, t.name
        )
        FROM EpicAssignment ea
        JOIN ea.user u
        LEFT JOIN TeamMember tm ON tm.user = u
        LEFT JOIN tm.team t
        WHERE ea.epic.project.id IN :projectIds
        GROUP BY ea.epic.project.id, u.id, u.name, t.id, t.name
        """,
    )
    fun findMembersByProjectIds(projectIds: List<Long>): List<ProjectMemberResponse>
}
