package com.pluxity.weekly.teams.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.core.response.PageResponse
import com.pluxity.weekly.core.response.toPageResponse
import com.pluxity.weekly.teams.dto.TeamsNotificationLogResponse
import com.pluxity.weekly.teams.dto.toResponse
import com.pluxity.weekly.teams.entity.TeamsNotificationLog
import com.pluxity.weekly.teams.entity.TeamsNotificationType
import com.pluxity.weekly.teams.repository.TeamsNotificationLogRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamsNotificationLogService(
    private val logRepository: TeamsNotificationLogRepository,
    private val authorizationService: AuthorizationService,
) {
    @Transactional
    fun savePending(
        userId: Long,
        type: TeamsNotificationType,
        message: String,
    ): TeamsNotificationLog {
        val log =
            TeamsNotificationLog(
                userId = userId,
                type = type,
                message = message,
            )
        return logRepository.save(log)
    }

    @Transactional
    fun markSent(logId: Long) {
        val log = logRepository.findByIdOrNull(logId) ?: return
        log.markSent()
    }

    @Transactional
    fun markFailed(
        logId: Long,
        reason: String?,
    ) {
        val log = logRepository.findByIdOrNull(logId) ?: return
        log.markFailed(reason)
    }

    fun findMine(pageable: Pageable): PageResponse<TeamsNotificationLogResponse> {
        val user = authorizationService.currentUser()
        val userId = user.id ?: throw CustomException(ErrorCode.PERMISSION_DENIED)
        return logRepository.findByUserId(userId, pageable).toPageResponse { it.toResponse() }
    }
}
