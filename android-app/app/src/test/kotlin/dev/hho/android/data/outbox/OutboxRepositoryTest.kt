package dev.hho.android.data.outbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.inMemoryHhoDatabase
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OutboxRepositoryTest {
    private lateinit var db: HhoDatabase
    private lateinit var repo: OutboxRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        repo = OutboxRepository(db, clock = { now++ })
    }

    @After
    fun tearDown() = db.close()

    private fun m(type: String, id: String, fields: String, op: String = "upsert", base: Long = 1) =
        LocalMutation(type, id, op, base, Json.parseToJsonElement(fields).jsonObject)

    private suspend fun rows() = db.outboxDao().getAllOrdered()

    private suspend fun row(mutationId: String) = db.outboxDao().getByMutationId(mutationId)!!

    private fun fieldsOf(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun enqueue_assignsFifoSeq_freshUuidV7Ids_pending() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        val b = repo.enqueue(m("item", "b", """{"name":"B"}"""))
        assertTrue(a.seq < b.seq)
        assertNotEquals(a.mutationId, b.mutationId)
        assertEquals('7', a.mutationId[14])
        assertEquals(listOf(OutboxState.PENDING, OutboxState.PENDING), rows().map { it.state })
    }

    @Test
    fun updatesToSamePendingEntity_coalesce_laterKeyWins_keepsSeqBaseAndMutationId() = runTest {
        val first = repo.enqueue(m("item", "a", """{"name":"A","description":"d"}""", base = 3))
        repo.enqueue(m("item", "b", """{"name":"B"}"""))
        val second = repo.enqueue(m("item", "a", """{"name":"A2","location_id":"L"}""", base = 9))
        assertTrue(second.coalesced)
        assertEquals(first.mutationId, second.mutationId)
        assertEquals(first.seq, second.seq)
        val all = rows()
        assertEquals(2, all.size)
        val a = all.first { it.entityId == "a" }
        assertEquals(3L, a.baseVersion)
        assertEquals(first.seq, a.seq)
        assertEquals(fieldsOf("""{"name":"A2","description":"d","location_id":"L"}"""), fieldsOf(a.fieldsJson))
    }

    @Test
    fun fiftyEditsOfOneItem_areOneMutation() = runTest {
        repeat(50) { repo.enqueue(m("item", "a", """{"name":"n$it"}""")) }
        assertEquals(1, rows().size)
        assertEquals(fieldsOf("""{"name":"n49"}"""), fieldsOf(rows().single().fieldsJson))
    }

    @Test
    fun createThenUpdate_isOneCreate() = runTest {
        val c = repo.enqueue(m("item", "n", """{"name":"New"}""", base = 0))
        repo.enqueue(m("item", "n", """{"description":"x"}""", base = 0))
        val only = rows().single()
        assertEquals(c.mutationId, only.mutationId)
        assertEquals(0L, only.baseVersion)
        assertEquals(fieldsOf("""{"name":"New","description":"x"}"""), fieldsOf(only.fieldsJson))
    }

    @Test
    fun stockAdjustmentAndItemLabel_neverCoalesce() = runTest {
        repo.enqueue(m("stock_adjustment", "s1", """{"delta":1}""", base = 0))
        repo.enqueue(m("stock_adjustment", "s1", """{"delta":2}""", base = 0))
        repo.enqueue(m("item_label", "e", """{"label_id":"x"}"""))
        repo.enqueue(m("item_label", "e", """{"label_id":"y"}"""))
        assertEquals(4, rows().size)
    }

    @Test
    fun deleteOpNeverCoalesces() = runTest {
        repo.enqueue(m("item", "a", """{"name":"A"}"""))
        val d = repo.enqueue(m("item", "a", "{}", op = "delete"))
        assertFalse(d.coalesced)
        assertEquals(2, rows().size)
    }

    @Test
    fun enqueueWhileInFlight_isHeld_andNextBatchSkipsIt() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.markInFlight(listOf(a.mutationId))
        val held = repo.enqueue(m("item", "a", """{"name":"A2"}""", base = 1))
        assertEquals(OutboxState.HELD, held.state)
        assertFalse(held.coalesced)
        assertTrue(repo.nextBatch().isEmpty())
    }

    @Test
    fun heldRowsCoalesceIntoOneHeldTail() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.markInFlight(listOf(a.mutationId))
        val h1 = repo.enqueue(m("item", "a", """{"name":"A2"}"""))
        val h2 = repo.enqueue(m("item", "a", """{"description":"d"}"""))
        assertTrue(h2.coalesced)
        assertEquals(h1.mutationId, h2.mutationId)
        assertEquals(2, rows().size)
    }

    @Test
    fun createUpdateInFlightUpdateAck_rebasesHeldOntoAppliedVersion() = runTest {
        val create = repo.enqueue(m("item", "n", """{"name":"New"}""", base = 0))
        repo.enqueue(m("item", "n", """{"description":"d"}""", base = 0))
        assertEquals(1, rows().size)
        repo.markInFlight(listOf(create.mutationId))
        val held = repo.enqueue(m("item", "n", """{"name":"Newer"}""", base = 0))
        assertEquals(OutboxState.HELD, held.state)

        repo.ack(create.mutationId, version = 1)

        val left = rows().single()
        assertEquals(held.mutationId, left.mutationId)
        assertEquals(OutboxState.PENDING, left.state)
        assertEquals(1L, left.baseVersion)
        assertEquals(listOf(held.mutationId), repo.nextBatch().map { it.mutationId })
    }

    @Test
    fun ack_deletesRow_andIgnoresUnknownId() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.markInFlight(listOf(a.mutationId))
        repo.ack("nope", 5)
        assertEquals(1, rows().size)
        repo.ack(a.mutationId, 5)
        assertTrue(rows().isEmpty())
    }

    @Test
    fun ackSkipped_deletes_followersStayHeldUntilReleaseHeld() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.markInFlight(listOf(a.mutationId))
        val h = repo.enqueue(m("item", "a", """{"name":"A2"}"""))
        repo.ackSkipped(a.mutationId)
        assertEquals(OutboxState.HELD, row(h.mutationId).state)
        assertTrue(repo.nextBatch().isEmpty())

        repo.releaseHeld { _, _ -> null }
        assertEquals(OutboxState.HELD, row(h.mutationId).state)

        repo.releaseHeld { t, id -> if (t == "item" && id == "a") 7L else null }
        val released = row(h.mutationId)
        assertEquals(OutboxState.PENDING, released.state)
        assertEquals(7L, released.baseVersion)
    }

    @Test
    fun releaseHeld_doesNothingWhileHeadStillInFlight() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.markInFlight(listOf(a.mutationId))
        val h = repo.enqueue(m("item", "a", """{"name":"A2"}"""))
        repo.releaseHeld { _, _ -> 9L }
        assertEquals(OutboxState.HELD, row(h.mutationId).state)
    }

    @Test
    fun quarantine_failsRow_neverBatched_keepsError_releasesFollowerWithOwnBase() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}""", base = 4))
        val other = repo.enqueue(m("item", "b", """{"name":"B"}"""))
        repo.markInFlight(listOf(a.mutationId))
        val h = repo.enqueue(m("item", "a", """{"name":"A2"}""", base = 4))
        repo.quarantine(a.mutationId, "mutations[0]: bad field")

        val failed = row(a.mutationId)
        assertEquals(OutboxState.FAILED, failed.state)
        assertEquals("mutations[0]: bad field", failed.lastError)
        assertEquals(setOf(h.mutationId, other.mutationId), repo.nextBatch().map { it.mutationId }.toSet())
        assertEquals(4L, row(h.mutationId).baseVersion)
        assertEquals(OutboxState.PENDING, repo.enqueue(m("item", "zz", """{"name":"Z"}""")).state)
        repo.resetInFlightToPending()
        assertEquals(OutboxState.FAILED, row(a.mutationId).state)
    }

    @Test
    fun processDeathReset_returnsInFlightToPending_keepsHeldHeld_andRowStaysPossiblySent() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.markInFlight(listOf(a.mutationId))
        val h = repo.enqueue(m("item", "a", """{"name":"A2"}"""))
        assertEquals(1, repo.resetInFlightToPending())
        assertEquals(OutboxState.PENDING, row(a.mutationId).state)
        assertEquals(OutboxState.HELD, row(h.mutationId).state)
        assertEquals(listOf(a.mutationId), repo.nextBatch().map { it.mutationId })

        val late = repo.enqueue(m("item", "a", """{"name":"A3"}"""))
        assertTrue(late.coalesced)
        assertEquals(h.mutationId, late.mutationId)
        assertEquals(fieldsOf("""{"name":"A"}"""), fieldsOf(row(a.mutationId).fieldsJson))
    }

    @Test
    fun editAfterResetWithNoHeldTail_isHeldBehindPossiblySentRow() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.markInFlight(listOf(a.mutationId))
        repo.resetInFlightToPending()
        val e = repo.enqueue(m("item", "a", """{"name":"A2"}"""))
        assertEquals(OutboxState.HELD, e.state)
        assertNotEquals(a.mutationId, e.mutationId)
        assertEquals(fieldsOf("""{"name":"A"}"""), fieldsOf(row(a.mutationId).fieldsJson))
    }

    @Test
    fun recordAttempt_incrementsCount_andStoresError() = runTest {
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.recordAttempt(a.mutationId, "503")
        repo.recordAttempt(a.mutationId, "timeout")
        val r = row(a.mutationId)
        assertEquals(2, r.attemptCount)
        assertEquals("timeout", r.lastError)
        assertTrue(r.lastAttemptAt != null)
    }

    @Test
    fun nextBatch_sixtyRows_twoBatches_seqOrder() = runTest {
        repeat(60) { repo.enqueue(m("item", "e$it", """{"name":"n$it"}""")) }
        val first = repo.nextBatch()
        assertEquals(50, first.size)
        assertEquals(first.map { it.seq }.sorted(), first.map { it.seq })
        repo.markInFlight(first.map { it.mutationId })
        val second = repo.nextBatch()
        assertEquals(10, second.size)
        assertTrue(second.first().seq > first.last().seq)
        first.forEach { repo.ack(it.mutationId, 1) }
        repo.markInFlight(second.map { it.mutationId })
        second.forEach { repo.ack(it.mutationId, 1) }
        assertTrue(rows().isEmpty())
    }

    @Test
    fun nextBatch_oneRowPerEntity_evenForNeverCoalesceTypes() = runTest {
        val l1 = repo.enqueue(m("item_label", "e", """{"label_id":"x"}"""))
        repo.enqueue(m("item_label", "e", """{"label_id":"y"}"""))
        repo.enqueue(m("item", "i", """{"name":"I"}"""))
        assertEquals(listOf(l1.mutationId), repo.nextBatch().map { it.mutationId }.take(1))
        assertEquals(listOf("e", "i"), repo.nextBatch().map { it.entityId })
    }

    @Test
    fun observePendingCount_countsLive_notFailed() = runTest {
        assertEquals(0, repo.observePendingCount().first())
        val a = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        repo.enqueue(m("item", "b", """{"name":"B"}"""))
        repo.markInFlight(listOf(a.mutationId))
        repo.enqueue(m("item", "a", """{"name":"A2"}"""))
        assertEquals(3, repo.observePendingCount().first())
        repo.quarantine(a.mutationId, "x")
        assertEquals(2, repo.observePendingCount().first())
    }

    @Test
    fun randomizedSequences_neverHaveTwoInFlightForOneEntity_norTwoUnackedInBatch() = runTest {
        val rnd = Random(20260930)
        repeat(30) { round ->
            rows().forEach { db.outboxDao().deleteBySeq(it.seq) }
            repeat(120) {
                val id = "e${rnd.nextInt(4)}"
                when (rnd.nextInt(7)) {
                    0, 1, 2 -> repo.enqueue(m(pick(rnd), id, """{"k${rnd.nextInt(3)}":"v$it"}""", base = rnd.nextLong(0, 5)))
                    3 -> {
                        val batch = repo.nextBatch(rnd.nextInt(1, 6))
                        assertEquals(batch.size, batch.map { it.entityType to it.entityId }.toSet().size)
                        repo.markInFlight(batch.map { it.mutationId })
                    }
                    4 -> rows().filter { it.state == OutboxState.IN_FLIGHT }.randomOrNull(rnd)?.let { repo.ack(it.mutationId, rnd.nextLong(1, 50)) }
                    5 -> if (rnd.nextInt(4) == 0) repo.resetInFlightToPending() else
                        rows().filter { it.state == OutboxState.IN_FLIGHT }.randomOrNull(rnd)?.let { repo.ackSkipped(it.mutationId) }
                    else -> if (rnd.nextBoolean()) {
                        rows().filter { it.state == OutboxState.IN_FLIGHT }.randomOrNull(rnd)?.let { repo.quarantine(it.mutationId, "e") }
                    } else {
                        repo.releaseHeld { _, _ -> 3L }
                    }
                }
                val live = rows().filter { it.state != OutboxState.FAILED }
                val inFlight = live.filter { it.state == OutboxState.IN_FLIGHT }.groupBy { it.entityType to it.entityId }
                assertTrue("round $round step $it", inFlight.values.all { g -> g.size == 1 })
                live.filter { it.state == OutboxState.PENDING }.groupBy { it.entityType to it.entityId }.forEach { (k, g) ->
                    if (k.first == "item" || k.first == "stock_adjustment_x") assertTrue("round $round step $it: two PENDING for $k", g.size <= 1)
                }
                val batchKeys = repo.nextBatch().map { it.entityType to it.entityId }
                assertTrue(batchKeys.none { it in inFlight.keys })
            }
        }
    }

    private fun pick(rnd: Random) = listOf("item", "item", "item_label", "stock_adjustment")[rnd.nextInt(4)]

    @Test
    fun releaseFollowers_quarantinesACorruptHeldFollowerAndReleasesTheHealthyOnes() = runTest {
        val head = repo.enqueue(m("item", "a", """{"name":"A"}""", base = 3))
        repo.markInFlight(listOf(head.mutationId))
        repo.enqueue(m("item", "a", """{"name":"A2"}""", base = 3))
        repo.enqueue(m("item", "a", """{"description":"d"}""", base = 3))
        val dao = db.outboxDao()
        dao.insert(
            dao.getByMutationId(head.mutationId)!!.copy(
                seq = 0, mutationId = "corrupt", state = OutboxState.HELD, fieldsJson = "not json", lastAttemptAt = null,
            ),
        )
        repo.enqueue(m("item", "b", """{"name":"B"}"""))

        repo.ack(head.mutationId, 4)

        val all = rows()
        val bad = all.single { it.mutationId == "corrupt" }
        assertEquals(OutboxState.FAILED, bad.state)
        assertTrue(bad.lastError!!.startsWith("Unreadable local edit: "))
        assertFalse(bad.lastError!!.contains("not json"))
        val healthy = all.single { it.entityId == "a" && it.mutationId != "corrupt" }
        assertEquals(OutboxState.PENDING, healthy.state)
        assertEquals(4L, healthy.baseVersion)
        assertEquals(fieldsOf("""{"name":"A2","description":"d"}"""), fieldsOf(healthy.fieldsJson))
    }

    private suspend fun insertCorruptTail(like: String, state: String): String {
        val dao = db.outboxDao()
        dao.insert(
            dao.getByMutationId(like)!!.copy(
                seq = 0, mutationId = "corrupt", state = state, fieldsJson = "{secret-raw not json", lastAttemptAt = null,
            ),
        )
        return "corrupt"
    }

    private fun assertQuarantinedSanitized(bad: dev.hho.android.data.room.OutboxMutationEntity) {
        assertEquals(OutboxState.FAILED, bad.state)
        assertTrue(bad.lastError!!.startsWith("Unreadable local edit: "))
        assertFalse(bad.lastError!!.contains("secret-raw"))
        assertFalse(bad.lastError!!.contains("{"))
    }

    @Test
    fun enqueue_nextToCorruptPendingTail_quarantinesItAndQueuesFreshPending() = runTest {
        val seed = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        val corrupt = insertCorruptTail(seed.mutationId, OutboxState.PENDING)
        db.outboxDao().deleteBySeq(seed.seq)

        val r = repo.enqueue(m("item", "a", """{"name":"A2"}"""))

        assertFalse(r.coalesced)
        assertEquals(OutboxState.PENDING, r.state)
        assertNotEquals(corrupt, r.mutationId)
        assertQuarantinedSanitized(row(corrupt))
        assertEquals(OutboxState.PENDING, row(r.mutationId).state)
        assertEquals(fieldsOf("""{"name":"A2"}"""), fieldsOf(row(r.mutationId).fieldsJson))
    }

    @Test
    fun enqueue_nextToCorruptHeldTailBehindInFlightHead_queuesFreshHeld() = runTest {
        val head = repo.enqueue(m("item", "a", """{"name":"A"}""", base = 3))
        repo.markInFlight(listOf(head.mutationId))
        val corrupt = insertCorruptTail(head.mutationId, OutboxState.HELD)

        val r = repo.enqueue(m("item", "a", """{"name":"A2"}""", base = 3))

        assertFalse(r.coalesced)
        assertEquals(OutboxState.HELD, r.state)
        assertQuarantinedSanitized(row(corrupt))
        assertEquals(OutboxState.IN_FLIGHT, row(head.mutationId).state)
        assertEquals(OutboxState.HELD, row(r.mutationId).state)
    }

    @Test
    fun enqueue_nextToCorruptHeldTailWithNoRemainingHead_queuesPendingNotHeld() = runTest {
        val seed = repo.enqueue(m("item", "a", """{"name":"A"}"""))
        val corrupt = insertCorruptTail(seed.mutationId, OutboxState.HELD)
        db.outboxDao().deleteBySeq(seed.seq)

        val r = repo.enqueue(m("item", "a", """{"name":"A2"}"""))

        assertEquals(OutboxState.PENDING, r.state)
        assertQuarantinedSanitized(row(corrupt))
    }

    @Test
    fun enqueue_nextToCorruptTail_newEditIsSentAndCorruptNeverIs() = runTest {
        val server = okhttp3.mockwebserver.MockWebServer()
        val ledger = LedgerFakeServer()
        server.dispatcher = ledger
        try {
            val seed = repo.enqueue(m("item", "a", """{"name":"A"}"""))
            val corrupt = insertCorruptTail(seed.mutationId, OutboxState.PENDING)
            db.outboxDao().deleteBySeq(seed.seq)
            val fresh = repo.enqueue(m("item", "a", """{"name":"A2"}""")).mutationId

            val prefs = java.io.File.createTempFile("outbox-repo", ".preferences_pb").also { it.deleteOnExit() }
            val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(produceFile = { prefs })
            dataStore.edit { it[dev.hho.android.data.settings.SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() }
            val cv = dev.hho.android.data.network.ClientVersionInterceptor()
            val provider = object : dev.hho.android.data.auth.DeviceTokenProvider {
                override suspend fun tokenFor(requestUrl: okhttp3.HttpUrl): String? = "tok"

                override suspend fun invalidate(requestUrl: okhttp3.HttpUrl, rejectedToken: String) = Unit
            }
            val http = dev.hho.android.di.NetworkModule.provideOkHttpClient(
                dev.hho.android.data.network.BaseUrlInterceptor(dev.hho.android.data.network.InstanceBaseUrlResolver(dataStore)),
                dev.hho.android.data.network.AuthHeaderInterceptor(provider),
                cv,
            )
            val api = dev.hho.android.data.apiclient.HhoApiClient(http, dev.hho.android.di.NetworkModule.provideProbeOkHttpClient(http, cv))
            val syncer = dev.hho.android.data.sync.OutboxSyncer(db, api, repo, clock = { now++ })

            val outcome = syncer.push("dev-1")

            assertEquals(dev.hho.android.data.sync.PushPhaseOutcome.Drained, outcome)
            assertEquals(listOf(fresh), ledger.applyOrder)
            assertEquals("A2", ledger.entityState["item/a"]!!["name"])
            assertTrue(ledger.sentIds.flatten().none { it == corrupt })
            assertEquals(listOf(corrupt), rows().map { it.mutationId })
            assertQuarantinedSanitized(row(corrupt))
        } finally {
            server.shutdown()
        }
    }
}
