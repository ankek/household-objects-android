package dev.hho.android.data.auth

import okhttp3.HttpUrl

interface DeviceTokenProvider {
    suspend fun tokenFor(requestUrl: HttpUrl): String?

    suspend fun invalidate(requestUrl: HttpUrl, rejectedToken: String)
}
