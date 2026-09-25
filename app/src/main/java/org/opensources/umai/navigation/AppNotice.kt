package org.opensources.umai.navigation

import androidx.annotation.StringRes
import org.opensources.umai.R
import org.opensources.umai.recipe.ui.ImportNotice

/**
 * Confirmations raised by a screen as it closes, shown by the navigation host
 * over the screen the user lands on.
 */
enum class AppNotice(@param:StringRes val messageRes: Int, val success: Boolean) {
    DRAFT_SAVED(R.string.notice_draft_saved, success = true),
    RECIPE_SAVED(R.string.notice_recipe_saved, success = true),
    RECIPE_DELETED(R.string.notice_recipe_deleted, success = true),
    RECIPE_CREATED_WITHOUT_IMAGE(R.string.notice_recipe_created_without_image, success = false),
    RECIPE_COOKED(R.string.notice_recipe_cooked, success = true),
    RECIPE_IMPORTED_WITHOUT_MEDIA(R.string.notice_recipe_imported_without_media, success = false),
    RECIPE_PLANNED(R.string.recipe_added_to_plan, success = true),
    VIDEO_IMPORTED_WITHOUT_MODEL(R.string.notice_video_without_model, success = true),
    VIDEO_MODEL_FAILED(R.string.notice_video_model_failed, success = false),
    VIDEO_NOT_LINKED(R.string.notice_video_not_linked, success = false),
    MEAL_PLAN_CREATED(R.string.notice_meal_plan_created, success = true),
    ;

    companion object {
        fun of(notice: ImportNotice): AppNotice = when (notice) {
            ImportNotice.MEDIA_FAILED -> RECIPE_IMPORTED_WITHOUT_MEDIA
            ImportNotice.VIDEO_WITHOUT_MODEL -> VIDEO_IMPORTED_WITHOUT_MODEL
            ImportNotice.VIDEO_MODEL_FAILED -> VIDEO_MODEL_FAILED
            ImportNotice.VIDEO_NOT_LINKED -> VIDEO_NOT_LINKED
        }
    }
}
