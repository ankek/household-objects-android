package dev.hho.android.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.settings.SettingsKeys
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceIdProvider {
    suspend fun deviceId(): String
}

@Singleton
class DataStoreDeviceIdProvider
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : DeviceIdProvider {
        override suspend fun deviceId(): String {
            val prefs =
                dataStore.edit { mutablePrefs ->
                    if (mutablePrefs[SettingsKeys.DEVICE_ID] == null) {
                        mutablePrefs[SettingsKeys.DEVICE_ID] = UUID.randomUUID().toString()
                    }
                }
            return requireNotNull(prefs[SettingsKeys.DEVICE_ID]) {
                "DataStoreDeviceIdProvider: DEVICE_ID missing immediately after being written."
            }
        }
    }
