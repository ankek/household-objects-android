package dev.hho.android.buildsrc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClientVersionTest {
    @Test
    fun `a strict MAJOR MINOR PATCH value is returned unchanged`() {
        assertEquals("1.2.3", ClientVersion.validate("1.2.3"))
    }

    @Test
    fun `an all-zero version is valid`() {
        assertEquals("0.1.0", ClientVersion.validate("0.1.0"))
    }

    @Test
    fun `a leading zero component is accepted, matching the server's isDigits plus strconv Atoi parser`() {
        assertEquals("01.2.3", ClientVersion.validate("01.2.3"))
    }

    @Test
    fun `a multi-digit component is valid`() {
        assertEquals("12.34.567", ClientVersion.validate("12.34.567"))
    }

    @Test
    fun `too few components is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("1.2") }
    }

    @Test
    fun `too many components is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("1.2.3.4") }
    }

    @Test
    fun `a pre-release suffix is rejected, not stripped`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("1.2.3-rc1") }
    }

    @Test
    fun `a debug suffix is rejected, not stripped`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("1.2.3-debug") }
    }

    @Test
    fun `a build metadata suffix is rejected, not stripped`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("1.2.3+b5") }
    }

    @Test
    fun `a negative component is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("-1.2.3") }
    }

    @Test
    fun `a non-numeric component is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("a.b.c") }
    }

    @Test
    fun `an empty string is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("") }
    }

    @Test
    fun `whitespace around an otherwise-valid value is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate(" 1.2.3") }
        assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("1.2.3 ") }
    }

    @Test
    fun `the failure message names the offending value so a bad build fails loudly and legibly`() {
        val error = assertThrows(IllegalArgumentException::class.java) { ClientVersion.validate("0.1.0-debug") }
        assertEquals(true, error.message?.contains("0.1.0-debug"))
        assertEquals(true, error.message?.contains("MAJOR.MINOR.PATCH"))
    }
}
