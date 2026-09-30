package dev.hho.android.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.ConflictOrigin
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ConflictLogTwoDeviceTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private val server = MockWebServer()
    private lateinit var db: HhoDatabase
    private lateinit var outbox: OutboxRepository
    private var pushed = false
    private var pushedMutationId: String? = null
    private val paths = mutableListOf<String>()

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        outbox = OutboxRepository(db)
        server.dispatcher = object : Dispatcher() {
            @Synchronized
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                paths += path.substringBefore('?')
                return when {
                    path.contains("/sync/push") -> {
                        val mutationId = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                            .getValue("mutations").let { (it as kotlinx.serialization.json.JsonArray).single() }
                            .jsonObject.getValue("mutation_id").jsonPrimitive.content
                        pushed = true
                        pushedMutationId = mutationId
                        json(
                            """{"applied":[],"skipped":[],"conflicts":[{"mutation_id":"$mutationId","entity_type":"item","entity_id":"x","field_name":"name"}],"new_watermark":7}""",
                        )
                    }
                    path.contains("/sync/pull") ->
                        json(page(if (pushed) change("A-name", 2) else change("Original", 1), if (pushed) 8 else 5))
                    path.contains("/sync/conflicts") && !pushed -> json("""{"conflicts":[],"next_cursor":null}""")
                    path.contains("/sync/conflicts") ->
                        json(
                            """{"conflicts":[{"id":"log-1","mutation_id":"$pushedMutationId","entity_type":"item","entity_id":"x","field_name":"name","server_value":"\"A-name\"","losing_client_value":"\"B-name\"","detected_at":4242}],"next_cursor":null}""",
                        )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    @Test
    fun sameFieldConflict_appearsInConflictRecordWithBothValues_afterOneRun() = runBlocking {
        val engine = testSyncEngine(apiClient(), db, outbox, withConflictLog = true)
        assertTrue(engine.sync("device-b").isSuccess)
        assertTrue(db.conflictRecordDao().getAll().isEmpty())

        val base = checkNotNull(db.itemDao().getById("x")?.version)
        val queued = outbox.enqueue(LocalMutation("item", "x", "upsert", base, buildJsonObject { put("name", JsonPrimitive("B-name")) }))
        paths.clear()

        val result = engine.sync("device-b")

        assertEquals(SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.ConflictReconcile), result.getOrNull())
        assertEquals(listOf("/sync/push", "/sync/pull", "/sync/conflicts"), paths.map { "/sync" + it.substringAfter("/sync") })
        val row = db.conflictRecordDao().getAll().single()
        assertEquals(queued.mutationId, row.mutationId)
        assertEquals("x", row.entityId)
        assertEquals("name", row.fieldName)
        assertEquals("\"B-name\"", row.losingValueJson)
        assertEquals("\"A-name\"", row.serverValueJson)
        assertEquals(ConflictOrigin.SERVER_LOG, row.origin)
        assertEquals("A-name", db.itemDao().getById("x")?.name)
    }

    private fun json(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun change(name: String, version: Long) =
        """{"entity_type":"item","id":"x","group_change_seq":$version,"data":{"id":"x","name":"$name","quantity":0,"created_at":1,"updated_at":1,"version":$version}}"""

    private fun page(changes: String, next: Long) =
        """{"changes":[$changes],"tombstones":[],"next_watermark":$next,"has_more":false}"""

    private fun apiClient(): HhoApiClient {
        val file = File.createTempFile("hho-two-device", ".preferences_pb").also { it.deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() } }
        val cv = ClientVersionInterceptor()
        val provider = object : DeviceTokenProvider {
            override suspend fun tokenFor(requestUrl: HttpUrl): String? = "tok-secret"

            override suspend fun invalidate(requestUrl: HttpUrl, rejectedToken: String) = Unit
        }
        val http = NetworkModule.provideOkHttpClient(BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore)), AuthHeaderInterceptor(provider), cv)
        return HhoApiClient(http, NetworkModule.provideProbeOkHttpClient(http, cv))
    }
}
