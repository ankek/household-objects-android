package dev.hho.android.domain

import androidx.room.withTransaction
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OptimisticApplier
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import javax.inject.Inject

internal sealed interface EditError {
    data class Invalid(val field: String, val message: String) : EditError

    data class NotFound(val kind: String, val id: String) : EditError

    data class StorageFailed(val cause: Throwable) : EditError
}

internal sealed interface EditResult<out T> {
    data class Success<T>(val value: T) : EditResult<T>

    data class Failure(val error: EditError) : EditResult<Nothing>
}

internal class EditRepository(
    private val db: HhoDatabase,
    private val outbox: OutboxRepository,
    private val ids: UuidV7Generator,
    private val syncNow: () -> Unit,
) {
    @Inject
    constructor(
        db: HhoDatabase,
        outbox: OutboxRepository,
        ids: UuidV7Generator,
        scheduler: SyncScheduler,
    ) : this(db, outbox, ids, scheduler::syncNow)

    suspend fun createItem(name: String, description: String? = null, locationId: String? = null): EditResult<String> =
        write { createItem(name, description, locationId) }

    suspend fun editItem(itemId: String, name: String? = null, description: String? = null): EditResult<Unit> =
        write { updateItem(itemId, name, description) }

    suspend fun moveItem(itemId: String, locationId: String?): EditResult<Unit> =
        write { updateItem(itemId, setLocation = true, locationId = locationId) }

    suspend fun adjustStock(itemId: String, delta: Long, reason: String? = null, note: String? = null): EditResult<String> =
        write { adjustStock(itemId, delta, reason, note) }

    suspend fun attachLabel(itemId: String, labelId: String): EditResult<String> =
        write {
            db.itemDao().getById(itemId) ?: return@write notFound("item", itemId)
            db.labelDao().getById(labelId) ?: return@write notFound("label", labelId)
            db.itemLabelDao().observeByItemId(itemId).first().firstOrNull { it.labelId == labelId }
                ?.let { return@write EditResult.Success(it.id) }
            val edgeId = ids.generate()
            val fields = JsonObject(mapOf("item_id" to JsonPrimitive(itemId), "label_id" to JsonPrimitive(labelId)))
            commit(LocalMutation("item_label", edgeId, UPSERT, 0, fields))
            EditResult.Success(edgeId)
        }

    suspend fun detachLabel(itemId: String, labelId: String): EditResult<Unit> =
        write {
            val edge = db.itemLabelDao().observeByItemId(itemId).first().firstOrNull { it.labelId == labelId }
                ?: return@write notFound("item_label", "$itemId/$labelId")
            val fields = JsonObject(mapOf("item_id" to JsonPrimitive(itemId), "label_id" to JsonPrimitive(labelId)))
            commit(LocalMutation("item_label", edge.id, "delete", 0, fields))
            EditResult.Success(Unit)
        }

    suspend fun addIdentification(itemId: String, kind: String, value: String): EditResult<String> =
        write { addIdentification(itemId, kind, value) }

    suspend fun stampPurchase(
        itemId: String,
        vendor: String? = null,
        purchasedOn: LocalDate? = null,
        purchasePriceMinor: Long? = null,
        orderReference: String? = null,
        notes: String? = null,
    ): EditResult<String> =
        write { stampPurchase(itemId, vendor, purchasedOn, purchasePriceMinor, orderReference, notes) }

    internal suspend fun <T> batch(body: suspend EditTx.() -> EditResult<T>): EditResult<T> {
        val tx = EditTx()
        val result = try {
            db.withTransaction {
                val r = tx.body()
                if (r is EditResult.Failure) throw BatchAborted(r)
                r
            }
        } catch (e: BatchAborted) {
            return e.failure
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EditResult.Failure(EditError.StorageFailed(e))
        }
        if (tx.wrote) triggerSync()
        return result
    }

    private class BatchAborted(val failure: EditResult.Failure) : RuntimeException(null, null, false, false)

    internal inner class EditTx {
        var wrote = false
            private set

        suspend fun createItem(name: String, description: String? = null, locationId: String? = null): EditResult<String> {
            if (name.isBlank()) return invalid("name", "name must not be blank")
            if (locationId != null && db.locationDao().getById(locationId) == null) return notFound("location", locationId)
            val id = ids.generate()
            val fields = buildMap<String, JsonElement> {
                put("name", JsonPrimitive(name.trim()))
                description?.let { put("description", JsonPrimitive(it)) }
                locationId?.let { put("location_id", JsonPrimitive(it)) }
            }
            commit(LocalMutation("item", id, UPSERT, 0, JsonObject(fields)))
            return EditResult.Success(id)
        }

        suspend fun updateItem(
            itemId: String,
            name: String? = null,
            description: String? = null,
            setLocation: Boolean = false,
            locationId: String? = null,
        ): EditResult<Unit> {
            if (name == null && description == null && !setLocation) return invalid("name", "nothing to change")
            if (name != null && name.isBlank()) return invalid("name", "name must not be blank")
            val item = db.itemDao().getById(itemId) ?: return notFound("item", itemId)
            if (setLocation && locationId != null && db.locationDao().getById(locationId) == null) {
                return notFound("location", locationId)
            }
            val fields = buildMap<String, JsonElement> {
                name?.let { put("name", JsonPrimitive(it.trim())) }
                description?.let { put("description", JsonPrimitive(it)) }
                if (setLocation) put("location_id", locationId?.let(::JsonPrimitive) ?: JsonNull)
            }
            commit(LocalMutation("item", itemId, UPSERT, item.version ?: 0, JsonObject(fields)))
            return EditResult.Success(Unit)
        }

        suspend fun addIdentification(itemId: String, kind: String, value: String): EditResult<String> {
            if (kind !in IDENTIFICATION_KINDS) return invalid("kind", "kind must be one of $IDENTIFICATION_KINDS")
            if (value.isBlank()) return invalid("value", "value must not be blank")
            db.itemDao().getById(itemId) ?: return notFound("item", itemId)
            val id = ids.generate()
            val fields = JsonObject(
                mapOf("item_id" to JsonPrimitive(itemId), "kind" to JsonPrimitive(kind), "value" to JsonPrimitive(value.trim())),
            )
            commit(LocalMutation("item_identification", id, UPSERT, 0, fields))
            return EditResult.Success(id)
        }

        suspend fun adjustStock(itemId: String, delta: Long, reason: String? = null, note: String? = null): EditResult<String> {
            if (delta == 0L) return invalid("delta", "delta must not be zero")
            db.itemDao().getById(itemId) ?: return notFound("item", itemId)
            val id = ids.generate()
            val fields = buildMap<String, JsonElement> {
                put("item_id", JsonPrimitive(itemId))
                put("delta", JsonPrimitive(delta))
                reason?.let { put("reason", JsonPrimitive(it)) }
                note?.let { put("note", JsonPrimitive(it)) }
            }
            commit(LocalMutation("stock_adjustment", id, UPSERT, 0, JsonObject(fields)))
            return EditResult.Success(id)
        }

        suspend fun stampPurchase(
            itemId: String,
            vendor: String? = null,
            purchasedOn: LocalDate? = null,
            purchasePriceMinor: Long? = null,
            orderReference: String? = null,
            notes: String? = null,
        ): EditResult<String> {
            if (vendor == null && purchasedOn == null && purchasePriceMinor == null && orderReference == null && notes == null) {
                return invalid("purchase", "nothing to record")
            }
            if (purchasePriceMinor != null && purchasePriceMinor < 0) return invalid("purchase_price_minor", "must not be negative")
            db.itemDao().getById(itemId) ?: return notFound("item", itemId)
            val existing = db.purchasedFromBlockDao().getByItemId(itemId)
            val fields = buildMap<String, JsonElement> {
                put("item_id", JsonPrimitive(itemId))
                vendor?.let { put("vendor", JsonPrimitive(it)) }
                purchasedOn?.let { put("purchased_on", JsonPrimitive(it.toString())) }
                purchasePriceMinor?.let { put("purchase_price_minor", JsonPrimitive(it)) }
                orderReference?.let { put("order_reference", JsonPrimitive(it)) }
                notes?.let { put("notes", JsonPrimitive(it)) }
            }
            val id = existing?.id ?: ids.generate()
            commit(LocalMutation("purchased_from_block", id, UPSERT, existing?.version ?: 0, JsonObject(fields)))
            return EditResult.Success(id)
        }

        suspend fun commit(m: LocalMutation) {
            OptimisticApplier.apply(db, m)
            outbox.enqueue(m)
            wrote = true
        }
    }

    private fun <T> invalid(field: String, message: String): EditResult<T> =
        EditResult.Failure(EditError.Invalid(field, message))

    private fun <T> notFound(kind: String, id: String): EditResult<T> =
        EditResult.Failure(EditError.NotFound(kind, id))

    private suspend fun <T> write(body: suspend EditTx.() -> EditResult<T>): EditResult<T> {
        val tx = EditTx()
        val result = try {
            db.withTransaction { tx.body() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EditResult.Failure(EditError.StorageFailed(e))
        }
        if (tx.wrote && result is EditResult.Success) triggerSync()
        return result
    }

    private fun triggerSync() {
        try {
            syncNow()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val UPSERT = "upsert"
        val IDENTIFICATION_KINDS = setOf("serial", "model", "asset_tag", "barcode", "other")
    }
}
