package dev.hho.android.data.receiving

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ReceivingStatus
import dev.hho.android.data.room.inMemoryHhoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReceivingRepositoryTest {
    private lateinit var db: HhoDatabase
    private lateinit var repo: ReceivingRepository
    private var ids = 0
    private var now = 1_000L

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        repo = ReceivingRepository(db, { "s-${ids++}" }, { now++ })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun mirror(vararg itemIds: String) =
        itemIds.forEach { db.itemDao().upsert(ItemEntity(it, 1, "n-$it", null, null, 0, null, null, null, 1)) }

    private suspend fun open(): String {
        mirror("a", "b", "c")
        return repo.startSession("Acme", "PO-1", LocalDate.of(2026, 9, 1)).id
    }

    @Test
    fun startSessionStoresTrimmedHeader() = runTest {
        val s = repo.startSession("  Acme ", " PO-9 ", LocalDate.of(2026, 9, 1))
        assertEquals("s-0", s.id)
        assertEquals("Acme", s.vendor)
        assertEquals("PO-9", s.orderReference)
        assertEquals(ReceivingStatus.OPEN, db.receivingDao().getSession(s.id)?.status)
        assertNull(repo.startSession("V", "  ").orderReference)
        assertEquals(2, repo.observeOpenSessions().first().size)
    }

    @Test
    fun blankVendorRejected() = runTest {
        assertThrows(ReceivingException.InvalidArgument::class.java) { kotlinx.coroutines.runBlocking { repo.startSession("  ") } }
    }

    @Test
    fun addExpectedIsIdempotentAndKeepsReceived() = runTest {
        val s = open()
        repo.addExpected(s, "a", 3)
        repo.recordScan(s, "a")
        repo.addExpected(s, "a", 3)
        assertEquals(1, repo.observeLines(s).first().size)
        assertEquals(1, db.receivingDao().getLine(s, "a")?.receivedQty)
        repo.addExpected(s, "a", 5)
        assertEquals(5, db.receivingDao().getLine(s, "a")?.expectedQty)
        assertEquals(1, db.receivingDao().getLine(s, "a")?.receivedQty)
    }

    @Test
    fun addExpectedRejectsBadQtyAndUnknownItem() = runTest {
        val s = open()
        assertThrows(ReceivingException.InvalidArgument::class.java) { kotlinx.coroutines.runBlocking { repo.addExpected(s, "a", 0) } }
        assertThrows(ReceivingException.UnknownItem::class.java) { kotlinx.coroutines.runBlocking { repo.addExpected(s, "zzz", 1) } }
        assertTrue(repo.observeLines(s).first().isEmpty())
    }

    @Test
    fun scanExpectedIncrements() = runTest {
        val s = open()
        repo.addExpected(s, "a", 2)
        repo.recordScan(s, "a")
        val out = repo.recordScan(s, "a") as ScanOutcome.Counted
        assertEquals(2, out.line.receivedQty)
        assertEquals(2, out.line.expectedQty)
    }

    @Test
    fun scanUnexpectedWritesNothingUntilConfirmed() = runTest {
        val s = open()
        repo.addExpected(s, "a", 1)
        assertEquals(ScanOutcome.Unexpected("b"), repo.recordScan(s, "b"))
        assertNull(db.receivingDao().getLine(s, "b"))
        val line = repo.confirmUnexpected(s, "b")
        assertNull(line.expectedQty)
        assertEquals(1, line.receivedQty)
        assertEquals(2, (repo.recordScan(s, "b") as ScanOutcome.Counted).line.receivedQty)
        assertEquals(3, repo.confirmUnexpected(s, "b").receivedQty)
    }

    @Test
    fun unexpectedCanBePromotedToExpected() = runTest {
        val s = open()
        repo.confirmUnexpected(s, "b")
        val promoted = repo.addExpected(s, "b", 4)
        assertEquals(4, promoted.expectedQty)
        assertEquals(1, promoted.receivedQty)
    }

    @Test
    fun scanUnknownItemRejected() = runTest {
        val s = open()
        assertThrows(ReceivingException.UnknownItem::class.java) { kotlinx.coroutines.runBlocking { repo.recordScan(s, "nope") } }
        assertThrows(ReceivingException.UnknownItem::class.java) { kotlinx.coroutines.runBlocking { repo.confirmUnexpected(s, "nope") } }
        assertTrue(repo.observeLines(s).first().isEmpty())
    }

    @Test
    fun manualAdjustSetsAndBounds() = runTest {
        val s = open()
        repo.addExpected(s, "a", 2)
        assertEquals(7, repo.setReceived(s, "a", 7).receivedQty)
        assertEquals(0, repo.setReceived(s, "a", 0).receivedQty)
        assertThrows(ReceivingException.InvalidArgument::class.java) { kotlinx.coroutines.runBlocking { repo.setReceived(s, "a", -1) } }
        assertThrows(ReceivingException.LineNotFound::class.java) { kotlinx.coroutines.runBlocking { repo.setReceived(s, "b", 1) } }
        assertEquals(0, db.receivingDao().getLine(s, "a")?.receivedQty)
    }

    @Test
    fun discrepancyClassesAndTotals() = runTest {
        val s = open()
        repo.addExpected(s, "a", 2)
        repo.addExpected(s, "b", 2)
        repo.addExpected(s, "c", 2)
        repo.recordScan(s, "a")
        repo.setReceived(s, "b", 3)
        repo.setReceived(s, "c", 2)
        mirror("d")
        repo.confirmUnexpected(s, "d")
        val sum = repo.summary(s)
        assertEquals(listOf("a"), sum.shortLines.map { it.itemId })
        assertEquals(-1, sum.shortLines.single().difference)
        assertEquals(listOf("b"), sum.overLines.map { it.itemId })
        assertEquals(1, sum.overLines.single().difference)
        assertEquals(listOf("d"), sum.unexpectedLines.map { it.itemId })
        assertNull(sum.unexpectedLines.single().difference)
        assertEquals(1, sum.matchedCount)
        assertEquals(6, sum.totalExpected)
        assertEquals(7, sum.totalReceived)
        assertEquals(false, sum.isClean)
        assertEquals(sum, repo.observeSummary(s).first())
    }

    @Test
    fun cleanSummaryWhenAllMatch() = runTest {
        val s = open()
        repo.addExpected(s, "a", 1)
        repo.recordScan(s, "a")
        assertTrue(repo.summary(s).isClean)
    }

    @Test
    fun cancelKeepsLinesAndIsIdempotent() = runTest {
        val s = open()
        repo.addExpected(s, "a", 1)
        repo.cancel(s)
        val row = db.receivingDao().getSession(s)!!
        assertEquals(ReceivingStatus.CANCELLED, row.status)
        assertTrue(row.completedAt != null)
        assertEquals(1, repo.summary(s).lines.size)
        repo.cancel(s)
        assertTrue(repo.observeOpenSessions().first().isEmpty())
    }

    @Test
    fun closedSessionsRejectMutations() = runTest {
        val s = open()
        repo.addExpected(s, "a", 1)
        repo.cancel(s)
        assertThrows(ReceivingException.SessionNotOpen::class.java) { kotlinx.coroutines.runBlocking { repo.addExpected(s, "b", 1) } }
        assertThrows(ReceivingException.SessionNotOpen::class.java) { kotlinx.coroutines.runBlocking { repo.recordScan(s, "a") } }
        assertThrows(ReceivingException.SessionNotOpen::class.java) { kotlinx.coroutines.runBlocking { repo.confirmUnexpected(s, "b") } }
        assertThrows(ReceivingException.SessionNotOpen::class.java) { kotlinx.coroutines.runBlocking { repo.setReceived(s, "a", 1) } }
        assertEquals(0, db.receivingDao().getLine(s, "a")?.receivedQty)
        assertNull(db.receivingDao().getLine(s, "b"))
    }

    @Test
    fun completedSessionRejectsCancelAndScan() = runTest {
        val s = open()
        val row = db.receivingDao().getSession(s)!!
        db.receivingDao().updateSession(row.copy(status = ReceivingStatus.COMPLETED))
        val e = assertThrows(ReceivingException.SessionNotOpen::class.java) { kotlinx.coroutines.runBlocking { repo.cancel(s) } }
        assertEquals(ReceivingStatus.COMPLETED, e.status)
        assertThrows(ReceivingException.SessionNotOpen::class.java) { kotlinx.coroutines.runBlocking { repo.recordScan(s, "a") } }
    }

    @Test
    fun missingSession() = runTest {
        assertThrows(ReceivingException.SessionNotFound::class.java) { kotlinx.coroutines.runBlocking { repo.cancel("ghost") } }
        assertThrows(ReceivingException.SessionNotFound::class.java) { kotlinx.coroutines.runBlocking { repo.recordScan("ghost", "a") } }
    }

    @Test
    fun flowsEmitUpdates() = runTest {
        val s = open()
        repo.addExpected(s, "a", 2)
        repo.recordScan(s, "a")
        assertEquals(1, repo.observeLines(s).first().single().receivedQty)
        assertEquals("Acme", repo.observeSession(s).first()?.vendor)
        assertNull(repo.observeSession("ghost").first())
    }

    @Test
    fun concurrentAdjustsLandExactly() = kotlinx.coroutines.runBlocking {
        val scope = this
        val s = open()
        repo.addExpected(s, "a", 100)
        (1..50).map { scope.async(kotlinx.coroutines.Dispatchers.IO) { repo.adjustReceived(s, "a", 1) } }
            .forEach { it.await() }
        assertEquals(50, db.receivingDao().getLine(s, "a")?.receivedQty)
        (1..80).map { scope.async(kotlinx.coroutines.Dispatchers.IO) { repo.adjustReceived(s, "a", -1) } }
            .forEach { it.await() }
        assertEquals(0, db.receivingDao().getLine(s, "a")?.receivedQty)
    }

    @Test
    fun adjustNeverGoesBelowZeroAndReturnsLine() = runTest {
        val s = open()
        repo.addExpected(s, "a", 2)
        assertEquals(0, repo.adjustReceived(s, "a", -1).receivedQty)
        assertEquals(3, repo.adjustReceived(s, "a", 3).receivedQty)
        assertEquals(0, repo.adjustReceived(s, "a", -9).receivedQty)
    }

    @Test
    fun adjustRefusesClosedSessionMissingLineAndUnknownSession() = runTest {
        val s = open()
        repo.addExpected(s, "a", 2)
        repo.adjustReceived(s, "a", 2)
        assertThrows(ReceivingException.LineNotFound::class.java) {
            kotlinx.coroutines.runBlocking { repo.adjustReceived(s, "b", 1) }
        }
        assertThrows(ReceivingException.SessionNotFound::class.java) {
            kotlinx.coroutines.runBlocking { repo.adjustReceived("ghost", "a", 1) }
        }
        repo.cancel(s)
        val e = assertThrows(ReceivingException.SessionNotOpen::class.java) {
            kotlinx.coroutines.runBlocking { repo.adjustReceived(s, "a", 1) }
        }
        assertEquals(ReceivingStatus.CANCELLED, e.status)
        assertEquals(2, db.receivingDao().getLine(s, "a")?.receivedQty)
    }
}
