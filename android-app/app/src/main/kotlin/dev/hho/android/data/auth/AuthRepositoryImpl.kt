package dev.hho.android.data.auth

import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.network.BaseUrlResolution
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.network.NoInstanceUrlConfiguredException
import dev.hho.android.domain.AuthRepository
import dev.hho.android.domain.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl
    @Inject
    constructor(
        private val hhoApiClient: HhoApiClient,
        private val deviceTokenStore: DeviceTokenStore,
        private val instanceBaseUrlResolver: InstanceBaseUrlResolver,
        private val deviceLabelProvider: DeviceLabelProvider,
    ) : AuthRepository {
        private val tokenChangeSignal = MutableStateFlow(0)

        override val authState: Flow<AuthState> =
            combine(
                instanceBaseUrlResolver.resolutionFlow()
                    .map { (it as? BaseUrlResolution.Configured)?.baseUrl?.toString() }
                    .distinctUntilChanged(),
                tokenChangeSignal,
            ) { configuredInstanceUrl, _ -> configuredInstanceUrl }
                .map { configuredInstanceUrl -> computeAuthState(configuredInstanceUrl) }

        private suspend fun computeAuthState(configuredInstanceUrl: String?): AuthState =
            if (deviceTokenStore.load().tokenFor(configuredInstanceUrl) != null) {
                AuthState.LoggedIn
            } else {
                AuthState.LoggedOut
            }

        override suspend fun login(
            username: String,
            password: String,
        ): Result<Unit> {
            val loginOutcome = hhoApiClient.login(username, password).getOrElse { return Result.failure(it) }

            return hhoApiClient
                .issueDeviceToken(deviceLabelProvider.currentLabel(), loginOutcome.sessionCookie)
                .fold(
                    onSuccess = { issued ->
                        val configured = instanceBaseUrlResolver.resolve() as? BaseUrlResolution.Configured
                        if (configured == null) {
                            deviceTokenStore.clear()
                            tokenChangeSignal.update { it + 1 }
                            return Result.failure(NoInstanceUrlConfiguredException())
                        }
                        deviceTokenStore.save(StoredToken(token = issued.token, instanceUrl = configured.baseUrl.toString()))
                        tokenChangeSignal.update { it + 1 }
                        Result.success(Unit)
                    },
                    onFailure = { error ->
                        deviceTokenStore.clear()
                        tokenChangeSignal.update { it + 1 }
                        Result.failure(error)
                    },
                )
        }

        override suspend fun logout() {
            deviceTokenStore.clear()
            tokenChangeSignal.update { it + 1 }
        }
    }
