package dev.hho.android.ui.syncstatus

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.PhotoQueueEntryEntity
import dev.hho.android.data.room.PhotoState
import dev.hho.android.data.room.SyncRunStateEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.sync.PhotoQueueStatus
import dev.hho.android.data.sync.SyncStatusRepository
import dev.hho.android.ui.items.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncStatusViewModelTest {
    private lateinit var db: HhoDatabase
    private lateinit var vm: SyncStatusViewModel
    private lateinit var dispatcher: kotlinx.coroutines.test.TestDispatcher
    private var syncCalls = 0
    private val viewModels = ViewModelTracker()

    @Before fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        db = inMemoryHhoDatabase()
        val photoStatus = object : PhotoQueueStatus {
            override fun observePendingCount(): Flow<Int> =
                db.photoQueueDao().observeCount(listOf(PhotoState.QUEUED, PhotoState.UPLOADING, PhotoState.WAITING_PARENT))

            override fun observeFailedCount(): Flow<Int> = db.photoQueueDao().observeCount(listOf(PhotoState.FAILED))
        }
        vm = viewModels.track(
            SyncStatusViewModel(
                SyncStatusRepository(db.outboxDao(), photoStatus, db.syncRunStateDao()),
                db.outboxDao(),
                db.photoQueueDao(),
                db.itemDao(),
                db.conflictRecordDao(),
            ) { syncCalls++ },
        )
    }

    @After fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) { while (!condition()) kotlinx.coroutines.delay(10) }
    }

    private fun subscribe() = runBlocking { vm.state.first() }

    private fun outbox(id: String, type: String, entity: String, state: String, error: String? = null, fields: String = "{}") =
        OutboxMutationEntity(
            mutationId = id, entityType = type, entityId = entity, op = "upsert", baseVersion = 1,
            fieldsJson = fields, state = state, lastError = error, lastAttemptAt = 500L, createdAt = 100L,
        )

    private fun photo(id: String, item: String, state: String, error: String? = null, attempts: Int = 0) =
        PhotoQueueEntryEntity(id, item, "/x/$id", "sha", 1, "photo", state, attempts, null, error, 10L)

    private fun item(id: String, name: String) = ItemEntity(id, 1, name, null, null, 0, null, 1, 1, 1)

    @Test fun noFailureShowsEmptyUpToDateState() {
        subscribe()
        val ui = vm.state.value
        assertEquals(SyncHeadline.UP_TO_DATE, ui.headline)
        assertTrue(ui.failedMutations.isEmpty() && ui.failedPhotos.isEmpty())
        assertNull(ui.status.lastSyncedAt)
    }

    @Test fun statusReflectsOutboxPhotoAndRunStateLive() = runBlocking {
        subscribe()
        db.outboxDao().insert(outbox("m1", "item", "i1", OutboxState.PENDING))
        db.photoQueueDao().insert(photo("p1", "i1", PhotoState.QUEUED))
        await { vm.state.value.status.pendingCount == 1 && vm.state.value.status.photoQueueDepth == 1 }
        assertEquals(SyncHeadline.PENDING, vm.state.value.headline)

        db.syncRunStateDao().upsert(SyncRunStateEntity(lastRunAt = 9_000L))
        await { vm.state.value.status.lastSyncedAt == 9_000L }

        db.outboxDao().markFailed("m1", "boom")
        await { vm.state.value.headline == SyncHeadline.PROBLEM }
        assertEquals(0, vm.state.value.status.pendingCount)
    }

    @Test fun failuresListShowsItemNamesAndSanitizedErrors() = runBlocking {
        db.itemDao().upsert(item("drill", "Drill"))
        db.itemDao().upsert(item("saw", "Saw"))
        db.outboxDao().insert(outbox("m1", "item", "drill", OutboxState.FAILED, "Unreadable local edit: bad json"))
        db.outboxDao().insert(
            outbox("m2", "stock_adjustment", "adj1", OutboxState.FAILED, "rejected: unknown field", """{"item_id":"saw","delta":2}"""),
        )
        db.outboxDao().insert(outbox("m3", "label", "lbl-9", OutboxState.FAILED, "gone"))
        db.outboxDao().insert(outbox("m4", "item", "drill", OutboxState.PENDING))
        db.photoQueueDao().insert(photo("p1", "saw", PhotoState.FAILED, "file missing", 5))
        db.photoQueueDao().insert(photo("p2", "ghost", PhotoState.FAILED, null, 2))
        db.photoQueueDao().insert(photo("p3", "saw", PhotoState.QUEUED))
        subscribe()
        await { vm.state.value.failedMutations.size == 3 && vm.state.value.failedPhotos.size == 2 }

        val ui = vm.state.value
        assertEquals(
            listOf("item: Drill", "stock_adjustment: Saw", "label: lbl-9"),
            ui.failedMutations.map { it.label },
        )
        assertEquals("Unreadable local edit: bad json", ui.failedMutations[0].error)
        assertEquals(500L, ui.failedMutations[0].at)
        assertEquals(listOf("Saw", "ghost"), ui.failedPhotos.map { it.itemName })
        assertEquals("file missing", ui.failedPhotos[0].error)
        assertEquals(5, ui.failedPhotos[0].attempts)
        assertEquals(5, ui.status.failedCount)
        assertEquals(1, ui.status.pendingCount)
    }

    @Test fun lastErrorFrom426RunIsShown() = runBlocking {
        subscribe()
        db.syncRunStateDao().upsert(
            SyncRunStateEntity(lastRunAt = 7_000L, lastError = "Client too old: server requires at least 1.4.0", lastErrorAt = 7_000L),
        )
        await { vm.state.value.status.lastError != null }
        val ui = vm.state.value
        assertEquals("Client too old: server requires at least 1.4.0", ui.status.lastError)
        assertNull(ui.status.lastSyncedAt)
        assertEquals(SyncHeadline.PROBLEM, ui.headline)
    }

    @Test fun syncNowCallsSchedulerOncePerClickAndGuardsDoubleClicks() {
        assertFalse(vm.syncing.value)
        vm.onSyncNow()
        vm.onSyncNow()
        vm.onSyncNow()
        assertEquals(1, syncCalls)
        assertTrue(vm.syncing.value)

        val scope = TestScope(dispatcher.scheduler)
        scope.advanceTimeBy(2_000L)
        assertFalse(vm.syncing.value)
        vm.onSyncNow()
        assertEquals(2, syncCalls)
    }
}
