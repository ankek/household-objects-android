package dev.hho.android.data.outbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OutboxWireBodyTest {
    private val server = MockWebServer()
    private lateinit var db: HhoDatabase

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun apiClient(): HhoApiClient {
        val file = File.createTempFile("hho-outbox-wire", ".preferences_pb").also { it.deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() } }
        val cv = ClientVersionInterceptor()
        val provider = object : DeviceTokenProvider {
            override suspend fun tokenFor(requestUrl: HttpUrl): String? = null

            override suspend fun invalidate(requestUrl: HttpUrl, rejectedToken: String) = Unit
        }
        val http = NetworkModule.provideOkHttpClient(BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore)), AuthHeaderInterceptor(provider), cv)
        return HhoApiClient(http, NetworkModule.provideProbeOkHttpClient(http, cv))
    }

    @Test
    fun pushBodyBuiltFromRows_hasNoTimestampKeys_andOnlyTheWireKeys() = runBlocking {
        val repo = OutboxRepository(db, clock = { 1_788_000_000_000L })
        val fields = Json.parseToJsonElement("""{"name":"Drill","location_id":"L1"}""").jsonObject
        repo.enqueue(LocalMutation("item", "i1", "upsert", 0, fields))
        repo.enqueue(LocalMutation("stock_adjustment", "s1", "upsert", 0, Json.parseToJsonElement("""{"item_id":"i1","delta":2}""").jsonObject))
        val batch = repo.nextBatch()
        assertTrue(batch.all { it.createdAt == 1_788_000_000_000L })

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"applied":[],"skipped":[],"conflicts":[],"new_watermark":1}"""))
        apiClient().syncPush("device-1", batch.map { it.toPushMutation() })

        val body = server.takeRequest().body.readUtf8()
        val mutations = Json.parseToJsonElement(body).jsonObject["mutations"]!!.jsonArray
        assertEquals(2, mutations.size)
        mutations.forEach { mm ->
            assertEquals(setOf("mutation_id", "entity_type", "entity_id", "base_version", "fields", "op"), mm.jsonObject.keys)
        }
        assertEquals(setOf("device_id", "mutations"), Json.parseToJsonElement(body).jsonObject.keys)
        assertFalse(body.contains("1788000000000"))
        assertFalse(Regex("""(?i)"[a-z_]*(_at|timestamp|time|date)"\s*:""").containsMatchIn(body))
        assertTrue(mutations.all { (it.jsonObject["fields"] as JsonObject).keys.none { k -> k.endsWith("_at") } })
    }
}
