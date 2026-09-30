package dev.hho.android.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.settings.SettingsKeys
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException

class BaseUrlInterceptorTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("hho-interceptor-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun clientConfiguredFor(instanceUrl: String): OkHttpClient {
        val dataStore = tempDataStore()
        runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = instanceUrl } }
        val interceptor = BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore))
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private fun clientWithNoInstanceConfigured(): OkHttpClient {
        val interceptor = BaseUrlInterceptor(InstanceBaseUrlResolver(tempDataStore()))
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @Test
    fun `bare origin sends the request path unchanged`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientConfiguredFor(server.url("/").toString())

        val placeholder = "http://placeholder.invalid/api/v1/status".toHttpUrl()
        client.newCall(Request.Builder().url(placeholder).build()).execute().close()

        assertEquals("/api/v1/status", server.takeRequest().path)
    }

    @Test
    fun `origin with a path prefix without trailing slash is prepended`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientConfiguredFor(server.url("/hho").toString())

        val placeholder = "http://placeholder.invalid/api/v1/status".toHttpUrl()
        client.newCall(Request.Builder().url(placeholder).build()).execute().close()

        assertEquals("/hho/api/v1/status", server.takeRequest().path)
    }

    @Test
    fun `origin with a path prefix with a trailing slash is prepended without a double slash`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientConfiguredFor(server.url("/hho/").toString())

        val placeholder = "http://placeholder.invalid/api/v1/status".toHttpUrl()
        client.newCall(Request.Builder().url(placeholder).build()).execute().close()

        assertEquals("/hho/api/v1/status", server.takeRequest().path)
    }

    @Test
    fun `request query string is preserved alongside a path prefix`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientConfiguredFor(server.url("/hho").toString())

        val placeholder = "http://placeholder.invalid/api/v1/sync/pull?since=5&limit=10".toHttpUrl()
        client.newCall(Request.Builder().url(placeholder).build()).execute().close()

        assertEquals("/hho/api/v1/sync/pull?since=5&limit=10", server.takeRequest().path)
    }

    @Test
    fun `no instance url configured throws NoInstanceUrlConfiguredException`() {
        val client = clientWithNoInstanceConfigured()
        val placeholder = "http://placeholder.invalid/api/v1/status".toHttpUrl()

        val thrown =
            assertThrows(IOException::class.java) {
                client.newCall(Request.Builder().url(placeholder).build()).execute()
            }

        assert(thrown is NoInstanceUrlConfiguredException) {
            "expected NoInstanceUrlConfiguredException, got $thrown"
        }
    }
}
