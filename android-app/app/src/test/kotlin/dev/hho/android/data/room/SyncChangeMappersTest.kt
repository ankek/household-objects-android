package dev.hho.android.data.room

import dev.hho.android.data.apiclient.SyncChange
import dev.hho.android.data.apiclient.generated.models.Attachment
import dev.hho.android.data.apiclient.generated.models.Identification
import dev.hho.android.data.apiclient.generated.models.Item
import dev.hho.android.data.apiclient.generated.models.ItemCustomField
import dev.hho.android.data.apiclient.generated.models.ItemLabelAssignment
import dev.hho.android.data.apiclient.generated.models.Label
import dev.hho.android.data.apiclient.generated.models.Location
import dev.hho.android.data.apiclient.generated.models.PurchaseBlock
import dev.hho.android.data.apiclient.generated.models.SaleBlock
import dev.hho.android.data.apiclient.generated.models.StockAdjustment
import dev.hho.android.data.apiclient.generated.models.WarrantyBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SyncChangeMappersTest {

    @Test
    fun `item entity id and fields come from the envelope and payload`() {
        val change =
            SyncChange.ItemChange(
                id = "item-envelope-id",
                groupChangeSeq = 7L,
                data =
                    Item(
                        name = "Drill",
                        id = "item-envelope-id",
                        description = "Cordless",
                        locationId = "loc-1",
                        quantity = 2L,
                        shortCode = "AB12",
                        createdAt = 100L,
                        updatedAt = 200L,
                        version = 3L,
                    ),
            )

        val entity = change.toEntity()

        assertEquals("item-envelope-id", entity.id)
        assertEquals(7L, entity.groupChangeSeq)
        assertEquals("Drill", entity.name)
        assertEquals("Cordless", entity.description)
        assertEquals("loc-1", entity.locationId)
        assertEquals(2L, entity.quantity)
        assertEquals("AB12", entity.shortCode)
        assertEquals(100L, entity.createdAt)
        assertEquals(200L, entity.updatedAt)
        assertEquals(3L, entity.version)
    }

    @Test
    fun `warranty block PK comes from the envelope id, never item_id`() {
        val change =
            SyncChange.WarrantyBlockChange(
                id = "warranty-envelope-id",
                groupChangeSeq = 1L,
                data =
                    WarrantyBlock(
                        isLifetime = true,
                        itemId = "item-42",
                        holder = "Acme",
                        provider = "Acme Warranty Co",
                        startsOn = LocalDate.of(2026, 1, 1),
                        expiresOn = LocalDate.of(2028, 1, 1),
                        notes = "Extended",
                        createdAt = 10L,
                        updatedAt = 20L,
                        version = 1L,
                    ),
            )

        val entity = change.toEntity()

        assertEquals("warranty-envelope-id", entity.id)
        assertNotEquals(entity.id, entity.itemId)
        assertEquals("item-42", entity.itemId)
        assertEquals(true, entity.isLifetime)
        assertEquals("Acme", entity.holder)
        assertEquals("Acme Warranty Co", entity.provider)
        assertEquals(LocalDate.of(2026, 1, 1), entity.startsOn)
        assertEquals(LocalDate.of(2028, 1, 1), entity.expiresOn)
        assertEquals("Extended", entity.notes)
    }

    @Test
    fun `sold_to block PK comes from the envelope id, never item_id`() {
        val change =
            SyncChange.SoldToBlockChange(
                id = "sold-to-envelope-id",
                groupChangeSeq = 2L,
                data =
                    SaleBlock(
                        salePriceMinor = 5000L,
                        itemId = "item-7",
                        buyerName = "Jane",
                        soldOn = LocalDate.of(2026, 6, 1),
                        notes = "eBay",
                    ),
            )

        val entity = change.toEntity()

        assertEquals("sold-to-envelope-id", entity.id)
        assertNotEquals(entity.id, entity.itemId)
        assertEquals("item-7", entity.itemId)
        assertEquals(5000L, entity.salePriceMinor)
        assertEquals("Jane", entity.buyerName)
        assertEquals(LocalDate.of(2026, 6, 1), entity.soldOn)
    }

    @Test
    fun `purchased_from block PK comes from the envelope id, never item_id`() {
        val change =
            SyncChange.PurchasedFromBlockChange(
                id = "purchased-from-envelope-id",
                groupChangeSeq = 3L,
                data =
                    PurchaseBlock(
                        purchasePriceMinor = 12000L,
                        itemId = "item-9",
                        vendor = "Acme Store",
                        purchasedOn = LocalDate.of(2025, 12, 25),
                        orderReference = "ORD-1",
                        notes = "Gift",
                    ),
            )

        val entity = change.toEntity()

        assertEquals("purchased-from-envelope-id", entity.id)
        assertNotEquals(entity.id, entity.itemId)
        assertEquals("item-9", entity.itemId)
        assertEquals(12000L, entity.purchasePriceMinor)
        assertEquals("Acme Store", entity.vendor)
        assertEquals(LocalDate.of(2025, 12, 25), entity.purchasedOn)
        assertEquals("ORD-1", entity.orderReference)
    }

    @Test
    fun `item_identification entity id comes from the envelope, not data id`() {
        val change =
            SyncChange.ItemIdentificationChange(
                id = "envelope-id",
                groupChangeSeq = 4L,
                data =
                    Identification(
                        kind = Identification.Kind.Barcode,
                        value = "0123456789",
                        id = "data-own-id-should-be-unused",
                        itemId = "item-3",
                    ),
            )

        val entity = change.toEntity()

        assertEquals("envelope-id", entity.id)
        assertNotEquals("data-own-id-should-be-unused", entity.id)
        assertEquals("item-3", entity.itemId)
        assertEquals("barcode", entity.kind)
        assertEquals("0123456789", entity.value)
    }

    @Test
    fun `item_custom_field entity id comes from the envelope, not data id`() {
        val change =
            SyncChange.ItemCustomFieldChange(
                id = "envelope-id",
                groupChangeSeq = 5L,
                data =
                    ItemCustomField(
                        name = "Warranty months",
                        fieldType = ItemCustomField.FieldType.Number,
                        id = "data-own-id-should-be-unused",
                        itemId = "item-5",
                        fieldDefId = "def-1",
                        numberValue = BigDecimal("12.50"),
                    ),
            )

        val entity = change.toEntity()

        assertEquals("envelope-id", entity.id)
        assertNotEquals("data-own-id-should-be-unused", entity.id)
        assertEquals("item-5", entity.itemId)
        assertEquals("number", entity.fieldType)
        assertEquals(BigDecimal("12.50"), entity.numberValue)
        assertEquals("def-1", entity.fieldDefId)
    }

    @Test
    fun `stock_adjustment entity id comes from the envelope, not data id`() {
        val change =
            SyncChange.StockAdjustmentChange(
                id = "envelope-id",
                groupChangeSeq = 6L,
                data =
                    StockAdjustment(
                        delta = -1L,
                        resultingQuantity = 4L,
                        id = "data-own-id-should-be-unused",
                        itemId = "item-6",
                        reason = "sold",
                    ),
            )

        val entity = change.toEntity()

        assertEquals("envelope-id", entity.id)
        assertNotEquals("data-own-id-should-be-unused", entity.id)
        assertEquals("item-6", entity.itemId)
        assertEquals(-1L, entity.delta)
        assertEquals(4L, entity.resultingQuantity)
        assertEquals("sold", entity.reason)
    }

    @Test
    fun `location entity id comes from the envelope, not data id`() {
        val change =
            SyncChange.LocationChange(
                id = "envelope-id",
                groupChangeSeq = 8L,
                data =
                    Location(
                        id = "data-own-id-should-be-unused",
                        name = "Garage",
                        createdAt = 1L,
                        updatedAt = 2L,
                        version = 1L,
                        parentId = "loc-parent",
                    ),
            )

        val entity = change.toEntity()

        assertEquals("envelope-id", entity.id)
        assertNotEquals("data-own-id-should-be-unused", entity.id)
        assertEquals("Garage", entity.name)
        assertEquals("loc-parent", entity.parentId)
    }

    @Test
    fun `label entity id comes from the envelope, not data id`() {
        val change =
            SyncChange.LabelChange(
                id = "envelope-id",
                groupChangeSeq = 9L,
                data =
                    Label(
                        id = "data-own-id-should-be-unused",
                        name = "Fragile",
                        color = "#ff0000",
                        createdAt = 1L,
                        updatedAt = 2L,
                        version = 1L,
                    ),
            )

        val entity = change.toEntity()

        assertEquals("envelope-id", entity.id)
        assertNotEquals("data-own-id-should-be-unused", entity.id)
        assertEquals("Fragile", entity.name)
        assertEquals("#ff0000", entity.color)
    }

    @Test
    fun `item_label PK comes from the envelope id, never item_id or label_id`() {
        val change =
            SyncChange.ItemLabelChange(
                id = "assignment-envelope-id",
                groupChangeSeq = 10L,
                data = ItemLabelAssignment(itemId = "item-1", labelId = "label-1"),
            )

        val entity = change.toEntity()

        assertEquals("assignment-envelope-id", entity.id)
        assertNotEquals(entity.id, entity.itemId)
        assertNotEquals(entity.id, entity.labelId)
        assertEquals("item-1", entity.itemId)
        assertEquals("label-1", entity.labelId)
    }

    @Test
    fun `attachment entity id comes from the envelope, not data id`() {
        val change =
            SyncChange.AttachmentChange(
                id = "envelope-id",
                groupChangeSeq = 11L,
                data =
                    Attachment(
                        id = "data-own-id-should-be-unused",
                        itemId = "item-8",
                        category = Attachment.Category.Image,
                        originalFilename = "photo.jpg",
                        contentType = "image/jpeg",
                        sizeBytes = 1024L,
                        sha256 = "abc123",
                        createdAt = 1L,
                        updatedAt = 2L,
                        version = 1L,
                        hasThumbnail = true,
                    ),
            )

        val entity = change.toEntity()

        assertEquals("envelope-id", entity.id)
        assertNotEquals("data-own-id-should-be-unused", entity.id)
        assertEquals("item-8", entity.itemId)
        assertEquals("image", entity.category)
        assertEquals("photo.jpg", entity.originalFilename)
        assertEquals(true, entity.hasThumbnail)
    }
}
