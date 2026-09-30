package dev.hho.android.data.outbox

import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.ItemLabelEntity
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.PurchasedFromBlockEntity
import dev.hho.android.data.room.StockAdjustmentEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate

internal class UnsupportedMutationException(message: String) : IllegalArgumentException(message)

internal data class LocalMutation(
    val entityType: String,
    val entityId: String,
    val op: String,
    val baseVersion: Long,
    val fields: JsonObject,
) {
    internal companion object {
        fun from(row: OutboxMutationEntity): LocalMutation =
            LocalMutation(
                entityType = row.entityType,
                entityId = row.entityId,
                op = row.op,
                baseVersion = row.baseVersion,
                fields = Json.parseToJsonElement(row.fieldsJson).jsonObject,
            )
    }
}

internal object OptimisticApplier {
    suspend fun apply(db: HhoDatabase, m: LocalMutation, pendingSeq: Long? = null): Boolean =
        when (m.entityType) {
            "item" -> applyItem(db, m)
            "stock_adjustment" -> applyStockAdjustment(db, m, pendingSeq)
            "item_label" -> applyItemLabel(db, m)
            "item_identification" -> applyIdentification(db, m)
            "purchased_from_block" -> applyPurchase(db, m)
            else -> throw UnsupportedMutationException("unsupported entity_type ${m.entityType}")
        }

    private fun requireUpsert(m: LocalMutation) {
        if (m.op != "upsert") throw UnsupportedMutationException("${m.entityType}: op ${m.op} unsupported")
    }

    private suspend fun applyItem(db: HhoDatabase, m: LocalMutation): Boolean {
        requireUpsert(m)
        if ("quantity" in m.fields) throw UnsupportedMutationException("item.quantity changes only via stock_adjustment (A177)")
        val dao = db.itemDao()
        val existing = dao.getById(m.entityId)
        if (m.baseVersion != 0L && existing == null) return false
        val base = existing ?: ItemEntity(
            id = m.entityId, groupChangeSeq = 0, name = "", description = null, locationId = null,
            quantity = 0, shortCode = null, createdAt = null, updatedAt = null, version = 0,
        )
        dao.upsert(
            base.copy(
                name = m.fields.string("name", base.name) ?: base.name,
                description = m.fields.string("description", base.description),
                locationId = m.fields.string("location_id", base.locationId),
            ),
        )
        return true
    }

    private suspend fun applyStockAdjustment(db: HhoDatabase, m: LocalMutation, pendingSeq: Long?): Boolean {
        requireUpsert(m)
        val itemId = m.fields.string("item_id", null) ?: throw UnsupportedMutationException("stock_adjustment needs item_id")
        val delta = m.fields["delta"]?.let { (it as? JsonPrimitive)?.longOrNull }
            ?: throw UnsupportedMutationException("stock_adjustment needs integer delta")
        val item = db.itemDao().getById(itemId)
        val earlier = OutboxOverlay.pendingDeltaFor(db, itemId, beforeSeq = pendingSeq)
        db.stockAdjustmentDao().upsert(
            StockAdjustmentEntity(
                id = m.entityId, groupChangeSeq = 0, itemId = itemId, delta = delta,
                resultingQuantity = (item?.quantity ?: 0) + earlier + delta,
                reason = m.fields.string("reason", null), note = m.fields.string("note", null),
                createdAt = null, updatedAt = null, version = 0,
            ),
        )
        return true
    }

    private suspend fun applyItemLabel(db: HhoDatabase, m: LocalMutation): Boolean {
        val dao = db.itemLabelDao()
        return when (m.op) {
            "delete" -> {
                dao.deleteById(m.entityId)
                true
            }
            "upsert" -> {
                val itemId = m.fields.string("item_id", null) ?: throw UnsupportedMutationException("item_label needs item_id")
                val labelId = m.fields.string("label_id", null) ?: throw UnsupportedMutationException("item_label needs label_id")
                dao.upsert(ItemLabelEntity(id = m.entityId, groupChangeSeq = 0, itemId = itemId, labelId = labelId))
                true
            }
            else -> throw UnsupportedMutationException("item_label: op ${m.op} unsupported")
        }
    }

    private suspend fun applyIdentification(db: HhoDatabase, m: LocalMutation): Boolean {
        requireUpsert(m)
        if (m.baseVersion != 0L) throw UnsupportedMutationException("item_identification is create-only in Phase 4")
        db.itemIdentificationDao().upsert(
            ItemIdentificationEntity(
                id = m.entityId, groupChangeSeq = 0,
                itemId = m.fields.string("item_id", null),
                kind = m.fields.string("kind", "") ?: "",
                value = m.fields.string("value", "") ?: "",
                createdAt = null, updatedAt = null, version = 0,
            ),
        )
        return true
    }

    private suspend fun applyPurchase(db: HhoDatabase, m: LocalMutation): Boolean {
        requireUpsert(m)
        val dao = db.purchasedFromBlockDao()
        val itemId = m.fields.string("item_id", null) ?: throw UnsupportedMutationException("purchased_from_block needs item_id")
        val existing = dao.getByItemId(itemId)?.takeIf { it.id == m.entityId }
        if (m.baseVersion != 0L && existing == null) return false
        val base = existing ?: PurchasedFromBlockEntity(
            id = m.entityId, groupChangeSeq = 0, itemId = itemId, purchasePriceMinor = 0, vendor = null,
            purchasedOn = null, orderReference = null, notes = null, createdAt = null, updatedAt = null, version = 0,
        )
        dao.upsert(
            base.copy(
                itemId = itemId,
                purchasePriceMinor = m.fields["purchase_price_minor"]?.let { (it as? JsonPrimitive)?.longOrNull }
                    ?: base.purchasePriceMinor,
                vendor = m.fields.string("vendor", base.vendor),
                purchasedOn = if ("purchased_on" in m.fields) {
                    m.fields.string("purchased_on", null)?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                } else {
                    base.purchasedOn
                },
                orderReference = m.fields.string("order_reference", base.orderReference),
                notes = m.fields.string("notes", base.notes),
            ),
        )
        return true
    }

    private fun JsonObject.string(key: String, default: String?): String? {
        val v = this[key] ?: return default
        return if (v is JsonNull) null else (v as? JsonPrimitive)?.contentOrNull
    }
}
