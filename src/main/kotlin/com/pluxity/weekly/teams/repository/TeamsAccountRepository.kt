package com.pluxity.weekly.teams.repository

import com.pluxity.weekly.teams.entity.TeamsAccount
import org.springframework.data.jpa.repository.JpaRepository

interface TeamsAccountRepository : JpaRepository<TeamsAccount, Long> {
    fun findByUserId(userId: Long): TeamsAccount?

    fun findByAadObjectId(aadObjectId: String): TeamsAccount?
}
