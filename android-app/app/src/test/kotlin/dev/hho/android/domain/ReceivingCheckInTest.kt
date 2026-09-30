package dev.hho.android.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxOverlay
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.receiving.ReceivingException
import dev.hho.android.data.receiving.ReceivingRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.PurchasedFromBlockEntity
import dev.hho.android.data.room.ReceivingStatus
import dev.hho.android.data.room.inMemoryHhoDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.outbox.LedgerFakeServer
import dev.hho.android.data.receiving.ScanOutcome
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.data.sync.OutboxSyncer
import dev.hho.android.data.sync.PushPhaseOutcome
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReceivingCheckInTest {
    private lateinit var db: HhoDatabase
    private lateinit var receiving: ReceivingRepository
    private lateinit var checkIn: ReceivingCheckIn
    private val syncCalls = mutableListOf<Pair<Boolean, Int>>()
    private val date = LocalDate.of(2026, 3, 4)

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = inMemoryHhoDatabase()
        val ids = UuidV7Generator()
        val edits = EditRepository(db, OutboxRepository(db, ids), ids) {
            syncCalls += db.openHelper.writableDatabase.inTransaction() to
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM outbox_mutation").use { it.moveToFirst(); it.getInt(0) }
        }
        checkIn = ReceivingCheckIn(db, edits) { 1234L }
        receiving = ReceivingRepository(db)
        for ((id, qty) in listOf("a" to 10L, "b" to 0L, "c" to 5L, "d" to 1L)) {
            db.itemDao().upsert(ItemEntity(id, 1, "Item $id", null, null, qty, null, 1, 1, 3))
        }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun rows() = db.outboxDao().getAllOrdered().map { LocalMutation.from(it) }

    private suspend fun session(): String {
        val s = receiving.startSession("Acme", "PO-7", date)
        receiving.addExpected(s.id, "a", 5)
        receiving.setReceived(s.id, "a", 4)
        receiving.addExpected(s.id, "b", 3)
        receiving.addExpected(s.id, "c", 2)
        receiving.setReceived(s.id, "c", 2)
        receiving.confirmUnexpected(s.id, "d")
        return s.id
    }

    @Test
    fun multiLine_writesAdjustmentsAndStamps_completes_syncsOnceAfterCommit() = runTest {
        val id = session()
        val r = checkIn.checkIn(id)
        assertEquals(CheckInResult.Completed(3), r)

        val m = rows()
        val adj = m.filter { it.entityType == "stock_adjustment" }
        assertEquals(3, adj.size)
        assertEquals(setOf("a" to 4L, "c" to 2L, "d" to 1L), adj.map { (it.fields["item_id"] as JsonPrimitive).content to (it.fields["delta"] as JsonPrimitive).content.toLong() }.toSet())
        adj.forEach {
            assertEquals(JsonPrimitive("receiving"), it.fields["reason"])
            assertEquals(JsonPrimitive("PO-7"), it.fields["note"])
        }
        val stamps = m.filter { it.entityType == "purchased_from_block" }
        assertEquals(3, stamps.size)
        stamps.forEach {
            assertEquals(setOf("item_id", "vendor", "purchased_on", "order_reference"), it.fields.keys)
            assertEquals(JsonPrimitive("Acme"), it.fields["vendor"])
            assertEquals(JsonPrimitive("2026-03-04"), it.fields["purchased_on"])
            assertEquals(0L, it.baseVersion)
        }
        assertEquals(14L, OutboxOverlay.derivedQuantity(db, "a"))
        assertEquals(0L, OutboxOverlay.derivedQuantity(db, "b"))
        assertEquals(7L, OutboxOverlay.derivedQuantity(db, "c"))
        assertEquals(2L, OutboxOverlay.derivedQuantity(db, "d"))
        assertNull(db.purchasedFromBlockDao().getByItemId("b"))
        assertNotNull(db.purchasedFromBlockDao().getByItemId("a"))

        val s = db.receivingDao().getSession(id)!!
        assertEquals(ReceivingStatus.COMPLETED, s.status)
        assertEquals(1234L, s.completedAt)
        assertEquals(listOf(false to 6), syncCalls)
    }

    @Test
    fun existingBlock_isUpdatedWithItsVersion_keepingPrice() = runTest {
        db.purchasedFromBlockDao().upsert(
            PurchasedFromBlockEntity("blk", 1, "a", 999, "Old", null, null, "n", 1, 1, 6),
        )
        val s = receiving.startSession("Acme", "PO-7", date)
        receiving.addExpected(s.id, "a", 1)
        receiving.setReceived(s.id, "a", 1)
        checkIn.checkIn(s.id)
        val stamp = rows().single { it.entityType == "purchased_from_block" }
        assertEquals("blk", stamp.entityId)
        assertEquals(6L, stamp.baseVersion)
        assertFalse("purchase_price_minor" in stamp.fields)
        assertEquals(999L, db.purchasedFromBlockDao().getByItemId("a")!!.purchasePriceMinor)
        assertEquals("Acme", db.purchasedFromBlockDao().getByItemId("a")!!.vendor)
    }

    @Test
    fun repeat_isNoOp_noRowsNoSync() = runTest {
        val id = session()
        checkIn.checkIn(id)
        val rowsBefore = rows()
        val r = checkIn.checkIn(id)
        assertEquals(CheckInResult.AlreadyCompleted, r)
        assertEquals(rowsBefore, rows())
        assertEquals(1, syncCalls.size)
    }

    @Test
    fun sessionWithNoReceivedLines_completesWithoutWritesOrSync() = runTest {
        val s = receiving.startSession("Acme")
        receiving.addExpected(s.id, "a", 2)
        assertEquals(CheckInResult.Completed(0), checkIn.checkIn(s.id))
        assertTrue(rows().isEmpty())
        assertTrue(syncCalls.isEmpty())
        assertEquals(ReceivingStatus.COMPLETED, db.receivingDao().getSession(s.id)!!.status)
    }

    @Test
    fun cancelledAndUnknownSessions_areErrors() = runTest {
        val s = receiving.startSession("Acme")
        receiving.cancel(s.id)
        try {
            checkIn.checkIn(s.id)
            fail()
        } catch (e: ReceivingException.SessionNotOpen) {
            assertEquals(ReceivingStatus.CANCELLED, e.status)
        }
        try {
            checkIn.checkIn("nope")
            fail()
        } catch (_: ReceivingException.SessionNotFound) {
        }
        assertTrue(rows().isEmpty())
        assertTrue(syncCalls.isEmpty())
    }

    @Test
    fun rejectedLine_rollsBackWholeCheckIn() = runTest {
        val id = session()
        db.openHelper.writableDatabase.execSQL("DELETE FROM item WHERE id = 'd'")
        val r = checkIn.checkIn(id)
        assertTrue((r as CheckInResult.Failed).error is EditError.NotFound)
        assertTrue(rows().isEmpty())
        assertEquals(ReceivingStatus.OPEN, db.receivingDao().getSession(id)!!.status)
        assertTrue(syncCalls.isEmpty())
    }

    @Test
    fun killMidCheckIn_leavesSessionOpen_outboxAndMirrorUntouched() = runTest {
        val id = session()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER die_4th BEFORE INSERT ON outbox_mutation WHEN (SELECT COUNT(*) FROM outbox_mutation) >= 3 " +
                "BEGIN SELECT RAISE(ABORT, 'killed'); END",
        )
        val r = checkIn.checkIn(id)
        assertTrue((r as CheckInResult.Failed).error is EditError.StorageFailed)
        assertTrue(rows().isEmpty())
        assertEquals(ReceivingStatus.OPEN, db.receivingDao().getSession(id)!!.status)
        assertNull(db.receivingDao().getSession(id)!!.completedAt)
        assertTrue(db.stockAdjustmentDao().observeByItemId("a").first().isEmpty())
        assertNull(db.purchasedFromBlockDao().getByItemId("a"))
        assertEquals(10L, OutboxOverlay.derivedQuantity(db, "a"))
        assertTrue(syncCalls.isEmpty())

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER die_4th")
        assertEquals(CheckInResult.Completed(3), checkIn.checkIn(id))
        assertEquals(6, rows().size)
    }

    private class JvmTripwire : Interceptor {
        val calls = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            calls += chain.request().url.toString()
            throw IOException("NetworkTripwire: network access attempted in an offline test")
        }
    }

    private fun tripwireClient(tripwire: JvmTripwire): HhoApiClient {
        val http = OkHttpClient.Builder().addInterceptor(tripwire).build()
        return HhoApiClient(http, http)
    }

    private fun serverClient(server: MockWebServer): HhoApiClient {
        val file = File.createTempFile("hho-checkin-test", ".preferences_pb").also { it.deleteOnExit() }
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

    private fun str(m: LocalMutation, key: String) = (m.fields[key] as JsonPrimitive).content

    @Test
    fun tripwire_recordsAndRefuses_soZeroCallsMeansZero() = runBlocking {
        val tripwire = JvmTripwire()
        assertTrue(tripwireClient(tripwire).getStatus().isFailure)
        assertEquals(1, tripwire.calls.size)
    }

    @Test
    fun offlineCheckIn_expectedAndUnexpected_mirrorOutboxAndLedgerPush() = runBlocking {
        val tripwire = JvmTripwire()
        val offlineApi = tripwireClient(tripwire)
        db.itemDao().upsert(ItemEntity("a", 1, "Item a", null, null, 5, null, 1, 1, 3))
        db.itemDao().upsert(ItemEntity("b", 1, "Item b", null, null, 0, null, 1, 1, 3))
        db.itemDao().upsert(ItemEntity("c", 1, "Item c", null, null, 0, null, 1, 1, 3))
        db.purchasedFromBlockDao().upsert(
            PurchasedFromBlockEntity("blk-a", 1, "a", 999, "Old", LocalDate.of(2020, 1, 1), "PO-OLD", "n", 1, 1, 6),
        )

        val s = receiving.startSession("ACME", "PO-9", date)
        receiving.addExpected(s.id, "a", 3)
        receiving.addExpected(s.id, "b", 2)
        repeat(3) { assertTrue(receiving.recordScan(s.id, "a") is ScanOutcome.Counted) }
        assertTrue(receiving.recordScan(s.id, "b") is ScanOutcome.Counted)
        assertEquals(ScanOutcome.Unexpected("c"), receiving.recordScan(s.id, "c"))
        receiving.confirmUnexpected(s.id, "c")
        assertEquals(CheckInResult.Completed(3), checkIn.checkIn(s.id))
        assertEquals("no network call during start -> scan -> check-in", emptyList<String>(), tripwire.calls)
        assertNotNull(offlineApi)

        assertEquals(8L, OutboxOverlay.derivedQuantity(db, "a"))
        assertEquals(1L, OutboxOverlay.derivedQuantity(db, "b"))
        assertEquals(1L, OutboxOverlay.derivedQuantity(db, "c"))
        assertEquals(ReceivingStatus.COMPLETED, db.receivingDao().getSession(s.id)!!.status)

        val blockA = db.purchasedFromBlockDao().getByItemId("a")!!
        assertEquals("blk-a", blockA.id)
        assertEquals("ACME", blockA.vendor)
        assertEquals("PO-9", blockA.orderReference)
        assertEquals(date, blockA.purchasedOn)
        assertEquals(999L, blockA.purchasePriceMinor)
        for (id in listOf("b", "c")) {
            val b = db.purchasedFromBlockDao().getByItemId(id)
            assertNotNull("block for $id created in the mirror", b)
            assertEquals("ACME", b!!.vendor)
            assertEquals("PO-9", b.orderReference)
            assertEquals(date, b.purchasedOn)
        }

        val rowsBefore = db.outboxDao().getAllOrdered()
        val m = rows()
        assertEquals(6, m.size)
        val adj = m.filter { it.entityType == "stock_adjustment" }
        assertEquals(mapOf("a" to 3L, "b" to 1L, "c" to 1L), adj.associate { str(it, "item_id") to str(it, "delta").toLong() })
        adj.forEach {
            assertEquals(setOf("item_id", "delta", "reason", "note"), it.fields.keys)
            assertEquals("receiving", str(it, "reason"))
            assertEquals("PO-9", str(it, "note"))
            assertEquals(0L, it.baseVersion)
        }
        val stamps = m.filter { it.entityType == "purchased_from_block" }.associateBy { str(it, "item_id") }
        assertEquals(setOf("a", "b", "c"), stamps.keys)
        stamps.values.forEach {
            assertEquals(setOf("item_id", "vendor", "order_reference", "purchased_on"), it.fields.keys)
            assertEquals("ACME", str(it, "vendor"))
            assertEquals("PO-9", str(it, "order_reference"))
            assertEquals("2026-03-04", str(it, "purchased_on"))
        }
        assertEquals("blk-a", stamps.getValue("a").entityId)
        assertEquals(6L, stamps.getValue("a").baseVersion)
        assertEquals(0L, stamps.getValue("b").baseVersion)
        assertEquals(0L, stamps.getValue("c").baseVersion)
        assertTrue(rowsBefore.all { it.state == dev.hho.android.data.room.OutboxState.PENDING })

        val fake = LedgerFakeServer()
        val server = MockWebServer().also { it.dispatcher = fake }
        try {
            val out = OutboxSyncer(db, serverClient(server), OutboxRepository(db)).push("device-1")
            assertEquals(PushPhaseOutcome.Drained, out)
        } finally {
            server.shutdown()
        }
        assertTrue("outbox drained", db.outboxDao().getAllOrdered().isEmpty())
        assertEquals(rowsBefore.map { it.mutationId }, fake.applyOrder)
        assertTrue("each mutation applied exactly once (none skipped / replayed)", fake.appliedCount.values.all { it == 1 })
        assertEquals(6, fake.appliedCount.size)
        rowsBefore.forEach { assertEquals(it.baseVersion, fake.baseVersions[it.mutationId]) }
        assertEquals(1L, fake.versions["purchased_from_block/blk-a"])
        val stateA = fake.entityState.getValue("purchased_from_block/blk-a")
        assertEquals("ACME", stateA["vendor"])
        assertEquals("PO-9", stateA["order_reference"])
        assertFalse("purchase_price_minor" in stateA)
        assertEquals(3, fake.entityState.keys.count { it.startsWith("stock_adjustment/") })
        assertEquals(
            listOf("1", "1", "3"),
            fake.entityState.filterKeys { it.startsWith("stock_adjustment/") }.values.map { it.getValue("delta") }.sorted(),
        )
        assertEquals(1, fake.pushRequests)
        assertEquals("the tripwire client stayed untouched through the push phase too", emptyList<String>(), tripwire.calls)
    }
}
