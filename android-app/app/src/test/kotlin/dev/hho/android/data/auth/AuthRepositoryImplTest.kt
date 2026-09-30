package dev.hho.android.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import dev.hho.android.domain.AuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AuthRepositoryImplTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempDataStoreFile(): File {
        val file = File.createTempFile("auth-repository-test", ".preferences_pb")
        file.deleteOnExit()
        return file
    }

    private fun dataStoreFor(
        file: File,
        instanceUrl: String,
    ): DataStore<Preferences> {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = instanceUrl } }
        return dataStore
    }

    private class Fixture(
        val hhoApiClient: HhoApiClient,
        val deviceTokenStore: DeviceTokenStore,
        val deviceTokenProvider: InstanceScopedDeviceTokenProvider,
        val repository: AuthRepositoryImpl,
        val instanceDataStore: DataStore<Preferences>,
    )

    private fun fixture(
        instanceUrl: String,
        deviceLabel: String = "Test Device",
        instanceDataStoreFile: File = tempDataStoreFile(),
        deviceTokenStore: DeviceTokenStore = FakeDeviceTokenStore(),
    ): Fixture {
        val dataStore = dataStoreFor(instanceDataStoreFile, instanceUrl)
        val resolver = InstanceBaseUrlResolver(dataStore)
        val clientVersionInterceptor = ClientVersionInterceptor()
        val tokenProvider = InstanceScopedDeviceTokenProvider(deviceTokenStore)
        val authHeaderInterceptor = AuthHeaderInterceptor(tokenProvider)
        val baseUrlInterceptor = BaseUrlInterceptor(resolver)
        val httpClient =
            NetworkModule.provideOkHttpClient(baseUrlInterceptor, authHeaderInterceptor, clientVersionInterceptor)
        val probeHttpClient = NetworkModule.provideProbeOkHttpClient(httpClient, clientVersionInterceptor)
        val hhoApiClient = HhoApiClient(httpClient, probeHttpClient)
        val repository =
            AuthRepositoryImpl(hhoApiClient, deviceTokenStore, resolver, DeviceLabelProvider { deviceLabel })
        return Fixture(hhoApiClient, deviceTokenStore, tokenProvider, repository, dataStore)
    }

    private val loginOkBody = """{"group_id":"g1","user_id":"u1","username":"alice","role":"owner"}"""

    private fun enqueueLoginOk(
        target: MockWebServer = server,
        sessionCookie: String = "hho_session=abc123; HttpOnly; Secure; SameSite=Lax; Max-Age=3600",
    ) {
        target.enqueue(MockResponse().setResponseCode(200).setHeader("Set-Cookie", sessionCookie).setBody(loginOkBody))
    }

    private fun enqueueDeviceTokenOk(
        target: MockWebServer = server,
        token: String = "device-token-xyz",
    ) {
        target.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"dt1","device_label":"Test Device","token":"$token"}""",
            ),
        )
    }

    @Test
    fun `login carries the session cookie from auth-login into the auth-device-tokens request`() =
        runBlocking {
            enqueueLoginOk()
            enqueueDeviceTokenOk()
            val fixture = fixture(server.url("/").toString())

            val result = fixture.repository.login("alice", "secret")

            assertTrue(result.isSuccess)
            server.takeRequest()
            val deviceTokenRequest = server.takeRequest()
            assertEquals("/api/v1/auth/device-tokens", deviceTokenRequest.path)
            assertEquals("hho_session=abc123", deviceTokenRequest.getHeader("Cookie"))
        }

    @Test
    fun `a subsequent unrelated call carries the issued device token but not the session cookie`() =
        runBlocking {
            enqueueLoginOk()
            enqueueDeviceTokenOk(token = "device-token-xyz")
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val fixture = fixture(server.url("/").toString())

            fixture.repository.login("alice", "secret")
            server.takeRequest()
            server.takeRequest()
            fixture.hhoApiClient.getStatus()

            val laterRequest = server.takeRequest()
            assertEquals("Bearer device-token-xyz", laterRequest.getHeader("Authorization"))
            assertNull(laterRequest.getHeader("Cookie"))
        }

    @Test
    fun `login flips authState to LoggedIn only after both calls succeed`() =
        runBlocking {
            enqueueLoginOk()
            enqueueDeviceTokenOk()
            val fixture = fixture(server.url("/").toString())

            fixture.repository.login("alice", "secret")

            assertEquals(AuthState.LoggedIn, fixture.repository.authState.first())
            assertEquals("device-token-xyz", fixture.deviceTokenProvider.tokenFor(server.url("/api/v1/status")))
        }

    @Test
    fun `401 on login fails without ever calling auth-device-tokens and leaves the repository logged out`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401))
            val fixture = fixture(server.url("/").toString())

            val result = fixture.repository.login("alice", "wrong-password")

            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals(AuthState.LoggedOut, fixture.repository.authState.first())
            assertNull(fixture.deviceTokenStore.load())
        }

    @Test
    fun `429 on login fails and leaves the repository logged out`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
            val fixture = fixture(server.url("/").toString())

            val result = fixture.repository.login("alice", "secret")

            assertTrue(result.isFailure)
            assertEquals(AuthState.LoggedOut, fixture.repository.authState.first())
        }

    @Test
    fun `a failure of the device-token call after a successful login leaves nothing half logged in`() =
        runBlocking {
            enqueueLoginOk()
            server.enqueue(MockResponse().setResponseCode(500))
            val fixture = fixture(server.url("/").toString())

            val result = fixture.repository.login("alice", "secret")

            assertTrue(result.isFailure)
            assertEquals(AuthState.LoggedOut, fixture.repository.authState.first())
            assertNull(fixture.deviceTokenStore.load())

            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            fixture.hhoApiClient.getStatus()
            server.takeRequest()
            server.takeRequest()
            val laterRequest = server.takeRequest()
            assertNull(laterRequest.getHeader("Cookie"))
            assertNull(laterRequest.getHeader("Authorization"))
        }

    @Test
    fun `logout clears the held device token so the next request carries no Authorization header`() =
        runBlocking {
            enqueueLoginOk()
            enqueueDeviceTokenOk()
            val fixture = fixture(server.url("/").toString())
            fixture.repository.login("alice", "secret")
            server.takeRequest()
            server.takeRequest()

            fixture.repository.logout()

            assertEquals(AuthState.LoggedOut, fixture.repository.authState.first())
            assertNull(fixture.deviceTokenStore.load())
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            fixture.hhoApiClient.getStatus()
            assertNull(server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun `a 401 from a server-side revocation clears the stored device token and flips authState to LoggedOut`() =
        runBlocking {
            enqueueLoginOk()
            enqueueDeviceTokenOk(token = "device-token-xyz")
            val fixture = fixture(server.url("/").toString())
            fixture.repository.login("alice", "secret")
            server.takeRequest()
            server.takeRequest()
            assertEquals(AuthState.LoggedIn, fixture.repository.authState.first())
            assertEquals(StoredToken(token = "device-token-xyz", instanceUrl = server.url("/").toString()), fixture.deviceTokenStore.load())

            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("WWW-Authenticate", "Bearer")
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"type":"about:blank","title":"Unauthorized","status":401}"""),
            )

            val result = fixture.hhoApiClient.getStatus()

            assertTrue("a revoked device's next call must fail, not succeed", result.isFailure)

            assertNull("the revoked token must be gone from the durable store", fixture.deviceTokenStore.load())
            assertEquals(AuthState.LoggedOut, fixture.repository.authState.first())
        }

    @Test
    fun `after server-side revocation, the client stops retrying with the dead credential`() =
        runBlocking {
            enqueueLoginOk()
            enqueueDeviceTokenOk(token = "device-token-xyz")
            val fixture = fixture(server.url("/").toString())
            fixture.repository.login("alice", "secret")
            server.takeRequest()
            server.takeRequest()
            server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer"))
            fixture.hhoApiClient.getStatus()
            server.takeRequest()

            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            fixture.hhoApiClient.getStatus()

            assertNull(server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun `a 401 on a request that carried no Authorization header never clears an unrelated stored token`() =
        runBlocking {
            val unrelatedToken = StoredToken(token = "token-for-other-instance", instanceUrl = "https://other.example.com/")
            val sharedTokenStore = FakeDeviceTokenStore(unrelatedToken)
            val fixture = fixture(server.url("/").toString(), deviceTokenStore = sharedTokenStore)
            server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer"))

            val result = fixture.hhoApiClient.getStatus()

            assertTrue(result.isFailure)
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertEquals(unrelatedToken, fixture.deviceTokenStore.load())
        }

    @Test
    fun `a token issued for instance A is not sent after the configured instance changes to B, and authState reads LoggedOut`() =
        runBlocking {
            val serverA = server
            val serverB = MockWebServer()
            try {
                enqueueLoginOk(serverA)
                enqueueDeviceTokenOk(serverA, token = "token-for-a")
                val instanceDataStoreFile = tempDataStoreFile()
                val sharedTokenStore = FakeDeviceTokenStore()
                val fixture = fixture(
                    instanceUrl = serverA.url("/").toString(),
                    instanceDataStoreFile = instanceDataStoreFile,
                    deviceTokenStore = sharedTokenStore,
                )

                val loginResult = fixture.repository.login("alice", "secret")
                assertTrue(loginResult.isSuccess)
                assertEquals(AuthState.LoggedIn, fixture.repository.authState.first())

                fixture.instanceDataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = serverB.url("/").toString() }

                assertEquals(AuthState.LoggedOut, fixture.repository.authState.first())

                serverB.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
                fixture.hhoApiClient.getStatus()
                val requestToB = serverB.takeRequest()
                assertNull("server B must never see server A's device token", requestToB.getHeader("Authorization"))
            } finally {
                serverB.shutdown()
            }
        }

    @Test
    fun `reconnecting back to the original instance reactivates its token without a fresh login`() =
        runBlocking {
            val serverA = server
            val serverB = MockWebServer()
            try {
                enqueueLoginOk(serverA)
                enqueueDeviceTokenOk(serverA, token = "token-for-a")
                val instanceDataStoreFile = tempDataStoreFile()
                val sharedTokenStore = FakeDeviceTokenStore()
                val fixture = fixture(
                    instanceUrl = serverA.url("/").toString(),
                    instanceDataStoreFile = instanceDataStoreFile,
                    deviceTokenStore = sharedTokenStore,
                )
                fixture.repository.login("alice", "secret")
                fixture.instanceDataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = serverB.url("/").toString() }
                assertEquals(AuthState.LoggedOut, fixture.repository.authState.first())

                fixture.instanceDataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = serverA.url("/").toString() }

                assertEquals(AuthState.LoggedIn, fixture.repository.authState.first())
            } finally {
                serverB.shutdown()
            }
        }

    @Test
    fun `authState and the held token are restored from durable storage across a simulated restart`() =
        runBlocking {
            enqueueLoginOk()
            enqueueDeviceTokenOk(token = "restart-token")
            val sharedTokenStore = FakeDeviceTokenStore()
            val instanceUrl = server.url("/").toString()

            val fixtureBeforeRestart = fixture(instanceUrl = instanceUrl, deviceTokenStore = sharedTokenStore)
            fixtureBeforeRestart.repository.login("alice", "secret")
            assertEquals(AuthState.LoggedIn, fixtureBeforeRestart.repository.authState.first())

            val fixtureAfterRestart = fixture(instanceUrl = instanceUrl, deviceTokenStore = sharedTokenStore)

            assertEquals(AuthState.LoggedIn, fixtureAfterRestart.repository.authState.first())
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            fixtureAfterRestart.hhoApiClient.getStatus()
            server.takeRequest()
            server.takeRequest()
            assertEquals("Bearer restart-token", server.takeRequest().getHeader("Authorization"))
        }
}
