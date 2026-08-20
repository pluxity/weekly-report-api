package com.pluxity.weekly.auth.authorization

import com.pluxity.weekly.auth.authorization.AccessPolicy
import com.pluxity.weekly.auth.authorization.CurrentUserProvider
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.auth.user.service.UserService
import com.pluxity.weekly.core.constant.ErrorCode
import com.pluxity.weekly.core.exception.CustomException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 요청을 보낸 사용자를 꺼낸다. 인증(누구냐)이지 인가(뭘 할 수 있냐)가 아니므로
 * [AccessPolicy] 와 분리한다.
 */
@Component
class CurrentUserProvider(
    private val userService: UserService,
) {
    fun get(): User {
        val authentication =
            SecurityContextHolder.getContext().authentication
                ?: throw CustomException(ErrorCode.PERMISSION_DENIED)
        return userService.findUserByUsername(authentication.name)
    }
}
