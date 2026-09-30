package dev.hho.android.ui.receiving

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.items.ItemSearch
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.receiving.LineStatus
import dev.hho.android.data.receiving.ReceivingRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.ReceivingStatus
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.scanner.BarcodeFormat
import dev.hho.android.data.scanner.DecodedBarcode
import dev.hho.android.domain.EditRepository
import dev.hho.android.domain.ReceivingCheckIn
import dev.hho.android.ui.deeplink.DeepLinkResolver
import dev.hho.android.ui.items.ViewModelTracker
import dev.hho.android.ui.scan.ScanResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReceivingViewModelTest {
    private lateinit var db: HhoDatabase
    private lateinit var vm: ReceivingViewModel
    private var syncCalls = 0
    private var clock = 0L
    private val viewModels = ViewModelTracker()

    private lateinit var drill: ItemEntity
    private lateinit var saw: ItemEntity
    private lateinit var tape: ItemEntity

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        drill = item("drill", "Drill")
        saw = item("saw", "Saw")
        tape = item("tape", "Tape")
        for ((n, item) in listOf(drill, saw, tape).withIndex()) {
            db.itemDao().upsert(item)
            db.itemIdentificationDao().upsert(
                ItemIdentificationEntity("id$n", 1L, item.id, "barcode", item.shortCode!!, 1L, 1L, 1L),
            )
        }
        for ((n, item) in listOf(drill, saw).withIndex()) {
            db.itemIdentificationDao().upsert(
                ItemIdentificationEntity("dup$n", 1L, item.id, "barcode", "999", 1L, 1L, 1L),
            )
        }
        val ids = UuidV7Generator()
        val edits = EditRepository(db, OutboxRepository(db, ids), ids) { syncCalls++ }
        vm = viewModels.track(
            ReceivingViewModel(
                repo = ReceivingRepository(db),
                checkIn = ReceivingCheckIn(db, edits),
                resolver = ScanResolver(DeepLinkResolver(db.itemDao()), ItemSearch(db.itemDao()), db.itemDao()),
                itemSearch = ItemSearch(db.itemDao()),
                itemDao = db.itemDao(),
            ),
        )
        vm.nowMillis = { clock }
    }

    @After fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun item(id: String, name: String) = ItemEntity(
        id, 1, name, null, null, 0, when (id) { "drill" -> "111"; "saw" -> "222"; else -> "333" }, 1, 1, 1,
    )

    private fun code(v: String) = listOf(DecodedBarcode(v, BarcodeFormat.QR_CODE))

    private fun await(what: String = "condition", condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) { while (!condition()) kotlinx.coroutines.delay(10) }
    }

    private fun sessionState() = vm.session.value

    private fun line(id: String) = sessionState()?.lines?.firstOrNull { it.itemId == id }

    private fun startSession(vendor: String = "Acme", order: String = "PO-7", date: String = "2026-03-04"): String {
        vm.newSession()
        vm.createSession(vendor, order, date)
        await { sessionState() != null }
        return sessionState()!!.session.id
    }

    private fun scan(value: String) {
        clock += 10_000
        vm.onBarcodes(code(value))
    }

    @Test fun headerRequiresVendorAndValidDate() {
        vm.newSession()
        vm.createSession("  ", "PO", "")
        assertNotNull(vm.header.value.vendorError)
        assertEquals(ReceivingMode.Header, vm.mode.value)

        vm.createSession("Acme", "PO", "04/03/2026")
        assertNotNull(vm.header.value.dateError)
        assertNull(vm.header.value.vendorError)
        assertEquals(ReceivingMode.Header, vm.mode.value)
        assertTrue(runBlocking { db.receivingDao().observeByStatus(ReceivingStatus.OPEN).first() }.isEmpty())
    }

    @Test fun validHeaderStartsAndOpensSession() {
        val id = startSession(order = " PO-7 ")
        val s = sessionState()!!.session
        assertEquals(id, s.id)
        assertEquals("Acme", s.vendor)
        assertEquals("PO-7", s.orderReference)
        assertEquals(LocalDate.of(2026, 3, 4), s.purchasedOn)
        assertEquals(ReceivingMode.Session(id), vm.mode.value)
        await { vm.openSessions.value.size == 1 }
    }

    @Test fun blankOrderAndDateAreOptional() {
        startSession(order = "", date = "")
        assertNull(sessionState()!!.session.orderReference)
        assertNull(sessionState()!!.session.purchasedOn)
    }

    @Test fun pickerAddsExpectedLineOnceAndPromotesNothingTwice() {
        startSession()
        vm.setQuery("dri")
        await { vm.pickerResults.value.map { it.id } == listOf("drill") }
        vm.addExpected(vm.pickerResults.value.single())
        await { line("drill") != null }
        assertEquals(1, line("drill")!!.discrepancy.expectedQty)

        vm.setExpected("drill", 4)
        await { line("drill")!!.discrepancy.expectedQty == 4 }
        vm.addExpected(drill)
        await { sessionState()!!.message?.contains("already") == true }
        assertEquals(4, line("drill")!!.discrepancy.expectedQty)
    }

    @Test fun scanSingleIncrementsAndSummaryIsLive() {
        startSession()
        vm.addExpected(drill)
        await { line("drill") != null }
        vm.setExpected("drill", 2)
        await { line("drill")?.discrepancy?.expectedQty == 2 }
        vm.setScanning(true)
        assertFalse(vm.paused)

        scan("111")
        await { line("drill")?.discrepancy?.receivedQty == 1 }
        assertEquals(1, sessionState()!!.summary.shortLines.size)
        scan("111")
        await { line("drill")?.discrepancy?.receivedQty == 2 }
        assertEquals(1, sessionState()!!.summary.matchedCount)
        assertEquals(LineStatus.MATCHED, line("drill")!!.discrepancy.status)
        vm.increment("drill")
        await { sessionState()!!.summary.overLines.size == 1 }
        vm.decrement("drill")
        await { line("drill")!!.discrepancy.receivedQty == 2 }
        vm.setReceived("drill", 0)
        await { line("drill")!!.discrepancy.receivedQty == 0 }
        vm.decrement("drill")
        Thread.sleep(200)
        assertEquals(0, line("drill")!!.discrepancy.receivedQty)
        assertEquals(1, sessionState()!!.summary.shortLines.size)
    }

    @Test fun unexpectedScanAsksThenConfirmAddsUnplannedLine() {
        startSession()
        vm.setScanning(true)
        scan("222")
        await { sessionState()?.pendingUnexpected != null }
        assertEquals("Saw", sessionState()!!.pendingUnexpected!!.name)
        assertTrue("analysis pauses while asking", vm.paused)
        assertNull("nothing is written before confirmation", line("saw"))

        vm.confirmUnexpected()
        await { line("saw") != null }
        assertEquals(LineStatus.UNEXPECTED, line("saw")!!.discrepancy.status)
        assertEquals(1, line("saw")!!.discrepancy.receivedQty)
        assertNull(sessionState()!!.pendingUnexpected)
        assertEquals(1, sessionState()!!.summary.unexpectedLines.size)
    }

    @Test fun decliningUnexpectedWritesNothing() {
        startSession()
        vm.setScanning(true)
        scan("222")
        await { sessionState()?.pendingUnexpected != null }
        vm.dismissUnexpected()
        await { sessionState()!!.pendingUnexpected == null }
        assertTrue(sessionState()!!.lines.isEmpty())
        await("analysis resumes") { !vm.paused }
    }

    @Test fun noMatchShowsMessageAndWritesNothing() {
        startSession()
        vm.setScanning(true)
        scan("nope")
        await { sessionState()?.message?.contains("nope") == true }
        assertTrue(sessionState()!!.lines.isEmpty())
        assertNull(sessionState()!!.pendingUnexpected)
    }

    @Test fun multipleMatchesAskThenCountChosenItem() {
        startSession()
        vm.addExpected(saw)
        await { line("saw") != null }
        vm.setScanning(true)
        scan("999")
        await { sessionState()?.choices?.size == 2 }
        vm.choose("saw")
        await { line("saw")?.discrepancy?.receivedQty == 1 }
        assertNull(sessionState()!!.choices)
    }

    @Test fun sameCodeInFrameIsNotCountedTwice() {
        startSession()
        vm.addExpected(drill)
        await { line("drill") != null }
        vm.setScanning(true)
        scan("111")
        await { line("drill")?.discrepancy?.receivedQty == 1 }
        await("first resolve finishes") { !vm.paused }
        clock += 1_000
        vm.onBarcodes(code("111"))
        assertFalse("a repeat in the suppression window starts no resolve", vm.paused)
        Thread.sleep(200)
        assertEquals(1, line("drill")!!.discrepancy.receivedQty)
    }

    @Test fun checkInQueuesAdjustmentsAndStampsAndSyncsOnce() {
        val id = startSession()
        vm.addExpected(drill)
        vm.addExpected(saw)
        await { sessionState()!!.lines.size == 2 }
        vm.setReceived("drill", 3)
        await { line("drill")!!.discrepancy.receivedQty == 3 }

        vm.requestCheckIn()
        await { sessionState()!!.confirmingCheckIn }
        vm.confirmCheckIn()
        vm.confirmCheckIn()
        await { sessionState()!!.checkedIn }

        assertNull(sessionState()!!.checkInError)
        assertEquals(ReceivingStatus.COMPLETED, sessionState()!!.session.status)
        val rows = runBlocking { db.outboxDao().getAllOrdered() }.map { LocalMutation.from(it) }
        val adjustments = rows.filter { it.entityType == "stock_adjustment" }
        assertEquals(1, adjustments.size)
        assertEquals("3", (adjustments.single().fields["delta"] as JsonPrimitive).content)
        assertEquals(1, rows.count { it.entityType == "purchased_from_block" })
        assertEquals(1, syncCalls)
        assertEquals(id, sessionState()!!.session.id)
    }

    @Test fun repeatCheckInIsAlreadyCompletedAndWritesNothingMore() {
        startSession()
        vm.addExpected(drill)
        await { line("drill") != null }
        vm.setReceived("drill", 1)
        await { line("drill")!!.discrepancy.receivedQty == 1 }
        vm.confirmCheckIn()
        await { sessionState()!!.checkedIn }
        val before = runBlocking { db.outboxDao().getAllOrdered() }.size

        vm.confirmCheckIn()
        await { sessionState()!!.checkInError != null }
        assertTrue(sessionState()!!.checkInError!!.contains("already"))
        assertEquals(before, runBlocking { db.outboxDao().getAllOrdered() }.size)
        assertEquals(1, syncCalls)
    }

    @Test fun cancelClosesSessionAndReturnsToList() {
        val id = startSession()
        vm.requestCancel()
        await { sessionState()!!.confirmingCancel }
        vm.confirmCancel()
        await { vm.mode.value == ReceivingMode.Sessions }
        val stored = runBlocking { db.receivingDao().getSession(id) }
        assertEquals(ReceivingStatus.CANCELLED, stored!!.status)
        await { vm.openSessions.value.isEmpty() }
        assertTrue(runBlocking { db.outboxDao().getAllOrdered() }.isEmpty())
        assertEquals(0, syncCalls)
    }

    @Test fun severalOpenSessionsCanBeSwitched() {
        val first = startSession(vendor = "One")
        vm.showSessions()
        val second = startSession(vendor = "Two")
        await { vm.openSessions.value.size == 2 }
        vm.openSession(first)
        await { sessionState()?.session?.id == first }
        vm.openSession(second)
        await { sessionState()?.session?.id == second }
    }

    @Test fun burstOfTapsBeforeReEmitLandsExactly() {
        val sid = startSession()
        vm.addExpected(drill)
        await { line("drill") != null }
        repeat(25) { vm.increment("drill") }
        await { line("drill")!!.discrepancy.receivedQty == 25 }
        repeat(10) { vm.decrement("drill") }
        await { line("drill")!!.discrepancy.receivedQty == 15 }
        assertEquals(15, runBlocking { db.receivingDao().getLine(sid, "drill")!!.receivedQty })
    }
}
