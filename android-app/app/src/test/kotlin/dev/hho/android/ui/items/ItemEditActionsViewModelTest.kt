package dev.hho.android.ui.items

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemLabelEntity
import dev.hho.android.data.room.LabelEntity
import dev.hho.android.data.room.LocationEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.domain.EditRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
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
class ItemEditActionsViewModelTest {

    private lateinit var db: HhoDatabase
    private var syncCalls = 0
    private val viewModels = ViewModelTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        runBlocking {
            db.itemDao().upsert(ItemEntity("i1", 5, "Drill", "d", "loc-garage", 5, "AB12", 1, 1, 4))
            db.locationDao().upsertAll(
                listOf(
                    LocationEntity("loc-house", 1, "House", null, 1, 1, 1),
                    LocationEntity("loc-garage", 1, "Garage", "loc-house", 1, 1, 2),
                    LocationEntity("loc-shed", 1, "Shed", null, 1, 1, 1),
                ),
            )
            db.labelDao().upsertAll(
                listOf(
                    LabelEntity("lab-a", 1, "Fragile", "#f00", 1, 1, 1),
                    LabelEntity("lab-b", 1, "Power tools", "#0f0", 1, 1, 1),
                ),
            )
            db.itemLabelDao().upsert(ItemLabelEntity("edge-a", 1, "i1", "lab-a"))
        }
    }

    @After
    fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun editViewModel(): ItemEditActionsViewModel {
        val ids = UuidV7Generator()
        val repo = EditRepository(db, OutboxRepository(db, ids), ids) { syncCalls++ }
        return viewModels.track(
            ItemEditActionsViewModel(repo, db.itemDao(), db.itemLabelDao(), db.labelDao(), db.locationDao()),
        )
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

    private fun outbox() = runBlocking { db.outboxDao().getAllOrdered() }

    @Test
    fun optionsShowLocationPathsAndSplitLabels() {
        val vm = editViewModel()
        vm.load("i1")
        val collector = CoroutineScope(Dispatchers.Default).launch { vm.options.collect { } }
        try {
            await { vm.options.value.locations.size == 3 && vm.options.value.attachedLabels.isNotEmpty() }
            val o = vm.options.value
            assertEquals(listOf("House", "House / Garage", "Shed"), o.locations.map { it.path })
            assertEquals("loc-garage", o.currentLocationId)
            assertEquals(listOf("lab-a"), o.attachedLabels.map { it.id })
            assertEquals(listOf("lab-b"), o.availableLabels.map { it.id })
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun moveToLocationQueuesUpsertWithBaseVersionAndCompletes() {
        val vm = editViewModel()
        vm.move("i1", "loc-shed")
        await { vm.state.value.completed }

        assertEquals(1, syncCalls)
        val row = outbox().single()
        val m = LocalMutation.from(row)
        assertEquals("item", row.entityType)
        assertEquals("i1", row.entityId)
        assertEquals(4L, m.baseVersion)
        assertEquals("loc-shed", (m.fields["location_id"] as JsonPrimitive).content)
        assertEquals("loc-shed", runBlocking { db.itemDao().getById("i1") }!!.locationId)
    }

    @Test
    fun moveToNoLocationSendsJsonNullAndClearsMirror() {
        val vm = editViewModel()
        vm.move("i1", null)
        await { vm.state.value.completed }

        val m = LocalMutation.from(outbox().single())
        assertEquals(JsonNull, m.fields["location_id"])
        assertEquals(4L, m.baseVersion)
        assertNull(runBlocking { db.itemDao().getById("i1") }!!.locationId)
    }

    @Test
    fun moveToUnknownLocationReportsNotFoundAndWritesNothing() {
        val vm = editViewModel()
        vm.move("i1", "ghost")
        await { vm.state.value.error != null }

        assertEquals("That location is no longer on this device.", vm.state.value.error)
        assertFalse(vm.state.value.completed)
        assertTrue(outbox().isEmpty())
        assertEquals(0, syncCalls)
    }

    @Test
    fun moveOfUnknownItemReportsNotFound() {
        val vm = editViewModel()
        vm.move("ghost", "loc-shed")
        await { vm.state.value.error != null }
        assertEquals("This item is no longer on this device.", vm.state.value.error)
    }

    @Test
    fun attachLabelUpsertsEdgeAndDoesNotCloseTheEditor() {
        val vm = editViewModel()
        vm.attach("i1", "lab-b")
        await { !vm.state.value.submitting && outbox().isNotEmpty() }

        assertFalse(vm.state.value.completed)
        assertNull(vm.state.value.error)
        val row = outbox().single()
        val m = LocalMutation.from(row)
        assertEquals("item_label", row.entityType)
        assertEquals("i1", (m.fields["item_id"] as JsonPrimitive).content)
        assertEquals("lab-b", (m.fields["label_id"] as JsonPrimitive).content)
        val edges = runBlocking { db.itemLabelDao().observeByItemId("i1").first() }
        assertEquals(setOf("lab-a", "lab-b"), edges.map { it.labelId }.toSet())
    }

    @Test
    fun attachingAnAlreadyAttachedLabelIsIdempotent() {
        val vm = editViewModel()
        vm.attach("i1", "lab-a")
        await { !vm.state.value.submitting }

        assertNull(vm.state.value.error)
        assertTrue(outbox().isEmpty())
        assertEquals(0, syncCalls)
    }

    @Test
    fun attachUnknownLabelReportsNotFound() {
        val vm = editViewModel()
        vm.attach("i1", "ghost")
        await { vm.state.value.error != null }
        assertEquals("That label is no longer on this device.", vm.state.value.error)
        assertTrue(outbox().isEmpty())
    }

    @Test
    fun detachLabelQueuesEdgeDeleteAndRemovesFromMirror() {
        val vm = editViewModel()
        vm.detach("i1", "lab-a")
        await { !vm.state.value.submitting && outbox().isNotEmpty() }

        val row = outbox().single()
        assertEquals("item_label", row.entityType)
        assertEquals("edge-a", row.entityId)
        assertEquals("delete", row.op)
        assertEquals(1, syncCalls)
        val edges = runBlocking { db.itemLabelDao().observeByItemId("i1").first() }
        assertTrue(edges.isEmpty())
    }

    @Test
    fun detachOfUnattachedLabelReportsNotFound() {
        val vm = editViewModel()
        vm.detach("i1", "lab-b")
        await { vm.state.value.error != null }
        assertEquals("That label is no longer on this device.", vm.state.value.error)
        assertTrue(outbox().isEmpty())
    }

    @Test
    fun submittingBlocksASecondEditUntilTheFirstFinishes() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val vm = editViewModel()

        vm.move("i1", "loc-shed")
        assertTrue(vm.state.value.submitting)
        vm.move("i1", "loc-house")
        await {
            dispatcher.scheduler.advanceUntilIdle()
            vm.state.value.completed
        }

        val rows = outbox()
        assertEquals(1, rows.size)
        assertEquals("loc-shed", (LocalMutation.from(rows.single()).fields["location_id"] as JsonPrimitive).content)
    }

    @Test
    fun storageFailureIsReported() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_enqueue BEFORE INSERT ON outbox_mutation BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val vm = editViewModel()
        vm.move("i1", "loc-shed")
        await { vm.state.value.error != null }
        assertEquals("Couldn't save the change. Please try again.", vm.state.value.error)
        assertFalse(vm.state.value.submitting)
        assertEquals("loc-garage", runBlocking { db.itemDao().getById("i1") }!!.locationId)
    }

    @Test
    fun resetClearsErrorAndCompletion() {
        val vm = editViewModel()
        vm.move("ghost", null)
        await { vm.state.value.error != null }
        vm.reset()
        assertEquals(ItemEditState(), vm.state.value)
    }

    @Test
    fun detailUiStateReflectsMoveAndLabelChanges() {
        val detail = detailViewModel()
        detail.loadItem("i1")
        val collector = CoroutineScope(Dispatchers.Default).launch { detail.uiState.collect { } }
        fun data() = (detail.uiState.value as? ItemDetailUiState.Data)?.item
        try {
            await { data()?.locationName == "Garage" && data()?.labelNames == listOf("Fragile") }
            val vm = editViewModel()
            vm.move("i1", "loc-shed")
            await { vm.state.value.completed }
            await { data()?.locationName == "Shed" }
            vm.reset()
            vm.attach("i1", "lab-b")
            await { data()?.labelNames?.toSet() == setOf("Fragile", "Power tools") }
            await { !vm.state.value.submitting }
            vm.detach("i1", "lab-a")
            await { data()?.labelNames == listOf("Power tools") }
            vm.reset()
            vm.move("i1", null)
            await { data()?.locationName == null }
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun locationOptionsSurviveCyclesAndDanglingParents() {
        val options = locationOptions(
            listOf(
                LocationEntity("a", 1, "A", "b", 1, 1, 1),
                LocationEntity("b", 1, "B", "a", 1, 1, 1),
                LocationEntity("c", 1, "C", "missing", 1, 1, 1),
            ),
        )
        assertEquals(setOf("A / B", "B / A"), options.filter { it.id != "c" }.map { it.path }.toSet())
        assertEquals(3, options.size)
        assertEquals("C", options.first { it.id == "c" }.path)
    }

    @Test
    fun filterLocationsMatchesPathCaseInsensitively() {
        val all = listOf(LocationOption("1", "House / Garage"), LocationOption("2", "Shed"))
        assertEquals(listOf("1"), filterLocations(all, " garage ").map { it.id })
        assertEquals(2, filterLocations(all, "  ").size)
    }
}
