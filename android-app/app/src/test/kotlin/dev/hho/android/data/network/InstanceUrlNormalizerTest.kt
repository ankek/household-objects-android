package dev.hho.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceUrlNormalizerTest {

    @Test
    fun `bare https origin normalises unchanged`() {
        val result = InstanceUrlNormalizer.normalize("https://hho.example.com")
        val valid = result as InstanceUrlNormalization.Valid
        assertEquals("https", valid.url.scheme)
        assertEquals("hho.example.com", valid.url.host)
        assertTrue(valid.url.pathSegments.filterNot { it.isEmpty() }.isEmpty())
    }

    @Test
    fun `http scheme on a LAN address is accepted, not rejected as insecure`() {
        val result = InstanceUrlNormalizer.normalize("http://192.168.1.10:7745")
        val valid = result as InstanceUrlNormalization.Valid
        assertEquals("http", valid.url.scheme)
        assertEquals("192.168.1.10", valid.url.host)
        assertEquals(7745, valid.url.port)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        val result = InstanceUrlNormalizer.normalize("  https://hho.example.com  ")
        val valid = result as InstanceUrlNormalization.Valid
        assertEquals("hho.example.com", valid.url.host)
    }

    @Test
    fun `a trailing slash on the bare origin is stripped`() {
        val result = InstanceUrlNormalizer.normalize("https://hho.example.com/")
        val valid = result as InstanceUrlNormalization.Valid
        assertEquals("https://hho.example.com/", valid.url.toString())
        assertTrue(valid.url.pathSegments.filterNot { it.isEmpty() }.isEmpty())
    }

    @Test
    fun `a reverse-proxy path prefix is kept`() {
        val result = InstanceUrlNormalizer.normalize("https://example.com/hho")
        val valid = result as InstanceUrlNormalization.Valid
        assertEquals(listOf("hho"), valid.url.pathSegments)
        assertEquals("https://example.com/hho", valid.url.toString())
    }

    @Test
    fun `a trailing slash on a path prefix is stripped but the prefix itself is kept`() {
        val result = InstanceUrlNormalizer.normalize("https://example.com/hho/")
        val valid = result as InstanceUrlNormalization.Valid
        assertEquals("https://example.com/hho", valid.url.toString())
    }

    @Test
    fun `a missing scheme is rejected, never silently defaulted`() {
        val result = InstanceUrlNormalizer.normalize("hho.example.com")
        assertTrue(result is InstanceUrlNormalization.Invalid)
    }

    @Test
    fun `blank input is rejected`() {
        val result = InstanceUrlNormalizer.normalize("   ")
        assertTrue(result is InstanceUrlNormalization.Invalid)
    }

    @Test
    fun `userinfo is rejected`() {
        val result = InstanceUrlNormalizer.normalize("https://user:pass@example.com")
        assertTrue(result is InstanceUrlNormalization.Invalid)
    }

    @Test
    fun `a query string is rejected`() {
        val result = InstanceUrlNormalizer.normalize("https://example.com/hho?debug=1")
        assertTrue(result is InstanceUrlNormalization.Invalid)
    }

    @Test
    fun `a fragment is rejected`() {
        val result = InstanceUrlNormalizer.normalize("https://example.com/hho#section")
        assertTrue(result is InstanceUrlNormalization.Invalid)
    }

    @Test
    fun `an unparsable value is rejected`() {
        val result = InstanceUrlNormalizer.normalize("https://")
        assertTrue(result is InstanceUrlNormalization.Invalid)
    }
}
