package com.pluxity.weekly.report.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "정제된 주간보고 (LLM 분류 결과). 항목 단위로 평탄화, assignee/category 등은 항목 메타.")
data class FormattedReport(
    @field:Schema(description = "이번주 진행 항목")
    val thisWeek: List<ReportItem> = emptyList(),
    @field:Schema(description = "다음주 예정 항목")
    val nextWeek: List<ReportItem> = emptyList(),
    @field:Schema(description = "이슈/리스크/장애 항목")
    val issues: List<ReportItem> = emptyList(),
    @field:Schema(description = "분류 모호 / 보고 외 정보로 판단된 항목")
    val others: List<ReportItem> = emptyList(),
)

@Schema(description = "주간보고 단위 항목. 원문 텍스트는 변질되지 않으며, assignee/category/progress/dueDate는 LLM이 추출한 메타.")
data class ReportItem(
    @field:Schema(description = "담당자명 (사람 헤더 인식 실패 시 null)", example = "홍길동")
    val assignee: String?,
    @field:Schema(description = "카테고리 (사업/프로젝트명/태그). 추출 실패 시 null", example = "ProductA v1.0")
    val category: String?,
    @field:Schema(description = "항목 본문 (원문 보존)", example = "#95 메인 페이지 구현")
    val text: String,
    @field:Schema(description = "진행률/상태 원문 표기. 추출 실패 시 null", example = "100%")
    val progress: String?,
    @field:Schema(description = "마감일 (텍스트에서 추출 가능 시). 추출 실패 시 null", example = "2026-05-22")
    val dueDate: LocalDate?,
)
