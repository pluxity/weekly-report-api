package com.pluxity.weekly.auth.authentication.repository

import com.pluxity.weekly.auth.authentication.entity.RefreshToken
import org.springframework.data.repository.CrudRepository

interface RefreshTokenRepository : CrudRepository<RefreshToken, String> {
    fun findByToken(token: String): RefreshToken?
}
