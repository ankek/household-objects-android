package dev.hho.android.ui.deeplink

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

sealed interface ItemDeepLink {
    data class ById(val itemId: String) : ItemDeepLink

    data class ByShortCode(val shortCode: String) : ItemDeepLink
}

private val UUID_SHAPE =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

private const val MAX_TOKEN_LENGTH = 128

fun parseItemDeepLink(uri: String?): ItemDeepLink? {
    if (uri.isNullOrBlank()) return null
    val parsed = try {
        URI(uri)
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    if ((scheme != "http" && scheme != "https") || parsed.host.isNullOrEmpty()) return null
    val segments = (parsed.path ?: return null).removeSuffix("/").split('/')
    if (segments.size != 3 || segments[0].isNotEmpty() || segments[1] != "i") return null
    val token = segments[2]
    if (token.isEmpty() || token.length > MAX_TOKEN_LENGTH || token.any { it.isWhitespace() }) return null
    return if (UUID_SHAPE.matches(token)) {
        ItemDeepLink.ById(token.lowercase(Locale.ROOT))
    } else {
        ItemDeepLink.ByShortCode(token)
    }
}
