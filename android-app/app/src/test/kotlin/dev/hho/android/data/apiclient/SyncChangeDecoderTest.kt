package dev.hho.android.data.apiclient

import dev.hho.android.data.apiclient.generated.infrastructure.Serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncChangeDecoderTest {

    private val json = Serializer.kotlinxSerializationJson

    private fun decode(element: String): SyncChange = SyncChangeDecoder.decodeChange(json.parseToJsonElement(element))

    @Test
    fun `item decodes to ItemChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"item","id":"item-1","group_change_seq":1,
                   |"data":{"id":"item-1","name":"Drill"}}""".trimMargin(),
            )

        val item = change as SyncChange.ItemChange
        assertEquals("item-1", item.id)
        assertEquals(1L, item.groupChangeSeq)
        assertEquals("Drill", item.data.name)
    }

    @Test
    fun `warranty_block decodes to WarrantyBlockChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"warranty_block","id":"wb-1","group_change_seq":2,
                   |"data":{"is_lifetime":true}}""".trimMargin(),
            )

        val warranty = change as SyncChange.WarrantyBlockChange
        assertEquals("wb-1", warranty.id)
        assertEquals(2L, warranty.groupChangeSeq)
        assertEquals(true, warranty.data.isLifetime)
    }

    @Test
    fun `sold_to_block decodes to SoldToBlockChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"sold_to_block","id":"stb-1","group_change_seq":3,
                   |"data":{"sale_price_minor":1000}}""".trimMargin(),
            )

        val soldTo = change as SyncChange.SoldToBlockChange
        assertEquals("stb-1", soldTo.id)
        assertEquals(3L, soldTo.groupChangeSeq)
        assertEquals(1000L, soldTo.data.salePriceMinor)
    }

    @Test
    fun `purchased_from_block decodes to PurchasedFromBlockChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"purchased_from_block","id":"pfb-1","group_change_seq":4,
                   |"data":{"purchase_price_minor":2000}}""".trimMargin(),
            )

        val purchasedFrom = change as SyncChange.PurchasedFromBlockChange
        assertEquals("pfb-1", purchasedFrom.id)
        assertEquals(4L, purchasedFrom.groupChangeSeq)
        assertEquals(2000L, purchasedFrom.data.purchasePriceMinor)
    }

    @Test
    fun `item_identification decodes to ItemIdentificationChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"item_identification","id":"ident-1","group_change_seq":5,
                   |"data":{"kind":"serial","value":"SN123"}}""".trimMargin(),
            )

        val identification = change as SyncChange.ItemIdentificationChange
        assertEquals("ident-1", identification.id)
        assertEquals(5L, identification.groupChangeSeq)
        assertEquals("SN123", identification.data.value)
    }

    @Test
    fun `item_custom_field decodes to ItemCustomFieldChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"item_custom_field","id":"icf-1","group_change_seq":6,
                   |"data":{"name":"Color","field_type":"text"}}""".trimMargin(),
            )

        val customField = change as SyncChange.ItemCustomFieldChange
        assertEquals("icf-1", customField.id)
        assertEquals(6L, customField.groupChangeSeq)
        assertEquals("Color", customField.data.name)
    }

    @Test
    fun `stock_adjustment decodes to StockAdjustmentChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"stock_adjustment","id":"sa-1","group_change_seq":7,
                   |"data":{"delta":1,"resulting_quantity":5}}""".trimMargin(),
            )

        val stockAdjustment = change as SyncChange.StockAdjustmentChange
        assertEquals("sa-1", stockAdjustment.id)
        assertEquals(7L, stockAdjustment.groupChangeSeq)
        assertEquals(5L, stockAdjustment.data.resultingQuantity)
    }

    @Test
    fun `location decodes to LocationChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"location","id":"loc-1","group_change_seq":8,
                   |"data":{"id":"loc-1","name":"Garage","created_at":1,"updated_at":1,"version":1}}""".trimMargin(),
            )

        val location = change as SyncChange.LocationChange
        assertEquals("loc-1", location.id)
        assertEquals(8L, location.groupChangeSeq)
        assertEquals("Garage", location.data.name)
    }

    @Test
    fun `label decodes to LabelChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"label","id":"lbl-1","group_change_seq":9,
                   |"data":{"id":"lbl-1","name":"Fragile","color":"#ff0000","created_at":1,
                   |"updated_at":1,"version":1}}""".trimMargin(),
            )

        val label = change as SyncChange.LabelChange
        assertEquals("lbl-1", label.id)
        assertEquals(9L, label.groupChangeSeq)
        assertEquals("Fragile", label.data.name)
    }

    @Test
    fun `item_label decodes to ItemLabelChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"item_label","id":"il-1","group_change_seq":10,
                   |"data":{"item_id":"item-1","label_id":"lbl-1"}}""".trimMargin(),
            )

        val itemLabel = change as SyncChange.ItemLabelChange
        assertEquals("il-1", itemLabel.id)
        assertEquals(10L, itemLabel.groupChangeSeq)
        assertEquals("lbl-1", itemLabel.data.labelId)
    }

    @Test
    fun `attachment decodes to AttachmentChange with its own payload`() {
        val change =
            decode(
                """{"entity_type":"attachment","id":"att-1","group_change_seq":11,
                   |"data":{"id":"att-1","item_id":"item-1","category":"image",
                   |"original_filename":"photo.jpg","content_type":"image/jpeg","size_bytes":1024,
                   |"sha256":"abc123","created_at":1,"updated_at":1,"version":1,
                   |"has_thumbnail":false}}""".trimMargin(),
            )

        val attachment = change as SyncChange.AttachmentChange
        assertEquals("att-1", attachment.id)
        assertEquals(11L, attachment.groupChangeSeq)
        assertEquals("photo.jpg", attachment.data.originalFilename)
    }

    @Test
    fun `unknown entity_type fails loudly instead of being dropped`() {
        val thrown =
            assertThrows(UnknownSyncEntityTypeException::class.java) {
                decode(
                    """{"entity_type":"widget","id":"w-1","group_change_seq":1,"data":{}}""",
                )
            }

        assertEquals("widget", thrown.rawEntityType)
    }

    @Test
    fun `missing entity_type field fails loudly`() {
        val thrown =
            assertThrows(UnknownSyncEntityTypeException::class.java) {
                decode("""{"id":"w-1","group_change_seq":1,"data":{}}""")
            }

        assertEquals(null, thrown.rawEntityType)
    }

    @Test
    fun `a change entry that fails to decode against its own variant is a MalformedSyncPullResponseException`() {
        assertThrows(MalformedSyncPullResponseException::class.java) {
            decode("""{"entity_type":"item","id":"item-1","group_change_seq":1,"data":{}}""")
        }
    }

    @Test
    fun `full pull envelope decodes changes and tombstones together`() {
        val outcome =
            SyncChangeDecoder.decodePullResponse(
                json.parseToJsonElement(
                    """
                    {
                      "changes": [
                        {"entity_type":"location","id":"loc-1","group_change_seq":1,
                         "data":{"id":"loc-1","name":"Garage","created_at":1,"updated_at":1,"version":1}}
                      ],
                      "tombstones": [
                        {"entity_type":"label","id":"lbl-9","deleted_at":123}
                      ],
                      "next_watermark": 42,
                      "has_more": false
                    }
                    """.trimIndent(),
                ),
            ) as SyncPullOutcome.Page

        assertEquals(1, outcome.changes.size)
        assertEquals(1, outcome.tombstones.size)
        assertEquals("lbl-9", outcome.tombstones[0].id)
        assertEquals(42L, outcome.nextWatermark)
        assertEquals(false, outcome.hasMore)
    }

    @Test
    fun `cursor_too_old shape is a distinct outcome, not a Page`() {
        val outcome = SyncChangeDecoder.decodePullResponse(json.parseToJsonElement("""{"cursor_too_old":true}"""))

        assertEquals(SyncPullOutcome.CursorTooOld, outcome)
    }
}
