package dev.hho.android.ui.connect

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.network.InstanceUrlNormalization
import dev.hho.android.data.network.InstanceUrlNormalizer
import dev.hho.android.data.settings.SettingsKeys
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel
    @Inject
    constructor(
        private val settingsDataStore: DataStore<Preferences>,
        private val hhoApiClient: HhoApiClient,
    ) : ViewModel() {

        private val _uiState = MutableStateFlow(ConnectUiState())
        val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

        private val _connected = Channel<Unit>(Channel.BUFFERED)
        val connected: Flow<Unit> = _connected.receiveAsFlow()

        init {
            viewModelScope.launch { prefillStoredUrl() }
        }

        internal suspend fun prefillStoredUrl() {
            val stored = settingsDataStore.data.map { it[SettingsKeys.INSTANCE_BASE_URL] }.first()
            if (!stored.isNullOrBlank()) {
                _uiState.update { it.copy(urlInput = stored) }
            }
        }

        fun onUrlChanged(value: String) {
            _uiState.update { it.copy(urlInput = value, errorMessage = null) }
        }

        fun onConnectClicked() {
            if (_uiState.value.isChecking) return
            viewModelScope.launch { connect(_uiState.value.urlInput) }
        }

        internal suspend fun connect(candidate: String) {
            _uiState.update { it.copy(isChecking = true, errorMessage = null) }
            when (val normalized = InstanceUrlNormalizer.normalize(candidate)) {
                is InstanceUrlNormalization.Invalid -> {
                    _uiState.update { it.copy(isChecking = false, errorMessage = normalized.reason) }
                }
                is InstanceUrlNormalization.Valid -> {
                    hhoApiClient.probeInstance(normalized.url).fold(
                        onSuccess = {
                            settingsDataStore.edit { prefs ->
                                prefs[SettingsKeys.INSTANCE_BASE_URL] = normalized.url.toString()
                            }
                            _uiState.update { it.copy(isChecking = false) }
                            _connected.send(Unit)
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(isChecking = false, errorMessage = connectErrorMessage(error))
                            }
                        },
                    )
                }
            }
        }
    }

data class ConnectUiState(
    val urlInput: String = "",
    val isChecking: Boolean = false,
    val errorMessage: String? = null,
)

private fun connectErrorMessage(error: Throwable): String {
    val apiError = error as? ApiError
        ?: return "Couldn't connect. Check the URL and try again."
    return when (apiError) {
        is ApiError.NoInstanceConfigured ->
            "Enter an instance URL."
        is ApiError.UpgradeRequired ->
            "This app is too old for that server (it requires client version " +
                "${apiError.minimumVersion} or newer). Update the app and try again."
        is ApiError.NotFound ->
            "No HHO server answered at that address. If it's behind a reverse-proxy " +
                "subpath, make sure the path is included, e.g. https://example.com/hho."
        is ApiError.UnexpectedPayload ->
            "That address answered, but it doesn't look like an HHO server."
        is ApiError.Network ->
            "Couldn't reach that address. Check the URL and that this device can reach the server."
        is ApiError.Unauthorized ->
            "That server rejected the request unexpectedly. Check the URL."
        is ApiError.Conflict, is ApiError.Validation, is ApiError.Server, is ApiError.Unknown ->
            "That server answered unexpectedly. Check the URL and try again."
    }
}
