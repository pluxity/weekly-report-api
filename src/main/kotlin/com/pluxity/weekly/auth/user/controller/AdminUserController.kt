package com.pluxity.weekly.auth.user.controller

import com.pluxity.weekly.auth.user.dto.UserCreateRequest
import com.pluxity.weekly.auth.user.dto.UserLoggedInResponse
import com.pluxity.weekly.auth.user.dto.UserPasswordUpdateRequest
import com.pluxity.weekly.auth.user.dto.UserResponse
import com.pluxity.weekly.auth.user.dto.UserRoleUpdateRequest
import com.pluxity.weekly.auth.user.dto.UserUpdateRequest
import com.pluxity.weekly.auth.user.service.UserService
import com.pluxity.weekly.core.annotation.ResponseCreated
import com.pluxity.weekly.core.response.DataResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/users")
@Tag(name = "Admin User Controller", description = "관리자용 사용자 관리 API")
class AdminUserController(
    private val service: UserService,
) {
    @Operation(summary = "사용자 목록 조회", description = "모든 사용자 목록을 조회합니다")
    @GetMapping
    fun getUsers(): ResponseEntity<DataResponseBody<List<UserResponse>>> = ResponseEntity.ok(DataResponseBody(service.findAll()))

    @Operation(summary = "사용자 상세 조회", description = "ID로 특정 사용자의 상세 정보를 조회합니다")
    @GetMapping("/{id}")
    fun getUser(
        @PathVariable @Parameter(description = "사용자 ID", required = true) id: Long,
    ): ResponseEntity<DataResponseBody<UserResponse>> = ResponseEntity.ok(DataResponseBody(service.findById(id)))

    @Operation(summary = "로그인된 사용자 정보 조회", description = "현재 로그인된 사용자의 정보를 조회합니다.")
    @GetMapping("/with-is-logged-in")
    fun getLoggedInUser(): ResponseEntity<DataResponseBody<List<UserLoggedInResponse>>> =
        ResponseEntity.ok(DataResponseBody(service.isLoggedIn()))

    @Operation(summary = "사용자 생성", description = "새로운 사용자를 생성합니다")
    @PostMapping
    @ResponseCreated(path = "/admin/users/{id}")
    fun saveUser(
        @Parameter(description = "사용자 생성 정보", required = true) @RequestBody @Valid request: UserCreateRequest,
    ): ResponseEntity<Long> = ResponseEntity.ok(service.save(request))

    @Operation(summary = "사용자 정보 수정", description = "기존 사용자의 정보를 수정합니다")
    @PatchMapping("/{id}")
    fun updateUser(
        @PathVariable @Parameter(description = "사용자 ID", required = true) id: Long,
        @Parameter(description = "사용자 수정 정보", required = true) @RequestBody @Valid dto: UserUpdateRequest,
    ): ResponseEntity<Void> {
        service.update(id, dto)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "사용자 비밀번호 변경", description = "사용자의 비밀번호를 변경합니다")
    @PatchMapping("/{id}/password")
    fun updatePassword(
        @PathVariable @Parameter(description = "사용자 ID", required = true) id: Long,
        @Parameter(description = "비밀번호 변경 정보", required = true) @Valid @RequestBody dto: UserPasswordUpdateRequest,
    ): ResponseEntity<Void> {
        service.updateUserPassword(id, dto)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "사용자 역할 수정", description = "사용자의 역할을 수정합니다")
    @PatchMapping("/{id}/roles")
    fun updateRoles(
        @PathVariable @Parameter(description = "사용자 ID", required = true) id: Long,
        @Parameter(description = "역할 수정 정보", required = true) @Valid @RequestBody dto: UserRoleUpdateRequest,
    ): ResponseEntity<Void> {
        service.updateUserRoles(id, dto)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "사용자 삭제", description = "ID로 사용자를 삭제합니다")
    @DeleteMapping("/{id}")
    fun deleteUser(
        @PathVariable @Parameter(description = "사용자 ID", required = true) id: Long,
    ): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "사용자에서 역할 제거", description = "특정 사용자에서 역할을 제거합니다")
    @DeleteMapping("/{userId}/roles/{roleId}")
    fun removeRoleFromUser(
        @PathVariable @Parameter(description = "사용자 ID", required = true) userId: Long,
        @PathVariable @Parameter(description = "역할 ID", required = true) roleId: Long,
    ): ResponseEntity<Void> {
        service.removeRoleFromUser(userId, roleId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "사용자 비밀번호 초기화", description = "사용자의 비밀번호를 초기화합니다")
    @PatchMapping("/{id}/password-init")
    fun initPassword(
        @PathVariable @Parameter(description = "사용자 ID", required = true) id: Long,
    ): ResponseEntity<Void> {
        service.initPassword(id)
        return ResponseEntity.noContent().build()
    }
}
