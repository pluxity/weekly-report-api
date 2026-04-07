package com.pluxity.weekly.epic.repository

import com.pluxity.weekly.chat.dto.EpicSearchFilter
import com.pluxity.weekly.epic.entity.Epic

interface EpicCustomRepository {
    fun findByFilter(filter: EpicSearchFilter): List<Epic>
}
