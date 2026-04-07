package com.pluxity.weekly.test.entity

import com.pluxity.weekly.auth.authentication.entity.RefreshToken
import com.pluxity.weekly.auth.user.entity.Role
import com.pluxity.weekly.auth.user.entity.User
import com.pluxity.weekly.test.withId

fun dummyUser(
    id: Long? = 1L,
    username: String = "username",
    password: String = "password",
    name: String = "name",
    code: String? = "code",
    phoneNumber: String? = null,
    department: String? = null,
): User = User(username, password, name, code, phoneNumber, department).withId(id)

fun dummyRole(
    id: Long? = 1L,
    name: String = "name",
    description: String? = "description",
): Role = Role(name, description).withId(id)

fun dummyRefreshToken(
    username: String = "username",
    token: String = "token",
    timeToLive: Int = 30,
): RefreshToken = RefreshToken(username, token, timeToLive)
