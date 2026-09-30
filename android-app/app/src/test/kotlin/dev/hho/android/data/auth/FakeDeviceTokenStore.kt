package dev.hho.android.data.auth

class FakeDeviceTokenStore(initial: StoredToken? = null) : DeviceTokenStore {
    private var stored: StoredToken? = initial

    override suspend fun load(): StoredToken? = stored

    override suspend fun save(token: StoredToken) {
        stored = token
    }

    override suspend fun clear() {
        stored = null
    }
}
