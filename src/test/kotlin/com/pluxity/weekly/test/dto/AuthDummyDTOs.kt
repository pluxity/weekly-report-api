package com.pluxity.weekly.test.dto

import com.pluxity.weekly.auth.authentication.dto.SignInRequest
import com.pluxity.weekly.auth.authentication.dto.SignUpRequest
import com.pluxity.weekly.auth.user.dto.RoleCreateRequest
import com.pluxity.weekly.auth.user.dto.RoleUpdateRequest
import com.pluxity.weekly.auth.user.dto.UserCreateRequest
import com.pluxity.weekly.auth.user.dto.UserPasswordUpdateRequest
import com.pluxity.weekly.auth.user.dto.UserRoleUpdateRequest
import com.pluxity.weekly.auth.user.dto.UserUpdateRequest
import com.pluxity.weekly.auth.user.entity.RoleType

fun dummyUserCreateRequest(
    username: String = "username",
    password: String = "password",
    name: String = "name",
    code: String = "code",
    phoneNumber: String? = null,
    email: String? = null,
    profileImageId: Long? = null,
    roleIds: List<Long> = listOf(),
): UserCreateRequest =
    UserCreateRequest(
        username = username,
        password = password,
        name = name,
        code = code,
        phoneNumber = phoneNumber,
        email = email,
        profileImageId = profileImageId,
        roleIds = roleIds,
    )

fun dummyUserUpdateRequest(
    name: String = "name",
    code: String = "code",
    phoneNumber: String? = null,
    email: String? = null,
    profileImageId: Long? = null,
    roleIds: List<Long>? = null,
): UserUpdateRequest =
    UserUpdateRequest(
        name = name,
        code = code,
        phoneNumber = phoneNumber,
        email = email,
        profileImageId = profileImageId,
        roleIds = roleIds,
    )

fun dummyUserRoleAssignRequest(roleIds: List<Long> = listOf()): UserRoleUpdateRequest = UserRoleUpdateRequest(roleIds)

fun dummyUserPasswordUpdateRequest(
    currentPassword: String = "currentPassword",
    newPassword: String = "newPassword",
): UserPasswordUpdateRequest = UserPasswordUpdateRequest(currentPassword, newPassword)

fun dummySignUpRequest(
    username: String = "username",
    password: String = "password",
    name: String = "name",
    code: String = "code",
): SignUpRequest = SignUpRequest(username, password, name, code)

fun dummySignInRequest(
    username: String = "username",
    password: String = "password",
): SignInRequest = SignInRequest(username, password)

fun dummyRoleCreateRequest(
    name: String = "일반 사용자",
    description: String? = "기본 역할",
    authority: RoleType = RoleType.USER,
): RoleCreateRequest = RoleCreateRequest(name, description, authority)

fun dummyRoleUpdateRequest(
    name: String? = "수정된 역할",
    description: String? = "수정된 설명",
): RoleUpdateRequest = RoleUpdateRequest(name, description)
