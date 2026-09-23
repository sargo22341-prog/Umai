package org.opensources.umai.navigation

import androidx.annotation.StringRes
import org.opensources.umai.R

/**
 * Confirmations raised by a screen as it closes, shown by the navigation host
 * over the screen the user lands on.
 */
enum class AppNotice(@param:StringRes val messageRes: Int, val success: Boolean) {
    DRAFT_SAVED(R.string.notice_draft_saved, success = true),
    RECIPE_SAVED(R.string.notice_recipe_saved, success = true),
    RECIPE_CREATED_WITHOUT_IMAGE(R.string.notice_recipe_created_without_image, success = false),
    RECIPE_COOKED(R.string.notice_recipe_cooked, success = true),
    RECIPE_IMPORTED_WITHOUT_MEDIA(R.string.notice_recipe_imported_without_media, success = false),
}
