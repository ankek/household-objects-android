package dev.hho.android.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.PhotoQueueEntryEntity
import dev.hho.android.data.room.PhotoState
import dev.hho.android.data.room.SyncRunStateEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncStatusRepositoryTest {
    private lateinit var db: HhoDatabase
    private lateinit var repo: SyncStatusRepository

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        val photos =
            object : PhotoQueueStatus {
                override fun observePendingCount(): Flow<Int> =
                    db.photoQueueDao().observeCount(listOf(PhotoState.QUEUED, PhotoState.UPLOADING, PhotoState.WAITING_PARENT))

                override fun observeFailedCount(): Flow<Int> = db.photoQueueDao().observeCount(listOf(PhotoState.FAILED))
            }
        repo = SyncStatusRepository(db.outboxDao(), photos, db.syncRunStateDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun await(predicate: (SyncStatus) -> Boolean): SyncStatus = withTimeout(10_000) { repo.observe().first(predicate) }

    private suspend fun outbox(id: String, state: String) =
        db.outboxDao().insert(
            OutboxMutationEntity(
                mutationId = id, entityType = "item", entityId = id, op = "upsert", baseVersion = 1,
                fieldsJson = "{}", state = state, createdAt = 1,
            ),
        )

    private suspend fun photo(id: String, state: String) =
        db.photoQueueDao().insert(
            PhotoQueueEntryEntity(id, "i", "/x/$id", "h", 1, "image", state, createdAt = 1),
        )

    @Test
    fun emptyDatabase_isAllZeroAndNeverSynced() = runBlocking {
        val s = await { true }
        assertEquals(SyncStatus(), s)
        assertFalse(s.hasProblem)
    }

    @Test
    fun countsFollowRowChanges() = runBlocking {
        outbox("p", OutboxState.PENDING)
        outbox("f", OutboxState.IN_FLIGHT)
        outbox("h", OutboxState.HELD)
        outbox("bad", OutboxState.FAILED)
        photo("q", PhotoState.QUEUED)
        photo("w", PhotoState.WAITING_PARENT)
        photo("bad", PhotoState.FAILED)

        val s = await { it.pendingCount == 3 && it.photoQueueDepth == 2 }
        assertEquals(1, s.failedOutboxCount)
        assertEquals(1, s.failedPhotoCount)
        assertEquals(2, s.failedCount)
        assertTrue(s.hasProblem)

        db.outboxDao().deleteBySeq(db.outboxDao().getAllOrdered().first { it.mutationId == "p" }.seq)
        db.photoQueueDao().deleteById("bad")
        val after = await { it.pendingCount == 2 && it.failedPhotoCount == 0 }
        assertEquals(1, after.failedCount)
    }

    @Test
    fun runState_flowsThrough_andErrorHidesLastSyncedAt() = runBlocking {
        db.syncRunStateDao().upsert(SyncRunStateEntity(lastRunAt = 50, lastPushAt = 40))
        val ok = await { it.lastSyncedAt != null }
        assertEquals(50L, ok.lastSyncedAt)
        assertEquals(40L, ok.lastPushAt)
        assertNull(ok.lastError)
        assertFalse(ok.hasProblem)

        SyncRunRecorder(db.syncRunStateDao()) { 60 }.recordFailure("Not authenticated")
        val bad = await { it.lastError != null }
        assertNull(bad.lastSyncedAt)
        assertEquals("Not authenticated", bad.lastError)
        assertEquals(40L, bad.lastPushAt)
        assertTrue(bad.hasProblem)
    }
}
