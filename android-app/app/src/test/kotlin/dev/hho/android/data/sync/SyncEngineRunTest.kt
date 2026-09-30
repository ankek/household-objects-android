package dev.hho.android.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OptimisticApplier
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.SyncRunStateEntity
import dev.hho.android.data.room.SyncStateEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncEngineRunTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(20)

    private val server = MockWebServer()
    private lateinit var db: HhoDatabase
    private lateinit var outbox: OutboxRepository

    private val paths = CopyOnWriteArrayList<String>()
    private val pullBodies = CopyOnWriteArrayList<String>()
    private var pushResponder: (String) -> MockResponse = { ok("""{"applied":[],"skipped":[],"conflicts":[],"new_watermark":1}""") }
    private var pullResponder: () -> MockResponse = { ok(page("", 5)) }

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        outbox = OutboxRepository(db)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                paths += path
                val body = request.body.readUtf8()
                return if (path.endsWith("/sync/push")) {
                    pushResponder(body)
                } else {
                    pullBodies += body
                    pullResponder()
                }
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun apiClient(): HhoApiClient {
        val file = File.createTempFile("hho-engine-run-test", ".preferences_pb").also { it.deleteOnExit() }
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

    private fun ok(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun problem(status: Int, extra: String = "") = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"t","status":$status,"detail":"d"$extra}""")

    private fun page(changes: String, next: Long) = """{"changes":[$changes],"tombstones":[],"next_watermark":$next,"has_more":false}"""

    private fun itemChange(id: String, name: String, version: Long) =
        """{"entity_type":"item","id":"$id","group_change_seq":2,"data":{"id":"$id","name":"$name","quantity":0,"created_at":1,"updated_at":1,"version":$version}}"""

    private fun mutation(type: String, id: String, fields: String, base: Long) =
        LocalMutation(type, id, "upsert", base, Json.parseToJsonElement(fields).jsonObject)

    private fun conflictResponse(mutationId: String, id: String, field: String) =
        ok("""{"applied":[],"skipped":[],"conflicts":[{"mutation_id":"$mutationId","entity_type":"item","entity_id":"$id","field_name":"$field"}],"new_watermark":1}""")

    private suspend fun seedWatermark() = db.syncStateDao().upsert(SyncStateEntity(watermark = 5L, lastSyncedAt = 1L))

    private suspend fun seedItem(id: String, version: Long = 1) =
        db.itemDao().upsert(ItemEntity(id, 1, "old", null, null, 0, null, 1, 1, version))

    private fun scripted(vararg outcomes: PushPhaseOutcome): suspend (String) -> PushPhaseOutcome {
        val queue = ArrayDeque(outcomes.toList())
        return { queue.removeFirst() }
    }

    private val failed = PushPhaseOutcome.Failed(ApiError.Unknown(RuntimeException("boom")))

    @Test
    fun pushRunsBeforePull_andBothHappen() = runBlocking {
        seedWatermark()
        seedItem("a")
        val row = outbox.enqueue(mutation("item", "a", """{"name":"New"}""", 1))
        pushResponder = { ok("""{"applied":[{"mutation_id":"${row.mutationId}","entity_type":"item","entity_id":"a","version":2}],"skipped":[],"conflicts":[],"new_watermark":9}""") }

        val result = testSyncEngine(apiClient(), db, outbox).sync("d")

        assertEquals(SyncRunOutcome.IncrementalPullRan, result.getOrNull())
        assertEquals(listOf("/sync/push", "/sync/pull"), paths.map { it.substringAfterLast("/sync").let { s -> "/sync$s" } })
        assertTrue(db.outboxDao().getAllOrdered().isEmpty())
    }

    @Test
    fun failedPush_skipsPull_returnsFailure_keepsRowsQueued() = runBlocking {
        seedWatermark()
        outbox.enqueue(mutation("item", "a", """{"name":"New"}""", 1))
        pushResponder = { problem(500) }
        val hook = CountingHook()

        val result = testSyncEngine(apiClient(), db, outbox, hooks = setOf(hook)).sync("d")

        assertTrue(result.isFailure)
        assertEquals(1, paths.size)
        assertEquals(0, hook.calls)
        assertEquals(OutboxState.PENDING, db.outboxDao().getAllOrdered().single().state)
        assertNotNull(db.syncRunStateDao().get()?.lastError)
    }

    @Test
    fun needsReconcile_forcesFullPull_evenWithWatermark() = runBlocking {
        seedWatermark()
        seedItem("a")
        val row = outbox.enqueue(mutation("item", "a", """{"name":"Mine"}""", 1))
        pushResponder = { conflictResponse(row.mutationId, "a", "name") }
        pullResponder = { ok(page(itemChange("a", "Server", 3), 12)) }

        val result = testSyncEngine(apiClient(), db, outbox).sync("d")

        assertEquals(SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.ConflictReconcile), result.getOrNull())
        assertTrue(pullBodies.single().contains("\"since\":0"))
        assertEquals("Server", db.itemDao().getById("a")?.name)
        assertFalse(db.syncRunStateDao().get()!!.reconcilePending)
    }

    @Test
    fun reconcileDebt_survivesFailedRun_andForcesFullPullNextRun() = runBlocking {
        seedWatermark()
        val engine = testSyncEngine(apiClient(), db, outbox, push = scripted(failed.copy(reconcilePending = true), PushPhaseOutcome.Drained))

        assertTrue(engine.sync("d").isFailure)
        assertTrue(db.syncRunStateDao().get()!!.reconcilePending)
        assertTrue(paths.isEmpty())

        val second = engine.sync("d")

        assertEquals(SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.ConflictReconcile), second.getOrNull())
        assertTrue(pullBodies.single().contains("\"since\":0"))
        assertFalse(db.syncRunStateDao().get()!!.reconcilePending)
    }

    @Test
    fun reconcileDebt_survivesFailedReconcilePull() = runBlocking {
        seedWatermark()
        val engine = testSyncEngine(apiClient(), db, outbox, push = scripted(PushPhaseOutcome.NeedsReconcile, PushPhaseOutcome.Drained))
        pullResponder = { problem(500) }

        assertTrue(engine.sync("d").isFailure)
        assertTrue(db.syncRunStateDao().get()!!.reconcilePending)

        pullResponder = { ok(page("", 8)) }
        assertEquals(
            SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.ConflictReconcile),
            engine.sync("d").getOrNull(),
        )
        assertFalse(db.syncRunStateDao().get()!!.reconcilePending)
    }

    @Test
    fun blockedAuth_andUpgrade_skipPull_mapToOutcomes_andKeepDebt() = runBlocking {
        seedWatermark()
        val engine = testSyncEngine(
            apiClient(), db, outbox,
            push = scripted(
                PushPhaseOutcome.Blocked(BlockedReason.AUTH, reconcilePending = true),
                PushPhaseOutcome.Blocked(BlockedReason.UPGRADE_REQUIRED, "9.9.9"),
            ),
        )

        assertEquals(SyncRunOutcome.AuthRequired, engine.sync("d").getOrNull())
        assertTrue(db.syncRunStateDao().get()!!.reconcilePending)
        assertEquals(SyncRunOutcome.UpgradeRequired("9.9.9"), engine.sync("d").getOrNull())
        val state = db.syncRunStateDao().get()!!
        assertTrue(state.reconcilePending)
        assertTrue(state.lastError!!.contains("9.9.9"))
        assertTrue(paths.isEmpty())
    }

    @Test
    fun releaseHeld_afterPull_releasesFollowerOfSkippedAckedMutation() = runBlocking {
        seedWatermark()
        seedItem("a")
        val first = outbox.enqueue(mutation("item", "a", """{"name":"first"}""", 1))
        outbox.markInFlight(listOf(first.mutationId))
        val follower = outbox.enqueue(mutation("item", "a", """{"name":"second"}""", 1))
        assertEquals(OutboxState.HELD, follower.state)
        outbox.resetInFlightToPending()
        pushResponder = { ok("""{"applied":[],"skipped":[{"mutation_id":"${first.mutationId}"}],"conflicts":[],"new_watermark":1}""") }
        pullResponder = { ok(page(itemChange("a", "first", 7), 9)) }

        assertTrue(testSyncEngine(apiClient(), db, outbox).sync("d").isSuccess)

        val left = db.outboxDao().getAllOrdered().single()
        assertEquals(follower.mutationId, left.mutationId)
        assertEquals(OutboxState.PENDING, left.state)
        assertEquals(7L, left.baseVersion)
    }

    @Test
    fun concurrentSyncCalls_neverOverlapPushes_secondWaitsThenRuns() = runBlocking {
        var active = 0
        var maxActive = 0
        var pushes = 0
        val gate = CompletableDeferred<Unit>()
        val engine = testSyncEngine(apiClient(), db, outbox, push = {
            pushes++
            active++
            maxActive = maxOf(maxActive, active)
            gate.await()
            active--
            PushPhaseOutcome.Drained
        })

        val a = async { engine.sync("d") }
        val b = async { engine.sync("d") }
        while (pushes < 1) delay(10)
        delay(300)
        assertEquals("second call must wait while the first push is in flight", 1, pushes)
        gate.complete(Unit)

        assertTrue(a.await().isSuccess)
        assertTrue(b.await().isSuccess)
        assertEquals(2, pushes)
        assertEquals(1, maxActive)
    }

    @Test
    fun fu59_pendingCreateAnsweredAsConflict_leavesNoGhostRowAfterForcedFullPull() = runBlocking {
        seedWatermark()
        seedItem("keep")
        val create = mutation("item", "ghost", """{"name":"Ghost"}""", 0)
        val queued = outbox.enqueue(create)
        OptimisticApplier.apply(db, create)
        assertNotNull(db.itemDao().getById("ghost"))
        pushResponder = { conflictResponse(queued.mutationId, "ghost", "_entity") }
        pullResponder = { ok(page(itemChange("keep", "Server keep", 2), 20)) }

        val result = testSyncEngine(apiClient(), db, outbox).sync("d")

        assertEquals(SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.ConflictReconcile), result.getOrNull())
        assertTrue(db.outboxDao().getAllOrdered().isEmpty())
        assertNull("optimistic create must not linger", db.itemDao().getById("ghost"))
        assertEquals("Server keep", db.itemDao().getById("keep")?.name)
    }

    @Test
    fun hooks_runOnceAfterSuccessfulPull_andFailureNeverFailsTheSync() = runBlocking {
        val failing = CountingHook(IllegalStateException("photo problem"))
        val fine = CountingHook()

        val result = testSyncEngine(apiClient(), db, outbox, hooks = setOf(failing, fine)).sync("d")

        assertTrue(result.isSuccess)
        assertEquals(1, failing.calls)
        assertEquals(1, fine.calls)
    }

    @Test
    fun hooks_notRunWhenPullFails() = runBlocking {
        pullResponder = { problem(500) }
        val hook = CountingHook()

        assertTrue(testSyncEngine(apiClient(), db, outbox, hooks = setOf(hook)).sync("d").isFailure)

        assertEquals(0, hook.calls)
    }

    @Test
    fun successfulRun_clearsLastError() = runBlocking {
        db.syncRunStateDao().upsert(SyncRunStateEntity(lastError = "old", lastErrorAt = 1))

        assertTrue(testSyncEngine(apiClient(), db, outbox).sync("d").isSuccess)

        assertNull(db.syncRunStateDao().get()!!.lastError)
    }

    @Test
    fun lastPushAt_setOnlyWhenPushReachedServer_andRunRecordingKeepsCursorAndDebt() = runBlocking {
        seedWatermark()
        db.syncRunStateDao().upsert(SyncRunStateEntity(conflictCursor = "999"))

        assertTrue(testSyncEngine(apiClient(), db, outbox, push = scripted(failed)).sync("d").isFailure)
        assertNull(db.syncRunStateDao().get()!!.lastPushAt)
        assertNotNull(db.syncRunStateDao().get()!!.lastError)

        val blocked = PushPhaseOutcome.Blocked(BlockedReason.UPGRADE_REQUIRED, minimumVersion = "9.9.9")
        assertTrue(testSyncEngine(apiClient(), db, outbox, push = scripted(blocked)).sync("d").isSuccess)
        assertNull(db.syncRunStateDao().get()!!.lastPushAt)

        assertTrue(testSyncEngine(apiClient(), db, outbox, push = scripted(PushPhaseOutcome.Drained)).sync("d").isSuccess)
        val row = db.syncRunStateDao().get()!!
        assertEquals(1_000L, row.lastPushAt)
        assertEquals(1_000L, row.lastRunAt)
        assertNull(row.lastError)
        assertEquals("999", row.conflictCursor)
    }

    @Test
    fun needsReconcile_stampsLastPushAt_evenWhenThePullFails() = runBlocking {
        seedWatermark()
        pullResponder = { problem(500) }

        val result = testSyncEngine(apiClient(), db, outbox, push = scripted(PushPhaseOutcome.NeedsReconcile)).sync("d")

        assertTrue(result.isFailure)
        val row = db.syncRunStateDao().get()!!
        assertEquals(1_000L, row.lastPushAt)
        assertTrue(row.reconcilePending)
        assertNotNull(row.lastError)
    }

    @Test
    fun conflictLog_runsOnceAfterNormalIncrementalPull() = runBlocking {
        seedWatermark()
        var calls = 0
        var pullsSeenAtCall = -1
        val step: suspend () -> Unit = {
            calls++
            pullsSeenAtCall = paths.count { it.endsWith("/sync/pull") }
        }

        val result = testSyncEngine(apiClient(), db, outbox, conflictLogStep = step).sync("d")

        assertEquals(SyncRunOutcome.IncrementalPullRan, result.getOrNull())
        assertEquals(1, calls)
        assertEquals("must run after the pull, not before", 1, pullsSeenAtCall)
    }

    @Test
    fun conflictLog_runsAfterForcedReconcilePull_seeingTheReconciledMirror() = runBlocking {
        seedWatermark()
        seedItem("a")
        val row = outbox.enqueue(mutation("item", "a", """{"name":"Mine"}""", 1))
        pushResponder = { conflictResponse(row.mutationId, "a", "name") }
        pullResponder = { ok(page(itemChange("a", "Server", 3), 12)) }
        var calls = 0
        var mirrorNameAtCall: String? = null
        val step: suspend () -> Unit = {
            calls++
            mirrorNameAtCall = db.itemDao().getById("a")?.name
        }

        val result = testSyncEngine(apiClient(), db, outbox, conflictLogStep = step).sync("d")

        assertEquals(SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.ConflictReconcile), result.getOrNull())
        assertEquals(1, calls)
        assertEquals("Server", mirrorNameAtCall)
    }

    @Test
    fun conflictLog_runsAfterFirstSyncFullPull() = runBlocking {
        var calls = 0

        val result = testSyncEngine(apiClient(), db, outbox, conflictLogStep = { calls++ }).sync("d")

        assertEquals(SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.FirstSync), result.getOrNull())
        assertEquals(1, calls)
    }

    @Test
    fun conflictLog_notRunWhenPullFailsOrPushFailsOrIsBlocked() = runBlocking {
        seedWatermark()
        var calls = 0
        val step: suspend () -> Unit = { calls++ }

        pullResponder = { problem(500) }
        assertTrue(testSyncEngine(apiClient(), db, outbox, conflictLogStep = step).sync("d").isFailure)
        assertTrue(testSyncEngine(apiClient(), db, outbox, push = scripted(failed), conflictLogStep = step).sync("d").isFailure)
        val blocked = scripted(PushPhaseOutcome.Blocked(BlockedReason.AUTH), PushPhaseOutcome.Blocked(BlockedReason.UPGRADE_REQUIRED, "9.9.9"))
        val engine = testSyncEngine(apiClient(), db, outbox, push = blocked, conflictLogStep = step)
        assertEquals(SyncRunOutcome.AuthRequired, engine.sync("d").getOrNull())
        assertEquals(SyncRunOutcome.UpgradeRequired("9.9.9"), engine.sync("d").getOrNull())

        assertEquals(0, calls)
    }

    @Test
    fun conflictLog_failureIsSwallowed_syncStillSucceeds_hooksRun_andLastErrorCleared() = runBlocking {
        seedWatermark()
        db.syncRunStateDao().upsert(SyncRunStateEntity(lastError = "old", lastErrorAt = 1))
        val hook = CountingHook()

        val result = testSyncEngine(
            apiClient(), db, outbox, hooks = setOf(hook),
            conflictLogStep = { throw ApiError.Unknown(RuntimeException("conflicts endpoint down")) },
        ).sync("d")

        assertEquals(SyncRunOutcome.IncrementalPullRan, result.getOrNull())
        assertEquals(1, hook.calls)
        assertNull(db.syncRunStateDao().get()!!.lastError)
    }

    @Test
    fun conflictLog_realStep_serverErrorOnConflictsEndpointNeverFailsTheEntitySync() = runBlocking {
        seedWatermark()
        val savedDispatcher = server.dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().contains("/sync/conflicts")) problem(500) else savedDispatcher.dispatch(request)
        }

        val result = testSyncEngine(apiClient(), db, outbox, withConflictLog = true).sync("d")

        assertEquals(SyncRunOutcome.IncrementalPullRan, result.getOrNull())
        assertTrue(db.conflictRecordDao().getAll().isEmpty())
        assertNull(db.syncRunStateDao().get()!!.lastError)
    }
}
