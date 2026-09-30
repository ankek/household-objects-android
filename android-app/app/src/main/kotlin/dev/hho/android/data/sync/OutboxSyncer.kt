package dev.hho.android.data.sync

import androidx.room.withTransaction
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.apiclient.PushMutation
import dev.hho.android.data.apiclient.SyncPushOutcome
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.outbox.toPushMutation
import dev.hho.android.data.room.ConflictOrigin
import dev.hho.android.data.room.ConflictRecordEntity
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.OutboxMutationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal enum class BlockedReason {
    AUTH,

    UPGRADE_REQUIRED,
}

internal sealed interface PushPhaseOutcome {
    data object Drained : PushPhaseOutcome

    data object NeedsReconcile : PushPhaseOutcome

    data class Failed(val cause: ApiError, val reconcilePending: Boolean = false) : PushPhaseOutcome

    data class Blocked(
        val reason: BlockedReason,
        val minimumVersion: String? = null,
        val reconcilePending: Boolean = false,
    ) : PushPhaseOutcome
}

internal class OutboxSyncer(
    private val db: HhoDatabase,
    private val api: HhoApiClient,
    private val outbox: OutboxRepository = OutboxRepository(db),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun push(deviceId: String): PushPhaseOutcome {
        outbox.resetInFlightToPending()
        val run = Run(deviceId)
        while (true) {
            val batch = outbox.nextBatch()
            if (batch.isEmpty()) {
                return if (run.reconcile) PushPhaseOutcome.NeedsReconcile else PushPhaseOutcome.Drained
            }
            run.send(batch)?.let { return it }
        }
    }

    private inner class Run(private val deviceId: String) {
        var reconcile = false
        private val quarantined = HashSet<String>()

        suspend fun send(allRows: List<OutboxMutationEntity>): PushPhaseOutcome? {
            val decoded = ArrayList<Pair<OutboxMutationEntity, PushMutation>>(allRows.size)
            for (row in allRows) {
                try {
                    decoded += row to row.toPushMutation()
                } catch (e: Exception) {
                    outbox.quarantine(row.mutationId, "Unreadable local edit: ${e::class.simpleName}")
                }
            }
            if (decoded.isEmpty()) return null
            val rows = decoded.map { it.first }
            outbox.markInFlight(rows.map { it.mutationId })
            val outcome = api.syncPush(deviceId, decoded.map { it.second })
                .getOrElse { return failure(it, rows) }
            return when (outcome) {
                is SyncPushOutcome.Success -> handleSuccess(rows, outcome)
                is SyncPushOutcome.StructuralRejection -> handleRejection(rows, outcome)
            }
        }

        private suspend fun handleRejection(rows: List<OutboxMutationEntity>, r: SyncPushOutcome.StructuralRejection): PushPhaseOutcome? {
            val offender = r.mutationId?.let { id -> rows.firstOrNull { it.mutationId == id } }
                ?: rows.singleOrNull()
            if (offender == null) {
                outbox.resetInFlightToPending()
                for (row in rows) {
                    send(listOf(row))?.let { return it }
                }
                return null
            }
            if (!quarantined.add(offender.mutationId)) {
                return failure(ApiError.UnexpectedPayload("row ${offender.mutationId} rejected again after quarantine"), rows)
            }
            outbox.quarantine(offender.mutationId, r.detail)
            outbox.resetInFlightToPending()
            return null
        }

        private suspend fun handleSuccess(rows: List<OutboxMutationEntity>, out: SyncPushOutcome.Success): PushPhaseOutcome? {
            var answered = 0
            val unanswered = ArrayList<OutboxMutationEntity>()
            for (row in rows) {
                val applied = out.applied.firstOrNull { it.mutationId == row.mutationId }
                val skipped = out.skipped.any { it.mutationId == row.mutationId }
                val conflicts = out.conflicts.filter { it.mutationId == row.mutationId }
                if (applied == null && !skipped && conflicts.isEmpty()) {
                    unanswered += row
                    continue
                }
                answered++
                db.withTransaction {
                    if (conflicts.isNotEmpty()) recordConflicts(row, conflicts.map { it.fieldName })
                    if (applied != null) {
                        stampMirrorVersion(row, applied.version)
                        outbox.ack(row.mutationId, applied.version)
                    } else {
                        outbox.ackSkipped(row.mutationId)
                    }
                }
                if (conflicts.isNotEmpty()) reconcile = true
            }
            if (unanswered.isEmpty()) return null
            unanswered.forEach { outbox.recordAttempt(it.mutationId, "no result in push response") }
            outbox.resetInFlightToPending()
            return if (answered == 0) {
                PushPhaseOutcome.Failed(ApiError.UnexpectedPayload("push response answered none of ${rows.size} mutations"), reconcile)
            } else {
                null
            }
        }

        private suspend fun failure(error: Throwable, rows: List<OutboxMutationEntity>): PushPhaseOutcome {
            val e = error as? ApiError ?: ApiError.Unknown(error)
            rows.forEach { outbox.recordAttempt(it.mutationId, e.message) }
            outbox.resetInFlightToPending()
            return when (e) {
                is ApiError.Unauthorized -> PushPhaseOutcome.Blocked(BlockedReason.AUTH, reconcilePending = reconcile)
                is ApiError.UpgradeRequired ->
                    PushPhaseOutcome.Blocked(BlockedReason.UPGRADE_REQUIRED, e.minimumVersion, reconcile)
                else -> PushPhaseOutcome.Failed(e, reconcile)
            }
        }
    }

    private suspend fun recordConflicts(row: OutboxMutationEntity, fieldNames: List<String>) {
        val fields: JsonObject = Json.parseToJsonElement(row.fieldsJson).jsonObject
        val now = clock()
        for (field in fieldNames) {
            val losing = if (field == ENTITY_SENTINEL) row.fieldsJson else fields[field]?.toString()
            db.conflictRecordDao().insertIgnore(
                ConflictRecordEntity(
                    mutationId = row.mutationId,
                    entityType = row.entityType,
                    entityId = row.entityId,
                    fieldName = field,
                    losingValueJson = losing,
                    serverValueJson = null,
                    detectedAt = now,
                    origin = ConflictOrigin.LOCAL_PUSH,
                ),
            )
        }
    }

    private suspend fun stampMirrorVersion(row: OutboxMutationEntity, version: Long) {
        when (row.entityType) {
            "item" -> db.itemDao().getById(row.entityId)?.let { db.itemDao().upsert(it.copy(version = version)) }
            "purchased_from_block" -> {
                val itemId = (Json.parseToJsonElement(row.fieldsJson).jsonObject["item_id"] as? JsonPrimitive)?.contentOrNull
                val dao = db.purchasedFromBlockDao()
                itemId?.let { dao.getByItemId(it) }?.takeIf { it.id == row.entityId }
                    ?.let { dao.upsert(it.copy(version = version)) }
            }
        }
    }

    private companion object {
        const val ENTITY_SENTINEL = "_entity"
    }
}
