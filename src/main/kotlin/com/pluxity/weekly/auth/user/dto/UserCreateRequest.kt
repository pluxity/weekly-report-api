package com.pluxity.weekly.auth.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "사용자 생성 요청")
data class UserCreateRequest(
    @field:NotBlank(message = "사용자 ID는 공백이 될 수 없습니다.")
    @field:Size(max = 20, message = "사용자 ID는 20자 이하 여야 합니다.")
    @field:Schema(description = "로그인 ID", example = "admin")
    val username: String,
    @field:NotBlank(message = "비밀번호는 공백이 될 수 없습니다.")
    @field:Size(min = 6, max = 20, message = "비밀번호는 6자 이상 20자 이하 여야 합니다.")
    @field:Schema(description = "비밀번호", example = "password123")
    val password: String,
    @field:NotBlank(message = "이름은 공백이 될 수 없습니다.")
    @field:Size(max = 10, message = "이름은 10자 이하 여야 합니다.")
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
    @field:Size(max = 20, message = "코드는 20자 이하 여야 합니다.")
    @field:Schema(description = "코드", example = "EMP001")
    val code: String? = null,
    @field:Size(max = 20, message = "연락처는 20자 이하 여야 합니다.")
    @field:Schema(description = "연락처", example = "010-1234-5678")
    val phoneNumber: String? = null,
    @field:Size(max = 50, message = "부서는 50자 이하 여야 합니다.")
    @field:Schema(description = "부서", example = "개발팀")
    val department: String? = null,
    @field:Schema(description = "프로필 이미지 파일 ID", example = "1")
    val profileImageId: Long? = null,
    @field:Schema(description = "역할 ID 목록", example = "[1, 2]")
    val roleIds: List<Long> = listOf(),
)
