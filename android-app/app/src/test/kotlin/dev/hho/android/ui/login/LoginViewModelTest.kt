package dev.hho.android.ui.login

import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.domain.AuthRepository
import dev.hho.android.domain.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepository(private val result: Result<Unit>) : AuthRepository {
        override val authState: Flow<AuthState> = MutableStateFlow(AuthState.LoggedOut)
        var lastUsername: String? = null
        var lastPassword: String? = null

        override suspend fun login(
            username: String,
            password: String,
        ): Result<Unit> {
            lastUsername = username
            lastPassword = password
            return result
        }

        override suspend fun logout() {
        }
    }

    @Test
    fun `a successful login forwards the credentials, clears the password, and emits loggedIn`() =
        runTest {
            val repository = FakeAuthRepository(Result.success(Unit))
            val viewModel = LoginViewModel(repository)

            viewModel.submit("alice", "secret")

            assertEquals("alice", repository.lastUsername)
            assertEquals("secret", repository.lastPassword)
            assertEquals(Unit, viewModel.loggedIn.first())
            assertNull(viewModel.uiState.value.errorMessage)
            assertEquals("", viewModel.uiState.value.password)
            assertTrue(!viewModel.uiState.value.isLoading)
        }

    @Test
    fun `an incorrect-credentials failure shows a specific message, stays on screen, and clears the password`() =
        runTest {
            val repository = FakeAuthRepository(Result.failure(ApiError.Unauthorized()))
            val viewModel = LoginViewModel(repository)

            viewModel.submit("alice", "wrong-password")

            assertEquals("Incorrect username or password.", viewModel.uiState.value.errorMessage)
            assertEquals("", viewModel.uiState.value.password)
            assertTrue(!viewModel.uiState.value.isLoading)
        }

    @Test
    fun `a 429 shows a rate-limited message`() =
        runTest {
            val repository = FakeAuthRepository(Result.failure(ApiError.Validation(status = 429, detail = null)))
            val viewModel = LoginViewModel(repository)

            viewModel.submit("alice", "secret")

            assertTrue(viewModel.uiState.value.errorMessage!!.contains("Too many login attempts"))
        }

    @Test
    fun `a device-token-call failure after a successful login surfaces the same way as a login failure`() =
        runTest {
            val repository = FakeAuthRepository(Result.failure(ApiError.Server(status = 500, detail = null)))
            val viewModel = LoginViewModel(repository)

            viewModel.submit("alice", "secret")

            assertEquals("That server answered unexpectedly. Try again.", viewModel.uiState.value.errorMessage)
            assertTrue(!viewModel.uiState.value.isLoading)
        }

    @Test
    fun `changing the username field clears a stale error message`() =
        runTest {
            val repository = FakeAuthRepository(Result.failure(ApiError.Unauthorized()))
            val viewModel = LoginViewModel(repository)
            viewModel.submit("alice", "wrong-password")
            assertTrue(viewModel.uiState.value.errorMessage != null)

            viewModel.onUsernameChanged("alice2")

            assertNull(viewModel.uiState.value.errorMessage)
        }
}
