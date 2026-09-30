package dev.hho.android.data.sync

import dev.hho.android.data.apiclient.MalformedSyncPullResponseException
import dev.hho.android.data.apiclient.SyncChange
import dev.hho.android.data.apiclient.SyncPullOutcome
import dev.hho.android.data.apiclient.UnknownSyncEntityTypeException
import dev.hho.android.data.apiclient.generated.models.SyncTombstoneEntry
import dev.hho.android.data.room.AttachmentEntity
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
import dev.hho.android.data.room.toEntity

internal object SyncMirrorApplier {
    suspend fun clearMirror(db: HhoDatabase) {
        db.itemDao().clearAll()
        db.warrantyBlockDao().clearAll()
        db.soldToBlockDao().clearAll()
        db.purchasedFromBlockDao().clearAll()
        db.itemIdentificationDao().clearAll()
        db.itemCustomFieldDao().clearAll()
        db.stockAdjustmentDao().clearAll()
        db.locationDao().clearAll()
        db.labelDao().clearAll()
        db.itemLabelDao().clearAll()
        db.attachmentDao().clearAll()
    }

    suspend fun applyPage(
        db: HhoDatabase,
        page: SyncPullOutcome.Page,
    ) {
        applyChanges(db, page.changes)
        applyTombstones(db, page.tombstones)
    }

    private suspend fun applyChanges(
        db: HhoDatabase,
        changes: List<SyncChange>,
    ) {
        if (changes.isEmpty()) return

        val items = mutableListOf<ItemEntity>()
        val warrantyBlocks = mutableListOf<WarrantyBlockEntity>()
        val soldToBlocks = mutableListOf<SoldToBlockEntity>()
        val purchasedFromBlocks = mutableListOf<PurchasedFromBlockEntity>()
        val identifications = mutableListOf<ItemIdentificationEntity>()
        val customFields = mutableListOf<ItemCustomFieldEntity>()
        val stockAdjustments = mutableListOf<StockAdjustmentEntity>()
        val locations = mutableListOf<LocationEntity>()
        val labels = mutableListOf<LabelEntity>()
        val itemLabels = mutableListOf<ItemLabelEntity>()
        val attachments = mutableListOf<AttachmentEntity>()

        for (change in changes) {
            when (change) {
                is SyncChange.ItemChange -> items += change.toEntity()
                is SyncChange.WarrantyBlockChange -> warrantyBlocks += change.toEntity()
                is SyncChange.SoldToBlockChange -> soldToBlocks += change.toEntity()
                is SyncChange.PurchasedFromBlockChange -> purchasedFromBlocks += change.toEntity()
                is SyncChange.ItemIdentificationChange -> identifications += change.toEntity()
                is SyncChange.ItemCustomFieldChange -> customFields += change.toEntity()
                is SyncChange.StockAdjustmentChange -> stockAdjustments += change.toEntity()
                is SyncChange.LocationChange -> locations += change.toEntity()
                is SyncChange.LabelChange -> labels += change.toEntity()
                is SyncChange.ItemLabelChange -> itemLabels += change.toEntity()
                is SyncChange.AttachmentChange -> attachments += change.toEntity()
            }
        }

        if (items.isNotEmpty()) db.itemDao().upsertAll(items)
        if (warrantyBlocks.isNotEmpty()) db.warrantyBlockDao().upsertAll(warrantyBlocks)
        if (soldToBlocks.isNotEmpty()) db.soldToBlockDao().upsertAll(soldToBlocks)
        if (purchasedFromBlocks.isNotEmpty()) db.purchasedFromBlockDao().upsertAll(purchasedFromBlocks)
        if (identifications.isNotEmpty()) db.itemIdentificationDao().upsertAll(identifications)
        if (customFields.isNotEmpty()) db.itemCustomFieldDao().upsertAll(customFields)
        if (stockAdjustments.isNotEmpty()) db.stockAdjustmentDao().upsertAll(stockAdjustments)
        if (locations.isNotEmpty()) db.locationDao().upsertAll(locations)
        if (labels.isNotEmpty()) db.labelDao().upsertAll(labels)
        if (itemLabels.isNotEmpty()) db.itemLabelDao().upsertAll(itemLabels)
        if (attachments.isNotEmpty()) db.attachmentDao().upsertAll(attachments)
    }

    private suspend fun applyTombstones(
        db: HhoDatabase,
        tombstones: List<SyncTombstoneEntry>,
    ) {
        for (tombstone in tombstones) {
            val id = tombstone.id ?: throw MalformedSyncPullResponseException("tombstone missing 'id'")
            when (val entityType = tombstone.entityType) {
                "item" -> db.itemDao().deleteById(id)
                "warranty_block" -> db.warrantyBlockDao().deleteById(id)
                "sold_to_block" -> db.soldToBlockDao().deleteById(id)
                "purchased_from_block" -> db.purchasedFromBlockDao().deleteById(id)
                "item_identification" -> db.itemIdentificationDao().deleteById(id)
                "item_custom_field" -> db.itemCustomFieldDao().deleteById(id)
                "stock_adjustment" -> db.stockAdjustmentDao().deleteById(id)
                "location" -> db.locationDao().deleteById(id)
                "label" -> db.labelDao().deleteById(id)
                "item_label" -> db.itemLabelDao().deleteById(id)
                "attachment" -> db.attachmentDao().deleteById(id)
                else -> throw UnknownSyncEntityTypeException(entityType)
            }
        }
    }
}
