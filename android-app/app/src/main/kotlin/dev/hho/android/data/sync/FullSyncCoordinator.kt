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

private const val FULL_PULL_SINCE = 0L

internal const val DEFAULT_FULL_PULL_PAGE_SIZE = 500

@Singleton
class FullSyncCoordinator
    @Inject
    constructor(
        private val apiClient: HhoApiClient,
        private val database: HhoDatabase,
    ) {
        suspend fun pullFull(
            deviceId: String,
            pageSize: Int = DEFAULT_FULL_PULL_PAGE_SIZE,
        ): Result<Unit> {
            val pages = mutableListOf<SyncPullOutcome.Page>()
            var since = FULL_PULL_SINCE

            while (true) {
                val outcome =
                    apiClient.syncPull(deviceId = deviceId, since = since, limit = pageSize)
                        .getOrElse { return Result.failure(it) }
                val page =
                    when (outcome) {
                        is SyncPullOutcome.Page -> outcome
                        is SyncPullOutcome.CursorTooOld ->
                            return Result.failure(UnexpectedCursorTooOldDuringFullPullException())
                    }
                pages += page
                if (!page.hasMore) break
                since = page.nextWatermark
            }

            return try {
                database.withTransaction {
                    SyncMirrorApplier.clearMirror(database)
                    for (page in pages) {
                        SyncMirrorApplier.applyPage(database, page)
                    }
                    OutboxOverlay.reapplyPending(database)
                    val finalWatermark = pages.last().nextWatermark
                    database.syncStateDao().upsert(
                        SyncStateEntity(watermark = finalWatermark, lastSyncedAt = System.currentTimeMillis()),
                    )
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
    }

class UnexpectedCursorTooOldDuringFullPullException :
    IllegalStateException("/sync/pull answered cursor_too_old during a full pull (since: 0) — server/client drift")
