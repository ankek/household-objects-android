package dev.hho.android.data.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object InstanceUrlNormalizer {
    fun normalize(raw: String): InstanceUrlNormalization {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return InstanceUrlNormalization.Invalid("Enter an instance URL.")
        }
        val hasExplicitScheme =
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        if (!hasExplicitScheme) {
            return InstanceUrlNormalization.Invalid(
                "Include the scheme, e.g. https://hho.example.com or http://192.168.1.10:7745.",
            )
        }
        val parsed =
            trimmed.toHttpUrlOrNull()
                ?: return InstanceUrlNormalization.Invalid("That doesn't look like a valid URL.")
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return InstanceUrlNormalization.Invalid("Remove the username/password from the URL.")
        }
        if (parsed.query != null) {
            return InstanceUrlNormalization.Invalid("Remove the query string (?...) from the URL.")
        }
        if (parsed.fragment != null) {
            return InstanceUrlNormalization.Invalid("Remove the # fragment from the URL.")
        }

        val pathSegments = parsed.pathSegments.filterNot { it.isEmpty() }
        val normalized =
            parsed.newBuilder()
                .encodedPath("/")
                .apply { pathSegments.forEach { addPathSegment(it) } }
                .build()
        return InstanceUrlNormalization.Valid(normalized)
    }
}

sealed interface InstanceUrlNormalization {
    data class Valid(val url: HttpUrl) : InstanceUrlNormalization

    data class Invalid(val reason: String) : InstanceUrlNormalization
}
