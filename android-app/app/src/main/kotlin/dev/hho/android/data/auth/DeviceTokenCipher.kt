package dev.hho.android.data.auth

interface DeviceTokenCipher {
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray

    fun decrypt(
        payload: ByteArray,
        associatedData: ByteArray,
    ): ByteArray
}
