package dev.hho.android.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.ConflictOrigin
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class OutboxSyncerTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private lateinit var db: HhoDatabase
    private lateinit var repo: OutboxRepository
    private lateinit var syncer: OutboxSyncer
    private val server = MockWebServer()
    private var now = 5_000L

    private val issuedClockValues = mutableListOf<Long>()
    private fun tick(): Long = now++.also { issuedClockValues += it }
    private val requests = mutableListOf<JsonObject>()

    private var responder: (List<JsonObject>) -> MockResponse = { applyAll(it, 10) }

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        repo = OutboxRepository(db, ids = UuidV7Generator(clock = { 1_700_000_000_000L }, random = java.util.Random(42)), clock = ::tick)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                requests += body
                return responder(body["mutations"]!!.jsonArray.map { it.jsonObject })
            }
        }
        syncer = OutboxSyncer(db, apiClient(), repo, clock = ::tick)
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun apiClient(): HhoApiClient {
        val file = File.createTempFile("hho-syncer-test", ".preferences_pb").also { it.deleteOnExit() }
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

    private fun ids(muts: List<JsonObject>) = muts.map { it["mutation_id"]!!.jsonPrimitive.content }

    private fun applyAll(muts: List<JsonObject>, version: Long) = json(
        200,
        """{"applied":[${muts.joinToString(",") { m ->
            """{"mutation_id":"${m["mutation_id"]!!.jsonPrimitive.content}","entity_type":"${m["entity_type"]!!.jsonPrimitive.content}",""" +
                """"entity_id":"${m["entity_id"]!!.jsonPrimitive.content}","version":$version}"""
        }}],"skipped":[],"conflicts":[],"new_watermark":99}""",
    )

    private fun json(status: Int, body: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private fun problem(status: Int, detail: String) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"t","status":$status,"detail":${JsonPrimitive(detail)}}""")

    private fun structural(id: String, index: Int = 0) = problem(400, """mutations[$index] (mutation_id="$id"): bad thing""")

    private fun m(type: String, id: String, fields: String, base: Long = 1) =
        LocalMutation(type, id, "upsert", base, Json.parseToJsonElement(fields).jsonObject)

    private suspend fun enqueueItem(id: String, name: String = "N", base: Long = 1) = repo.enqueue(m("item", id, """{"name":"$name"}""", base))

    private suspend fun rows() = db.outboxDao().getAllOrdered()

    private suspend fun putItem(id: String, version: Long? = 1) =
        db.itemDao().upsert(ItemEntity(id, 0, "old", null, null, 0, null, null, null, version))

    @Test
    fun applied_acksStampsMirrorVersion_releasesHeld_andDrains() = runBlocking {
        putItem("a", 1)
        val first = enqueueItem("a", "first")
        val held = enqueueItem("a", "second")
        assertTrue(held.coalesced)
        repo.markInFlight(listOf(first.mutationId))
        val follower = enqueueItem("a", "third")
        assertEquals(OutboxState.HELD, follower.state)
        repo.resetInFlightToPending()
        responder = { applyAll(it, 7) }

        val out = syncer.push("dev-1")

        assertEquals(PushPhaseOutcome.Drained, out)
        assertEquals(7L, db.itemDao().getById("a")!!.version)
        assertEquals(2, requests.size)
        assertEquals(7L, requests[1]["mutations"]!!.jsonArray.single().jsonObject["base_version"]!!.jsonPrimitive.content.toLong())
        assertTrue(rows().isEmpty())
        assertEquals("dev-1", requests[0]["device_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun skipped_acksButKeepsFollowersHeld() = runBlocking {
        val first = enqueueItem("a", "first")
        repo.markInFlight(listOf(first.mutationId))
        val follower = enqueueItem("a", "second")
        repo.resetInFlightToPending()
        responder = { json(200, """{"applied":[],"skipped":[{"mutation_id":"${first.mutationId}"}],"conflicts":[],"new_watermark":1}""") }

        val out = syncer.push("d")

        assertEquals(PushPhaseOutcome.Drained, out)
        val left = rows().single()
        assertEquals(follower.mutationId, left.mutationId)
        assertEquals(OutboxState.HELD, left.state)
        assertEquals(1, requests.size)
    }

    @Test
    fun conflicts_writeRecordPerField_deleteRow_needsReconcile() = runBlocking {
        val e = repo.enqueue(m("item", "a", """{"name":"Mine","description":"D","location_id":"L"}"""))
        responder = {
            json(
                200,
                """{"applied":[],"skipped":[],"conflicts":[""" +
                    """{"mutation_id":"${e.mutationId}","entity_type":"item","entity_id":"a","field_name":"name"},""" +
                    """{"mutation_id":"${e.mutationId}","entity_type":"item","entity_id":"a","field_name":"description"}],"new_watermark":1}""",
            )
        }

        val out = syncer.push("d")

        assertEquals(PushPhaseOutcome.NeedsReconcile, out)
        assertTrue(rows().isEmpty())
        val recs = db.conflictRecordDao().getAll().sortedBy { it.fieldName }
        assertEquals(listOf("description", "name"), recs.map { it.fieldName })
        assertEquals(listOf("\"D\"", "\"Mine\""), recs.map { it.losingValueJson })
        assertTrue(recs.all { it.origin == ConflictOrigin.LOCAL_PUSH && it.serverValueJson == null && it.mutationId == e.mutationId })
    }

    @Test
    fun entitySentinelConflict_keepsWholeFields() = runBlocking {
        val e = repo.enqueue(m("item", "gone", """{"name":"X"}"""))
        responder = { json(200, """{"applied":[],"skipped":[],"conflicts":[{"mutation_id":"${e.mutationId}","entity_type":"item","entity_id":"gone","field_name":"_entity"}],"new_watermark":1}""") }

        assertEquals(PushPhaseOutcome.NeedsReconcile, syncer.push("d"))
        val rec = db.conflictRecordDao().getAll().single()
        assertEquals("_entity", rec.fieldName)
        assertEquals("""{"name":"X"}""", rec.losingValueJson)
    }

    @Test
    fun structuralRejection_quarantinesOffender_resendsRest() = runBlocking {
        val a = enqueueItem("a")
        val b = enqueueItem("b")
        val c = enqueueItem("c")
        responder = { muts ->
            if (ids(muts).contains(b.mutationId)) structural(b.mutationId, ids(muts).indexOf(b.mutationId)) else applyAll(muts, 3)
        }

        val out = syncer.push("d")

        assertEquals(PushPhaseOutcome.Drained, out)
        assertEquals(2, requests.size)
        assertEquals(listOf(a.mutationId, c.mutationId), ids(requests[1]["mutations"]!!.jsonArray.map { it.jsonObject }))
        val left = rows().single()
        assertEquals(b.mutationId, left.mutationId)
        assertEquals(OutboxState.FAILED, left.state)
        assertTrue(left.lastError!!.contains("bad thing"))
    }

    @Test
    fun unparseableStructural_isolatesBySingletons_findsOffender() = runBlocking {
        val a = enqueueItem("a")
        val b = enqueueItem("b")
        val c = enqueueItem("c")
        responder = { muts ->
            when {
                ids(muts).contains(b.mutationId) && muts.size > 1 -> problem(400, "mutations[?] garbled")
                ids(muts) == listOf(b.mutationId) -> problem(400, "mutations[0] garbled")
                else -> applyAll(muts, 3)
            }
        }

        val out = syncer.push("d")

        assertEquals(PushPhaseOutcome.Drained, out)
        val left = rows().single()
        assertEquals(b.mutationId, left.mutationId)
        assertEquals(OutboxState.FAILED, left.state)
        assertEquals(listOf(3, 1, 1, 1), requests.map { it["mutations"]!!.jsonArray.size })
    }

    @Test
    fun envelope400_isUnattributable_failsAndKeepsAllRows() = runBlocking {
        enqueueItem("a")
        enqueueItem("b")
        responder = { problem(400, "device_id is required") }

        val out = syncer.push("d")

        assertTrue(out is PushPhaseOutcome.Failed && out.cause is ApiError.Validation)
        assertEquals(1, requests.size)
        assertEquals(listOf(OutboxState.PENDING, OutboxState.PENDING), rows().map { it.state })
        assertEquals(listOf(1, 1), rows().map { it.attemptCount })
    }

    @Test
    fun unauthorized_blocksAuth_recordsAttempt_rowsStayPending() = runBlocking {
        val a = enqueueItem("a")
        responder = { problem(401, "no") }

        val out = syncer.push("d")

        assertEquals(PushPhaseOutcome.Blocked(BlockedReason.AUTH), out)
        val row = rows().single()
        assertEquals(a.mutationId, row.mutationId)
        assertEquals(OutboxState.PENDING, row.state)
        assertEquals(1, row.attemptCount)
        assertTrue(row.lastAttemptAt != null)
    }

    @Test
    fun upgradeRequired_blocks() = runBlocking {
        enqueueItem("a")
        responder = {
            MockResponse().setResponseCode(426).setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type":"about:blank","title":"t","status":426,"detail":"old","minimum_version":"9.9.9"}""")
        }

        val out = syncer.push("d")

        assertTrue(out is PushPhaseOutcome.Blocked && out.reason == BlockedReason.UPGRADE_REQUIRED)
        assertEquals(1, rows().single().attemptCount)
    }

    @Test
    fun serverError_failsThenRetrySucceeds() = runBlocking {
        val a = enqueueItem("a")
        responder = { problem(503, "down") }

        val first = syncer.push("d")

        assertTrue(first is PushPhaseOutcome.Failed && first.cause is ApiError.Server)
        val row = rows().single()
        assertEquals(OutboxState.PENDING, row.state)
        assertEquals(1, row.attemptCount)
        assertEquals(a.mutationId, row.mutationId)

        responder = { applyAll(it, 2) }
        assertEquals(PushPhaseOutcome.Drained, syncer.push("d"))
        assertTrue(rows().isEmpty())
        assertEquals(ids(requests[0]["mutations"]!!.jsonArray.map { it.jsonObject }), ids(requests[1]["mutations"]!!.jsonArray.map { it.jsonObject }))
    }

    @Test
    fun transportFailure_failsWithNetwork() = runBlocking {
        enqueueItem("a")
        server.shutdown()

        val out = syncer.push("d")

        assertTrue(out is PushPhaseOutcome.Failed && out.cause is ApiError.Network)
        assertEquals(1, rows().single().attemptCount)
    }

    @Test
    fun multiBatchDrain_60Rows_inFifoOrder() = runBlocking {
        val all = (1..60).map { enqueueItem("e$it").mutationId }

        val out = syncer.push("d")

        assertEquals(PushPhaseOutcome.Drained, out)
        assertEquals(listOf(50, 10), requests.map { it["mutations"]!!.jsonArray.size })
        assertEquals(all, requests.flatMap { r -> ids(r["mutations"]!!.jsonArray.map { it.jsonObject }) })
        assertTrue(rows().isEmpty())
    }

    @Test
    fun conflictThenTransientFailure_reportsReconcilePending() = runBlocking {
        val a = enqueueItem("a")
        enqueueItem("b")
        var call = 0
        responder = { muts ->
            if (call++ == 0) {
                json(200, """{"applied":[],"skipped":[],"conflicts":[{"mutation_id":"${a.mutationId}","entity_type":"item","entity_id":"a","field_name":"name"}],"new_watermark":1}""")
            } else {
                problem(503, "x")
            }
        }
        val out = syncer.push("d")
        assertTrue(out is PushPhaseOutcome.Failed && out.reconcilePending)
    }

    @Test
    fun noRequestBodyCarriesAClientTimestamp() = runBlocking {
        putItem("a")
        enqueueItem("a")
        repo.enqueue(m("stock_adjustment", "s1", """{"item_id":"a","delta":2}""", 0))
        syncer.push("d")

        assertTrue(requests.isNotEmpty())
        assertTrue("clock was never consulted; test proves nothing", issuedClockValues.isNotEmpty())
        val timestampKey = Regex("(_at|At)$|time|date", RegexOption.IGNORE_CASE)
        val clockNumbers = issuedClockValues.map { it.toString() }.toSet()
        fun walk(e: JsonElement, path: String) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) ->
                    assertFalse("timestamp-like key at $path.$k", timestampKey.containsMatchIn(k))
                    walk(v, "$path.$k")
                }
                is JsonArray -> e.forEachIndexed { i, v -> walk(v, "$path[$i]") }
                is JsonPrimitive -> assertFalse("clock value ${e.content} at $path", e.content in clockNumbers)
            }
        }
        requests.forEachIndexed { i, body -> walk(body, "request[$i]") }
    }

    @Test
    fun emptyOutbox_drainsWithoutRequest() = runBlocking {
        assertEquals(PushPhaseOutcome.Drained, syncer.push("d"))
        assertTrue(requests.isEmpty())
        assertNull(server.takeRequest(10, java.util.concurrent.TimeUnit.MILLISECONDS))
    }
}
