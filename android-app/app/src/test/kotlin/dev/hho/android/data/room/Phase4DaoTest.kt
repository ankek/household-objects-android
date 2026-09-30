package dev.hho.android.data.room

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class Phase4DaoTest {
    private lateinit var db: HhoDatabase

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
    }

    @After
    fun tearDown() = db.close()

    private fun session(id: String, status: String = ReceivingStatus.OPEN, createdAt: Long = 1) =
        ReceivingSessionEntity(
            id = id,
            vendor = "Acme",
            orderReference = "PO-1",
            purchasedOn = LocalDate.of(2026, 3, 4),
            status = status,
            createdAt = createdAt,
        )

    @Test
    fun receiving_sessionRoundTrips_andObservesByStatusNewestFirst() = runTest {
        val dao = db.receivingDao()
        dao.insertSession(session("s1", createdAt = 1))
        dao.insertSession(session("s2", createdAt = 2))
        dao.insertSession(session("s3", ReceivingStatus.COMPLETED, 3))
        assertEquals(LocalDate.of(2026, 3, 4), dao.getSession("s1")?.purchasedOn)
        assertEquals(listOf("s2", "s1"), dao.observeByStatus(ReceivingStatus.OPEN).first().map { it.id })
        dao.updateSession(dao.getSession("s1")!!.copy(status = ReceivingStatus.COMPLETED, completedAt = 9))
        assertEquals(listOf("s2"), dao.observeByStatus(ReceivingStatus.OPEN).first().map { it.id })
        assertEquals(9L, dao.getSession("s1")?.completedAt)
    }

    @Test
    fun receiving_linePkIsSessionAndItem_upsertReplaces_andAddReceivedIncrements() = runTest {
        val dao = db.receivingDao()
        dao.insertSession(session("s1"))
        dao.upsertLine(ReceivingLineEntity("s1", "i1", expectedQty = 5, receivedQty = 0))
        dao.upsertLine(ReceivingLineEntity("s1", "i2"))
        dao.upsertLine(ReceivingLineEntity("s1", "i1", expectedQty = 6, receivedQty = 1))
        assertEquals(listOf("i1", "i2"), dao.getLines("s1").map { it.itemId })
        assertEquals(6, dao.getLines("s1").first().expectedQty)
        assertNull(dao.getLines("s1")[1].expectedQty)

        assertEquals(1, dao.addReceived("s1", "i1", 2))
        assertEquals(3, dao.getLines("s1").first().receivedQty)
        assertEquals(0, dao.addReceived("s1", "missing", 1))

        dao.deleteLines("s1")
        dao.deleteSession("s1")
        assertEquals(emptyList<ReceivingLineEntity>(), dao.getLines("s1"))
        assertNull(dao.getSession("s1"))
    }

    private fun photo(id: String, state: String = PhotoState.QUEUED, createdAt: Long = 1, next: Long? = null) =
        PhotoQueueEntryEntity(
            id = id,
            itemId = "i1",
            filePath = "/f/$id.jpg",
            sha256 = "ab",
            sizeBytes = 10,
            category = "image",
            state = state,
            nextAttemptAt = next,
            createdAt = createdAt,
        )

    @Test
    fun photoQueue_ordersOldestFirst_filtersByState_andHonoursBackoff() = runTest {
        val dao = db.photoQueueDao()
        dao.insert(photo("p2", createdAt = 2))
        dao.insert(photo("p1", createdAt = 1, next = 500))
        dao.insert(photo("p3", PhotoState.WAITING_PARENT, 3))
        dao.insert(photo("p4", PhotoState.FAILED, 4))
        assertEquals(listOf("p1", "p2", "p3", "p4"), dao.getAllOrdered().map { it.id })
        val eligible = listOf(PhotoState.QUEUED, PhotoState.WAITING_PARENT)
        assertEquals(listOf("p1", "p2", "p3"), dao.getByStates(eligible).map { it.id })
        assertEquals(listOf("p2", "p3"), dao.getDue(eligible, now = 100).map { it.id })
        assertEquals(listOf("p1", "p2", "p3"), dao.getDue(eligible, now = 500).map { it.id })
        assertEquals(1, dao.observeCount(listOf(PhotoState.FAILED)).first())
    }

    @Test
    fun photoQueue_updateProgress_andDelete() = runTest {
        val dao = db.photoQueueDao()
        dao.insert(photo("p1"))
        dao.updateProgress("p1", PhotoState.FAILED, 8, null, "413")
        val row = dao.getById("p1")!!
        assertEquals(PhotoState.FAILED, row.state)
        assertEquals(8, row.attemptCount)
        assertEquals("413", row.lastError)
        dao.deleteById("p1")
        assertNull(dao.getById("p1"))
    }

    @Test
    fun photoQueue_duplicateIdIsRejected() = runTest {
        val dao = db.photoQueueDao()
        dao.insert(photo("p1"))
        assertThrows(SQLiteConstraintException::class.java) { kotlinx.coroutines.runBlocking { dao.insert(photo("p1")) } }
    }

    private fun conflict(mutationId: String, entityId: String, field: String, origin: String = ConflictOrigin.LOCAL_PUSH, at: Long = 1) =
        ConflictRecordEntity(
            mutationId = mutationId,
            entityType = "item",
            entityId = entityId,
            fieldName = field,
            detectedAt = at,
            origin = origin,
        )

    @Test
    fun conflict_uniqueOnWireKey_secondInsertIsIgnored() = runTest {
        val dao = db.conflictRecordDao()
        val first = dao.insertIgnore(conflict("m1", "e1", "name"))
        assertNotEquals(-1L, first)
        assertEquals(-1L, dao.insertIgnore(conflict("m1", "e1", "name", ConflictOrigin.SERVER_LOG)))
        assertNotEquals(-1L, dao.insertIgnore(conflict("m1", "e1", "description")))
        assertNotEquals(-1L, dao.insertIgnore(conflict("m1", "e2", "name")))
        assertNotEquals(-1L, dao.insertIgnore(conflict("m2", "e1", "name")))
        assertEquals(4, dao.getAll().size)
        assertEquals(ConflictOrigin.LOCAL_PUSH, dao.find("m1", "e1", "name")?.origin)
    }

    @Test
    fun conflict_enrichFillsValues_andListIsNewestFirst() = runTest {
        val dao = db.conflictRecordDao()
        dao.insertIgnore(conflict("m1", "e1", "name", at = 10))
        dao.insertIgnore(conflict("m2", "e1", "name", at = 20))
        assertEquals(1, dao.enrich("m1", "e1", "name", "\"mine\"", "\"theirs\""))
        assertEquals(0, dao.enrich("nope", "e1", "name", null, null))
        val found = dao.find("m1", "e1", "name")!!
        assertEquals("\"mine\"", found.losingValueJson)
        assertEquals("\"theirs\"", found.serverValueJson)
        assertEquals(listOf("m2", "m1"), dao.observeAll().first().map { it.mutationId })
    }

    @Test
    fun syncRunState_isAbsentUntilWritten_andUpsertReplacesTheSingleton() = runTest {
        val dao = db.syncRunStateDao()
        assertNull(dao.get())
        dao.upsert(SyncRunStateEntity(lastRunAt = 1, conflictCursor = "c1"))
        dao.upsert(SyncRunStateEntity(lastRunAt = 2, lastError = "boom", lastErrorAt = 2, conflictCursor = "c2"))
        val row = dao.observe().first()!!
        assertEquals(SyncRunStateEntity.SINGLETON_ID, row.id)
        assertEquals(2L, row.lastRunAt)
        assertEquals("c2", row.conflictCursor)
        assertNull(row.lastPushAt)
        assertNull(db.syncStateDao().get())
    }
}
