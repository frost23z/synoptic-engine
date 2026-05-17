package com.synopticengine.api.shared.web

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val size: Int,
    val number: Int,
    val first: Boolean,
    val last: Boolean,
    val numberOfElements: Int,
    val empty: Boolean,
) {
    companion object {
        fun <T : Any, R> of(
            page: Page<T>,
            mapper: (T) -> R,
        ): PageResponse<R> =
            PageResponse(
                content = page.content.map(mapper),
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                size = page.size,
                number = page.number,
                first = page.isFirst,
                last = page.isLast,
                numberOfElements = page.numberOfElements,
                empty = page.isEmpty,
            )
    }
}
