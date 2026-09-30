package dev.hho.android.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import dev.hho.android.data.auth.FakeDeviceTokenStore
import dev.hho.android.data.auth.InstanceScopedDeviceTokenProvider
import dev.hho.android.data.auth.StoredToken
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AuthHeaderBaseUrlTocTouTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private class SequentialInstanceUrlDataStore(private val urlsByCallIndex: List<String>) : DataStore<Preferences> {
        val callCount = AtomicInteger(0)

        override val data: Flow<Preferences> =
            flow {
                val index = callCount.getAndIncrement().coerceAtMost(urlsByCallIndex.size - 1)
                emit(preferencesOf(SettingsKeys.INSTANCE_BASE_URL to urlsByCallIndex[index]))
            }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw UnsupportedOperationException("read-only fake — this test never writes through it")
    }

    @Test
    fun `exactly one instance-url read happens per request, so the attach decision can never see a different instance than the routing decision`() =
        runBlocking {
            val instanceA = server.url("/").toString()
            val instanceB = "https://instance-b.invalid/"
            val sequentialDataStore = SequentialInstanceUrlDataStore(listOf(instanceA, instanceB))
            val resolver = InstanceBaseUrlResolver(sequentialDataStore)
            val deviceTokenStore = FakeDeviceTokenStore(StoredToken(token = "token-for-a", instanceUrl = instanceA))
            val tokenProvider = InstanceScopedDeviceTokenProvider(deviceTokenStore)
            val baseUrlInterceptor = BaseUrlInterceptor(resolver)
            val authHeaderInterceptor = AuthHeaderInterceptor(tokenProvider)
            val clientVersionInterceptor = ClientVersionInterceptor()
            val httpClient =
                NetworkModule.provideOkHttpClient(baseUrlInterceptor, authHeaderInterceptor, clientVersionInterceptor)

            server.enqueue(MockResponse().setResponseCode(200))
            httpClient
                .newCall(Request.Builder().url("http://placeholder.invalid/api/v1/status").build())
                .execute()
                .close()

            assertEquals(1, sequentialDataStore.callCount.get())
            val recorded = server.takeRequest()
            assertEquals("Bearer token-for-a", recorded.getHeader("Authorization"))
        }

    @Test
    fun `a token bound to an instance the resolver no longer reports is never attached, regardless of read timing`() =
        runBlocking {
            val instanceA = "https://instance-a.invalid/"
            val instanceB = server.url("/").toString()
            val sequentialDataStore = SequentialInstanceUrlDataStore(listOf(instanceB))
            val resolver = InstanceBaseUrlResolver(sequentialDataStore)
            val deviceTokenStore = FakeDeviceTokenStore(StoredToken(token = "token-for-a", instanceUrl = instanceA))
            val tokenProvider = InstanceScopedDeviceTokenProvider(deviceTokenStore)
            val baseUrlInterceptor = BaseUrlInterceptor(resolver)
            val authHeaderInterceptor = AuthHeaderInterceptor(tokenProvider)
            val clientVersionInterceptor = ClientVersionInterceptor()
            val httpClient =
                NetworkModule.provideOkHttpClient(baseUrlInterceptor, authHeaderInterceptor, clientVersionInterceptor)

            server.enqueue(MockResponse().setResponseCode(200))
            httpClient
                .newCall(Request.Builder().url("http://placeholder.invalid/api/v1/status").build())
                .execute()
                .close()

            assertNull("server B (the actually-routed instance) must never see server A's device token", server.takeRequest().getHeader("Authorization"))
        }
}
