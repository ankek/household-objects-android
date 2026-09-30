package dev.hho.android.data.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

interface DeviceTokenStore {
    suspend fun load(): StoredToken?

    suspend fun save(token: StoredToken)

    suspend fun clear()
}

data class StoredToken(val token: String, val instanceUrl: String)

internal fun StoredToken?.tokenFor(configuredInstanceUrl: String?): String? =
    this?.takeIf { configuredInstanceUrl != null && it.instanceUrl == configuredInstanceUrl }?.token

internal fun StoredToken?.tokenForRequest(requestUrl: HttpUrl): String? =
    this?.takeIf { matchesBoundInstance(requestUrl, it.instanceUrl) }?.token

internal fun matchesBoundInstance(
    requestUrl: HttpUrl,
    boundInstanceUrl: String,
): Boolean {
    val bound = boundInstanceUrl.toHttpUrlOrNull() ?: return false
    if (bound.scheme != requestUrl.scheme) return false
    if (bound.host != requestUrl.host) return false
    if (bound.port != requestUrl.port) return false
    val boundSegments = bound.pathSegments.filterNot { it.isEmpty() }
    val requestSegments = requestUrl.pathSegments.filterNot { it.isEmpty() }
    if (requestSegments.size < boundSegments.size) return false
    return requestSegments.subList(0, boundSegments.size) == boundSegments
}
