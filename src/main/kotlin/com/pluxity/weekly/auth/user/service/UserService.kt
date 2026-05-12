package com.pluxity.weekly.auth.user.service

import com.pluxity.weekly.auth.authentication.repository.RefreshTokenRepository
import com.pluxity.weekly.auth.properties.UserProperties
import com.pluxity.weekly.auth.user.dto.UserCreateRequest
import com.pluxity.weekly.auth.user.dto.UserLoggedInResponse
import com.pluxity.weekly.auth.user.dto.UserPasswordUpdateRequest
import com.pluxity.weekly.auth.user.dto.UserResponse
import com.pluxity.weekly.auth.user.dto.UserRoleUpdateRequest
import com.pluxity.weekly.auth.user.dto.UserUpdateRequest
import com.pluxity.weekly.auth.user.dto.toLoggedInResponse
import com.pluxity.weekly.auth.user.dto.toResponse
import com.pluxity.weekly.auth.user.entity.Role
import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.repository.RoleRepository
import com.pluxity.weekly.auth.user.repository.UserRepository
import com.pluxity.weekly.auth.user.repository.UserRoleRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import com.pluxity.weekly.core.utils.SortUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRoleRepository: UserRoleRepository,
    private val userProperties: UserProperties,
) {
    fun findById(id: Long): UserResponse {
        val user = findUserById(id)
        return user.toResponse()
    }

    fun findAll(): List<UserResponse> {
        val users = userRepository.findAllBy(SortUtils.orderByCreatedAtDesc)
        return users.map { it.toResponse() }
    }

    fun findByUsername(username: String): UserResponse {
        val user = findUserByUsername(username)
        return user.toResponse()
    }

    @Transactional
    fun save(request: UserCreateRequest): Long {
        val user =
            User(
                username = request.username,
                password = requireNotNull(passwordEncoder.encode(request.password)),
                name = request.name,
                code = request.code,
                phoneNumber = request.phoneNumber,
                email = request.email,
            )

        user.changeProfileImageId(request.profileImageId)

        if (request.roleIds.isNotEmpty()) {
            val roles = request.roleIds.map { findRoleById(it) }
            user.addRoles(roles)
        }

        val savedUser = userRepository.save(user)

        return savedUser.requiredId
    }

    @Transactional
    fun update(
        id: Long,
        request: UserUpdateRequest,
    ): UserResponse {
        val user = findUserById(id)
        val oldProfileImageId = user.profileImageId
        updateUserFields(user, request)
        changeRole(request.roleIds, user)

        return user.toResponse()
    }

    private fun changeRole(
        roleIds: List<Long>?,
        user: User,
    ) {
        if (roleIds == null) {
            return
        }
        val newRoles = roleRepository.findAllById(roleIds)
        val newRoleIds =
            newRoles
                .map {
                    it.id
                }.toSet()

        val rolesToRemove =
            user.userRoles
                .filter {
                    !newRoleIds.contains(it.role.id)
                }

        if (rolesToRemove.isNotEmpty()) {
            userRoleRepository.deleteAll(rolesToRemove)
        }
        user.updateRoles(newRoles)
    }

    @Transactional
    fun delete(id: Long) {
        val user = findUserById(id)
        refreshTokenRepository.findByIdOrNull(user.username)?.let { refreshTokenRepository.delete(it) }
        userRepository.delete(user)
    }

    /**
     * Teams 채널을 통한 자동 가입/복원.
     * - 같은 aadObjectId가 이미 있으면(soft-deleted 포함) 복원 후 Teams 정보 갱신
     * - 같은 email로 미리 등록된 user가 있으면 aadObjectId 연결만
     * - 둘 다 없으면 신규 생성 (USER role 부여, 임의 비밀번호)
     * 도메인 검증 실패 또는 필수 정보 누락 시 null 반환.
     */
    @Transactional
    fun provisionFromTeams(
        aadObjectId: String,
        displayName: String?,
        email: String?,
        teamsServiceUrl: String?,
        teamsConversationId: String?,
    ): User? {
        if (email.isNullOrBlank() || displayName.isNullOrBlank()) {
            log.warn { "Teams 자동 가입 실패 - 필수 정보 누락 (aadObjectId: $aadObjectId, name: $displayName, email: $email)" }
            return null
        }
        if (!isEmailDomainAllowed(email)) {
            log.warn { "Teams 자동 가입 거부 - 허용되지 않은 도메인 (email: $email)" }
            return null
        }

        userRepository.findByAadObjectIdIncludingDeleted(aadObjectId)?.let { existing ->
            return restoreAndSync(existing, displayName, teamsServiceUrl, teamsConversationId)
        }

        userRepository.findByEmail(email)?.let { existing ->
            log.info { "기존 email 사용자에 Teams 정보 연결 - userId: ${existing.requiredId}, email: $email" }
            if (teamsServiceUrl != null && teamsConversationId != null) {
                existing.updateTeamsInfo(aadObjectId, teamsServiceUrl, teamsConversationId)
            } else {
                existing.aadObjectId = aadObjectId
            }
            return existing
        }

        return createTeamsUser(aadObjectId, displayName, email, teamsServiceUrl, teamsConversationId)
    }

    private fun isEmailDomainAllowed(email: String): Boolean {
        val allowed = userProperties.allowedEmailDomains
        if (allowed.isEmpty()) return false
        val domain = email.substringAfter('@', missingDelimiterValue = "").lowercase()
        return domain.isNotBlank() && allowed.any { it.lowercase() == domain }
    }

    private fun restoreAndSync(
        user: User,
        displayName: String,
        teamsServiceUrl: String?,
        teamsConversationId: String?,
    ): User {
        val id = user.requiredId
        val wasDeleted = userRepository.restoreById(id) > 0
        if (wasDeleted) log.info { "soft-deleted 사용자 복원 - userId: $id" }
        val fresh = userRepository.findByIdOrNull(id) ?: error("복원 직후 사용자 조회 실패: $id")
        fresh.changeName(displayName)
        if (teamsServiceUrl != null && teamsConversationId != null) {
            fresh.updateTeamsInfo(fresh.aadObjectId ?: error("aadObjectId 누락"), teamsServiceUrl, teamsConversationId)
        }
        return fresh
    }

    private fun createTeamsUser(
        aadObjectId: String,
        displayName: String,
        email: String,
        teamsServiceUrl: String?,
        teamsConversationId: String?,
    ): User {
        val newUser =
            User(
                username = email,
                password = requireNotNull(passwordEncoder.encode(UUID.randomUUID().toString())),
                name = displayName,
                code = null,
                phoneNumber = null,
                email = email,
            )
        newUser.aadObjectId = aadObjectId
        if (teamsServiceUrl != null && teamsConversationId != null) {
            newUser.updateTeamsInfo(aadObjectId, teamsServiceUrl, teamsConversationId)
        }
        roleRepository.findByName(RoleType.USER.roleName)?.let { newUser.addRole(it) }
            ?: log.warn { "기본 USER role 미존재 - role 없이 사용자 생성됨" }
        val saved = userRepository.save(newUser)
        log.info { "Teams 자동 가입 - userId: ${saved.requiredId}, email: $email" }
        return saved
    }

    @Transactional
    fun removeProfileImage(id: Long) {
        val user = findUserById(id)
        user.changeProfileImageId(null)
    }

    @Transactional
    fun removeProfileImage(username: String) {
        val user = findUserByUsername(username)
        user.changeProfileImageId(null)
    }

    @Transactional
    fun removeRoleFromUser(
        userId: Long,
        roleId: Long,
    ) {
        val user = findUserById(userId)
        val role = findRoleById(roleId)
        user.removeRole(role)
    }

    private fun findUserById(id: Long): User =
        userRepository.findWithGraphById(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_USER, id)

    private fun findRoleById(id: Long): Role =
        roleRepository
            .findByIdOrNull(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_ROLE, id)

    fun findUserByUsername(username: String): User =
        userRepository
            .findByUsername(username)
            ?: throw CustomException(ErrorCode.NOT_FOUND_USER, username)

    @Transactional
    fun updateUserPassword(
        id: Long,
        request: UserPasswordUpdateRequest,
    ) {
        val user = findUserById(id)

        if (!passwordEncoder.matches(request.currentPassword, user.password)) {
            throw CustomException(ErrorCode.INVALID_ID_OR_PASSWORD, "현재 비밀번호가 일치하지 않습니다.")
        }

        user.changePassword(requireNotNull(passwordEncoder.encode(request.newPassword)))
    }

    @Transactional
    fun updateUserRoles(
        id: Long,
        request: UserRoleUpdateRequest,
    ) {
        val user = findUserById(id)
        changeRole(request.roleIds, user)
    }

    private fun updateUserFields(
        user: User,
        request: UserUpdateRequest,
    ) {
        if (!request.name.isNullOrBlank()) {
            user.changeName(request.name)
        }
        if (!request.code.isNullOrBlank()) {
            user.changeCode(request.code)
        }
        if (request.phoneNumber != null) {
            user.changePhoneNumber(request.phoneNumber)
        }
        if (request.email != null) {
            user.changeEmail(request.email)
        }
        if (request.profileImageId != null) {
            user.changeProfileImageId(request.profileImageId)
        }
    }

    fun isLoggedIn(): List<UserLoggedInResponse> {
        val users = userRepository.findAllBy(SortUtils.orderByCreatedAtDesc)
        return users.map { user ->
            val refreshToken = refreshTokenRepository.findByIdOrNull(user.username)
            val isLoggedIn = refreshToken != null
            user.toLoggedInResponse(isLoggedIn)
        }
    }

    @Transactional
    fun initPassword(id: Long) {
        val user = findUserById(id)
        user.initPassword(requireNotNull(passwordEncoder.encode(userProperties.initPassword)))
    }

    @Transactional
    fun updateUserPassword(
        name: String,
        dto: UserPasswordUpdateRequest,
    ) {
        val id = findByUsername(name).id
        updateUserPassword(id, dto)
    }

    fun findAllUserNames(): List<String> =
        userRepository
            .findAll()
            .map { it.username }
}
