package org.opensources.umai.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `PaginationBase[T]` from the Mealie OpenAPI schema. */
@Serializable
data class PaginationDto<T>(
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
    val total: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    val items: List<T> = emptyList(),
    val next: String? = null,
    val previous: String? = null,
)
