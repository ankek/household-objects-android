package dev.hho.android.data.outbox

import dev.hho.android.data.room.HhoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object PendingDeltas {
    fun observe(db: HhoDatabase): Flow<Map<String, Long>> =
        db.invalidationTracker
            .createFlow("outbox_mutation", emitInitialState = true)
            .map { deltasByItem(db) }

    suspend fun deltasByItem(db: HhoDatabase): Map<String, Long> {
        val totals = HashMap<String, Long>()
        for (row in db.outboxDao().getLive()) {
            if (row.entityType != "stock_adjustment") continue
            val fields = LocalMutation.from(row).fields
            val itemId = (fields["item_id"] as? JsonPrimitive)?.content ?: continue
            val delta = (fields["delta"] as? JsonPrimitive)?.longOrNull ?: 0L
            totals[itemId] = (totals[itemId] ?: 0L) + delta
        }
        return totals
    }
}
