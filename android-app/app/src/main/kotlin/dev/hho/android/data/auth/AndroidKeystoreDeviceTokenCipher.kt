package dev.hho.android.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "dev.hho.android.device_token_key"
private const val KEY_SIZE_BITS = 256

@Singleton
class AndroidKeystoreDeviceTokenCipher
    @Inject
    constructor() : DeviceTokenCipher {
        private val delegate = AesGcmDeviceTokenCipher(secretKey = ::getOrCreateSecretKey)

        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray = delegate.encrypt(plaintext, associatedData)

        override fun decrypt(
            payload: ByteArray,
            associatedData: ByteArray,
        ): ByteArray = delegate.decrypt(payload, associatedData)

        private fun getOrCreateSecretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
            val spec =
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
    }
