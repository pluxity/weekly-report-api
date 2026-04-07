package com.pluxity.weekly.core.response

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val content: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalElements: Long,
    val last: Boolean,
    val first: Boolean,
)

fun <T : Any, R> Page<T>.toPageResponse(transform: (T) -> R): PageResponse<R> =
    PageResponse(
        content = this.content.map(transform),
        pageNumber = this.number + 1,
        pageSize = this.size,
        totalElements = this.totalElements,
        last = this.isLast,
        first = this.isFirst,
    )

data class CursorPageResponse<T>(
    val content: List<T>,
    val nextCursor: Long?,
    val hasNext: Boolean,
)

fun <T> List<T>.toCursorPageResponse(
    hasNext: Boolean,
    getCursor: (T) -> Long?,
): CursorPageResponse<T> =
    CursorPageResponse(
        content = if (hasNext) this.dropLast(1) else this,
        nextCursor = if (hasNext) this.lastOrNull()?.let(getCursor) else null,
        hasNext = hasNext,
    )
