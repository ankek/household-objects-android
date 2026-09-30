package dev.hho.android.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.SyncRunStateEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import kotlinx.coroutines.runBlocking
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncRunRecorderTest {
    private lateinit var db: HhoDatabase
    private var now = 100L
    private lateinit var recorder: SyncRunRecorder

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        recorder = SyncRunRecorder(db.syncRunStateDao()) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun row() = db.syncRunStateDao().get()!!

    @Test
    fun firstWrite_createsTheRow() = runBlocking {
        assertNull(db.syncRunStateDao().get())
        recorder.recordSuccess()
        assertEquals(100L, row().lastRunAt)
        assertNull(row().lastError)
    }

    @Test
    fun recordingARun_neverErasesReconcilePendingOrConflictCursor() = runBlocking {
        db.syncRunStateDao().upsert(SyncRunStateEntity(reconcilePending = true, conflictCursor = "777", lastPushAt = 5))

        recorder.recordSuccess()
        assertTrue(row().reconcilePending)
        assertEquals("777", row().conflictCursor)

        now = 200
        recorder.recordFailure("boom")
        assertTrue(row().reconcilePending)
        assertEquals("777", row().conflictCursor)
        assertEquals(5L, row().lastPushAt)

        recorder.recordPushReachedServer()
        assertTrue(row().reconcilePending)
        assertEquals("777", row().conflictCursor)
    }

    @Test
    fun failure_recordsErrorAndOnlyRaisesDebt() = runBlocking {
        now = 300
        recorder.recordFailure(null, reconcileOwed = false)
        assertEquals("sync failed", row().lastError)
        assertEquals(300L, row().lastErrorAt)
        assertFalse(row().reconcilePending)

        recorder.recordFailure("x", reconcileOwed = true)
        assertTrue(row().reconcilePending)
        recorder.recordFailure("y", reconcileOwed = false)
        assertTrue("a later failure must not clear the debt", row().reconcilePending)
        assertEquals("y", row().lastError)
    }

    @Test
    fun success_clearsTheErrorAndStampsTheRun() = runBlocking {
        recorder.recordFailure("boom")
        now = 400
        recorder.recordSuccess()
        assertNull(row().lastError)
        assertNull(row().lastErrorAt)
        assertEquals(400L, row().lastRunAt)
    }

    @Test
    fun pushTimestamp_andDebtAndWatermark_areIndependent() = runBlocking {
        recorder.setReconcilePending(true)
        recorder.advanceConflictWatermark(42)
        now = 500
        recorder.recordPushReachedServer()
        assertEquals(500L, row().lastPushAt)
        assertNull(row().lastRunAt)
        assertTrue(recorder.reconcileOwed())
        assertEquals(42L, recorder.conflictWatermark())
        recorder.setReconcilePending(false)
        assertFalse(recorder.reconcileOwed())
        assertNotNull(row().conflictCursor)
    }
}
