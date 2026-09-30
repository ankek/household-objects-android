package dev.hho.android.ui.items

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.items.ItemSearch
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.inMemoryHhoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ItemListViewModelTest {

    private lateinit var db: HhoDatabase
    private lateinit var itemSearch: ItemSearch
    private val viewModels = ViewModelTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        itemSearch = ItemSearch(db.itemDao())
    }

    @After
    fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun itemFixture(id: String, name: String, quantity: Long? = 1L, shortCode: String? = null) =
        ItemEntity(
            id = id,
            groupChangeSeq = 1L,
            name = name,
            description = null,
            locationId = null,
            quantity = quantity,
            shortCode = shortCode,
            createdAt = 1L,
            updatedAt = 1L,
            version = 1L,
        )

    @Test
    fun `empty Room mirror surfaces the empty state, not a crash or an infinite loading state`() = runTest {
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))

        val state = viewModel.uiState.first { it !is ItemListUiState.Loading }

        assertEquals(ItemListUiState.Empty, state)
    }

    @Test
    fun `emits every synced item, sorted by name`() = runTest {
        db.itemDao().upsert(itemFixture("item-2", name = "Saw", quantity = 3L))
        db.itemDao().upsert(itemFixture("item-1", name = "Drill", quantity = 1L, shortCode = "DRL-1"))
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))

        val state = viewModel.uiState.first { it is ItemListUiState.Data } as ItemListUiState.Data

        assertEquals(
            listOf(
                ItemRow(id = "item-1", name = "Drill", quantity = 1L, shortCode = "DRL-1"),
                ItemRow(id = "item-2", name = "Saw", quantity = 3L, shortCode = null),
            ),
            state.items,
        )
    }

    @Test
    fun `a row inserted by a background sync after the ViewModel is created updates the list live`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))
        val initial = viewModel.uiState.first { it is ItemListUiState.Data } as ItemListUiState.Data
        assertEquals(1, initial.items.size)

        db.itemDao().upsert(itemFixture("item-2", name = "Saw"))

        val updated = viewModel.uiState.first { it is ItemListUiState.Data && it.items.size == 2 }
            as ItemListUiState.Data
        assertEquals(setOf("item-1", "item-2"), updated.items.map { it.id }.toSet())
    }

    @Test
    fun `onQueryChange filters to items whose name, short code, or identifier value matches, case-insensitively`() =
        runTest {
            db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill", shortCode = "DRL-1"))
            db.itemDao().upsert(itemFixture("item-2", name = "Table Saw"))
            db.itemIdentificationDao().upsert(
                ItemIdentificationEntity(
                    id = "ident-1",
                    groupChangeSeq = 1L,
                    itemId = "item-2",
                    kind = "barcode",
                    value = "DRILL-CODE-9",
                    createdAt = 1L,
                    updatedAt = 1L,
                    version = 1L,
                ),
            )
            val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))
            viewModel.uiState.first { it is ItemListUiState.Data }

            viewModel.onQueryChange("drill")

            val state = viewModel.uiState.first {
                it is ItemListUiState.Data && it.items.size == 2
            } as ItemListUiState.Data
            assertEquals(setOf("item-1", "item-2"), state.items.map { it.id }.toSet())
        }

    @Test
    fun `a query matching nothing in a non-empty mirror surfaces NoMatches, not Empty`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))
        viewModel.uiState.first { it is ItemListUiState.Data }

        viewModel.onQueryChange("no-such-item")

        val state = viewModel.uiState.first { it is ItemListUiState.NoMatches }
        assertEquals(ItemListUiState.NoMatches("no-such-item"), state)
    }

    @Test
    fun `clearing the query back to blank restores the full, unfiltered list`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.itemDao().upsert(itemFixture("item-2", name = "Saw"))
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))
        viewModel.onQueryChange("drill")
        viewModel.uiState.first { it is ItemListUiState.Data && it.items.size == 1 }

        viewModel.onQueryChange("")

        val state = viewModel.uiState.first { it is ItemListUiState.Data && it.items.size == 2 }
            as ItemListUiState.Data
        assertEquals(setOf("item-1", "item-2"), state.items.map { it.id }.toSet())
    }

    private suspend fun adjustment(itemId: String, delta: Long, state: String = OutboxState.PENDING): Long =
        db.outboxDao().insert(
            OutboxMutationEntity(
                mutationId = "m-${System.nanoTime()}",
                entityType = "stock_adjustment",
                entityId = "s-${System.nanoTime()}",
                op = "create",
                baseVersion = 0L,
                fieldsJson = """{"item_id":"$itemId","delta":$delta}""",
                state = state,
                createdAt = 1L,
            ),
        )

    private suspend fun ItemListViewModel.quantities(expect: Map<String, Long?>): Map<String, Long?> =
        (uiState.first { s -> s is ItemListUiState.Data && s.items.associate { it.id to it.quantity } == expect }
            as ItemListUiState.Data).items.associate { it.id to it.quantity }

    @Test
    fun `pending stock delta is added to the mirror quantity in the list`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", "Drill", quantity = 5L))
        adjustment("item-1", 2L)
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))

        assertEquals(mapOf("item-1" to 7L), viewModel.quantities(mapOf("item-1" to 7L)))
    }

    @Test
    fun `after the ack and a pulled server value there is no double count`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", "Drill", quantity = 5L))
        val seq = adjustment("item-1", 2L)
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))
        viewModel.quantities(mapOf("item-1" to 7L))

        db.outboxDao().deleteBySeq(seq)
        db.itemDao().upsert(itemFixture("item-1", "Drill", quantity = 7L))

        assertEquals(mapOf("item-1" to 7L), viewModel.quantities(mapOf("item-1" to 7L)))
    }

    @Test
    fun `several items each get only their own deltas, summed`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", "Drill", quantity = 5L))
        db.itemDao().upsert(itemFixture("item-2", "Saw", quantity = 10L))
        db.itemDao().upsert(itemFixture("item-3", "Nails", quantity = 3L))
        adjustment("item-1", 2L)
        adjustment("item-1", -1L)
        adjustment("item-2", -4L, OutboxState.IN_FLIGHT)
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))

        val expected = mapOf("item-1" to 6L, "item-2" to 6L, "item-3" to 3L)
        assertEquals(expected, viewModel.quantities(expected))
    }

    @Test
    fun `FAILED adjustments are ignored`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", "Drill", quantity = 5L))
        adjustment("item-1", 100L, OutboxState.FAILED)
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))

        assertEquals(mapOf("item-1" to 5L), viewModel.quantities(mapOf("item-1" to 5L)))
    }

    @Test
    fun `search results also show derived quantities and a new adjustment updates live`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", "Drill", quantity = 5L))
        db.itemDao().upsert(itemFixture("item-2", "Saw", quantity = 1L))
        adjustment("item-1", 2L)
        val viewModel = viewModels.track(ItemListViewModel(itemSearch, db))
        viewModel.onQueryChange("drill")

        assertEquals(mapOf("item-1" to 7L), viewModel.quantities(mapOf("item-1" to 7L)))
        adjustment("item-1", 1L)
        assertEquals(mapOf("item-1" to 8L), viewModel.quantities(mapOf("item-1" to 8L)))
    }
}
