package dev.hho.android.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.apiclient.UnknownSyncEntityTypeException
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.outbox.OutboxOverlay
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.SyncStateEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
class PullInterplayTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(15)

    private val server = MockWebServer()
    private lateinit var db: HhoDatabase

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("incremental-sync-coordinator-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun apiClient(): HhoApiClient {
        val dataStore = tempDataStore()
        runBlocking {
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() }
        }
        val clientVersionInterceptor = ClientVersionInterceptor()
        val authHeaderInterceptor =
            AuthHeaderInterceptor(
                object : DeviceTokenProvider {
                    override suspend fun tokenFor(requestUrl: HttpUrl): String? = "device-token"

                    override suspend fun invalidate(
                        requestUrl: HttpUrl,
                        rejectedToken: String,
                    ) = Unit
                },
            )
        val baseUrlInterceptor = BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore))
        val httpClient =
            NetworkModule.provideOkHttpClient(baseUrlInterceptor, authHeaderInterceptor, clientVersionInterceptor)
        val probeHttpClient = NetworkModule.provideProbeOkHttpClient(httpClient, clientVersionInterceptor)
        return HhoApiClient(httpClient, probeHttpClient)
    }

    private fun incremental() = IncrementalSyncCoordinator(apiClient(), db)

    private fun full() = FullSyncCoordinator(apiClient(), db)

    private fun page(changes: String = "", tombstones: String = "", next: Long, hasMore: Boolean = false) =
        """{"changes":[$changes],"tombstones":[$tombstones],"next_watermark":$next,"has_more":$hasMore}"""

    private fun itemChange(id: String, name: String, quantity: Long = 0, version: Long = 2) =
        """{"entity_type":"item","id":"$id","group_change_seq":2,"data":{"id":"$id","name":"$name","quantity":$quantity,"created_at":1,"updated_at":1,"version":$version}}"""

    private fun tomb(type: String, id: String) = """{"entity_type":"$type","id":"$id","deleted_at":1}"""

    private fun enqueueBody(json: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))
    }

    private var n = 0

    private suspend fun queue(
        type: String, id: String, fields: String, base: Long = 0, state: String = OutboxState.PENDING,
    ) = db.outboxDao().insert(
        OutboxMutationEntity(
            mutationId = "m-${n++}", entityType = type, entityId = id, op = "upsert", baseVersion = base,
            fieldsJson = fields, state = state, createdAt = 0,
        ),
    )

    private suspend fun seedItem(id: String, name: String, quantity: Long = 5, version: Long = 1) {
        db.itemDao().upsert(ItemEntity(id, 1, name, null, null, quantity, "SC", 1, 1, version))
    }

    private suspend fun seedWatermark(w: Long) = db.syncStateDao().upsert(SyncStateEntity(watermark = w, lastSyncedAt = 1L))

    @Test
    fun `a full pull with a pending create keeps the not-yet-pushed item`() = runTest {
        queue("item", "new-1", """{"name":"Local only"}""", base = 0)
        enqueueBody(page(changes = itemChange("srv-1", "Server"), next = 9))

        assertTrue(full().pullFull("d").isSuccess)

        assertEquals("Local only", db.itemDao().getById("new-1")?.name)
        assertEquals("Server", db.itemDao().getById("srv-1")?.name)
        assertEquals(9L, db.syncStateDao().get()?.watermark)
    }

    @Test
    fun `a full pull with a pending edit shows the edit over the pulled row`() = runTest {
        queue("item", "i1", """{"name":"Edited"}""", base = 1)
        enqueueBody(page(changes = itemChange("i1", "Server name"), next = 9))

        assertTrue(full().pullFull("d").isSuccess)

        assertEquals("Edited", db.itemDao().getById("i1")?.name)
    }

    @Test
    fun `a pending create survives a full re-pull after cursor_too_old`() = runTest {
        seedWatermark(3)
        queue("item", "new-1", """{"name":"Local only"}""", base = 0)
        enqueueBody("""{"cursor_too_old":true}""")
        assertEquals(IncrementalPullOutcome.CursorTooOld, incremental().pullIncremental("d").getOrNull())
        enqueueBody(page(changes = itemChange("srv-1", "Server"), next = 20))
        assertTrue(full().pullFull("d").isSuccess)

        assertEquals("Local only", db.itemDao().getById("new-1")?.name)
        assertEquals("Server", db.itemDao().getById("srv-1")?.name)
    }

    @Test
    fun `a pending edit survives an incremental page that updates the same row`() = runTest {
        seedItem("i1", "Old")
        seedWatermark(5)
        queue("item", "i1", """{"name":"Mine"}""", base = 1)
        enqueueBody(page(changes = itemChange("i1", "Theirs", version = 2), next = 10))

        assertTrue(incremental().pullIncremental("d").isSuccess)

        val row = db.itemDao().getById("i1")!!
        assertEquals("Mine", row.name)
        assertEquals(2L, row.version)
        assertEquals(10L, db.syncStateDao().get()?.watermark)
    }

    @Test
    fun `a tombstone beats a pending update and the orphan outbox row is left in place`() = runTest {
        seedItem("i1", "Old")
        seedWatermark(5)
        queue("item", "i1", """{"name":"Mine"}""", base = 1)
        enqueueBody(page(tombstones = tomb("item", "i1"), next = 10))

        assertTrue(incremental().pullIncremental("d").isSuccess)

        assertNull(db.itemDao().getById("i1"))
        assertEquals(1, db.outboxDao().getAllOrdered().size)
    }

    @Test
    fun `a tombstone and a same-page change for one id both leave a pending update skipped`() = runTest {
        seedWatermark(5)
        queue("item", "i1", """{"name":"Mine"}""", base = 1)
        enqueueBody(page(changes = itemChange("i1", "Theirs"), tombstones = tomb("item", "i1"), next = 10))

        assertTrue(incremental().pullIncremental("d").isSuccess)

        assertNull(db.itemDao().getById("i1"))
    }

    @Test
    fun `a pending create whose id arrives as a tombstone is re-applied`() = runTest {
        seedWatermark(5)
        queue("item", "c1", """{"name":"Created here"}""", base = 0, state = OutboxState.IN_FLIGHT)
        enqueueBody(page(tombstones = tomb("item", "c1"), next = 10))

        assertTrue(incremental().pullIncremental("d").isSuccess)

        assertEquals("Created here", db.itemDao().getById("c1")?.name)
    }

    @Test
    fun `an acked adjustment plus pulled quantity is not double counted, a still-pending one is added once`() = runTest {
        seedItem("i1", "Drill", quantity = 5)
        seedWatermark(5)
        queue("stock_adjustment", "adj-2", """{"item_id":"i1","delta":2}""")
        enqueueBody(page(changes = itemChange("i1", "Drill", quantity = 8), next = 10))

        assertTrue(incremental().pullIncremental("d").isSuccess)

        assertEquals(8L, db.itemDao().getById("i1")?.quantity)
        assertEquals(10L, OutboxOverlay.derivedQuantity(db, "i1"))
    }

    @Test
    fun `an acked adjustment alone leaves the derived quantity equal to the pulled one`() = runTest {
        seedItem("i1", "Drill", quantity = 5)
        seedWatermark(5)
        enqueueBody(page(changes = itemChange("i1", "Drill", quantity = 8), next = 10))

        assertTrue(incremental().pullIncremental("d").isSuccess)

        assertEquals(8L, OutboxOverlay.derivedQuantity(db, "i1"))
    }

    private fun failReplayWrites() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_replay BEFORE INSERT ON stock_adjustment BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
    }

    @Test
    fun `the replay runs inside the page transaction, so a failing replay rolls back the page and its watermark`() = runTest {
        seedItem("i1", "Old")
        seedWatermark(5)
        queue("stock_adjustment", "adj-1", """{"item_id":"i1","delta":2}""")
        failReplayWrites()
        enqueueBody(page(changes = itemChange("i1", "Theirs"), next = 10))

        val result = incremental().pullIncremental("d")

        assertTrue(result.isFailure)
        assertEquals("Old", db.itemDao().getById("i1")?.name)
        assertEquals(5L, db.syncStateDao().get()?.watermark)
        assertEquals(OutboxState.PENDING, db.outboxDao().getAllOrdered().single().state)
    }

    @Test
    fun `a failing replay rolls back a whole full pull, mirror and watermark`() = runTest {
        seedItem("i1", "Old")
        seedWatermark(5)
        queue("stock_adjustment", "adj-1", """{"item_id":"i1","delta":2}""")
        failReplayWrites()
        enqueueBody(page(changes = itemChange("srv-1", "Server"), next = 10))

        val result = full().pullFull("d")

        assertTrue(result.isFailure)
        assertEquals("Old", db.itemDao().getById("i1")?.name)
        assertNull(db.itemDao().getById("srv-1"))
        assertEquals(5L, db.syncStateDao().get()?.watermark)
    }

    private suspend fun queueCorruptHeldFollower() {
        queue("item", "i1", """{"name":"Mine"}""", base = 1)
        queue("item", "i1", "not json", base = 1, state = OutboxState.HELD)
    }

    private suspend fun assertCorruptQuarantined() {
        val rows = db.outboxDao().getAllOrdered()
        val bad = rows.single { it.fieldsJson == "not json" }
        assertEquals(OutboxState.FAILED, bad.state)
        assertTrue(bad.lastError!!.startsWith("Unreadable local edit: "))
        assertEquals(OutboxState.PENDING, rows.single { it.fieldsJson != "not json" }.state)
    }

    @Test
    fun `a corrupt HELD follower does not block an incremental pull and is quarantined`() = runTest {
        seedItem("i1", "Old")
        seedWatermark(5)
        queueCorruptHeldFollower()
        enqueueBody(page(changes = itemChange("i1", "Theirs"), next = 10))

        assertTrue(incremental().pullIncremental("d").isSuccess)

        assertEquals("Mine", db.itemDao().getById("i1")?.name)
        assertEquals(10L, db.syncStateDao().get()?.watermark)
        assertCorruptQuarantined()
    }

    @Test
    fun `a corrupt HELD follower does not block a full pull and is quarantined`() = runTest {
        seedItem("i1", "Old")
        seedWatermark(5)
        queue("item", "new-1", """{"name":"Local only"}""", base = 0)
        queue("item", "new-1", "not json", base = 0, state = OutboxState.HELD)
        enqueueBody(page(changes = itemChange("srv-1", "Server"), next = 10))

        assertTrue(full().pullFull("d").isSuccess)

        assertEquals("Server", db.itemDao().getById("srv-1")?.name)
        assertEquals("Local only", db.itemDao().getById("new-1")?.name)
        assertEquals(10L, db.syncStateDao().get()?.watermark)
        assertCorruptQuarantined()
    }

    @Test
    fun `a corrupt live stock adjustment does not break the derived quantity`() = runTest {
        seedItem("i1", "Drill", quantity = 5)
        queue("stock_adjustment", "adj-1", "not json")
        queue("stock_adjustment", "adj-2", """{"item_id":"i1","delta":2}""")

        assertEquals(7L, OutboxOverlay.derivedQuantity(db, "i1"))
    }
}
