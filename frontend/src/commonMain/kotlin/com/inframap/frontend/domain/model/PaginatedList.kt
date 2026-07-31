package com.inframap.frontend.domain.model

data class PaginatedList<T>(
    val items: List<T>,
    val total: Long,
    val page: Int = 1,
    val perPage: Int = 50,
)
