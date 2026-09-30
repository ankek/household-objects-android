package dev.hho.android.data.network

import dev.hho.android.data.auth.DeviceTokenProvider
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthHeaderInterceptorTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWithToken(token: String?): OkHttpClient {
        val provider = fixedTokenProvider(token)
        return OkHttpClient.Builder().addInterceptor(AuthHeaderInterceptor(provider)).build()
    }

    @Test
    fun `a stored token is attached as a Bearer Authorization header`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientWithToken("secret-device-token")

        client.newCall(Request.Builder().url(server.url("/api/v1/status")).build()).execute().close()

        assertEquals("Bearer secret-device-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `no stored token sends no Authorization header at all`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientWithToken(null)

        client.newCall(Request.Builder().url(server.url("/api/v1/status")).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a blank stored token sends no Authorization header at all`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientWithToken("   ")

        client.newCall(Request.Builder().url(server.url("/api/v1/status")).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `an existing Authorization header on the request is overwritten, never duplicated`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientWithToken("fresh-token")

        client
            .newCall(
                Request.Builder()
                    .url(server.url("/api/v1/status"))
                    .header("Authorization", "Bearer stale-token")
                    .build(),
            ).execute()
            .close()

        val recorded = server.takeRequest()
        assertEquals("Bearer fresh-token", recorded.getHeader("Authorization"))
        assertEquals(1, recorded.headers.values("Authorization").size)
    }

    @Test
    fun `the actual outgoing request url is what gets passed to the token provider (T156e TOCTOU fix)`() {
        server.enqueue(MockResponse().setResponseCode(200))
        var capturedUrl: HttpUrl? = null
        val provider =
            object : DeviceTokenProvider {
                override suspend fun tokenFor(requestUrl: HttpUrl): String? {
                    capturedUrl = requestUrl
                    return "token-for-captured-url"
                }

                override suspend fun invalidate(
                    requestUrl: HttpUrl,
                    rejectedToken: String,
                ) = Unit
            }
        val client = OkHttpClient.Builder().addInterceptor(AuthHeaderInterceptor(provider)).build()
        val requestUrl = server.url("/api/v1/status")

        client.newCall(Request.Builder().url(requestUrl).build()).execute().close()

        assertEquals(requestUrl, capturedUrl)
        assertEquals("Bearer token-for-captured-url", server.takeRequest().getHeader("Authorization"))
    }
}

private fun fixedTokenProvider(token: String?): DeviceTokenProvider =
    object : DeviceTokenProvider {
        override suspend fun tokenFor(requestUrl: HttpUrl): String? = token

        override suspend fun invalidate(
            requestUrl: HttpUrl,
            rejectedToken: String,
        ) = Unit
    }
