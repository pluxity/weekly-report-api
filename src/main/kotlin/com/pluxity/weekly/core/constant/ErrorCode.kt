package com.pluxity.weekly.core.constant

import org.springframework.http.HttpStatus

enum class ErrorCode(
    private val httpStatus: HttpStatus,
    private val message: String,
) : Code {
    // ── Auth ──
    INVALID_ID_OR_PASSWORD(HttpStatus.BAD_REQUEST, "아이디 또는 비밀번호가 틀렸습니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "ACCESS 토큰이 유효하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "REFRESH 토큰이 유효하지 않습니다."),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "ACCESS 토큰이 만료되었습니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "REFRESH 토큰이 만료되었습니다."),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "해당 작업에 대한 권한이 없습니다."),

    // ── User / Role ──
    DUPLICATE_USERNAME(HttpStatus.BAD_REQUEST, "이미 존재하는 아이디입니다."),
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "요청하신 사용자를 찾을 수 없습니다."),
    NOT_FOUND_ROLE(HttpStatus.NOT_FOUND, "요청하신 권한(Role)을 찾을 수 없습니다."),
    DUPLICATE_ROLE(HttpStatus.BAD_REQUEST, "이미 할당된 권한입니다."),
    NOT_FOUND_USER_ROLE(HttpStatus.NOT_FOUND, "사용자에게 할당되지 않은 권한입니다."),
    USER_HAS_ACTIVE_RESPONSIBILITY(
        HttpStatus.CONFLICT,
        "사용자가 프로젝트 PM 또는 팀 리더로 배정되어 있어 삭제할 수 없습니다. 먼저 다른 사용자로 인계해주세요.",
    ),

    // ── Team ──
    NOT_FOUND_TEAM(HttpStatus.NOT_FOUND, "요청하신 팀을 찾을 수 없습니다."),
    NOT_FOUND_TEAM_MEMBER(HttpStatus.NOT_FOUND, "해당 사용자가 팀에 소속되어 있지 않습니다."),
    DUPLICATE_TEAM_MEMBER(HttpStatus.BAD_REQUEST, "이미 해당 팀에 소속된 사용자입니다."),

    // ── Project / Epic / Task ──
    NOT_FOUND_PROJECT(HttpStatus.NOT_FOUND, "요청하신 프로젝트를 찾을 수 없습니다."),
    NOT_FOUND_EPIC(HttpStatus.NOT_FOUND, "요청하신 업무 그룹을 찾을 수 없습니다."),
    DUPLICATE_EPIC_ASSIGNMENT(HttpStatus.BAD_REQUEST, "이미 해당 업무 그룹에 배정된 사용자입니다."),
    NOT_FOUND_EPIC_ASSIGNMENT(HttpStatus.NOT_FOUND, "해당 사용자가 업무 그룹에 배정되어 있지 않습니다."),
    NOT_FOUND_TASK(HttpStatus.NOT_FOUND, "요청하신 태스크를 찾을 수 없습니다."),
    DUPLICATE_TASK(HttpStatus.BAD_REQUEST, "업무 그룹 '%s'에 이미 '%s' 태스크가 존재합니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "현재 상태에서는 해당 동작을 수행할 수 없습니다."),
    INVALID_INITIAL_STATUS(HttpStatus.BAD_REQUEST, "해당 상태로는 생성할 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일(%s)이 마감일(%s)보다 늦을 수 없습니다."),
    EPIC_NOT_ALL_DONE(HttpStatus.BAD_REQUEST, "프로젝트를 완료하려면 모든 하위 업무 그룹을 먼저 완료해야 합니다."),
    TASK_NOT_ALL_DONE(HttpStatus.BAD_REQUEST, "업무 그룹을 완료하려면 모든 하위 태스크를 먼저 완료해야 합니다."),
    PARENT_PROJECT_DELETED(HttpStatus.BAD_REQUEST, "상위 프로젝트가 삭제 상태입니다. 프로젝트부터 복구해주세요."),
    PARENT_EPIC_DELETED(HttpStatus.BAD_REQUEST, "상위 업무 그룹이 삭제 상태입니다. 업무 그룹부터 복구해주세요."),

    // ── Teams Notification ──
    NOT_FOUND_TEAMS_NOTIFICATION(HttpStatus.NOT_FOUND, "요청하신 Teams 알림을 찾을 수 없습니다."),

    // ── Chat / LLM ──
    LLM_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "LLM 서비스에 연결할 수 없습니다."),
    LLM_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."),
    CHAT_SELECT_REQUIRED(HttpStatus.BAD_REQUEST, "%s"),
    CHAT_CLARIFY(HttpStatus.BAD_REQUEST, "%s"),
    CHAT_SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "선택 세션이 만료되었거나 존재하지 않습니다."),
    CHAT_RESOLVE_INVALID(HttpStatus.BAD_REQUEST, "%s"),
    CHAT_ALREADY_PROCESSING(HttpStatus.TOO_MANY_REQUESTS, "이전 요청을 처리 중입니다. 잠시 후 다시 시도해주세요."),

    NOT_FOUND_WEEKLY_REPORT(HttpStatus.NOT_FOUND, "요청하신 주간보고를 찾을 수 없습니다."),

    // ── Common (DB / Request) ──
    DUPLICATE_RESOURCE_ID(HttpStatus.BAD_REQUEST, "중복된 리소스 ID가 포함되어 있습니다."),
    REFERENCED_RESOURCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "참조 대상이 존재하지 않습니다."),
    RESOURCE_STILL_REFERENCED(HttpStatus.CONFLICT, "다른 데이터에서 참조 중이므로 삭제할 수 없습니다."),
    MISSING_REQUIRED_VALUE(HttpStatus.BAD_REQUEST, "필수 값이 누락되었습니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.BAD_REQUEST, "데이터 무결성 제약 조건 위반입니다."),
    ;

    override fun getHttpStatus(): HttpStatus = httpStatus

    override fun getMessage(): String = message

    override fun getStatusName(): String = httpStatus.name

    override fun getCodeName(): String = name
}
