package com.pluxity.weekly.project.repository

import com.pluxity.weekly.chat.dto.ProjectSearchFilter
import com.pluxity.weekly.project.entity.Project

interface ProjectCustomRepository {
    fun findByFilter(filter: ProjectSearchFilter): List<Project>
}
