package dev.hho.android.data.network

import dev.hho.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientVersionInterceptorTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWithInterceptor(): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(ClientVersionInterceptor()).build()

    @Test
    fun `sets the header to BuildConfig HHO_CLIENT_VERSION on a request that had none`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(server.url("/")).build()

        clientWithInterceptor().newCall(request).execute().close()

        assertEquals(BuildConfig.HHO_CLIENT_VERSION, server.takeRequest().getHeader("X-HHO-Client-Version"))
    }

    @Test
    fun `replaces rather than duplicates a header the request already carried`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val request =
            Request.Builder()
                .url(server.url("/"))
                .header("X-HHO-Client-Version", "9.9.9-should-be-overwritten")
                .build()

        clientWithInterceptor().newCall(request).execute().close()

        val recorded = server.takeRequest()
        assertEquals(listOf(BuildConfig.HHO_CLIENT_VERSION), recorded.headers.values("X-HHO-Client-Version"))
    }
}
