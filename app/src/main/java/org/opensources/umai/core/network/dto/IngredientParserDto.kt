package org.opensources.umai.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class IngredientsRequestDto(val ingredients: List<String>, val parser: String = "nlp")

@Serializable
data class IngredientNameDto(val name: String)

@Serializable
data class ParsedIngredientDto(
    val input: String? = null,
    val ingredient: RecipeIngredientDto,
    val confidence: IngredientConfidenceDto = IngredientConfidenceDto(),
)

@Serializable
data class IngredientConfidenceDto(
    val average: Double? = null,
    val quantity: Double? = null,
    val unit: Double? = null,
    val food: Double? = null,
    val name: Double? = null,
)
