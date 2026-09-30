package dev.hho.android.data.room

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.SyncChange
import dev.hho.android.data.apiclient.generated.models.ItemLabelAssignment
import dev.hho.android.data.apiclient.generated.models.WarrantyBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RoomDaoTest {

    private lateinit var db: HhoDatabase

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `item upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.itemDao()
        dao.upsert(itemFixture("item-1", name = "Drill"))
        dao.upsert(itemFixture("item-2", name = "Saw"))
        assertEquals("Drill", dao.getById("item-1")?.name)
        assertEquals(2, dao.observeAll().first().size)

        dao.upsert(itemFixture("item-1", name = "Drill v2"))
        assertEquals("Drill v2", dao.getById("item-1")?.name)
        assertEquals(2, dao.observeAll().first().size)

        dao.deleteById("item-2")
        assertNull(dao.getById("item-2"))
        assertEquals(1, dao.observeAll().first().size)

        dao.clearAll()
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `warranty_block upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.warrantyBlockDao()
        dao.upsert(warrantyFixture("wb-1", itemId = "item-1", holder = "Acme"))
        dao.upsert(warrantyFixture("wb-2", itemId = "item-2", holder = "Globex"))
        assertEquals("Acme", dao.getByItemId("item-1")?.holder)

        dao.upsert(warrantyFixture("wb-1", itemId = "item-1", holder = "Acme v2"))
        assertEquals("Acme v2", dao.getByItemId("item-1")?.holder)

        dao.deleteById("wb-2")
        assertNull(dao.getByItemId("item-2"))

        dao.clearAll()
        assertNull(dao.getByItemId("item-1"))
    }

    @Test
    fun `sold_to_block upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.soldToBlockDao()
        dao.upsert(soldToFixture("st-1", itemId = "item-1", buyerName = "Jane"))
        dao.upsert(soldToFixture("st-2", itemId = "item-2", buyerName = "Joe"))
        assertEquals("Jane", dao.getByItemId("item-1")?.buyerName)

        dao.upsert(soldToFixture("st-1", itemId = "item-1", buyerName = "Jane v2"))
        assertEquals("Jane v2", dao.getByItemId("item-1")?.buyerName)

        dao.deleteById("st-2")
        assertNull(dao.getByItemId("item-2"))

        dao.clearAll()
        assertNull(dao.getByItemId("item-1"))
    }

    @Test
    fun `purchased_from_block upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.purchasedFromBlockDao()
        dao.upsert(purchasedFromFixture("pf-1", itemId = "item-1", vendor = "Acme Store"))
        dao.upsert(purchasedFromFixture("pf-2", itemId = "item-2", vendor = "Other Store"))
        assertEquals("Acme Store", dao.getByItemId("item-1")?.vendor)

        dao.upsert(purchasedFromFixture("pf-1", itemId = "item-1", vendor = "Acme Store v2"))
        assertEquals("Acme Store v2", dao.getByItemId("item-1")?.vendor)

        dao.deleteById("pf-2")
        assertNull(dao.getByItemId("item-2"))

        dao.clearAll()
        assertNull(dao.getByItemId("item-1"))
    }

    @Test
    fun `item_identification upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.itemIdentificationDao()
        dao.upsert(identificationFixture("ident-1", itemId = "item-1", value = "0111111111111"))
        dao.upsert(identificationFixture("ident-2", itemId = "item-1", value = "0222222222222"))
        assertEquals(2, dao.observeByItemId("item-1").first().size)

        dao.upsert(identificationFixture("ident-1", itemId = "item-1", value = "0999999999999"))
        val afterReplace = dao.observeByItemId("item-1").first()
        assertEquals(2, afterReplace.size)
        assertEquals("0999999999999", afterReplace.first { it.id == "ident-1" }.value)
        assertTrue(dao.findByValue("0111111111111").isEmpty())

        dao.deleteById("ident-2")
        assertEquals(1, dao.observeByItemId("item-1").first().size)

        dao.clearAll()
        assertTrue(dao.observeByItemId("item-1").first().isEmpty())
    }

    @Test
    fun `findByValue returns the matching identification row (FR-113)`() = runTest {
        val dao = db.itemIdentificationDao()
        dao.upsert(identificationFixture("ident-1", itemId = "item-1", value = "0111111111111"))
        dao.upsert(identificationFixture("ident-2", itemId = "item-2", value = "0222222222222"))

        val found = dao.findByValue("0111111111111")

        assertEquals(1, found.size)
        assertEquals("ident-1", found.single().id)
        assertEquals("item-1", found.single().itemId)
    }

    @Test
    fun `item_custom_field upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.itemCustomFieldDao()
        dao.upsert(customFieldFixture("cf-1", itemId = "item-1", name = "Warranty months"))
        dao.upsert(customFieldFixture("cf-2", itemId = "item-1", name = "Color"))
        assertEquals(2, dao.observeByItemId("item-1").first().size)

        dao.upsert(customFieldFixture("cf-1", itemId = "item-1", name = "Warranty months v2"))
        val afterReplace = dao.observeByItemId("item-1").first()
        assertEquals(2, afterReplace.size)
        assertEquals("Warranty months v2", afterReplace.first { it.id == "cf-1" }.name)

        dao.deleteById("cf-2")
        assertEquals(1, dao.observeByItemId("item-1").first().size)

        dao.clearAll()
        assertTrue(dao.observeByItemId("item-1").first().isEmpty())
    }

    @Test
    fun `stock_adjustment upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.stockAdjustmentDao()
        dao.upsert(stockAdjustmentFixture("sa-1", itemId = "item-1", reason = "sold"))
        dao.upsert(stockAdjustmentFixture("sa-2", itemId = "item-1", reason = "damaged"))
        assertEquals(2, dao.observeByItemId("item-1").first().size)

        dao.upsert(stockAdjustmentFixture("sa-1", itemId = "item-1", reason = "sold v2"))
        val afterReplace = dao.observeByItemId("item-1").first()
        assertEquals(2, afterReplace.size)
        assertEquals("sold v2", afterReplace.first { it.id == "sa-1" }.reason)

        dao.deleteById("sa-2")
        assertEquals(1, dao.observeByItemId("item-1").first().size)

        dao.clearAll()
        assertTrue(dao.observeByItemId("item-1").first().isEmpty())
    }

    @Test
    fun `location upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.locationDao()
        dao.upsert(locationFixture("loc-1", name = "Garage"))
        dao.upsert(locationFixture("loc-2", name = "Attic"))
        assertEquals("Garage", dao.getById("loc-1")?.name)
        assertEquals(2, dao.observeAll().first().size)

        dao.upsert(locationFixture("loc-1", name = "Garage v2"))
        assertEquals("Garage v2", dao.getById("loc-1")?.name)
        assertEquals(2, dao.observeAll().first().size)

        dao.deleteById("loc-2")
        assertNull(dao.getById("loc-2"))
        assertEquals(1, dao.observeAll().first().size)

        dao.clearAll()
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `label upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.labelDao()
        dao.upsert(labelFixture("label-1", name = "Fragile"))
        dao.upsert(labelFixture("label-2", name = "Heavy"))
        assertEquals("Fragile", dao.getById("label-1")?.name)
        assertEquals(2, dao.observeAll().first().size)

        dao.upsert(labelFixture("label-1", name = "Fragile v2"))
        assertEquals("Fragile v2", dao.getById("label-1")?.name)
        assertEquals(2, dao.observeAll().first().size)

        dao.deleteById("label-2")
        assertNull(dao.getById("label-2"))
        assertEquals(1, dao.observeAll().first().size)

        dao.clearAll()
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun `item_label upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.itemLabelDao()
        dao.upsert(itemLabelFixture("il-1", itemId = "item-1", labelId = "label-1"))
        dao.upsert(itemLabelFixture("il-2", itemId = "item-1", labelId = "label-2"))
        assertEquals(2, dao.observeByItemId("item-1").first().size)

        dao.upsert(itemLabelFixture("il-1", itemId = "item-1", labelId = "label-3"))
        val afterReplace = dao.observeByItemId("item-1").first()
        assertEquals(2, afterReplace.size)
        assertEquals("label-3", afterReplace.first { it.id == "il-1" }.labelId)

        dao.deleteById("il-2")
        assertEquals(1, dao.observeByItemId("item-1").first().size)

        dao.clearAll()
        assertTrue(dao.observeByItemId("item-1").first().isEmpty())
    }

    @Test
    fun `attachment upsert, replace, deleteById and clearAll`() = runTest {
        val dao = db.attachmentDao()
        dao.upsert(attachmentFixture("att-1", itemId = "item-1", filename = "a.jpg"))
        dao.upsert(attachmentFixture("att-2", itemId = "item-1", filename = "b.jpg"))
        assertEquals("a.jpg", dao.getById("att-1")?.originalFilename)

        dao.upsert(attachmentFixture("att-1", itemId = "item-1", filename = "a-v2.jpg"))
        assertEquals("a-v2.jpg", dao.getById("att-1")?.originalFilename)
        assertEquals(2, dao.observeByItemId("item-1").first().size)

        dao.deleteById("att-2")
        assertEquals(1, dao.observeByItemId("item-1").first().size)

        dao.clearAll()
        assertTrue(dao.observeByItemId("item-1").first().isEmpty())
    }

    @Test
    fun `sync_state get() is null before anything, round-trips, then clear() deletes the row`() = runTest {
        val dao = db.syncStateDao()
        assertNull(dao.get())

        dao.upsert(SyncStateEntity(watermark = 10L, lastSyncedAt = 100L))
        assertEquals(SyncStateEntity(watermark = 10L, lastSyncedAt = 100L), dao.get())

        dao.upsert(SyncStateEntity(watermark = 20L, lastSyncedAt = 200L))
        assertEquals(20L, dao.get()?.watermark)

        dao.clear()
        assertNull(dao.get())
    }

    @Test
    fun `warranty_block PK persisted is the envelope id, never data itemId`() = runTest {
        val dao = db.warrantyBlockDao()
        val change =
            SyncChange.WarrantyBlockChange(
                id = "warranty-envelope-1",
                groupChangeSeq = 1L,
                data = warrantyBlockData(itemId = "item-alpha", holder = "H"),
            )

        dao.upsert(change.toEntity())

        val persisted = dao.getByItemId("item-alpha")
        assertEquals("warranty-envelope-1", persisted?.id)
        assertNotEquals("item-alpha", persisted?.id)
    }

    @Test
    fun `item_label PK persisted is the envelope id, never data itemId or labelId`() = runTest {
        val dao = db.itemLabelDao()
        val change =
            SyncChange.ItemLabelChange(
                id = "assignment-envelope-1",
                groupChangeSeq = 1L,
                data = ItemLabelAssignment(itemId = "item-beta", labelId = "label-beta"),
            )

        dao.upsert(change.toEntity())

        val persisted = dao.observeByItemId("item-beta").first().single()
        assertEquals("assignment-envelope-1", persisted.id)
        assertNotEquals("item-beta", persisted.id)
        assertNotEquals("label-beta", persisted.id)
    }

    @Test
    fun `two warranty blocks for two different items persist independently, keyed by envelope id`() = runTest {
        val dao = db.warrantyBlockDao()
        val changeA =
            SyncChange.WarrantyBlockChange(
                id = "wb-env-a",
                groupChangeSeq = 1L,
                data = warrantyBlockData(itemId = "item-a", holder = "A"),
            )
        val changeB =
            SyncChange.WarrantyBlockChange(
                id = "wb-env-b",
                groupChangeSeq = 1L,
                data = warrantyBlockData(itemId = "item-b", holder = "B"),
            )

        dao.upsert(changeA.toEntity())
        dao.upsert(changeB.toEntity())

        assertEquals("wb-env-a", dao.getByItemId("item-a")?.id)
        assertEquals("wb-env-b", dao.getByItemId("item-b")?.id)
    }

    private class BoomException : RuntimeException("boom")

    @Test
    fun `a multi-DAO transaction that throws rolls back atomically, including the watermark`() =
        runTest {
            val itemDao = db.itemDao()
            val warrantyDao = db.warrantyBlockDao()
            val syncStateDao = db.syncStateDao()

            assertNull(syncStateDao.get())

            val thrown =
                runCatching {
                    db.withTransaction {
                        itemDao.upsert(itemFixture("item-tx-1", name = "Drill"))
                        warrantyDao.upsert(warrantyFixture("wb-tx-1", itemId = "item-tx-1", holder = "H"))
                        syncStateDao.upsert(SyncStateEntity(watermark = 42L, lastSyncedAt = 1L))
                        throw BoomException()
                    }
                }.exceptionOrNull()

            assertTrue(thrown is BoomException)
            assertNull(itemDao.getById("item-tx-1"))
            assertNull(warrantyDao.getByItemId("item-tx-1"))
            assertNull(syncStateDao.get())

            db.withTransaction {
                itemDao.upsert(itemFixture("item-tx-1", name = "Drill"))
                warrantyDao.upsert(warrantyFixture("wb-tx-1", itemId = "item-tx-1", holder = "H"))
                syncStateDao.upsert(SyncStateEntity(watermark = 42L, lastSyncedAt = 1L))
            }

            assertEquals("Drill", itemDao.getById("item-tx-1")?.name)
            assertEquals("H", warrantyDao.getByItemId("item-tx-1")?.holder)
            assertEquals(42L, syncStateDao.get()?.watermark)
        }

    @Test
    fun `applicationContext is a real Robolectric Android context`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(context is android.app.Application)
        assertTrue(context.packageName.isNotBlank())
    }
}

private fun itemFixture(id: String, name: String) =
    ItemEntity(
        id = id,
        groupChangeSeq = 1L,
        name = name,
        description = null,
        locationId = null,
        quantity = 1L,
        shortCode = null,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun warrantyFixture(id: String, itemId: String, holder: String) =
    WarrantyBlockEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        isLifetime = false,
        holder = holder,
        provider = null,
        startsOn = null,
        expiresOn = null,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun warrantyBlockData(itemId: String, holder: String) =
    WarrantyBlock(
        isLifetime = false,
        itemId = itemId,
        holder = holder,
        provider = null,
        startsOn = null,
        expiresOn = null,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun soldToFixture(id: String, itemId: String, buyerName: String) =
    SoldToBlockEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        salePriceMinor = 100L,
        buyerName = buyerName,
        soldOn = null,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun purchasedFromFixture(id: String, itemId: String, vendor: String) =
    PurchasedFromBlockEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        purchasePriceMinor = 100L,
        vendor = vendor,
        purchasedOn = null,
        orderReference = null,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun identificationFixture(id: String, itemId: String, value: String) =
    ItemIdentificationEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        kind = "barcode",
        value = value,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun customFieldFixture(id: String, itemId: String, name: String) =
    ItemCustomFieldEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        name = name,
        fieldType = "text",
        fieldDefId = null,
        textValue = "x",
        numberValue = null,
        boolValue = null,
        dateValue = null,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun stockAdjustmentFixture(id: String, itemId: String, reason: String) =
    StockAdjustmentEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        delta = -1L,
        resultingQuantity = 4L,
        reason = reason,
        note = null,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun locationFixture(id: String, name: String) =
    LocationEntity(
        id = id,
        groupChangeSeq = 1L,
        name = name,
        parentId = null,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun labelFixture(id: String, name: String) =
    LabelEntity(
        id = id,
        groupChangeSeq = 1L,
        name = name,
        color = "#ffffff",
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

private fun itemLabelFixture(id: String, itemId: String, labelId: String) =
    ItemLabelEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        labelId = labelId,
    )

private fun attachmentFixture(id: String, itemId: String, filename: String) =
    AttachmentEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        category = "image",
        originalFilename = filename,
        contentType = "image/jpeg",
        sizeBytes = 10L,
        sha256 = "abc123",
        hasThumbnail = false,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )
