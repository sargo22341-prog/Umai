package org.opensources.umai.profile.data

import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.ImageCropper
import org.opensources.umai.core.model.HouseholdPreferences
import org.opensources.umai.core.model.HouseholdStatistics
import org.opensources.umai.core.model.UserProfile
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.flatMap
import org.opensources.umai.core.network.formPart
import org.opensources.umai.core.network.map
import org.opensources.umai.core.network.orInvalid

/**
 * The signed-in account and the preferences its household owns.
 *
 * Nothing is cached locally: Mealie stays the source of truth, Umai only reads
 * and writes back.
 */
class ProfileRepository(
    private val apiProvider: () -> MealieApi?,
    private val imageCropper: ImageCropper,
) {

    /** How changing the profile picture ended. */
    sealed interface AvatarUpdate {
        data class Updated(val user: UserProfile) : AvatarUpdate

        /** The picked picture could not be opened: nothing was sent. */
        data object PictureUnreadable : AvatarUpdate

        data class Failed(val error: NetworkError) : AvatarUpdate
    }

    suspend fun currentUser(): ApiResult<UserProfile> = apiProvider.call { currentUser() }.map { it.toDomain() }.orInvalid()

    suspend fun statistics(): ApiResult<HouseholdStatistics> = apiProvider.call { householdStatistics().toDomain() }

    suspend fun householdPreferences(): ApiResult<HouseholdPreferences> =
        apiProvider.call { householdPreferences().toDomain() }

    suspend fun updateHouseholdPreferences(preferences: HouseholdPreferences): ApiResult<HouseholdPreferences> =
        apiProvider.call { updateHouseholdPreferences(preferences.toUpdateDto()).toDomain() }

    /**
     * Crops the picked picture to the square the user framed and uploads it as
     * the new profile picture. Mealie answers with nothing useful, so the
     * refreshed user is read back to pick up the new cache key.
     */
    suspend fun updateAvatar(userId: String, imageUri: String, region: CropRegion): AvatarUpdate {
        val upload = imageCropper.crop(imageUri, region, AVATAR_SIZE) ?: return AvatarUpdate.PictureUnreadable
        val result = apiProvider.call { updateUserImage(userId, upload.formPart("profile", fileName = "profile")) }
            .flatMap { currentUser() }
        return when (result) {
            is ApiResult.Success -> AvatarUpdate.Updated(result.value)
            is ApiResult.Failure -> AvatarUpdate.Failed(result.error)
        }
    }

    private companion object {
        /** Mealie shows avatars small; a larger upload would only cost bandwidth. */
        const val AVATAR_SIZE = 512
    }
}
