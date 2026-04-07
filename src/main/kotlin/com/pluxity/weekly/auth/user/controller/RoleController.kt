package com.pluxity.weekly.auth.user.controller

import com.pluxity.weekly.auth.user.dto.RoleCreateRequest
import com.pluxity.weekly.auth.user.dto.RoleResponse
import com.pluxity.weekly.auth.user.dto.RoleUpdateRequest
import com.pluxity.weekly.auth.user.service.RoleService
import com.pluxity.weekly.core.annotation.ResponseCreated
import com.pluxity.weekly.core.response.DataResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/roles")
@Tag(name = "Role Controller", description = "역할 관리 API")
class RoleController(
    private val roleService: RoleService,
) {
    @Operation(summary = "역할 상세 조회", description = "ID로 특정 역할의 상세 정보를 조회합니다")
    @GetMapping("/{id}")
    fun getRole(
        @PathVariable @Parameter(description = "역할 ID", required = true) id: Long,
    ): ResponseEntity<DataResponseBody<RoleResponse>> = ResponseEntity.ok(DataResponseBody(roleService.findById(id)))

    @Operation(summary = "역할 목록 조회", description = "모든 역할 목록을 조회합니다")
    @GetMapping
    fun getAllRoles(): ResponseEntity<DataResponseBody<List<RoleResponse>>> = ResponseEntity.ok(DataResponseBody(roleService.findAll()))

    @Operation(summary = "역할 생성", description = "새로운 역할을 생성합니다")
    @PostMapping
    @ResponseCreated(path = "/roles/{id}")
    fun createRole(
        authentication: Authentication,
        @Parameter(description = "역할 생성 정보", required = true) @RequestBody @Valid request: RoleCreateRequest,
    ): ResponseEntity<Long> = ResponseEntity.ok(roleService.save(request, authentication))

    @Operation(summary = "역할 수정", description = "기존 역할의 정보를 수정합니다")
    @PatchMapping("/{id}")
    fun updateRole(
        @PathVariable @Parameter(description = "역할 ID", required = true) id: Long,
        @Parameter(description = "역할 수정 정보", required = true) @RequestBody @Valid request: RoleUpdateRequest,
    ): ResponseEntity<Void> {
        roleService.update(id, request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "역할 삭제", description = "ID로 역할을 삭제합니다")
    @DeleteMapping("/{id}")
    fun deleteRole(
        @PathVariable @Parameter(description = "역할 ID", required = true) id: Long,
    ): ResponseEntity<Void> {
        roleService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
