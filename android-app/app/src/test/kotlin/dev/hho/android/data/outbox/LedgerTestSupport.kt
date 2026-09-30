package dev.hho.android.data.outbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.mockwebserver.MockWebServer
import java.io.File

internal fun ledgerApiClient(server: MockWebServer, extraInterceptors: List<Interceptor> = emptyList()): HhoApiClient {
    val prefs = File.createTempFile("ledger-api", ".preferences_pb").also { it.deleteOnExit() }
    val dataStore = PreferenceDataStoreFactory.create(produceFile = { prefs })
    runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() } }
    val cv = ClientVersionInterceptor()
    val provider = object : DeviceTokenProvider {
        override suspend fun tokenFor(requestUrl: HttpUrl): String? = "tok"

        override suspend fun invalidate(requestUrl: HttpUrl, rejectedToken: String) = Unit
    }
    val base = NetworkModule.provideOkHttpClient(BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore)), AuthHeaderInterceptor(provider), cv)
    val http = base.newBuilder().apply { extraInterceptors.forEach { addInterceptor(it) } }.build()
    return HhoApiClient(http, NetworkModule.provideProbeOkHttpClient(http, cv))
}
