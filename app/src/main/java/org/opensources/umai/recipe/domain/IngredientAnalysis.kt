package org.opensources.umai.recipe.domain

import kotlinx.serialization.Serializable

@Serializable
data class IngredientChoice(val name: String, val id: String? = null)

/** Structured quantities remain in the recipe's original unit; no guessed mass conversion. */
@Serializable
data class IngredientAnalysis(
    val quantity: Double?,
    val food: IngredientChoice?,
    val unit: IngredientChoice? = null,
    val note: String = "",
    val needsReview: Boolean = false,
    val keptAsText: Boolean = false,
    val confidence: Double? = null,
    val modelSuggestion: Boolean = false,
) {
    val isScalable: Boolean get() = quantity != null && quantity.isFinite() && quantity > 0 && !food?.name.isNullOrBlank()
    val isResolved: Boolean get() = keptAsText || (isScalable && !needsReview)
}

data class IngredientChoices(val foods: List<IngredientChoice>, val units: List<IngredientChoice>)
