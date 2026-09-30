package dev.hho.android.data.outbox

import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object OutboxOverlay {

    private val LIVE = setOf(OutboxState.PENDING, OutboxState.IN_FLIGHT, OutboxState.HELD)

    suspend fun reapplyPending(db: HhoDatabase): Int {
        var written = 0
        for (row in liveRows(db)) {
            val mutation = try {
                LocalMutation.from(row)
            } catch (e: Exception) {
                OutboxRepository(db).quarantine(row.mutationId, OutboxRepository.unreadableMessage(e))
                continue
            }
            val applied = try {
                OptimisticApplier.apply(db, mutation, pendingSeq = row.seq)
            } catch (_: UnsupportedMutationException) {
                false
            }
            if (applied) written++
        }
        return written
    }

    suspend fun derivedQuantity(db: HhoDatabase, itemId: String): Long? {
        val item = db.itemDao().getById(itemId) ?: return null
        return (item.quantity ?: 0) + pendingDeltaFor(db, itemId, beforeSeq = null)
    }

    fun observeDerivedQuantity(db: HhoDatabase, itemId: String): Flow<Long?> =
        db.invalidationTracker
            .createFlow("item", "outbox_mutation", emitInitialState = true)
            .map { derivedQuantity(db, itemId) }

    suspend fun pendingDeltaFor(db: HhoDatabase, itemId: String, beforeSeq: Long?): Long =
        liveRows(db)
            .filter { it.entityType == "stock_adjustment" && (beforeSeq == null || it.seq < beforeSeq) }
            .mapNotNull { decodeOrNull(it)?.fields }
            .filter { (it["item_id"] as? JsonPrimitive)?.content == itemId }
            .sumOf { (it["delta"] as? JsonPrimitive)?.longOrNull ?: 0L }

    private fun decodeOrNull(row: OutboxMutationEntity): LocalMutation? =
        try {
            LocalMutation.from(row)
        } catch (_: Exception) {
            null
        }

    private suspend fun liveRows(db: HhoDatabase): List<OutboxMutationEntity> =
        db.outboxDao().getAllOrdered().filter { it.state in LIVE }
}
