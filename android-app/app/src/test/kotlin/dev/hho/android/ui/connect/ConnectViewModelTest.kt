package dev.hho.android.ui.connect

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceUrlNormalization
import dev.hho.android.data.network.InstanceUrlNormalizer
import dev.hho.android.data.settings.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("connect-viewmodel-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun apiClient(): HhoApiClient =
        HhoApiClient(OkHttpClient(), OkHttpClient.Builder().addInterceptor(ClientVersionInterceptor()).build())

    private suspend fun storedUrl(dataStore: DataStore<Preferences>): String? =
        dataStore.data.map { it[SettingsKeys.INSTANCE_BASE_URL] }.first()

    private val statusOkBody = """{"status":"ok","version":"1.0.0","schema_version":3}"""

    @Test
    fun `success persists the normalised url and emits the navigate-on event`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(statusOkBody))
            val dataStore = tempDataStore()
            val viewModel = ConnectViewModel(dataStore, apiClient())
            val candidate = server.url("/").toString()

            val expected = (InstanceUrlNormalizer.normalize(candidate) as InstanceUrlNormalization.Valid).url.toString()

            viewModel.connect(candidate)

            assertEquals(expected, storedUrl(dataStore))
            assertEquals(Unit, viewModel.connected.first())
            assertNull(viewModel.uiState.value.errorMessage)
            assertTrue(!viewModel.uiState.value.isChecking)
        }

    @Test
    fun `a prefix instance validates against its own prefixed status path and persists the prefix`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(statusOkBody))
            val dataStore = tempDataStore()
            val viewModel = ConnectViewModel(dataStore, apiClient())
            val candidate = server.url("/hho").toString()
            val expected = (InstanceUrlNormalizer.normalize(candidate) as InstanceUrlNormalization.Valid).url.toString()

            viewModel.connect(candidate)

            assertEquals("/hho/api/v1/status", server.takeRequest().path)
            assertEquals(expected, storedUrl(dataStore))
            assertEquals(Unit, viewModel.connected.first())
        }

    @Test
    fun `unreachable host fails with a network error and leaves DataStore unchanged`() =
        runTest {
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://already-configured.example.com" }
            val before = storedUrl(dataStore)
            val viewModel = ConnectViewModel(dataStore, apiClient())
            val candidate = server.url("/").toString()
            server.shutdown()

            viewModel.connect(candidate)

            assertEquals(before, storedUrl(dataStore))
            assertTrue(viewModel.uiState.value.errorMessage!!.contains("Couldn't reach"))
        }

    @Test
    fun `a 200 from a non-HHO server fails as unexpected-payload and leaves DataStore unchanged`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html>hello, wrong server</html>"),
            )
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://already-configured.example.com" }
            val before = storedUrl(dataStore)
            val viewModel = ConnectViewModel(dataStore, apiClient())

            viewModel.connect(server.url("/").toString())

            assertEquals(before, storedUrl(dataStore))
            assertTrue(viewModel.uiState.value.errorMessage!!.contains("doesn't look like an HHO server"))
        }

    @Test
    fun `a 200 with a JSON body missing the required StatusResponse fields fails and leaves DataStore unchanged`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"hello":"world"}"""))
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://already-configured.example.com" }
            val before = storedUrl(dataStore)
            val viewModel = ConnectViewModel(dataStore, apiClient())

            viewModel.connect(server.url("/").toString())

            assertEquals(before, storedUrl(dataStore))
            assertTrue(viewModel.uiState.value.errorMessage!!.contains("doesn't look like an HHO server"))
        }

    @Test
    fun `426 fails with an upgrade-required message and leaves DataStore unchanged`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(426).setBody(
                    """{"type":"urn:hho:problem:upgrade-required","title":"Upgrade Required",""" +
                        """"status":426,"minimum_version":"2.0.0"}""",
                ),
            )
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://already-configured.example.com" }
            val before = storedUrl(dataStore)
            val viewModel = ConnectViewModel(dataStore, apiClient())

            viewModel.connect(server.url("/").toString())

            assertEquals(before, storedUrl(dataStore))
            assertTrue(viewModel.uiState.value.errorMessage!!.contains("2.0.0"))
        }

    @Test
    fun `404 fails with a path-prefix hint and leaves DataStore unchanged`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://already-configured.example.com" }
            val before = storedUrl(dataStore)
            val viewModel = ConnectViewModel(dataStore, apiClient())

            viewModel.connect(server.url("/").toString())

            assertEquals(before, storedUrl(dataStore))
            assertTrue(viewModel.uiState.value.errorMessage!!.contains("reverse-proxy"))
        }

    @Test
    fun `an invalid url is rejected before ever reaching the network and leaves DataStore unchanged`() =
        runTest {
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://already-configured.example.com" }
            val before = storedUrl(dataStore)
            val viewModel = ConnectViewModel(dataStore, apiClient())

            viewModel.connect("not-a-url-at-all")

            assertEquals(before, storedUrl(dataStore))
            assertEquals(0, server.requestCount)
            assertTrue(viewModel.uiState.value.errorMessage!!.isNotBlank())
        }

    @Test
    fun `the url field is pre-filled from a previously stored instance url`() =
        runTest {
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://stored.example.com" }
            val viewModel = ConnectViewModel(dataStore, apiClient())

            viewModel.prefillStoredUrl()

            assertEquals("https://stored.example.com", viewModel.uiState.value.urlInput)
        }

    @Test
    fun `the url field stays blank when nothing is stored yet`() =
        runTest {
            val viewModel = ConnectViewModel(tempDataStore(), apiClient())

            viewModel.prefillStoredUrl()

            assertEquals("", viewModel.uiState.value.urlInput)
        }
}
