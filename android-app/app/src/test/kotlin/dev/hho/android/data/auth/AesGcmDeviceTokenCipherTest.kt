package dev.hho.android.data.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AesGcmDeviceTokenCipherTest {

    private val defaultAad = "https://hho.example.com/".toByteArray(Charsets.UTF_8)

    private fun softwareAesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun cipherWithFixedKey(key: SecretKey = softwareAesKey()): AesGcmDeviceTokenCipher =
        AesGcmDeviceTokenCipher(secretKey = { key })

    @Test
    fun `decrypt reverses encrypt exactly`() {
        val cipher = cipherWithFixedKey()
        val plaintext = "device-token-abc123".toByteArray(Charsets.UTF_8)

        val ciphertext = cipher.encrypt(plaintext, defaultAad)
        val decrypted = cipher.decrypt(ciphertext, defaultAad)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `ciphertext never contains the plaintext token bytes verbatim`() {
        val cipher = cipherWithFixedKey()
        val plaintext = "super-secret-device-token".toByteArray(Charsets.UTF_8)

        val ciphertext = cipher.encrypt(plaintext, defaultAad)

        assertNotEquals(String(plaintext, Charsets.ISO_8859_1), String(ciphertext, Charsets.ISO_8859_1))
        assertFalse(
            "ciphertext leaked the plaintext token",
            String(ciphertext, Charsets.ISO_8859_1).contains(String(plaintext, Charsets.ISO_8859_1)),
        )
    }

    @Test
    fun `encrypting the same plaintext twice produces different ciphertext (fresh IV per call)`() {
        val cipher = cipherWithFixedKey()
        val plaintext = "device-token-abc123".toByteArray(Charsets.UTF_8)

        val first = cipher.encrypt(plaintext, defaultAad)
        val second = cipher.encrypt(plaintext, defaultAad)

        assertNotEquals(first.toList(), second.toList())
        assertArrayEquals(plaintext, cipher.decrypt(first, defaultAad))
        assertArrayEquals(plaintext, cipher.decrypt(second, defaultAad))
    }

    @Test
    fun `decrypting a tampered payload throws instead of returning garbage`() {
        val cipher = cipherWithFixedKey()
        val ciphertext = cipher.encrypt("device-token-abc123".toByteArray(Charsets.UTF_8), defaultAad)
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1] + 1).toByte()

        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(ciphertext, defaultAad) }
    }

    @Test
    fun `decrypting with the wrong key throws`() {
        val ciphertext = cipherWithFixedKey(softwareAesKey()).encrypt("device-token-abc123".toByteArray(Charsets.UTF_8), defaultAad)
        val otherKeyCipher = cipherWithFixedKey(softwareAesKey())

        assertThrows(GeneralSecurityException::class.java) { otherKeyCipher.decrypt(ciphertext, defaultAad) }
    }

    @Test
    fun `decrypting a too-short payload throws`() {
        val cipher = cipherWithFixedKey()

        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(ByteArray(4), defaultAad) }
    }

    @Test
    fun `decrypting with mismatched associated data throws (AAD binding)`() {
        val cipher = cipherWithFixedKey()
        val ciphertext = cipher.encrypt("device-token-abc123".toByteArray(Charsets.UTF_8), defaultAad)

        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(ciphertext, "https://attacker.example.com/".toByteArray(Charsets.UTF_8))
        }
    }
}
