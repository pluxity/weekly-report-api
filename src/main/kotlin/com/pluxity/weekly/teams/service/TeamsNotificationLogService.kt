package com.pluxity.weekly.teams.service

import com.pluxity.weekly.auth.authorization.AuthorizationService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.core.response.PageResponse
import com.pluxity.weekly.core.response.toPageResponse
import com.pluxity.weekly.teams.dto.TeamsNotificationLogResponse
import com.pluxity.weekly.teams.dto.toResponse
import com.pluxity.weekly.teams.entity.TeamsNotificationLog
import com.pluxity.weekly.teams.entity.TeamsNotificationStatus
import com.pluxity.weekly.teams.entity.TeamsNotificationType
import com.pluxity.weekly.teams.event.TeamsNotificationEvent
import com.pluxity.weekly.teams.repository.TeamsNotificationLogRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamsNotificationLogService(
    private val logRepository: TeamsNotificationLogRepository,
    private val authorizationService: AuthorizationService,
    private val eventPublisher: ApplicationEventPublisher,
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

    /** ADMIN 전용 — status null 이면 전체, 값이 있으면 해당 상태만 페이징 조회. */
    fun findAllForAdmin(
        status: TeamsNotificationStatus?,
        pageable: Pageable,
    ): PageResponse<TeamsNotificationLogResponse> {
        val user = authorizationService.currentUser()
        authorizationService.requireAdmin(user)
        val page =
            if (status == null) {
                logRepository.findAll(pageable)
            } else {
                logRepository.findByStatus(status, pageable)
            }
        return page.toPageResponse { it.toResponse() }
    }

    /**
     * 실패한 알림을 다시 발사한다. ADMIN 전용.
     * 카드 알림이었더라도 메시지만 재전송한다(원본 카드 콘텐츠는 보존되지 않음).
     */
    @Transactional
    fun retry(logId: Long): TeamsNotificationLogResponse {
        val user = authorizationService.currentUser()
        authorizationService.requireAdmin(user)

        val log =
            logRepository.findByIdOrNull(logId)
                ?: throw CustomException(ErrorCode.NOT_FOUND_TEAMS_NOTIFICATION, logId)

        if (log.status != TeamsNotificationStatus.FAILED) {
            throw CustomException(ErrorCode.INVALID_STATUS_TRANSITION, log.status, "RETRY")
        }

        log.markPending()

        eventPublisher.publishEvent(
            TeamsNotificationEvent(
                logId = log.requiredId,
                userId = log.userId,
                message = log.message,
                card = null,
            ),
        )

        return log.toResponse()
    }
}
