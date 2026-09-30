package dev.hho.android.ui.syncstatus

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.ConflictOrigin
import dev.hho.android.data.room.ConflictRecordEntity
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.StockAdjustmentEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.ui.items.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ConflictLogViewModelTest {
    private lateinit var db: HhoDatabase
    private lateinit var vm: ConflictLogViewModel
    private val viewModels = ViewModelTracker()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        vm = viewModels.track(ConflictLogViewModel(db.conflictRecordDao()))
    }

    @After fun tearDown() {
        collector.cancel()
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) { while (!condition()) kotlinx.coroutines.delay(10) }
    }

    private val collector by lazy {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch { vm.state.collect { } }
    }

    private fun rows(expected: Int): List<ConflictUi> {
        collector
        await { vm.state.value.size == expected }
        return vm.state.value
    }

    private fun conflict(
        mutation: String = "m1",
        type: String = "item",
        entity: String = "item-1",
        field: String = "name",
        losing: String? = "\"mine\"",
        server: String? = "\"theirs\"",
        at: Long = 100L,
        origin: String = ConflictOrigin.LOCAL_PUSH,
    ) = ConflictRecordEntity(
        mutationId = mutation, entityType = type, entityId = entity, fieldName = field,
        losingValueJson = losing, serverValueJson = server, detectedAt = at, origin = origin,
    )

    private fun insert(vararg c: ConflictRecordEntity) = runBlocking { c.forEach { db.conflictRecordDao().insertIgnore(it) } }

    private fun item(id: String, name: String) = ItemEntity(id, 1, name, null, null, 0, null, 1, 1, 1)

    @Test fun `newest first with item name and child type resolved via item id`() {
        runBlocking {
            db.itemDao().upsert(item("item-1", "Drill"))
            db.stockAdjustmentDao().upsert(StockAdjustmentEntity("adj-1", 1, "item-1", 1, 1, null, null, 1, 1, 1))
        }
        insert(
            conflict(mutation = "old", at = 100),
            conflict(mutation = "new", type = "stock_adjustment", entity = "adj-1", field = "delta", at = 300),
            conflict(mutation = "mid", type = "purchased_from_block", entity = "abcdefghijk", field = "vendor", at = 200),
        )
        val r = rows(3)
        assertEquals(listOf(300L, 200L, 100L), r.map { it.detectedAt })
        assertEquals("Drill", r[0].entityLabel)
        assertEquals("purchased_from_block abcdefgh", r[1].entityLabel)
        assertEquals("Drill", r[2].entityLabel)
        assertEquals("Rejected when this device pushed", r[2].originLabel)
    }

    @Test fun `whole entity sentinel is labelled whole item`() {
        insert(conflict(field = "_entity", losing = "{\"name\":\"x\"}", server = null))
        val c = rows(1).single()
        assertEquals("whole item", c.fieldLabel)
        assertEquals("{\"name\":\"x\"}", c.rejectedValue)
        assertEquals("not yet known", c.keptValue)
    }

    @Test fun `json values render plainly`() {
        assertEquals("mine", renderJsonValue("\"mine\"", "n/a"))
        assertEquals("42", renderJsonValue("42", "n/a"))
        assertEquals("true", renderJsonValue("true", "n/a"))
        assertEquals("—", renderJsonValue("null", "n/a"))
        assertEquals("n/a", renderJsonValue(null, "n/a"))
        assertEquals("{\"a\":1}", renderJsonValue("{ \"a\" : 1 }", "n/a"))
        assertEquals("not json {", renderJsonValue("not json {", "n/a"))
    }

    @Test fun `synthetic server mutation id is not shown`() {
        insert(
            conflict(mutation = "server:77", origin = ConflictOrigin.SERVER_LOG, entity = "a"),
            conflict(mutation = "real-1", entity = "b", at = 50),
        )
        val r = rows(2)
        assertNull(r[0].mutationId)
        assertEquals("From the server log (any device in the group)", r[0].originLabel)
        assertEquals("real-1", r[1].mutationId)
    }

    @Test fun `live update when a row is inserted and empty state before`() {
        collector
        assertTrue(vm.state.value.isEmpty())
        insert(conflict())
        assertEquals(1, rows(1).size)
        insert(conflict(mutation = "m2", at = 900))
        assertEquals(900L, rows(2).first().detectedAt)
    }
}
