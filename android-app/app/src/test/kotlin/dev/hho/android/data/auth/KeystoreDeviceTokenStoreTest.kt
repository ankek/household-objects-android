package dev.hho.android.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeystoreDeviceTokenStoreTest {

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("device-token-store-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun softwareAesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun store(
        dataStore: DataStore<Preferences> = tempDataStore(),
        key: SecretKey = softwareAesKey(),
    ): KeystoreDeviceTokenStore = KeystoreDeviceTokenStore(dataStore, AesGcmDeviceTokenCipher(secretKey = { key }))

    @Test
    fun `load returns null when nothing has ever been saved`() =
        runBlocking {
            assertNull(store().load())
        }

    @Test
    fun `save then load round-trips the token and its instance url`() =
        runBlocking {
            val tokenStore = store()

            tokenStore.save(StoredToken(token = "device-token-xyz", instanceUrl = "https://hho.example.com/"))
            val loaded = tokenStore.load()

            assertEquals(StoredToken(token = "device-token-xyz", instanceUrl = "https://hho.example.com/"), loaded)
        }

    @Test
    fun `clear removes the stored token`() =
        runBlocking {
            val tokenStore = store()
            tokenStore.save(StoredToken(token = "device-token-xyz", instanceUrl = "https://hho.example.com/"))

            tokenStore.clear()

            assertNull(tokenStore.load())
        }

    @Test
    fun `save overwrites whatever was stored before`() =
        runBlocking {
            val tokenStore = store()
            tokenStore.save(StoredToken(token = "first-token", instanceUrl = "https://a.example.com/"))

            tokenStore.save(StoredToken(token = "second-token", instanceUrl = "https://b.example.com/"))

            assertEquals(StoredToken(token = "second-token", instanceUrl = "https://b.example.com/"), tokenStore.load())
        }

    @Test
    fun `a token saved with one key cannot be decrypted with another`() =
        runBlocking {
            val dataStore = tempDataStore()
            store(dataStore, key = softwareAesKey()).save(
                StoredToken(token = "device-token-xyz", instanceUrl = "https://hho.example.com/"),
            )

            val loadedWithWrongKey = store(dataStore, key = softwareAesKey()).load()

            assertNull(loadedWithWrongKey)
        }

    @Test
    fun `editing the persisted instance url after save invalidates the ciphertext (AAD binding)`() =
        runBlocking {
            val dataStore = tempDataStore()
            val tokenStore = store(dataStore)
            tokenStore.save(StoredToken(token = "device-token-xyz", instanceUrl = "https://a.example.com/"))

            dataStore.edit { it[stringPreferencesKey("device_token_instance_url")] = "https://attacker.example.com/" }

            assertNull(tokenStore.load())
        }

    @Test
    fun `the persisted preference value never contains the plaintext token`() =
        runBlocking {
            val dataStore = tempDataStore()
            val tokenStore = store(dataStore)
            val secretToken = "super-secret-device-token-value"

            tokenStore.save(StoredToken(token = secretToken, instanceUrl = "https://hho.example.com/"))

            val rawPrefs = dataStore.data.first()
            val ciphertextB64 = rawPrefs.asMap().values.joinToString { it.toString() }
            assertFalse(ciphertextB64.contains(secretToken))
        }
}
