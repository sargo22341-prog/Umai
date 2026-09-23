package org.opensources.umai.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.model.HouseholdStatistics
import org.opensources.umai.core.model.UserProfile
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.session.SessionManager
import org.opensources.umai.profile.data.ProfileRepository
import org.opensources.umai.recipe.domain.RecipeDraft

/** One-shot messages shown as a snackbar. */
sealed interface ProfileEvent {
    data object AvatarUpdated : ProfileEvent
    data object CameraUnavailable : ProfileEvent
    data class Failed(val error: NetworkError) : ProfileEvent
}

data class ProfileUiState(
    val user: UserProfile? = null,
    val statistics: HouseholdStatistics? = null,
    val draftCount: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val uploadingAvatar: Boolean = false,
    val error: NetworkError? = null,
    val event: ProfileEvent? = null,
) {
    /** The page is worth showing as soon as the account is known. */
    val hasContent: Boolean get() = user != null
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val sessionManager: SessionManager,
    drafts: Flow<List<RecipeDraft>>,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load(initial = true)
        viewModelScope.launch {
            drafts.collect { list -> _state.update { it.copy(draftCount = list.size) } }
        }
    }

    fun refresh() = load(initial = false)

    fun retry() = load(initial = true)

    /** [imageUri] is the picked picture, [region] the square the user framed in it. */
    fun updateAvatar(imageUri: String, region: CropRegion) {
        val user = _state.value.user ?: return
        _state.update { it.copy(uploadingAvatar = true) }
        viewModelScope.launch {
            when (val result = profileRepository.updateAvatar(user.id, imageUri, region)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(uploadingAvatar = false, event = ProfileEvent.Failed(result.error))
                }
                is ApiResult.Success -> {
                    adopt(result.value)
                    _state.update {
                        it.copy(uploadingAvatar = false, event = ProfileEvent.AvatarUpdated)
                    }
                }
            }
        }
    }

    fun onCameraUnavailable() = _state.update { it.copy(event = ProfileEvent.CameraUnavailable) }

    fun consumeEvent() = _state.update { it.copy(event = null) }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.user == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            when (val user = profileRepository.currentUser()) {
                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, refreshing = false, error = user.error) }
                    return@launch
                }
                is ApiResult.Success -> {
                    adopt(user.value)
                    _state.update { it.copy(loading = false, error = null) }
                }
            }
            // Statistics are a bonus: a failure here must not hide the account.
            val statistics = profileRepository.statistics()
            _state.update {
                it.copy(
                    statistics = (statistics as? ApiResult.Success)?.value ?: it.statistics,
                    refreshing = false,
                )
            }
        }
    }

    /**
     * Keeps the session in step with the account, so the avatar and the name
     * shown by the navigation bar follow a change made here.
     */
    private suspend fun adopt(user: UserProfile) {
        _state.update { it.copy(user = user) }
        sessionManager.updateIdentity(
            displayName = user.displayName,
            isAdmin = user.isAdmin,
            avatarCacheKey = user.cacheKey.takeIf { it.isNotBlank() },
        )
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ProfileViewModel(
                    profileRepository = container.profileRepository,
                    sessionManager = container.sessionManager,
                    drafts = container.recipeDraftStore.drafts,
                )
            }
        }
    }
}
