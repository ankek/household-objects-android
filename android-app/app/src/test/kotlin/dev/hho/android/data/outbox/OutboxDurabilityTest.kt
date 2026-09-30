package dev.hho.android.data.outbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.ListenableWorker
import androidx.work.WorkRequest
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceIdProvider
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.deleteHhoDatabaseFile
import dev.hho.android.data.room.fileHhoDatabase
import dev.hho.android.data.sync.OutboxSyncer
import dev.hho.android.data.sync.PushPhaseOutcome
import dev.hho.android.data.sync.SyncRunRecorder
import dev.hho.android.data.sync.SyncScheduler
import dev.hho.android.data.sync.SyncWorker
import dev.hho.android.data.sync.testSyncEngine
import dev.hho.android.domain.AuthRepository
import dev.hho.android.domain.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OutboxDurabilityTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val server = MockWebServer()
    private val ledger = LedgerFakeServer()
    private val dbs = mutableListOf<HhoDatabase>()
    private val dbName = "outbox-durability-${System.nanoTime()}"
    private var now = 1_000L

    @Before
    fun setUp() {
        deleteHhoDatabaseFile(dbName)
        server.dispatcher = ledger
    }

    @After
    fun tearDown() {
        dbs.forEach { runCatching { it.close() } }
        deleteHhoDatabaseFile(dbName)
        server.shutdown()
    }

    @Test
    fun `kill after enqueue loses no row and keeps seq order`() =
        runBlocking {
            val before = open()
            val ids = listOf("a", "b", "c", "d").map { OutboxRepository(before, clock = { now++ }).enqueue(edit(it, "v-$it")).mutationId }
            val seqsBefore = before.outboxDao().getAllOrdered().map { it.seq }
            before.close()

            val after = open()
            val rows = after.outboxDao().getAllOrdered()
            assertEquals(ids, rows.map { it.mutationId })
            assertEquals(seqsBefore, rows.map { it.seq })
            assertEquals(seqsBefore.sorted(), seqsBefore)
            assertTrue(rows.all { it.state == OutboxState.PENDING })

            assertEquals(PushPhaseOutcome.Drained, syncer(after).push(DEVICE))
            assertTrue(after.outboxDao().getAllOrdered().isEmpty())
            assertEquals("applied in seq order", ids, ledger.applyOrder)
            listOf("a", "b", "c", "d").forEach { assertEquals("v-$it", ledger.entityState["item/$it"]!!["name"]) }
        }

    @Test
    fun `kill with request sent but no ack - in-flight rows recover and replay is skipped, not duplicated`() =
        runBlocking {
            val before = open()
            val repo = OutboxRepository(before, clock = { now++ })
            val ids = listOf("a", "b", "c").map { repo.enqueue(edit(it, "v-$it")).mutationId }
            val batch = repo.nextBatch()
            repo.markInFlight(batch.map { it.mutationId })
            assertTrue(api().syncPush(DEVICE, batch.map { it.toPushMutation() }).isSuccess)
            before.close()

            val after = open()
            val stale = after.outboxDao().getAllOrdered()
            assertEquals(ids, stale.map { it.mutationId })
            assertTrue("rows are still IN_FLIGHT after the kill", stale.all { it.state == OutboxState.IN_FLIGHT })
            assertTrue(OutboxRepository(after).nextBatch().isEmpty())

            assertEquals(PushPhaseOutcome.Drained, syncer(after).push(DEVICE))
            assertTrue(after.outboxDao().getAllOrdered().isEmpty())
            assertEquals(2, ledger.pushRequests)
            ids.forEach { assertEquals("applied exactly once: $it", 1, ledger.appliedCount[it]) }
            listOf("a", "b", "c").forEach { assertEquals("v-$it", ledger.entityState["item/$it"]!!["name"]) }
        }

    @Test
    fun `resetInFlightToPending after a reopen restores every in-flight row in seq order`() =
        runBlocking {
            val before = open()
            val repo = OutboxRepository(before, clock = { now++ })
            val ids = listOf("a", "b", "c").map { repo.enqueue(edit(it, "v-$it")).mutationId }
            repo.markInFlight(ids)
            before.close()

            val after = open()
            val repo2 = OutboxRepository(after)
            assertTrue(repo2.nextBatch().isEmpty())
            assertEquals(3, repo2.resetInFlightToPending())
            assertEquals(ids, repo2.nextBatch().map { it.mutationId })
            assertTrue(after.outboxDao().getAllOrdered().all { it.lastAttemptAt != null })
        }

    @Test
    fun `kill after ack keeps the released follower in order rebased on the acked version`() =
        runBlocking {
            val before = open()
            val repo = OutboxRepository(before, clock = { now++ })
            val first = repo.enqueue(edit("a", "one", base = 1))
            val other = repo.enqueue(edit("b", "other", base = 1))
            repo.markInFlight(listOf(first.mutationId))
            val follower = repo.enqueue(edit("a", "two", base = 1))
            assertEquals(OutboxState.HELD, follower.state)
            val sent = before.outboxDao().getByMutationId(first.mutationId)!!
            assertTrue(api().syncPush(DEVICE, listOf(sent.toPushMutation())).isSuccess)
            repo.ack(first.mutationId, 1L)
            before.close()

            val after = open()
            val rows = after.outboxDao().getAllOrdered()
            assertEquals(listOf(other.mutationId, follower.mutationId), rows.map { it.mutationId })
            assertTrue(rows[0].seq < rows[1].seq)
            assertTrue(rows.all { it.state == OutboxState.PENDING })
            assertEquals("follower rebased onto the acked version", 1L, rows[1].baseVersion)

            assertEquals(PushPhaseOutcome.Drained, syncer(after).push(DEVICE))
            assertTrue(after.outboxDao().getAllOrdered().isEmpty())
            assertEquals(1, ledger.appliedCount[first.mutationId])
            assertEquals("two", ledger.entityState["item/a"]!!["name"])
            assertEquals("other", ledger.entityState["item/b"]!!["name"])
            assertEquals(2L, ledger.versions["item/a"])
        }

    @Test
    fun `a lost response is replayed with the same mutation ids and never applied twice`() =
        runBlocking {
            val db = open()
            val repo = OutboxRepository(db, clock = { now++ })
            val ids = listOf("a", "b", "c").map { repo.enqueue(edit(it, "v-$it")).mutationId }
            ledger.faults += LedgerFakeServer.Fault.APPLY_THEN_DROP_RESPONSE

            val first = syncer(db).push(DEVICE)
            assertTrue("transport failure surfaces as Failed: $first", first is PushPhaseOutcome.Failed)
            ids.forEach { assertEquals("server applied it once already", 1, ledger.appliedCount[it]) }
            val queued = db.outboxDao().getAllOrdered()
            assertEquals("rows survive the lost response", ids, queued.map { it.mutationId })
            assertTrue(queued.all { it.attemptCount == 1 && it.state == OutboxState.PENDING })

            assertEquals(PushPhaseOutcome.Drained, syncer(db).push(DEVICE))

            assertEquals("replay used the same ids", ledger.sentIds[0], ledger.sentIds[1])
            assertEquals(ids, ledger.sentIds[1])
            ids.forEach { assertEquals("zero duplicate applications: $it", 1, ledger.appliedCount[it]) }
            assertEquals(1L, ledger.versions["item/a"])
            assertEquals("v-a", ledger.entityState["item/a"]!!["name"])
            assertTrue(db.outboxDao().getAllOrdered().isEmpty())
        }

    @Test
    fun `an edit made after a lost response is held behind the possibly-sent row, then applied once, in order`() =
        runBlocking {
            val db = open()
            val repo = OutboxRepository(db, clock = { now++ })
            val first = repo.enqueue(edit("a", "one")).mutationId
            ledger.faults += LedgerFakeServer.Fault.APPLY_THEN_DROP_RESPONSE
            assertTrue(syncer(db).push(DEVICE) is PushPhaseOutcome.Failed)

            val second = repo.enqueue(edit("a", "two"))
            assertTrue("never merged into a possibly-sent row", !second.coalesced && second.mutationId != first)
            assertEquals(OutboxState.HELD, second.state)

            assertEquals(PushPhaseOutcome.Drained, syncer(db).push(DEVICE))
            val left = db.outboxDao().getAllOrdered()
            assertEquals(listOf(second.mutationId), left.map { it.mutationId })
            assertEquals(1, ledger.appliedCount[first])
            assertEquals("one", ledger.entityState["item/a"]!!["name"])
            repo.releaseHeld { _, _ -> 1L }
            assertEquals(PushPhaseOutcome.Drained, syncer(db).push(DEVICE))
            assertEquals("two", ledger.entityState["item/a"]!!["name"])
            assertEquals(listOf(first, second.mutationId), ledger.applyOrder)
            assertTrue(db.outboxDao().getAllOrdered().isEmpty())
        }

    @Test
    fun `repeated 5xx grows attempt_count each run and the worker returns retry every time`() =
        runBlocking {
            val db = open()
            val repo = OutboxRepository(db, clock = { now++ })
            val id = repo.enqueue(edit("a", "one")).mutationId
            val engine = testSyncEngine(api(), db, repo)
            repeat(4) { ledger.faults += LedgerFakeServer.Fault.SERVER_ERROR_500 }

            for (run in 1..4) {
                val result = worker(engine, db).doWork()
                assertEquals("run $run", ListenableWorker.Result.retry(), result)
                val row = db.outboxDao().getByMutationId(id)!!
                assertEquals("attempt_count after run $run", run, row.attemptCount)
                assertEquals(OutboxState.PENDING, row.state)
                assertTrue(row.lastError != null)
            }
            assertEquals("no pull was attempted, only pushes", 4, server.requestCount)

            assertEquals(PushPhaseOutcome.Drained, syncer(db).push(DEVICE))
            assertEquals(1, ledger.appliedCount[id])
            assertTrue(db.outboxDao().getAllOrdered().isEmpty())
        }

    @Test
    fun `the sync work requests use exponential backoff`() {
        val onDemand = SyncScheduler.buildOnDemandRequest().workSpec
        val periodic = SyncScheduler.buildPeriodicRequest().workSpec
        assertEquals(BackoffPolicy.EXPONENTIAL, onDemand.backoffPolicy)
        assertEquals(BackoffPolicy.EXPONENTIAL, periodic.backoffPolicy)
        assertEquals(WorkRequest.MIN_BACKOFF_MILLIS, onDemand.backoffDelayDuration)
        val delays = (1..4).map { attempts ->
            onDemand.also { it.runAttemptCount = attempts }.calculateNextRunTime() - onDemand.lastEnqueueTime
        }
        assertEquals(delays.sorted(), delays)
        assertEquals(delays[0] * 2, delays[1])
        assertEquals(delays[1] * 2, delays[2])
        assertEquals(delays[2] * 2, delays[3])
    }

    @Test
    fun `a poisoned mutation is quarantined, the rest apply, and it is never resent`() =
        runBlocking {
            val db = open()
            val repo = OutboxRepository(db, clock = { now++ })
            val good1 = repo.enqueue(edit("a", "v-a")).mutationId
            val bad = repo.enqueue(edit("bad", "v-bad")).mutationId
            val good2 = repo.enqueue(edit("c", "v-c")).mutationId
            ledger.poisonedEntityIds += "bad"

            assertEquals(PushPhaseOutcome.Drained, syncer(db).push(DEVICE))

            val left = db.outboxDao().getAllOrdered()
            assertEquals(listOf(bad), left.map { it.mutationId })
            assertEquals(OutboxState.FAILED, left.single().state)
            assertTrue(left.single().lastError!!.contains(bad))
            assertEquals(listOf(good1, good2), ledger.applyOrder)
            assertEquals("v-a", ledger.entityState["item/a"]!!["name"])
            assertEquals("v-c", ledger.entityState["item/c"]!!["name"])
            assertTrue(ledger.entityState["item/bad"] == null)

            val requestsBefore = ledger.pushRequests
            ledger.sentIds.clear()
            assertEquals(PushPhaseOutcome.Drained, syncer(db).push(DEVICE))
            assertEquals("nothing left to send: no request", requestsBefore, ledger.pushRequests)

            val next = repo.enqueue(edit("d", "v-d")).mutationId
            assertEquals(PushPhaseOutcome.Drained, syncer(db).push(DEVICE))
            assertEquals(listOf(next), ledger.sentIds.flatten())
            assertTrue(bad !in ledger.sentIds.flatten())
            assertEquals(listOf(bad), db.outboxDao().getAllOrdered().map { it.mutationId })
        }

    @Test
    fun `a poisoned row survives a kill and is still not resent after reopen`() =
        runBlocking {
            val before = open()
            val repo = OutboxRepository(before, clock = { now++ })
            val bad = repo.enqueue(edit("bad", "x")).mutationId
            repo.enqueue(edit("a", "v-a"))
            ledger.poisonedEntityIds += "bad"
            assertEquals(PushPhaseOutcome.Drained, syncer(before).push(DEVICE))
            before.close()

            val after = open()
            ledger.sentIds.clear()
            assertEquals(PushPhaseOutcome.Drained, syncer(after).push(DEVICE))
            assertTrue(ledger.sentIds.isEmpty())
            val row = after.outboxDao().getByMutationId(bad)!!
            assertEquals(OutboxState.FAILED, row.state)
        }

    @Test
    fun `a row whose fields_json cannot be decoded does not wedge the push`() =
        runBlocking {
            val db = open()
            val repo = OutboxRepository(db, clock = { now++ })
            val good1 = repo.enqueue(edit("a", "v-a")).mutationId
            val corrupt = OutboxMutationEntity(
                mutationId = "corrupt-1", entityType = "item", entityId = "zzz", op = "upsert", baseVersion = 1,
                fieldsJson = "{not json", state = OutboxState.PENDING, createdAt = now++,
            )
            db.outboxDao().insert(corrupt)
            val good2 = repo.enqueue(edit("c", "v-c")).mutationId

            val outcome = runCatching { syncer(db).push(DEVICE) }

            assertTrue(
                "push must neither throw nor leave the healthy rows stuck; outcome=$outcome ledger=${ledger.applyOrder}",
                outcome.isSuccess && ledger.applyOrder.containsAll(listOf(good1, good2)),
            )
            val remaining = db.outboxDao().getAllOrdered()
            assertTrue("healthy rows drained: $remaining", remaining.none { it.mutationId == good1 || it.mutationId == good2 })
            assertEquals("corrupt row kept for D4.6, not retried", OutboxState.FAILED, remaining.single().state)
        }

    private fun open(): HhoDatabase = fileHhoDatabase(dbName).also { dbs += it }

    private fun syncer(db: HhoDatabase) = OutboxSyncer(db, api(), OutboxRepository(db, clock = { now++ }), clock = { now++ })

    private fun edit(id: String, name: String, base: Long = 1) =
        LocalMutation("item", id, "upsert", base, buildJsonObject { put("name", JsonPrimitive(name)) })

    private fun api(): HhoApiClient = ledgerApiClient(server)

    private fun worker(engine: dev.hho.android.data.sync.SyncEngine, db: HhoDatabase): SyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val auth = object : AuthRepository {
            override val authState: Flow<AuthState> = MutableStateFlow(AuthState.LoggedIn)

            override suspend fun login(username: String, password: String): Result<Unit> = error("unused")

            override suspend fun logout() = error("unused")
        }
        val ids = object : DeviceIdProvider {
            override suspend fun deviceId(): String = DEVICE
        }
        return TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                        SyncWorker(appContext, workerParameters, engine, ids, auth, SyncRunRecorder(db.syncRunStateDao()))
                },
            ).build()
    }

    private companion object {
        const val DEVICE = "dev-1"
    }
}
