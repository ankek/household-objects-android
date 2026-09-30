package dev.hho.android.data.outbox

import androidx.room.withTransaction
import dev.hho.android.data.apiclient.PushMutation
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

internal data class EnqueueResult(
    val mutationId: String,
    val seq: Long,
    val state: String,
    val coalesced: Boolean,
)

internal fun OutboxMutationEntity.toPushMutation(): PushMutation =
    PushMutation(
        mutationId = mutationId,
        entityType = entityType,
        entityId = entityId,
        baseVersion = baseVersion,
        fields = LocalMutation.from(this).fields,
        op = op,
    )

internal class OutboxRepository(
    private val db: HhoDatabase,
    private val ids: UuidV7Generator = UuidV7Generator(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = db.outboxDao()

    suspend fun enqueue(m: LocalMutation): EnqueueResult = db.withTransaction {
        var live = dao.getLiveForEntity(m.entityType, m.entityId)
        if (coalescible(m.entityType) && m.op == UPSERT) {
            val target = live.lastOrNull()?.takeIf {
                it.op == UPSERT && it.lastAttemptAt == null &&
                    (it.state == OutboxState.PENDING || it.state == OutboxState.HELD)
            }
            if (target != null) {
                val targetFields = try {
                    LocalMutation.from(target).fields
                } catch (e: Exception) {
                    quarantine(target.mutationId, unreadableMessage(e))
                    live = dao.getLiveForEntity(m.entityType, m.entityId)
                    null
                }
                if (targetFields != null) {
                    dao.updateFields(target.seq, JsonObject(targetFields + m.fields).toString())
                    return@withTransaction EnqueueResult(target.mutationId, target.seq, target.state, true)
                }
            }
        }
        val blocked = live.any { it.state == OutboxState.IN_FLIGHT || it.state == OutboxState.HELD || it.lastAttemptAt != null }
        val state = if (blocked) OutboxState.HELD else OutboxState.PENDING
        val mutationId = ids.generate()
        val seq = dao.insert(
            OutboxMutationEntity(
                mutationId = mutationId,
                entityType = m.entityType,
                entityId = m.entityId,
                op = m.op,
                baseVersion = m.baseVersion,
                fieldsJson = m.fields.toString(),
                state = state,
                createdAt = clock(),
            ),
        )
        EnqueueResult(mutationId, seq, state, false)
    }

    suspend fun nextBatch(limit: Int = BATCH_SIZE): List<OutboxMutationEntity> {
        val live = dao.getLive()
        val seen = live.filter { it.state == OutboxState.IN_FLIGHT }.mapTo(HashSet()) { it.entityType to it.entityId }
        return live.asSequence()
            .filter { it.state == OutboxState.PENDING }
            .filter { seen.add(it.entityType to it.entityId) }
            .take(limit)
            .toList()
    }

    suspend fun markInFlight(mutationIds: List<String>) {
        if (mutationIds.isEmpty()) return
        db.withTransaction { dao.markInFlight(mutationIds, clock()) }
    }

    suspend fun resetInFlightToPending(): Int = db.withTransaction { dao.resetInFlightToPending() }

    suspend fun ack(mutationId: String, version: Long) {
        db.withTransaction {
            val row = dao.getByMutationId(mutationId) ?: return@withTransaction
            dao.deleteBySeq(row.seq)
            releaseFollowers(row.entityType, row.entityId, version)
        }
    }

    suspend fun ackSkipped(mutationId: String) {
        db.withTransaction { dao.getByMutationId(mutationId)?.let { dao.deleteBySeq(it.seq) } }
    }

    suspend fun releaseHeld(mirrorVersion: suspend (entityType: String, entityId: String) -> Long?) {
        db.withTransaction {
            dao.getByState(OutboxState.HELD).map { it.entityType to it.entityId }.distinct().forEach { (t, id) ->
                mirrorVersion(t, id)?.let { releaseFollowers(t, id, it) }
            }
        }
    }

    suspend fun quarantine(mutationId: String, error: String) {
        db.withTransaction {
            val row = dao.getByMutationId(mutationId) ?: return@withTransaction
            dao.markFailed(mutationId, error)
            releaseFollowers(row.entityType, row.entityId, null)
        }
    }

    suspend fun recordAttempt(mutationId: String, error: String?) {
        db.withTransaction { dao.recordAttempt(mutationId, error, clock()) }
    }

    fun observePendingCount(): Flow<Int> = dao.observeCount(LIVE_STATES)

    private suspend fun releaseFollowers(type: String, id: String, base: Long?) {
        val allLive = dao.getLiveForEntity(type, id)
        if (allLive.any { it.state != OutboxState.HELD }) return
        val live = allLive.filter { row ->
            try {
                LocalMutation.from(row)
                true
            } catch (e: Exception) {
                dao.markFailed(row.mutationId, unreadableMessage(e))
                false
            }
        }
        val first = live.firstOrNull() ?: return
        if (coalescible(type) && first.op == UPSERT) {
            val rest = live.drop(1).filter { it.op == UPSERT }
            if (rest.isNotEmpty()) {
                val merged = rest.fold(LocalMutation.from(first).fields) { acc, r -> JsonObject(acc + LocalMutation.from(r).fields) }
                rest.forEach { dao.deleteBySeq(it.seq) }
                dao.updateFields(first.seq, merged.toString())
            }
        }
        dao.updateStateAndBase(first.seq, OutboxState.PENDING, base ?: first.baseVersion)
    }

    private fun coalescible(type: String) = type !in NEVER_COALESCE

    internal companion object {
        const val BATCH_SIZE = 50

        fun unreadableMessage(e: Exception): String = "Unreadable local edit: ${e::class.simpleName}"
        private const val UPSERT = "upsert"
        private val NEVER_COALESCE = setOf("stock_adjustment", "item_label")
        private val LIVE_STATES = listOf(OutboxState.PENDING, OutboxState.IN_FLIGHT, OutboxState.HELD)
    }
}
