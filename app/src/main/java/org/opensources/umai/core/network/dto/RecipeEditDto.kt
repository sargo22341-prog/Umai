package org.opensources.umai.core.network.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Payloads that create a recipe. Mealie creates an empty recipe from a name and
 * returns its slug; the details are then sent with `PUT /api/recipes/{slug}`,
 * which takes the very same shape as [RecipeDetailDto].
 */
@Serializable
data class CreateRecipeDto(val name: String)

/**
 * Body of `POST /api/recipes/create/url`; the answer is the new slug.
 *
 * The two switches are always written out: what the user ticked is stated to
 * the server rather than left to whatever its own default happens to be.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScrapeRecipeDto(
    val url: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val includeTags: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val includeCategories: Boolean = false,
)
