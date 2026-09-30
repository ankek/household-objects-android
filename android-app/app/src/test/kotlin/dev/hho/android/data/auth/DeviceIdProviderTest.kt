package dev.hho.android.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.hho.android.data.settings.SettingsKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class DeviceIdProviderTest {

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("device-id-provider-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    @Test
    fun `generates and persists a random UUID on first call`() =
        runBlocking {
            val dataStore = tempDataStore()
            val provider = DataStoreDeviceIdProvider(dataStore)

            val id = provider.deviceId()

            assertNotNull(id)
            UUID.fromString(id)
            assertEquals(id, dataStore.data.first()[SettingsKeys.DEVICE_ID])
        }

    @Test
    fun `returns the same id on every subsequent call`() =
        runBlocking {
            val provider = DataStoreDeviceIdProvider(tempDataStore())

            val first = provider.deviceId()
            val second = provider.deviceId()
            val third = provider.deviceId()

            assertEquals(first, second)
            assertEquals(first, third)
        }

    @Test
    fun `stays stable across independently constructed instances sharing the same DataStore file (survives a process restart)`() =
        runBlocking {
            val dataStore = tempDataStore()

            val idBeforeRestart = DataStoreDeviceIdProvider(dataStore).deviceId()
            val idAfterRestart = DataStoreDeviceIdProvider(dataStore).deviceId()

            assertEquals(idBeforeRestart, idAfterRestart)
        }

    @Test
    fun `two different DataStore files (a reinstall) get two different ids`() =
        runBlocking {
            val idA = DataStoreDeviceIdProvider(tempDataStore()).deviceId()
            val idB = DataStoreDeviceIdProvider(tempDataStore()).deviceId()

            assertTrue(idA != idB)
        }
}
