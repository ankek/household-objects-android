package dev.hho.android.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hho.android.di.DeviceTokenDataStore
import kotlinx.coroutines.flow.first
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val DEVICE_TOKEN_CIPHERTEXT_KEY = stringPreferencesKey("device_token_ciphertext_b64")
private val DEVICE_TOKEN_INSTANCE_URL_KEY = stringPreferencesKey("device_token_instance_url")

@Singleton
class KeystoreDeviceTokenStore
    @Inject
    constructor(
        @DeviceTokenDataStore private val dataStore: DataStore<Preferences>,
        private val cipher: DeviceTokenCipher,
    ) : DeviceTokenStore {
        override suspend fun load(): StoredToken? {
            val prefs = dataStore.data.first()
            val ciphertextB64 = prefs[DEVICE_TOKEN_CIPHERTEXT_KEY] ?: return null
            val instanceUrl = prefs[DEVICE_TOKEN_INSTANCE_URL_KEY] ?: return null
            val plaintext =
                runCatching {
                    cipher.decrypt(
                        payload = Base64.getDecoder().decode(ciphertextB64),
                        associatedData = instanceUrl.toByteArray(Charsets.UTF_8),
                    )
                }.getOrNull() ?: return null
            return StoredToken(token = String(plaintext, Charsets.UTF_8), instanceUrl = instanceUrl)
        }

        override suspend fun save(token: StoredToken) {
            val ciphertext =
                cipher.encrypt(
                    plaintext = token.token.toByteArray(Charsets.UTF_8),
                    associatedData = token.instanceUrl.toByteArray(Charsets.UTF_8),
                )
            dataStore.edit { prefs ->
                prefs[DEVICE_TOKEN_CIPHERTEXT_KEY] = Base64.getEncoder().encodeToString(ciphertext)
                prefs[DEVICE_TOKEN_INSTANCE_URL_KEY] = token.instanceUrl
            }
        }

        override suspend fun clear() {
            dataStore.edit { prefs ->
                prefs.remove(DEVICE_TOKEN_CIPHERTEXT_KEY)
                prefs.remove(DEVICE_TOKEN_INSTANCE_URL_KEY)
            }
        }
    }
