package com.pluxity.weekly.auth.user.service

import com.pluxity.weekly.auth.user.dto.RoleCreateRequest
import com.pluxity.weekly.auth.user.dto.RoleResponse
import com.pluxity.weekly.auth.user.dto.RoleUpdateRequest
import com.pluxity.weekly.auth.user.dto.toResponse
import com.pluxity.weekly.auth.user.entity.Role
import com.pluxity.weekly.auth.user.entity.RoleType
import com.pluxity.weekly.auth.user.repository.RoleRepository
import com.pluxity.weekly.auth.user.repository.UserRoleRepository
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import jakarta.persistence.EntityManager
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RoleService(
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val em: EntityManager,
) {
    @Transactional
    fun save(
        request: RoleCreateRequest,
        authentication: Authentication,
    ): Long {
        if (request.authority == RoleType.ADMIN && authentication.authorities.none { it.authority == "ROLE_${RoleType.ADMIN.name}" }) {
            throw CustomException(ErrorCode.PERMISSION_DENIED)
        }
        val role =
            roleRepository.save(
                Role(
                    name = request.name,
                    description = request.description,
                    auth = request.authority.name,
                ),
            )
        return role.requiredId
    }

    fun findById(id: Long): RoleResponse = findRoleById(id).toResponse()

    fun findAll(): List<RoleResponse> = roleRepository.findAllByOrderByCreatedAtDesc().map { it.toResponse() }

    @Transactional
    fun update(
        id: Long,
        request: RoleUpdateRequest,
    ) {
        val role = findRoleById(id)

        request.name?.takeIf { it.isNotBlank() }?.let {
            role.changeRoleName(it)
        }
        request.description?.let { role.changeDescription(request.description) }
    }

    @Transactional
    fun delete(id: Long) {
        val role = findRoleById(id)
        userRoleRepository.deleteAllByRole(role)
        em.flush()
        em.clear()
        roleRepository.deleteById(role.requiredId)
    }

    fun findRoleById(id: Long): Role =
        roleRepository.findWithInfoById(id)
            ?: throw CustomException(ErrorCode.NOT_FOUND_ROLE, id)
}
