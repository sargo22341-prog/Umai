package org.opensources.umai.recipe.domain

/**
 * What the signed-in user gave a recipe: their stars, `null` when they never
 * rated it, and whether it is among their favourites. Mealie keeps both on the
 * same row.
 */
data class OwnRating(val stars: Int?, val isFavorite: Boolean) {
    companion object {
        val None = OwnRating(stars = null, isFavorite = false)
    }
}
