package dev.hho.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.domain.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LoginUiState())
        val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

        private val _loggedIn = Channel<Unit>(Channel.BUFFERED)
        val loggedIn: Flow<Unit> = _loggedIn.receiveAsFlow()

        fun onUsernameChanged(value: String) {
            _uiState.update { it.copy(username = value, errorMessage = null) }
        }

        fun onPasswordChanged(value: String) {
            _uiState.update { it.copy(password = value, errorMessage = null) }
        }

        fun onSubmitClicked() {
            val current = _uiState.value
            if (current.isLoading) return
            viewModelScope.launch { submit(current.username, current.password) }
        }

        internal suspend fun submit(
            username: String,
            password: String,
        ) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, password = "") }
            authRepository.login(username, password).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    _loggedIn.send(Unit)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = loginErrorMessage(error)) }
                },
            )
        }
    }

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private fun loginErrorMessage(error: Throwable): String {
    val apiError = error as? ApiError
        ?: return "Couldn't log in. Try again."
    return when (apiError) {
        is ApiError.Unauthorized ->
            "Incorrect username or password."
        is ApiError.Validation ->
            if (apiError.status == 429) {
                "Too many login attempts. Wait a moment and try again."
            } else {
                "That server rejected the request. Check your details and try again."
            }
        is ApiError.NoInstanceConfigured ->
            "Connect to an instance first."
        is ApiError.UpgradeRequired ->
            "This app is too old for that server (it requires client version " +
                "${apiError.minimumVersion} or newer). Update the app and try again."
        is ApiError.Network ->
            "Couldn't reach the server. Check your connection and try again."
        is ApiError.NotFound, is ApiError.Conflict, is ApiError.Server, is ApiError.Unknown, is ApiError.UnexpectedPayload ->
            "That server answered unexpectedly. Try again."
    }
}
