package org.opensources.umai.setup.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.R
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.network.LocalNetworkAccess
import org.opensources.umai.core.network.MealieUrl
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.session.AuthRepository
import org.opensources.umai.core.session.SessionState

enum class SetupAuthMethod { PASSWORD, API_TOKEN }

data class SetupUiState(
    val url: String = "",
    val authMethod: SetupAuthMethod = SetupAuthMethod.PASSWORD,
    val username: String = "",
    val password: String = "",
    val apiToken: String = "",
    val passwordVisible: Boolean = false,
    val cleartextWarning: Boolean = false,
    val connecting: Boolean = false,
    @param:StringRes val formError: Int? = null,
    val networkError: NetworkError? = null,
    val expiredForUrl: String? = null,
    /**
     * Set when the instance is on the local network and Android has not
     * granted `ACCESS_LOCAL_NETWORK` yet; the screen reacts by showing the
     * system prompt.
     */
    val requestLocalNetworkPermission: Boolean = false,
) {
    val canSubmit: Boolean get() = !connecting && url.isNotBlank()
}

/**
 * Drives the first-run setup screen and the "sign in again" screen: both need
 * the same validation, the same error handling and the same connection flow.
 */
class SetupViewModel(
    private val authRepository: AuthRepository,
    sessionState: StateFlow<SessionState>,
    private val isLocalNetworkPermissionGranted: () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    private var connectJob: Job? = null

    init {
        // An expired session pre-fills the instance and the username so the
        // user only has to type their password again.
        (sessionState.value as? SessionState.Expired)?.let { expired ->
            _state.update {
                it.copy(
                    url = expired.baseUrl,
                    username = expired.username.orEmpty(),
                    expiredForUrl = expired.baseUrl,
                    cleartextWarning = expired.baseUrl.startsWith("http://", ignoreCase = true),
                )
            }
        }
    }

    fun onUrlChange(value: String) {
        val parsed = MealieUrl.parse(value)
        _state.update {
            it.copy(
                url = value,
                cleartextWarning = (parsed as? MealieUrl.Result.Valid)?.isCleartext == true,
                formError = null,
                networkError = null,
            )
        }
    }

    fun onAuthMethodChange(method: SetupAuthMethod) {
        _state.update { it.copy(authMethod = method, formError = null, networkError = null) }
    }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, formError = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, formError = null) }

    fun onApiTokenChange(value: String) = _state.update { it.copy(apiToken = value, formError = null) }

    fun togglePasswordVisibility() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun useAnotherInstance() {
        _state.update {
            SetupUiState(authMethod = it.authMethod)
        }
        viewModelScope.launch { authRepository.signOut() }
    }

    fun connect() {
        val current = _state.value
        if (current.connecting) return

        val urlError = validateUrl(current.url)
        if (urlError != null) {
            _state.update { it.copy(formError = urlError) }
            return
        }
        val credentialError = validateCredentials(current)
        if (credentialError != null) {
            _state.update { it.copy(formError = credentialError) }
            return
        }

        val baseUrl = AuthRepository.normalize(current.url) ?: return
        _state.update { it.copy(connecting = true, formError = null, networkError = null) }

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            // Reaching a LAN instance needs an explicit grant on Android 17;
            // asking first avoids a 15-second connect timeout with no clue.
            if (!isLocalNetworkPermissionGranted() && LocalNetworkAccess.isLocalInstance(baseUrl)) {
                _state.update { it.copy(connecting = false, requestLocalNetworkPermission = true) }
                return@launch
            }

            val result = when (current.authMethod) {
                SetupAuthMethod.PASSWORD -> authRepository.connectWithPassword(
                    baseUrl = baseUrl,
                    username = current.username.trim(),
                    password = current.password,
                )
                SetupAuthMethod.API_TOKEN -> authRepository.connectWithApiToken(
                    baseUrl = baseUrl,
                    apiToken = current.apiToken.trim(),
                )
            }

            when (result) {
                is AuthRepository.ConnectResult.Success -> {
                    // The password is dropped as soon as the token is obtained.
                    _state.update { it.copy(connecting = false, password = "", apiToken = "") }
                    authRepository.adopt(result.session)
                }
                is AuthRepository.ConnectResult.Failure ->
                    _state.update { it.copy(connecting = false, networkError = result.error) }
                AuthRepository.ConnectResult.PasswordLoginDisabled ->
                    _state.update {
                        it.copy(
                            connecting = false,
                            authMethod = SetupAuthMethod.API_TOKEN,
                            formError = R.string.setup_error_password_login_disabled,
                        )
                    }
            }
        }
    }

    /** Called once the system prompt has been answered. */
    fun onLocalNetworkPermissionResult(granted: Boolean) {
        _state.update {
            it.copy(
                requestLocalNetworkPermission = false,
                formError = if (granted) null else R.string.setup_error_local_network_denied,
            )
        }
        if (granted) connect()
    }

    @StringRes
    private fun validateUrl(raw: String): Int? = when (MealieUrl.parse(raw)) {
        MealieUrl.Result.Empty -> R.string.setup_error_url_empty
        MealieUrl.Result.Malformed -> R.string.setup_error_url_invalid
        MealieUrl.Result.UnsupportedScheme -> R.string.setup_error_url_scheme
        is MealieUrl.Result.Valid -> null
    }

    @StringRes
    private fun validateCredentials(state: SetupUiState): Int? = when (state.authMethod) {
        SetupAuthMethod.PASSWORD ->
            if (state.username.isBlank() || state.password.isEmpty()) {
                R.string.setup_error_credentials_empty
            } else {
                null
            }
        SetupAuthMethod.API_TOKEN ->
            if (state.apiToken.isBlank()) R.string.setup_error_token_empty else null
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SetupViewModel(
                    authRepository = container.authRepository,
                    sessionState = container.sessionManager.state,
                    isLocalNetworkPermissionGranted = container.localNetworkPermission,
                )
            }
        }
    }
}
