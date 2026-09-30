package dev.hho.android.data.apiclient

import dev.hho.android.data.apiclient.generated.infrastructure.Serializer
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
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryAttachment
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryItem
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryItemCustomField
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryItemIdentification
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryItemLabel
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryLabel
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryLocation
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryPurchasedFromBlock
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntrySoldToBlock
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryStockAdjustment
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryWarrantyBlock
import dev.hho.android.data.apiclient.generated.models.SyncTombstoneEntry
import dev.hho.android.data.apiclient.generated.models.WarrantyBlock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface SyncChange {
    val id: String

    val groupChangeSeq: Long

    data class ItemChange(override val id: String, override val groupChangeSeq: Long, val data: Item) : SyncChange

    data class WarrantyBlockChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: WarrantyBlock,
    ) : SyncChange

    data class SoldToBlockChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: SaleBlock,
    ) : SyncChange

    data class PurchasedFromBlockChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: PurchaseBlock,
    ) : SyncChange

    data class ItemIdentificationChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: Identification,
    ) : SyncChange

    data class ItemCustomFieldChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: ItemCustomField,
    ) : SyncChange

    data class StockAdjustmentChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: StockAdjustment,
    ) : SyncChange

    data class LocationChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: Location,
    ) : SyncChange

    data class LabelChange(override val id: String, override val groupChangeSeq: Long, val data: Label) : SyncChange

    data class ItemLabelChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: ItemLabelAssignment,
    ) : SyncChange

    data class AttachmentChange(
        override val id: String,
        override val groupChangeSeq: Long,
        val data: Attachment,
    ) : SyncChange
}

sealed interface SyncPullOutcome {
    data class Page(
        val changes: List<SyncChange>,
        val tombstones: List<SyncTombstoneEntry>,
        val nextWatermark: Long,
        val hasMore: Boolean,
    ) : SyncPullOutcome

    data object CursorTooOld : SyncPullOutcome
}

class UnknownSyncEntityTypeException(val rawEntityType: String?) :
    IllegalStateException("Unknown sync entity_type: $rawEntityType — client/server drift (A7 closed set)")

class MalformedSyncPullResponseException(detail: String, cause: Throwable? = null) :
    IllegalStateException("Malformed /sync/pull response: $detail", cause)

internal object SyncChangeDecoder {

    private val json = Serializer.kotlinxSerializationJson

    fun decodePullResponse(root: JsonElement): SyncPullOutcome {
        val obj =
            root as? JsonObject
                ?: throw MalformedSyncPullResponseException("expected a JSON object, got $root")

        if (obj["cursor_too_old"]?.jsonPrimitive?.booleanOrNull == true) {
            return SyncPullOutcome.CursorTooOld
        }

        try {
            val changesJson =
                obj["changes"] as? JsonArray
                    ?: throw MalformedSyncPullResponseException("missing or non-array 'changes'")
            val tombstonesJson =
                obj["tombstones"] as? JsonArray
                    ?: throw MalformedSyncPullResponseException("missing or non-array 'tombstones'")
            val nextWatermark =
                obj["next_watermark"]?.jsonPrimitive?.longOrNull
                    ?: throw MalformedSyncPullResponseException("missing or non-numeric 'next_watermark'")
            val hasMore =
                obj["has_more"]?.jsonPrimitive?.booleanOrNull
                    ?: throw MalformedSyncPullResponseException("missing or non-boolean 'has_more'")

            return SyncPullOutcome.Page(
                changes = changesJson.map { decodeChange(it) },
                tombstones = tombstonesJson.map { json.decodeFromJsonElement<SyncTombstoneEntry>(it) },
                nextWatermark = nextWatermark,
                hasMore = hasMore,
            )
        } catch (e: SerializationException) {
            throw MalformedSyncPullResponseException(e.message ?: "decode failure", e)
        }
    }

    fun decodeChange(element: JsonElement): SyncChange {
        val entityType =
            (element as? JsonObject)?.get("entity_type")?.jsonPrimitive?.contentOrNull
                ?: throw UnknownSyncEntityTypeException(rawEntityType = null)

        return try {
            when (entityType) {
                "item" ->
                    json.decodeFromJsonElement<SyncChangeEntryItem>(element).let {
                        SyncChange.ItemChange(it.id, it.groupChangeSeq, it.data)
                    }
                "warranty_block" ->
                    json.decodeFromJsonElement<SyncChangeEntryWarrantyBlock>(element).let {
                        SyncChange.WarrantyBlockChange(it.id, it.groupChangeSeq, it.data)
                    }
                "sold_to_block" ->
                    json.decodeFromJsonElement<SyncChangeEntrySoldToBlock>(element).let {
                        SyncChange.SoldToBlockChange(it.id, it.groupChangeSeq, it.data)
                    }
                "purchased_from_block" ->
                    json.decodeFromJsonElement<SyncChangeEntryPurchasedFromBlock>(element).let {
                        SyncChange.PurchasedFromBlockChange(it.id, it.groupChangeSeq, it.data)
                    }
                "item_identification" ->
                    json.decodeFromJsonElement<SyncChangeEntryItemIdentification>(element).let {
                        SyncChange.ItemIdentificationChange(it.id, it.groupChangeSeq, it.data)
                    }
                "item_custom_field" ->
                    json.decodeFromJsonElement<SyncChangeEntryItemCustomField>(element).let {
                        SyncChange.ItemCustomFieldChange(it.id, it.groupChangeSeq, it.data)
                    }
                "stock_adjustment" ->
                    json.decodeFromJsonElement<SyncChangeEntryStockAdjustment>(element).let {
                        SyncChange.StockAdjustmentChange(it.id, it.groupChangeSeq, it.data)
                    }
                "location" ->
                    json.decodeFromJsonElement<SyncChangeEntryLocation>(element).let {
                        SyncChange.LocationChange(it.id, it.groupChangeSeq, it.data)
                    }
                "label" ->
                    json.decodeFromJsonElement<SyncChangeEntryLabel>(element).let {
                        SyncChange.LabelChange(it.id, it.groupChangeSeq, it.data)
                    }
                "item_label" ->
                    json.decodeFromJsonElement<SyncChangeEntryItemLabel>(element).let {
                        SyncChange.ItemLabelChange(it.id, it.groupChangeSeq, it.data)
                    }
                "attachment" ->
                    json.decodeFromJsonElement<SyncChangeEntryAttachment>(element).let {
                        SyncChange.AttachmentChange(it.id, it.groupChangeSeq, it.data)
                    }
                else -> throw UnknownSyncEntityTypeException(entityType)
            }
        } catch (e: SerializationException) {
            throw MalformedSyncPullResponseException("entity_type=$entityType: ${e.message}", e)
        }
    }
}
