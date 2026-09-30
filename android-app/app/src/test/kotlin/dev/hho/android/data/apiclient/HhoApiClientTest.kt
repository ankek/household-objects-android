package dev.hho.android.data.apiclient

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.hho.android.BuildConfig
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.Timeout
import java.io.File

class HhoApiClientTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(15)

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("hho-api-client-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun apiClient(
        instanceUrl: String? = server.url("/").toString(),
        token: String? = null,
    ): HhoApiClient {
        val dataStore = tempDataStore()
        if (instanceUrl != null) {
            runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = instanceUrl } }
        }
        val clientVersionInterceptor = ClientVersionInterceptor()
        val authHeaderInterceptor = AuthHeaderInterceptor(fixedTokenProvider(token))
        val baseUrlInterceptor = BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore))

        val httpClient =
            NetworkModule.provideOkHttpClient(baseUrlInterceptor, authHeaderInterceptor, clientVersionInterceptor)
        val probeHttpClient = NetworkModule.provideProbeOkHttpClient(httpClient, clientVersionInterceptor)
        return HhoApiClient(httpClient, probeHttpClient)
    }

    private fun fixedTokenProvider(token: String?): DeviceTokenProvider =
        object : DeviceTokenProvider {
            override suspend fun tokenFor(requestUrl: HttpUrl): String? = token

            override suspend fun invalidate(
                requestUrl: HttpUrl,
                rejectedToken: String,
            ) = Unit
        }

    @Test
    fun `a facade call against an instance with a path prefix sends the exact prefixed path`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient(instanceUrl = server.url("/hho").toString())

            val result = client.getStatus()

            assertTrue(result.isSuccess)
            assertEquals("/hho/api/v1/status", server.takeRequest().path)
        }

    @Test
    fun `sync pull against a prefixed instance sends the exact prefixed path`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"changes":[],"tombstones":[],"next_watermark":0,"has_more":false}""",
                ),
            )
            val client = apiClient(instanceUrl = server.url("/hho").toString())

            val result = client.syncPull(deviceId = "device-1", since = 0, limit = 100)

            assertTrue(result.isSuccess)
            val recorded = server.takeRequest()
            assertEquals("/hho/api/v1/sync/pull", recorded.path)
            assertEquals("POST", recorded.method)
            assertTrue(recorded.body.readUtf8().contains("\"device_id\":\"device-1\""))
        }

    @Test
    fun `a stored device token is attached as Authorization on facade calls`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient(token = "device-token-123")

            client.getStatus()

            assertEquals("Bearer device-token-123", server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun `no stored device token means no Authorization header at all on facade calls`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient(token = null)

            client.getStatus()

            assertEquals(null, server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun `a facade call sends the exact byte-for-byte client version header`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient()

            client.getStatus()

            assertEquals(BuildConfig.HHO_CLIENT_VERSION, server.takeRequest().getHeader("X-HHO-Client-Version"))
        }

    @Test
    fun `probeInstance sends the exact byte-for-byte client version header`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient(instanceUrl = null)

            client.probeInstance(server.url("/"))

            assertEquals(BuildConfig.HHO_CLIENT_VERSION, server.takeRequest().getHeader("X-HHO-Client-Version"))
        }

    @Test
    fun `426 on a facade call maps to ApiError UpgradeRequired carrying the minimum version`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(426).setBody(
                    """{"type":"urn:hho:problem:upgrade-required","title":"Upgrade Required",""" +
                        """"status":426,"minimum_version":"2.4.0"}""",
                ),
            )
            val client = apiClient()

            val result = client.getStatus()

            val error = result.exceptionOrNull() as ApiError.UpgradeRequired
            assertEquals("2.4.0", error.minimumVersion)
        }

    @Test
    fun `no instance configured maps to ApiError NoInstanceConfigured`() =
        runBlocking {
            val client = apiClient(instanceUrl = null)

            val result = client.getStatus()

            assertTrue(result.exceptionOrNull() is ApiError.NoInstanceConfigured)
        }

    @Test
    fun `401 maps to ApiError Unauthorized`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401))
            val client = apiClient()

            val result = client.getStatus()

            assertTrue(result.exceptionOrNull() is ApiError.Unauthorized)
        }

    @Test
    fun `404 maps to ApiError NotFound`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404))
            val client = apiClient()

            val result = client.getStatus()

            assertTrue(result.exceptionOrNull() is ApiError.NotFound)
        }

    @Test
    fun `409 maps to ApiError Conflict carrying the problem detail`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(409).setBody(
                    """{"type":"urn:hho:problem:conflict","title":"Conflict","status":409,"detail":"field diverged"}""",
                ),
            )
            val client = apiClient()

            val result = client.getStatus()

            val error = result.exceptionOrNull() as ApiError.Conflict
            assertEquals("field diverged", error.detail)
        }

    @Test
    fun `422 maps to ApiError Validation carrying the problem detail`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(422).setBody(
                    """{"type":"urn:hho:problem:validation","title":"Unprocessable","status":422,"detail":"since must be >= 0"}""",
                ),
            )
            val client = apiClient()

            val result = client.getStatus()

            val error = result.exceptionOrNull() as ApiError.Validation
            assertEquals(422, error.status)
            assertEquals("since must be >= 0", error.detail)
        }

    @Test
    fun `400 with problem+json detail maps to ApiError Validation carrying that detail`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(400).setBody(
                    """{"type":"urn:hho:problem:bad-request","title":"Bad Request","status":400,"detail":"malformed body"}""",
                ),
            )
            val client = apiClient()

            val result = client.getStatus()

            val error = result.exceptionOrNull() as ApiError.Validation
            assertEquals(400, error.status)
            assertEquals("malformed body", error.detail)
        }

    @Test
    fun `500 maps to ApiError Server`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(500).setBody(
                    """{"type":"urn:hho:problem:internal","title":"Internal Server Error","status":500}""",
                ),
            )
            val client = apiClient()

            val result = client.getStatus()

            val error = result.exceptionOrNull() as ApiError.Server
            assertEquals(500, error.status)
        }

    @Test
    fun `an IO failure maps to ApiError Network`() =
        runBlocking {
            val client = apiClient()
            server.shutdown()

            val result = client.getStatus()

            assertTrue(result.exceptionOrNull() is ApiError.Network)
        }

    @Test
    fun `sync pull decodes a normal page through the entity_type dispatcher`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "changes": [
                        {"entity_type":"location","id":"loc-1","group_change_seq":1,
                         "data":{"id":"loc-1","name":"Garage","created_at":1,"updated_at":1,"version":1}}
                      ],
                      "tombstones": [],
                      "next_watermark": 7,
                      "has_more": false
                    }
                    """.trimIndent(),
                ),
            )
            val client = apiClient()

            val result = client.syncPull(deviceId = "device-1", since = 0, limit = 100)

            val page = result.getOrThrow() as SyncPullOutcome.Page
            assertEquals(1, page.changes.size)
            assertTrue(page.changes[0] is SyncChange.LocationChange)
            assertEquals(7L, page.nextWatermark)
        }

    @Test
    fun `sync pull surfaces cursor_too_old as its own distinct outcome`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"cursor_too_old":true}"""))
            val client = apiClient()

            val result = client.syncPull(deviceId = "device-1", since = 1, limit = 100)

            assertEquals(SyncPullOutcome.CursorTooOld, result.getOrThrow())
        }

    @Test
    fun `sync pull with an unknown entity_type fails as ApiError UnexpectedPayload, not a silent drop`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "changes": [{"entity_type":"widget","id":"w-1","group_change_seq":1,"data":{}}],
                      "tombstones": [],
                      "next_watermark": 1,
                      "has_more": false
                    }
                    """.trimIndent(),
                ),
            )
            val client = apiClient()

            val result = client.syncPull(deviceId = "device-1", since = 0, limit = 100)

            assertTrue(result.exceptionOrNull() is ApiError.UnexpectedPayload)
        }

    @Test
    fun `sync pull 401 maps to ApiError Unauthorized via the hand-rolled call path too`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401))
            val client = apiClient()

            val result = client.syncPull(deviceId = "device-1", since = 0, limit = 100)

            assertTrue(result.exceptionOrNull() is ApiError.Unauthorized)
        }

    @Test
    fun `probeInstance against a prefixed candidate hits the prefixed api-v1-status path`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient(instanceUrl = null)

            val result = client.probeInstance(server.url("/hho"))

            assertTrue(result.isSuccess)
            assertEquals("/hho/api/v1/status", server.takeRequest().path)
        }

    @Test
    fun `probeInstance is not host-rewritten to whatever instance is currently configured`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient(instanceUrl = "https://configured.invalid/hho")

            val result = client.probeInstance(server.url("/"))

            assertTrue(result.isSuccess)
            assertEquals("/api/v1/status", server.takeRequest().path)
        }

    @Test
    fun `probeInstance never sends the stored device token`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient(instanceUrl = null, token = "should-never-be-sent")

            client.probeInstance(server.url("/"))

            assertEquals(null, server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun `probeInstance 426 maps to ApiError UpgradeRequired carrying the minimum version`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(426).setBody(
                    """{"type":"urn:hho:problem:upgrade-required","title":"Upgrade Required",""" +
                        """"status":426,"minimum_version":"2.4.0"}""",
                ),
            )
            val client = apiClient(instanceUrl = null)

            val result = client.probeInstance(server.url("/"))

            val error = result.exceptionOrNull() as ApiError.UpgradeRequired
            assertEquals("2.4.0", error.minimumVersion)
        }

    @Test
    fun `probeInstance against a 200 with a non-JSON content type maps to ApiError UnexpectedPayload`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html>not an HHO server</html>"),
            )
            val client = apiClient(instanceUrl = null)

            val result = client.probeInstance(server.url("/"))

            assertTrue(result.exceptionOrNull() is ApiError.UnexpectedPayload)
        }

    @Test
    fun `probeInstance against a 200 JSON body missing required StatusResponse fields maps to ApiError UnexpectedPayload`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"hello":"world"}"""))
            val client = apiClient(instanceUrl = null)

            val result = client.probeInstance(server.url("/"))

            assertTrue(result.exceptionOrNull() is ApiError.UnexpectedPayload)
        }

    private val loginOkBody = """{"group_id":"g1","user_id":"u1","username":"alice","role":"owner"}"""

    @Test
    fun `login extracts the session cookie out of Set-Cookie, trimmed to name=value`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Set-Cookie", "hho_session=abc123; HttpOnly; Secure; SameSite=Lax; Max-Age=3600")
                    .setBody(loginOkBody),
            )
            val client = apiClient()

            val outcome = client.login("alice", "secret").getOrThrow()

            assertEquals("hho_session=abc123", outcome.sessionCookie)
            assertEquals("alice", outcome.response.username)
        }

    @Test
    fun `a 200 login response with no Set-Cookie header maps to ApiError UnexpectedPayload`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody(loginOkBody))
            val client = apiClient()

            val result = client.login("alice", "secret")

            assertTrue(result.exceptionOrNull() is ApiError.UnexpectedPayload)
        }

    @Test
    fun `login 401 maps to ApiError Unauthorized`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401))
            val client = apiClient()

            val result = client.login("alice", "wrong-password")

            assertTrue(result.exceptionOrNull() is ApiError.Unauthorized)
        }

    @Test
    fun `login 429 maps to ApiError Validation carrying status 429`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
            val client = apiClient()

            val result = client.login("alice", "secret")

            val error = result.exceptionOrNull() as ApiError.Validation
            assertEquals(429, error.status)
        }

    @Test
    fun `issueDeviceToken sends exactly the given session cookie as the Cookie header`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"dt1","device_label":"Pixel","token":"device-token-xyz"}"""))
            val client = apiClient()

            val result = client.issueDeviceToken("Pixel", "hho_session=abc123")

            assertTrue(result.isSuccess)
            assertEquals("hho_session=abc123", server.takeRequest().getHeader("Cookie"))
        }

    @Test
    fun `issueDeviceToken sends no Authorization header even when a device token is already held`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"dt2","device_label":"Pixel","token":"new-device-token"}"""))
            val client = apiClient(token = "already-held-device-token")

            val result = client.issueDeviceToken("Pixel", "hho_session=abc123")

            assertTrue(result.isSuccess)
            val recorded = server.takeRequest()
            assertNull(recorded.getHeader("Authorization"))
            assertEquals("hho_session=abc123", recorded.getHeader("Cookie"))
        }

    @Test
    fun `issueDeviceToken's session cookie never leaks onto a later unrelated facade call`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"dt1","device_label":"Pixel","token":"device-token-xyz"}"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok","version":"1.0.0","schema_version":1}"""))
            val client = apiClient()

            client.issueDeviceToken("Pixel", "hho_session=abc123")
            server.takeRequest()

            client.getStatus()

            assertNull(server.takeRequest().getHeader("Cookie"))
        }
}
