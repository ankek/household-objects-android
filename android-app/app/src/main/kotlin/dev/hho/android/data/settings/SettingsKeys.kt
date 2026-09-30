package dev.hho.android.data.settings

import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val INSTANCE_BASE_URL = stringPreferencesKey("instance_base_url")

    val DEVICE_ID = stringPreferencesKey("device_id")
}
