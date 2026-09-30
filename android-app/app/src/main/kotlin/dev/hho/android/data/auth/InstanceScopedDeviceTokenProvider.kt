package dev.hho.android.data.auth

import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstanceScopedDeviceTokenProvider
    @Inject
    constructor(
        private val dataStore: DeviceTokenStore,
    ) : DeviceTokenProvider {
        override suspend fun tokenFor(requestUrl: HttpUrl): String? = dataStore.load().tokenForRequest(requestUrl)

        override suspend fun invalidate(
            requestUrl: HttpUrl,
            rejectedToken: String,
        ) {
            val current = dataStore.load()
            if (current.tokenForRequest(requestUrl) == rejectedToken) {
                dataStore.clear()
            }
        }
    }
