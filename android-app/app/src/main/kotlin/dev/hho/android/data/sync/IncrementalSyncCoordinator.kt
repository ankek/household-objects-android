package dev.hho.android.data.sync

import androidx.room.withTransaction
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.apiclient.SyncPullOutcome
import dev.hho.android.data.outbox.OutboxOverlay
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.SyncStateEntity
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

internal const val DEFAULT_INCREMENTAL_PULL_PAGE_SIZE = 500

sealed interface IncrementalPullOutcome {
    data object Applied : IncrementalPullOutcome

    data object CursorTooOld : IncrementalPullOutcome
}

@Singleton
class IncrementalSyncCoordinator
    @Inject
    constructor(
        private val apiClient: HhoApiClient,
        private val database: HhoDatabase,
    ) {
        suspend fun pullIncremental(
            deviceId: String,
            pageSize: Int = DEFAULT_INCREMENTAL_PULL_PAGE_SIZE,
        ): Result<IncrementalPullOutcome> {
            while (true) {
                val since = database.syncStateDao().get()?.watermark ?: 0L

                val outcome =
                    apiClient.syncPull(deviceId = deviceId, since = since, limit = pageSize)
                        .getOrElse { return Result.failure(it) }
                val page =
                    when (outcome) {
                        is SyncPullOutcome.Page -> outcome
                        is SyncPullOutcome.CursorTooOld ->
                            return Result.success(IncrementalPullOutcome.CursorTooOld)
                    }

                try {
                    database.withTransaction {
                        SyncMirrorApplier.applyPage(database, page)
                        OutboxOverlay.reapplyPending(database)
                        database.syncStateDao().upsert(
                            SyncStateEntity(watermark = page.nextWatermark, lastSyncedAt = System.currentTimeMillis()),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    return Result.failure(e)
                }

                if (!page.hasMore) return Result.success(IncrementalPullOutcome.Applied)
            }
        }
    }
