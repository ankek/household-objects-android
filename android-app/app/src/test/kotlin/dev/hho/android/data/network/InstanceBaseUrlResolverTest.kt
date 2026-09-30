package dev.hho.android.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.settings.SettingsKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InstanceBaseUrlResolverTest {

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("hho-settings-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    @Test
    fun `resolves NotConfigured when no url has ever been stored`() =
        runBlocking {
            val resolver = InstanceBaseUrlResolver(tempDataStore())

            assertEquals(BaseUrlResolution.NotConfigured, resolver.resolve())
        }

    @Test
    fun `resolves Configured with the stored url`() =
        runBlocking {
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://hho.example.com" }

            val resolution = InstanceBaseUrlResolver(dataStore).resolve()

            val configured = resolution as? BaseUrlResolution.Configured
            assertEquals("hho.example.com", configured?.baseUrl?.host)
            assertEquals("https", configured?.baseUrl?.scheme)
        }

    @Test
    fun `resolves NotConfigured when the stored value does not parse as a url`() =
        runBlocking {
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "not a url" }

            val resolution = InstanceBaseUrlResolver(dataStore).resolve()

            assertTrue(resolution is BaseUrlResolution.NotConfigured)
        }

    @Test
    fun `reflects a url changed after the resolver was created, without caching`() =
        runBlocking {
            val dataStore = tempDataStore()
            val resolver = InstanceBaseUrlResolver(dataStore)
            assertTrue(resolver.resolve() is BaseUrlResolution.NotConfigured)

            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://second.example.com" }

            val resolution = resolver.resolve()
            assertEquals("second.example.com", (resolution as BaseUrlResolution.Configured).baseUrl.host)
        }

    @Test
    fun `resolutionFlow reflects the value currently in DataStore on first collection`() =
        runBlocking {
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://hho.example.com" }

            val resolution = InstanceBaseUrlResolver(dataStore).resolutionFlow().first()

            assertEquals("hho.example.com", (resolution as BaseUrlResolution.Configured).baseUrl.host)
        }

    @Test
    fun `resolutionFlow re-emits when a different collaborator writes a new instance url`() =
        runBlocking {
            val dataStore = tempDataStore()
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://first.example.com" }
            val resolver = InstanceBaseUrlResolver(dataStore)

            val emissions =
                resolver.resolutionFlow().let { flow ->
                    val first = flow.first()
                    dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = "https://second.example.com" }
                    val second = flow.first()
                    listOf(first, second)
                }

            assertEquals("first.example.com", (emissions[0] as BaseUrlResolution.Configured).baseUrl.host)
            assertEquals("second.example.com", (emissions[1] as BaseUrlResolution.Configured).baseUrl.host)
        }
}
