package dev.hho.android.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstanceScopedDeviceTokenProviderTest {

    @Test
    fun `no token stored means no token returned`() =
        runBlocking {
            val provider = InstanceScopedDeviceTokenProvider(FakeDeviceTokenStore())

            assertNull(provider.tokenFor("https://hho.example.com/api/v1/status".toHttpUrl()))
        }

    @Test
    fun `a token bound to the request's exact host and port is returned`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "device-token-xyz", instanceUrl = "https://hho.example.com/"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            assertEquals("device-token-xyz", provider.tokenFor("https://hho.example.com/api/v1/status".toHttpUrl()))
        }

    @Test
    fun `a token bound to a different host than the request is refused`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "token-for-a", instanceUrl = "https://a.example.com/"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            assertNull(provider.tokenFor("https://b.example.com/api/v1/status".toHttpUrl()))
        }

    @Test
    fun `a token bound to a different port on the same host is refused`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "token-for-8443", instanceUrl = "https://hho.example.com:8443/"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            assertNull(provider.tokenFor("https://hho.example.com:9443/api/v1/status".toHttpUrl()))
        }

    @Test
    fun `a token bound to a different scheme on the same host and port is refused`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "token-for-https", instanceUrl = "https://hho.example.com/"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            assertNull(provider.tokenFor("http://hho.example.com/api/v1/status".toHttpUrl()))
        }

    @Test
    fun `a token bound to a reverse-proxy prefix is returned for a request under that exact prefix`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "token-for-hho", instanceUrl = "https://ex.com/hho"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            assertEquals("token-for-hho", provider.tokenFor("https://ex.com/hho/api/v1/status".toHttpUrl()))
        }

    @Test
    fun `a token bound to a reverse-proxy prefix is refused for a request whose segment merely starts with the same characters`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "token-for-hho", instanceUrl = "https://ex.com/hho"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            assertNull(provider.tokenFor("https://ex.com/hhox/api/v1/status".toHttpUrl()))
        }

    @Test
    fun `a token bound to a reverse-proxy prefix is refused for a request with no prefix at all`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "token-for-hho", instanceUrl = "https://ex.com/hho"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            assertNull(provider.tokenFor("https://ex.com/api/v1/status".toHttpUrl()))
        }

    @Test
    fun `a token bound to a bare origin (no prefix) is returned for any path under that host`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "token-for-bare", instanceUrl = "https://ex.com/"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            assertEquals("token-for-bare", provider.tokenFor("https://ex.com/api/v1/status".toHttpUrl()))
            assertEquals("token-for-bare", provider.tokenFor("https://ex.com/anything/else".toHttpUrl()))
        }

    @Test
    fun `invalidate clears a token that is still bound to the request's instance and still has the rejected value`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "device-token-xyz", instanceUrl = "https://hho.example.com/"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            provider.invalidate("https://hho.example.com/api/v1/status".toHttpUrl(), rejectedToken = "device-token-xyz")

            assertNull("the rejected token must be gone from the durable store, not merely from the read side", store.load())
        }

    @Test
    fun `invalidate is a no-op when nothing is stored`() =
        runBlocking {
            val store = FakeDeviceTokenStore()
            val provider = InstanceScopedDeviceTokenProvider(store)

            provider.invalidate("https://hho.example.com/api/v1/status".toHttpUrl(), rejectedToken = "some-token")

            assertNull(store.load())
        }

    @Test
    fun `invalidate never touches a token bound to a different instance than the rejected request`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "token-for-a", instanceUrl = "https://a.example.com/"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            provider.invalidate("https://b.example.com/api/v1/status".toHttpUrl(), rejectedToken = "token-for-a")

            assertEquals(StoredToken(token = "token-for-a", instanceUrl = "https://a.example.com/"), store.load())
        }

    @Test
    fun `invalidate never clears a newer token for the same instance that no longer matches the rejected value (TOCTOU safety)`() =
        runBlocking {
            val store = FakeDeviceTokenStore(StoredToken(token = "brand-new-token", instanceUrl = "https://hho.example.com/"))
            val provider = InstanceScopedDeviceTokenProvider(store)

            provider.invalidate("https://hho.example.com/api/v1/status".toHttpUrl(), rejectedToken = "stale-token")

            assertEquals(StoredToken(token = "brand-new-token", instanceUrl = "https://hho.example.com/"), store.load())
        }
}
