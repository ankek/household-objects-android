package dev.hho.android.data.sync

import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.apiclient.generated.models.SyncConflictLogEntry
import dev.hho.android.data.room.ConflictOrigin
import dev.hho.android.data.room.ConflictRecordDao
import dev.hho.android.data.room.ConflictRecordEntity
import javax.inject.Inject

data class ConflictLogSyncResult(
    val fetched: Int,
    val inserted: Int,
    val merged: Int,
    val complete: Boolean,
)

class ConflictLogSync
    @Inject
    constructor(
        private val api: HhoApiClient,
        private val conflicts: ConflictRecordDao,
        private val recorder: SyncRunRecorder,
    ) {
        suspend fun sync(
            pageSize: Int = PAGE_SIZE,
            maxPages: Int = MAX_PAGES,
        ): Result<ConflictLogSyncResult> {
            val watermark = recorder.conflictWatermark()
            var after: String? = null
            var newest: Long? = null
            var fetched = 0
            var inserted = 0
            var merged = 0
            var complete = false
            var pages = 0
            while (pages < maxPages) {
                pages++
                val page = api.getConflicts(after = after, limit = pageSize).getOrElse { return Result.failure(it) }
                var reachedKnown = false
                for (entry in page.conflicts) {
                    if (watermark != null && entry.detectedAt < watermark) {
                        reachedKnown = true
                        break
                    }
                    newest = maxOf(newest ?: entry.detectedAt, entry.detectedAt)
                    fetched++
                    if (upsert(entry)) inserted++ else merged++
                }
                val next = page.nextCursor
                if (reachedKnown || next == null || next == after) {
                    complete = true
                    break
                }
                after = next
            }
            if (complete) advance(maxOf(newest ?: Long.MIN_VALUE, watermark ?: Long.MIN_VALUE))
            return Result.success(ConflictLogSyncResult(fetched, inserted, merged, complete))
        }

        private suspend fun upsert(e: SyncConflictLogEntry): Boolean {
            val key = e.mutationId ?: (NULL_MUTATION_PREFIX + e.id)
            val changed = conflicts.mergeServerLog(key, e.entityId, e.fieldName, e.losingClientValue, e.serverValue, e.detectedAt)
            if (changed > 0) return false
            conflicts.insertIgnore(
                ConflictRecordEntity(
                    mutationId = key,
                    entityType = e.entityType,
                    entityId = e.entityId,
                    fieldName = e.fieldName,
                    losingValueJson = e.losingClientValue,
                    serverValueJson = e.serverValue,
                    detectedAt = e.detectedAt,
                    origin = ConflictOrigin.SERVER_LOG,
                ),
            )
            return true
        }

        private suspend fun advance(watermark: Long) {
            if (watermark == Long.MIN_VALUE) return
            recorder.advanceConflictWatermark(watermark)
        }

        companion object {
            const val PAGE_SIZE: Int = 100
            const val MAX_PAGES: Int = 25

            const val NULL_MUTATION_PREFIX: String = "server:"
        }
    }
