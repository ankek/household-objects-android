package dev.hho.android.ui.items

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.domain.EditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StockActionsViewModelTest {

    private lateinit var db: HhoDatabase
    private var syncCalls = 0
    private val viewModels = ViewModelTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        runBlocking { db.itemDao().upsert(ItemEntity("i1", 5, "Drill", "d", null, 5, "AB12", 1, 1, 4)) }
    }

    @After
    fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun stockViewModel(): StockActionsViewModel {
        val ids = UuidV7Generator()
        val repo = EditRepository(db, OutboxRepository(db, ids), ids) { syncCalls++ }
        return viewModels.track(StockActionsViewModel(repo))
    }

    private fun detailViewModel() = viewModels.track(
        ItemDetailViewModel(
            itemDao = db.itemDao(),
            itemLabelDao = db.itemLabelDao(),
            labelDao = db.labelDao(),
            locationDao = db.locationDao(),
            warrantyBlockDao = db.warrantyBlockDao(),
            soldToBlockDao = db.soldToBlockDao(),
            purchasedFromBlockDao = db.purchasedFromBlockDao(),
            itemIdentificationDao = db.itemIdentificationDao(),
            itemCustomFieldDao = db.itemCustomFieldDao(),
            stockAdjustmentDao = db.stockAdjustmentDao(),
            db = db,
        ),
    )

    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) { while (!condition()) kotlinx.coroutines.delay(10) }
    }

    private fun detailQuantity(vm: ItemDetailViewModel): Long? =
        ((vm.uiState.value as? ItemDetailUiState.Data)?.item)?.quantity

    @Test
    fun successQueuesAdjustmentTriggersSyncAndCompletes() {
        val vm = stockViewModel()
        vm.submit("i1", 3L, "  Restocked ", " ")
        await { vm.state.value.completed }

        assertFalse(vm.state.value.submitting)
        assertNull(vm.state.value.deltaError)
        assertEquals(1, syncCalls)
        val row = runBlocking { db.outboxDao().getAllOrdered() }.single()
        val fields = LocalMutation.from(row).fields
        assertEquals("stock_adjustment", row.entityType)
        assertEquals("3", (fields["delta"] as JsonPrimitive).content)
        assertEquals("Restocked", (fields["reason"] as JsonPrimitive).content)
        assertFalse("blank note is sent as absent", fields.containsKey("note"))
    }

    @Test
    fun zeroDeltaShowsInlineErrorAndWritesNothing() {
        val vm = stockViewModel()
        vm.submit("i1", 0L, "", "")
        await { vm.state.value.deltaError != null }

        assertFalse(vm.state.value.completed)
        assertTrue(runBlocking { db.outboxDao().getAllOrdered() }.isEmpty())
        assertEquals(0, syncCalls)
    }

    @Test
    fun unparseableDeltaShowsInlineErrorWithoutTouchingRepository() {
        val vm = stockViewModel()
        vm.submit("i1", null, "", "")

        assertNotNull(vm.state.value.deltaError)
        assertFalse(vm.state.value.submitting)
        assertTrue(runBlocking { db.outboxDao().getAllOrdered() }.isEmpty())
    }

    @Test
    fun missingItemReportsNotFound() {
        val vm = stockViewModel()
        vm.submit("ghost", 2L, "", "")
        await { vm.state.value.generalError != null }

        assertEquals("This item is no longer on this device.", vm.state.value.generalError)
        assertFalse(vm.state.value.completed)
    }

    @Test
    fun storageFailureIsReportedAndLeavesNoRow() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_enqueue BEFORE INSERT ON outbox_mutation BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val vm = stockViewModel()
        vm.submit("i1", 2L, "", "")
        await { vm.state.value.generalError != null }

        assertFalse(vm.state.value.completed)
        assertFalse(vm.state.value.submitting)
        assertTrue(runBlocking { db.stockAdjustmentDao().observeByItemId("i1").first() }.isEmpty())
        assertEquals(0, syncCalls)
    }

    @Test
    fun submittingBlocksASecondSubmitUntilTheFirstFinishes() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val vm = stockViewModel()

        vm.submit("i1", 1L, "", "")
        assertTrue("confirm must be disabled while submitting", vm.state.value.submitting)
        vm.submit("i1", 1L, "", "")
        await {
            dispatcher.scheduler.advanceUntilIdle()
            vm.state.value.completed
        }

        assertEquals(1, runBlocking { db.outboxDao().getAllOrdered() }.size)
    }

    @Test
    fun resetClearsErrorsAndCompletion() {
        val vm = stockViewModel()
        vm.submit("i1", 0L, "", "")
        await { vm.state.value.deltaError != null }
        vm.reset()
        assertEquals(AdjustStockState(), vm.state.value)
    }

    @Test
    fun detailQuantityReflectsPendingAdjustmentImmediately() {
        val detail = detailViewModel()
        detail.loadItem("i1")
        val collector = CoroutineScope(Dispatchers.Default).launch { detail.uiState.collect { } }
        try {
            await { detailQuantity(detail) == 5L }
            val vm = stockViewModel()
            vm.submit("i1", -2L, "Used", "")
            await { vm.state.value.completed }
            await { detailQuantity(detail) == 3L }
            assertEquals(5L, runBlocking { db.itemDao().getById("i1") }!!.quantity)
        } finally {
            collector.cancel()
        }
    }
}
