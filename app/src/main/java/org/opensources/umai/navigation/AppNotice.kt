package org.opensources.umai.navigation

import androidx.annotation.StringRes
import org.opensources.umai.R
import org.opensources.umai.recipe.domain.ImportNotice

/**
 * Confirmations raised by a screen as it closes, shown by the navigation host
 * over the screen the user lands on.
 */
data class AppNotice(
    @param:StringRes val messageRes: Int,
    val success: Boolean,
    @param:StringRes val additionalMessageRes: Int? = null,
) {
    companion object {
        val INGREDIENTS_NEED_REVIEW = AppNotice(R.string.ingredients_import_review, success = false)
        val DRAFT_SAVED = AppNotice(R.string.notice_draft_saved, success = true)
        val RECIPE_SAVED = AppNotice(R.string.notice_recipe_saved, success = true)
        val RECIPE_DELETED = AppNotice(R.string.notice_recipe_deleted, success = true)
        val RECIPE_CREATED_WITHOUT_IMAGE = AppNotice(R.string.notice_recipe_created_without_image, success = false)
        val RECIPE_COOKED = AppNotice(R.string.notice_recipe_cooked, success = true)
        val RECIPE_IMPORTED_WITHOUT_MEDIA = AppNotice(R.string.notice_recipe_imported_without_media, success = false)
        val RECIPE_PLANNED = AppNotice(R.string.recipe_added_to_plan, success = true)
        val FOOD_PLANNED_WITHOUT_PHOTO = AppNotice(R.string.notice_food_added_without_photo, success = false)
        val VIDEO_IMPORTED_WITHOUT_MODEL = AppNotice(R.string.notice_video_without_model, success = true)
        val VIDEO_MODEL_FAILED = AppNotice(R.string.notice_video_model_failed, success = false)
        val VIDEO_NOT_LINKED = AppNotice(R.string.notice_video_not_linked, success = false)
        val VIDEO_CAPTIONS_REFUSED = AppNotice(R.string.notice_video_captions_refused, success = false)
        val MEAL_PLAN_CREATED = AppNotice(R.string.notice_meal_plan_created, success = true)

        fun of(notice: ImportNotice, ingredientsNeedReview: Boolean = false): AppNotice = when (notice) {
            ImportNotice.INGREDIENTS_NEED_REVIEW -> INGREDIENTS_NEED_REVIEW
            ImportNotice.MEDIA_FAILED -> RECIPE_IMPORTED_WITHOUT_MEDIA
            ImportNotice.VIDEO_WITHOUT_MODEL -> VIDEO_IMPORTED_WITHOUT_MODEL
            ImportNotice.VIDEO_MODEL_FAILED -> VIDEO_MODEL_FAILED
            ImportNotice.VIDEO_NOT_LINKED -> VIDEO_NOT_LINKED
            ImportNotice.VIDEO_CAPTIONS_REFUSED -> VIDEO_CAPTIONS_REFUSED
        }.let { primary ->
            if (ingredientsNeedReview && notice != ImportNotice.INGREDIENTS_NEED_REVIEW) {
                primary.copy(success = false, additionalMessageRes = R.string.ingredients_import_review)
            } else primary
        }
    }
}
