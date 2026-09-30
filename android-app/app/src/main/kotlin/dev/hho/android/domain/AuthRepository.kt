package dev.hho.android.domain

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun login(username: String, password: String): Result<Unit>

    suspend fun logout()
}

sealed interface AuthState {
    data object LoggedOut : AuthState

    data object LoggedIn : AuthState
}
