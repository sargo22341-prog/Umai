package org.opensources.umai.profile.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.opensources.umai.core.model.HouseholdPreferences
import org.opensources.umai.core.model.HouseholdStatistics
import org.opensources.umai.core.model.UserProfile
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall

/**
 * The signed-in account and the preferences its household owns.
 *
 * Nothing is cached locally: Mealie stays the source of truth, Umai only reads
 * and writes back.
 */
class ProfileRepository(
    private val apiProvider: () -> MealieApi?,
    private val avatarImages: AvatarImageReader,
) {

    suspend fun currentUser(): ApiResult<UserProfile> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return when (val result = apiCall { api.currentUser() }) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.toDomain()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(NetworkError.InvalidResponse)
        }
    }

    suspend fun statistics(): ApiResult<HouseholdStatistics> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.householdStatistics().toDomain() }
    }

    suspend fun householdPreferences(): ApiResult<HouseholdPreferences> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.householdPreferences().toDomain() }
    }

    suspend fun updateHouseholdPreferences(
        preferences: HouseholdPreferences,
    ): ApiResult<HouseholdPreferences> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.updateHouseholdPreferences(preferences.toUpdateDto()).toDomain() }
    }

    /**
     * Uploads a new profile picture. Mealie answers with nothing useful, so the
     * refreshed user is read back to pick up the new cache key.
     */
    suspend fun updateAvatar(userId: String, imageUri: String): ApiResult<UserProfile> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val upload = avatarImages.read(imageUri)
            ?: return ApiResult.Failure(NetworkError.InvalidResponse)

        val part = MultipartBody.Part.createFormData(
            name = "profile",
            filename = upload.fileName,
            body = upload.bytes.toRequestBody(upload.mediaType.toMediaTypeOrNull()),
        )
        return when (val result = apiCall { api.updateUserImage(userId, part) }) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> currentUser()
        }
    }
}
