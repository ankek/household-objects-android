package dev.hho.android.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.hho.android.data.settings.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class InstanceBaseUrlResolver
    @Inject
    constructor(
        private val settingsDataStore: DataStore<Preferences>,
    ) {
        suspend fun resolve(): BaseUrlResolution = resolutionFlow().first()

        fun resolutionFlow(): Flow<BaseUrlResolution> =
            settingsDataStore.data
                .map { it[SettingsKeys.INSTANCE_BASE_URL] }
                .map { stored ->
                    val parsed = stored?.toHttpUrlOrNull()
                    if (parsed != null) {
                        BaseUrlResolution.Configured(parsed)
                    } else {
                        BaseUrlResolution.NotConfigured
                    }
                }
    }

sealed interface BaseUrlResolution {
    data class Configured(val baseUrl: HttpUrl) : BaseUrlResolution

    data object NotConfigured : BaseUrlResolution
}
