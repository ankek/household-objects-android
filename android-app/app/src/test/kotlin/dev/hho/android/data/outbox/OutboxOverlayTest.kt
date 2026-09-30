package dev.hho.android.data.outbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemLabelEntity
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.PurchasedFromBlockEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.sync.SyncMirrorApplier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OutboxOverlayTest {
    private lateinit var db: HhoDatabase
    private var n = 0

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
    }

    @After
    fun tearDown() = db.close()

    private fun item(id: String, qty: Long = 5, name: String = "Drill") =
        ItemEntity(id, 1, name, "d", "loc-1", qty, "SC1", 10, 20, 3)

    private suspend fun queue(
        type: String, id: String, fields: String, op: String = "upsert", base: Long = 0, state: String = OutboxState.PENDING,
    ): Long = db.outboxDao().insert(
        OutboxMutationEntity(
            mutationId = "m-${n++}", entityType = type, entityId = id, op = op, baseVersion = base,
            fieldsJson = fields, state = state, createdAt = 0,
        ),
    )

    private fun m(type: String, id: String, fields: String, op: String = "upsert", base: Long = 0) =
        LocalMutation(type, id, op, base, kotlinx.serialization.json.Json.parseToJsonElement(fields) as kotlinx.serialization.json.JsonObject)

    @Test
    fun itemCreate_writesRowWithoutShortCodeOrQuantity() = runTest {
        assertEquals(true, OptimisticApplier.apply(db, m("item", "i1", """{"name":"Saw","description":"x","location_id":"l1"}""")))
        val row = db.itemDao().getById("i1")!!
        assertEquals("Saw", row.name)
        assertEquals("l1", row.locationId)
        assertNull(row.shortCode)
        assertEquals(0L, row.quantity)
        assertEquals(0L, row.version)
    }

    @Test
    fun itemUpdate_changesOnlyNamedFields_andNullClears() = runTest {
        db.itemDao().upsert(item("i1"))
        OptimisticApplier.apply(db, m("item", "i1", """{"name":"New","description":null}""", base = 3))
        val row = db.itemDao().getById("i1")!!
        assertEquals("New", row.name)
        assertNull(row.description)
        assertEquals("loc-1", row.locationId)
        assertEquals("SC1", row.shortCode)
        assertEquals(3L, row.version)
        OptimisticApplier.apply(db, m("item", "i1", """{"location_id":"l2"}""", base = 3))
        assertEquals("l2", db.itemDao().getById("i1")!!.locationId)
    }

    @Test
    fun itemMutationCarryingQuantity_isRejected() = runTest {
        assertThrows(UnsupportedMutationException::class.java) {
            kotlinx.coroutines.runBlocking { OptimisticApplier.apply(db, m("item", "i1", """{"name":"a","quantity":9}""")) }
        }
    }

    @Test
    fun updateOnAbsentRow_isSkipped_forItemAndPurchase() = runTest {
        assertEquals(false, OptimisticApplier.apply(db, m("item", "gone", """{"name":"x"}""", base = 2)))
        assertNull(db.itemDao().getById("gone"))
        assertEquals(false, OptimisticApplier.apply(db, m("purchased_from_block", "p1", """{"item_id":"i1","vendor":"V"}""", base = 2)))
        assertNull(db.purchasedFromBlockDao().getByItemId("i1"))
    }

    @Test
    fun stockAdjustmentCreate_writesHistoryRow_butNotItemQuantity() = runTest {
        db.itemDao().upsert(item("i1", qty = 5))
        OptimisticApplier.apply(db, m("stock_adjustment", "s1", """{"item_id":"i1","delta":2,"reason":"found","note":"n"}"""))
        assertEquals(5L, db.itemDao().getById("i1")!!.quantity)
        val h = db.stockAdjustmentDao().observeByItemId("i1").first().single()
        assertEquals(2L, h.delta)
        assertEquals(7L, h.resultingQuantity)
        assertEquals("found", h.reason)
        assertEquals("n", h.note)
    }

    @Test
    fun itemLabel_upsertThenDelete() = runTest {
        OptimisticApplier.apply(db, m("item_label", "e1", """{"item_id":"i1","label_id":"lb1"}"""))
        assertEquals(listOf(ItemLabelEntity("e1", 0, "i1", "lb1")), db.itemLabelDao().observeByItemId("i1").first())
        OptimisticApplier.apply(db, m("item_label", "e1", """{"item_id":"i1","label_id":"lb1"}""", op = "delete"))
        assertEquals(emptyList<ItemLabelEntity>(), db.itemLabelDao().observeByItemId("i1").first())
    }

    @Test
    fun identificationCreate_writesRow() = runTest {
        OptimisticApplier.apply(db, m("item_identification", "d1", """{"item_id":"i1","kind":"barcode","value":"123"}"""))
        val row = db.itemIdentificationDao().findByValue("123").single()
        assertEquals("d1", row.id)
        assertEquals("i1", row.itemId)
        assertEquals("barcode", row.kind)
    }

    @Test
    fun purchaseCreateThenUpdate() = runTest {
        OptimisticApplier.apply(
            db,
            m("purchased_from_block", "p1", """{"item_id":"i1","vendor":"V","purchased_on":"2026-09-01","order_reference":"PO-1"}"""),
        )
        var row: PurchasedFromBlockEntity = db.purchasedFromBlockDao().getByItemId("i1")!!
        assertEquals("V", row.vendor)
        assertEquals(LocalDate.of(2026, 9, 1), row.purchasedOn)
        assertEquals(0L, row.purchasePriceMinor)
        db.purchasedFromBlockDao().upsert(row.copy(purchasePriceMinor = 999, version = 4))
        OptimisticApplier.apply(db, m("purchased_from_block", "p1", """{"item_id":"i1","vendor":"W"}""", base = 4))
        row = db.purchasedFromBlockDao().getByItemId("i1")!!
        assertEquals("W", row.vendor)
        assertEquals(999L, row.purchasePriceMinor)
        assertEquals("PO-1", row.orderReference)
    }

    @Test
    fun reapply_createSurvivesFullClear_updateOnTombstonedRowSkipped() = runTest {
        queue("item", "new", """{"name":"Local"}""")
        queue("item", "gone", """{"name":"Edit"}""", base = 2)
        queue("item_label", "e1", """{"item_id":"new","label_id":"lb"}""")
        db.itemDao().upsert(item("gone"))
        SyncMirrorApplier.clearMirror(db)
        assertEquals(2, OutboxOverlay.reapplyPending(db))
        assertEquals("Local", db.itemDao().getById("new")!!.name)
        assertNull(db.itemDao().getById("gone"))
        assertEquals(1, db.itemLabelDao().observeByItemId("new").first().size)
    }

    @Test
    fun reapply_replaysInSeqOrder_lastUpdateWins() = runTest {
        db.itemDao().upsert(item("i1"))
        queue("item", "i1", """{"name":"first"}""", base = 3)
        queue("item", "i1", """{"name":"second"}""", base = 3)
        OutboxOverlay.reapplyPending(db)
        assertEquals("second", db.itemDao().getById("i1")!!.name)
    }

    @Test
    fun reapply_ignoresFailedRows_andSkipsUnsupported() = runTest {
        db.itemDao().upsert(item("i1"))
        queue("item", "i1", """{"name":"failed"}""", base = 3, state = OutboxState.FAILED)
        queue("warranty_block", "w1", """{"item_id":"i1"}""")
        queue("item", "i1", """{"name":"held"}""", base = 3, state = OutboxState.HELD)
        assertEquals(1, OutboxOverlay.reapplyPending(db))
        assertEquals("held", db.itemDao().getById("i1")!!.name)
    }

    @Test
    fun reapply_pendingUpdateOverridesFreshServerValue() = runTest {
        queue("item", "i1", """{"name":"mine"}""", base = 3)
        db.itemDao().upsert(item("i1", name = "server"))
        OutboxOverlay.reapplyPending(db)
        assertEquals("mine", db.itemDao().getById("i1")!!.name)
    }

    @Test
    fun derivedQuantity_mirrorPlusPendingDelta() = runTest {
        db.itemDao().upsert(item("i1", qty = 5))
        queue("stock_adjustment", "s1", """{"item_id":"i1","delta":2}""")
        assertEquals(7L, OutboxOverlay.derivedQuantity(db, "i1"))
        assertEquals(7L, OutboxOverlay.observeDerivedQuantity(db, "i1").first())
    }

    @Test
    fun derivedQuantity_noDoubleCount_afterPullDeliversAckedAdjustment() = runTest {
        db.itemDao().upsert(item("i1", qty = 5))
        val seq = queue("stock_adjustment", "s1", """{"item_id":"i1","delta":2}""")
        assertEquals(7L, OutboxOverlay.derivedQuantity(db, "i1"))
        db.outboxDao().deleteBySeq(seq)
        db.itemDao().upsert(item("i1", qty = 7))
        OutboxOverlay.reapplyPending(db)
        assertEquals(7L, OutboxOverlay.derivedQuantity(db, "i1"))
        assertEquals(7L, db.itemDao().getById("i1")!!.quantity)
    }

    @Test
    fun derivedQuantity_pullWhileAdjustmentStillPending_countsOnce_evenRepeated() = runTest {
        db.itemDao().upsert(item("i1", qty = 5))
        queue("stock_adjustment", "s1", """{"item_id":"i1","delta":2}""")
        queue("stock_adjustment", "s2", """{"item_id":"i1","delta":-1}""", state = OutboxState.IN_FLIGHT)
        db.itemDao().upsert(item("i1", qty = 10))
        OutboxOverlay.reapplyPending(db)
        OutboxOverlay.reapplyPending(db)
        assertEquals(11L, OutboxOverlay.derivedQuantity(db, "i1"))
        SyncMirrorApplier.clearMirror(db)
        db.itemDao().upsert(item("i1", qty = 10))
        OutboxOverlay.reapplyPending(db)
        assertEquals(11L, OutboxOverlay.derivedQuantity(db, "i1"))
        assertNotNull(db.stockAdjustmentDao().observeByItemId("i1").first().firstOrNull { it.id == "s1" })
    }

    @Test
    fun derivedQuantity_ignoresFailedAndOtherItems() = runTest {
        db.itemDao().upsert(item("i1", qty = 5))
        queue("stock_adjustment", "s1", """{"item_id":"i1","delta":2}""", state = OutboxState.FAILED)
        queue("stock_adjustment", "s2", """{"item_id":"other","delta":9}""")
        assertEquals(5L, OutboxOverlay.derivedQuantity(db, "i1"))
        assertNull(OutboxOverlay.derivedQuantity(db, "missing"))
    }
}
