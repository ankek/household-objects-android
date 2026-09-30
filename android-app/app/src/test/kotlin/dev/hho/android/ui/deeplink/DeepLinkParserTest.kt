package dev.hho.android.ui.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    private val id = "0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"

    @Test fun validUuidOnHttps() =
        assertEquals(ItemDeepLink.ById(id), parseItemDeepLink("https://hho.example.com/i/$id"))

    @Test fun validUuidOnLanHttpWithPort() =
        assertEquals(ItemDeepLink.ById(id), parseItemDeepLink("http://192.168.1.5:7745/i/$id"))

    @Test fun uppercaseUuidIsLowercased() =
        assertEquals(ItemDeepLink.ById(id), parseItemDeepLink("https://h/i/${id.uppercase()}"))

    @Test fun trailingSlashQueryAndFragmentIgnored() {
        assertEquals(ItemDeepLink.ById(id), parseItemDeepLink("https://h/i/$id/"))
        assertEquals(ItemDeepLink.ById(id), parseItemDeepLink("https://h/i/$id?utm=1&x=/y"))
        assertEquals(ItemDeepLink.ById(id), parseItemDeepLink("https://h/i/$id/?a=b#frag"))
    }

    @Test fun nonUuidTokenIsShortCode() =
        assertEquals(ItemDeepLink.ByShortCode("AB-12"), parseItemDeepLink("https://h/i/AB-12"))

    @Test fun malformedUuidFallsBackToShortCodeLikeServer() =
        assertEquals(
            ItemDeepLink.ByShortCode("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5"),
            parseItemDeepLink("https://h/i/0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5"),
        )

    @Test fun wrongPathsRejected() {
        listOf(
            "https://h/items/$id", "https://h/x/i/$id", "https://h/i", "https://h/i/", "https://h/i//",
            "https://h/i/$id/extra", "https://h/I/$id", "https://h/api/v1/i/$id", "https://h/",
        ).forEach { assertNull(it, parseItemDeepLink(it)) }
    }

    @Test fun badSchemeOrHostRejected() {
        listOf("ftp://h/i/$id", "hho://h/i/$id", "/i/$id", "https:///i/$id", "i/$id")
            .forEach { assertNull(it, parseItemDeepLink(it)) }
    }

    @Test fun garbageRejected() {
        listOf(null, "", "   ", "https://h/i/a b", "https://h/i/%2F", "ht tp://x", "https://h/i/${"a".repeat(129)}")
            .forEach { assertNull(it, parseItemDeepLink(it)) }
    }
}
