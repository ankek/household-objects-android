package dev.hho.android.ui.items

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemCustomFieldEntity
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.ItemLabelEntity
import dev.hho.android.data.room.LabelEntity
import dev.hho.android.data.room.LocationEntity
import dev.hho.android.data.room.PurchasedFromBlockEntity
import dev.hho.android.data.room.SoldToBlockEntity
import dev.hho.android.data.room.StockAdjustmentEntity
import dev.hho.android.data.room.WarrantyBlockEntity
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ItemDetailViewModelTest {

    private lateinit var db: HhoDatabase
    private lateinit var viewModel: ItemDetailViewModel
    private val viewModels = ViewModelTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        viewModel = viewModels.track(ItemDetailViewModel(
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
        ))
    }

    @After
    fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun itemFixture(
        id: String,
        name: String,
        locationId: String? = null,
        quantity: Long? = 2L,
        shortCode: String? = "SC-1",
        description: String? = "A cordless drill",
    ) = ItemEntity(
        id = id,
        groupChangeSeq = 1L,
        name = name,
        description = description,
        locationId = locationId,
        quantity = quantity,
        shortCode = shortCode,
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
        ItemLabelEntity(id = id, groupChangeSeq = 1L, itemId = itemId, labelId = labelId)

    private fun warrantyFixture(
        id: String,
        itemId: String,
        isLifetime: Boolean = false,
        holder: String? = "Jane Doe",
        provider: String? = "Acme Warranties",
        startsOn: LocalDate? = LocalDate.of(2024, 1, 1),
        expiresOn: LocalDate? = LocalDate.of(2026, 1, 1),
        notes: String? = "Keep receipt",
    ) = WarrantyBlockEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        isLifetime = isLifetime,
        holder = holder,
        provider = provider,
        startsOn = startsOn,
        expiresOn = expiresOn,
        notes = notes,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

    private fun soldToFixture(
        id: String,
        itemId: String,
        salePriceMinor: Long = 5_000L,
        buyerName: String? = "Alex Buyer",
        soldOn: LocalDate? = LocalDate.of(2024, 6, 1),
        notes: String? = "Sold at yard sale",
    ) = SoldToBlockEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        salePriceMinor = salePriceMinor,
        buyerName = buyerName,
        soldOn = soldOn,
        notes = notes,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

    private fun purchasedFromFixture(
        id: String,
        itemId: String,
        purchasePriceMinor: Long = 12_000L,
        vendor: String? = "Hardware Store",
        purchasedOn: LocalDate? = LocalDate.of(2023, 3, 1),
        orderReference: String? = "PO-42",
        notes: String? = "Bought on sale",
    ) = PurchasedFromBlockEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        purchasePriceMinor = purchasePriceMinor,
        vendor = vendor,
        purchasedOn = purchasedOn,
        orderReference = orderReference,
        notes = notes,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

    private fun identifierFixture(id: String, itemId: String, kind: String = "barcode", value: String = "012345") =
        ItemIdentificationEntity(
            id = id,
            groupChangeSeq = 1L,
            itemId = itemId,
            kind = kind,
            value = value,
            createdAt = 1L,
            updatedAt = 1L,
            version = 1L,
        )

    private fun customFieldFixture(
        id: String,
        itemId: String,
        name: String,
        fieldType: String,
        textValue: String? = null,
        numberValue: BigDecimal? = null,
        boolValue: Boolean? = null,
        dateValue: LocalDate? = null,
    ) = ItemCustomFieldEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        name = name,
        fieldType = fieldType,
        fieldDefId = null,
        textValue = textValue,
        numberValue = numberValue,
        boolValue = boolValue,
        dateValue = dateValue,
        createdAt = 1L,
        updatedAt = 1L,
        version = 1L,
    )

    private fun stockAdjustmentFixture(
        id: String,
        itemId: String,
        delta: Long,
        resultingQuantity: Long,
        reason: String? = "manual",
        note: String? = null,
        createdAt: Long = 1L,
    ) = StockAdjustmentEntity(
        id = id,
        groupChangeSeq = 1L,
        itemId = itemId,
        delta = delta,
        resultingQuantity = resultingQuantity,
        reason = reason,
        note = note,
        createdAt = createdAt,
        updatedAt = createdAt,
        version = 1L,
    )

    @Test
    fun `shows the requested item's core fields, location and labels`() = runTest {
        db.locationDao().upsert(locationFixture("loc-1", "Garage"))
        db.labelDao().upsert(labelFixture("label-1", "Power tools"))
        db.labelDao().upsert(labelFixture("label-2", "Fragile"))
        db.itemDao().upsert(itemFixture("item-1", name = "Drill", locationId = "loc-1"))
        db.itemLabelDao().upsert(itemLabelFixture("il-1", itemId = "item-1", labelId = "label-1"))
        db.itemLabelDao().upsert(itemLabelFixture("il-2", itemId = "item-1", labelId = "label-2"))

        viewModel.loadItem("item-1")
        val state = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data

        assertEquals(
            ItemDetailData(
                id = "item-1",
                name = "Drill",
                description = "A cordless drill",
                quantity = 2L,
                shortCode = "SC-1",
                locationName = "Garage",
                labelNames = listOf("Power tools", "Fragile"),
                warranty = null,
                soldTo = null,
                purchasedFrom = null,
                identifiers = emptyList(),
                customFields = emptyList(),
                stockAdjustments = emptyList(),
            ),
            state.item,
        )
    }

    @Test
    fun `renders the warranty, sold_to and purchased_from blocks when present`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.warrantyBlockDao().upsert(warrantyFixture("warranty-1", itemId = "item-1"))
        db.soldToBlockDao().upsert(soldToFixture("sold-1", itemId = "item-1"))
        db.purchasedFromBlockDao().upsert(purchasedFromFixture("purchased-1", itemId = "item-1"))

        viewModel.loadItem("item-1")
        val state = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data

        assertEquals(
            WarrantyBlockData(
                isLifetime = false,
                holder = "Jane Doe",
                provider = "Acme Warranties",
                startsOn = LocalDate.of(2024, 1, 1),
                expiresOn = LocalDate.of(2026, 1, 1),
                notes = "Keep receipt",
            ),
            state.item.warranty,
        )
        assertEquals(
            SoldToBlockData(
                salePriceMinor = 5_000L,
                buyerName = "Alex Buyer",
                soldOn = LocalDate.of(2024, 6, 1),
                notes = "Sold at yard sale",
            ),
            state.item.soldTo,
        )
        assertEquals(
            PurchasedFromBlockData(
                purchasePriceMinor = 12_000L,
                vendor = "Hardware Store",
                purchasedOn = LocalDate.of(2023, 3, 1),
                orderReference = "PO-42",
                notes = "Bought on sale",
            ),
            state.item.purchasedFrom,
        )
    }

    @Test
    fun `absent warranty, sold_to and purchased_from blocks render as null, not empty placeholders`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))

        viewModel.loadItem("item-1")
        val state = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data

        assertNull(state.item.warranty)
        assertNull(state.item.soldTo)
        assertNull(state.item.purchasedFrom)
    }

    @Test
    fun `renders identifiers with their type and value`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.itemIdentificationDao().upsert(identifierFixture("id-1", itemId = "item-1", kind = "barcode", value = "111"))
        db.itemIdentificationDao().upsert(identifierFixture("id-2", itemId = "item-1", kind = "serial", value = "SN-9"))

        viewModel.loadItem("item-1")
        val state = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data

        assertEquals(
            listOf(
                IdentifierData(kind = "barcode", value = "111"),
                IdentifierData(kind = "serial", value = "SN-9"),
            ),
            state.item.identifiers.sortedBy { it.kind },
        )
    }

    @Test
    fun `a custom field of each type maps to the right typed value`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.itemCustomFieldDao().upsert(
            customFieldFixture("cf-text", itemId = "item-1", name = "Color", fieldType = "text", textValue = "Red"),
        )
        db.itemCustomFieldDao().upsert(
            customFieldFixture(
                "cf-number",
                itemId = "item-1",
                name = "Weight (kg)",
                fieldType = "number",
                numberValue = BigDecimal("1.50"),
            ),
        )
        db.itemCustomFieldDao().upsert(
            customFieldFixture("cf-bool", itemId = "item-1", name = "Cordless", fieldType = "boolean", boolValue = true),
        )
        db.itemCustomFieldDao().upsert(
            customFieldFixture(
                "cf-date",
                itemId = "item-1",
                name = "Last serviced",
                fieldType = "date",
                dateValue = LocalDate.of(2025, 5, 1),
            ),
        )

        viewModel.loadItem("item-1")
        val state = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data

        val byName = state.item.customFields.associateBy { it.name }
        assertEquals(CustomFieldValue.Text("Red"), byName.getValue("Color").value)
        assertEquals(CustomFieldValue.Number(BigDecimal("1.50")), byName.getValue("Weight (kg)").value)
        assertEquals(CustomFieldValue.Bool(true), byName.getValue("Cordless").value)
        assertEquals(CustomFieldValue.Date(LocalDate.of(2025, 5, 1)), byName.getValue("Last serviced").value)
    }

    @Test
    fun `renders stock adjustments as history`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.stockAdjustmentDao().upsert(
            stockAdjustmentFixture("adj-1", itemId = "item-1", delta = 5L, resultingQuantity = 5L, createdAt = 1L),
        )
        db.stockAdjustmentDao().upsert(
            stockAdjustmentFixture("adj-2", itemId = "item-1", delta = -2L, resultingQuantity = 3L, createdAt = 2L),
        )

        viewModel.loadItem("item-1")
        val state = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data

        assertEquals(
            listOf(
                StockAdjustmentData(delta = 5L, resultingQuantity = 5L, reason = "manual", note = null, createdAt = 1L),
                StockAdjustmentData(delta = -2L, resultingQuantity = 3L, reason = "manual", note = null, createdAt = 2L),
            ),
            state.item.stockAdjustments,
        )
    }

    @Test
    fun `absent identifiers, custom fields and stock adjustments render as empty lists`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))

        viewModel.loadItem("item-1")
        val state = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data

        assertTrue(state.item.identifiers.isEmpty())
        assertTrue(state.item.customFields.isEmpty())
        assertTrue(state.item.stockAdjustments.isEmpty())
    }

    @Test
    fun `inserting a warranty row after load shows it live`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        viewModel.loadItem("item-1")
        val before = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data
        assertNull(before.item.warranty)

        db.warrantyBlockDao().upsert(warrantyFixture("warranty-1", itemId = "item-1", holder = "New Holder"))

        val after = viewModel.uiState.first {
            it is ItemDetailUiState.Data && it.item.warranty != null
        } as ItemDetailUiState.Data
        assertEquals("New Holder", after.item.warranty?.holder)
    }

    @Test
    fun `shows a different item after loadItem is called again with a new id`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.itemDao().upsert(itemFixture("item-2", name = "Saw"))

        viewModel.loadItem("item-1")
        val first = viewModel.uiState.first { it is ItemDetailUiState.Data } as ItemDetailUiState.Data
        assertEquals("Drill", first.item.name)

        viewModel.loadItem("item-2")
        val second = viewModel.uiState.first {
            it is ItemDetailUiState.Data && it.item.id == "item-2"
        } as ItemDetailUiState.Data
        assertEquals("Saw", second.item.name)
    }

    @Test
    fun `a missing id gives a not-found state, not a crash`() = runTest {
        viewModel.loadItem("does-not-exist")

        val state = viewModel.uiState.first { it !is ItemDetailUiState.Loading }

        assertEquals(ItemDetailUiState.NotFound, state)
    }

    @Test
    fun `an item deleted after being shown transitions live from Data to NotFound`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        viewModel.loadItem("item-1")
        val before = viewModel.uiState.first { it is ItemDetailUiState.Data }
        assertEquals(ItemDetailData::class, (before as ItemDetailUiState.Data).item::class)

        db.itemDao().deleteById("item-1")

        val after = viewModel.uiState.first { it is ItemDetailUiState.NotFound }
        assertEquals(ItemDetailUiState.NotFound, after)
    }
}
