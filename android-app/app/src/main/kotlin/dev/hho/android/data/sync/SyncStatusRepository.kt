package dev.hho.android.data.sync

import dev.hho.android.data.room.OutboxDao
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.SyncRunStateDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

interface PhotoQueueStatus {
    fun observePendingCount(): Flow<Int>

    fun observeFailedCount(): Flow<Int>
}

data class SyncStatus(
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val failedOutboxCount: Int = 0,
    val failedPhotoCount: Int = 0,
    val photoQueueDepth: Int = 0,
    val lastSyncedAt: Long? = null,
    val lastPushAt: Long? = null,
    val lastError: String? = null,
) {
    val hasProblem: Boolean get() = failedCount > 0 || lastError != null
}

@Singleton
class SyncStatusRepository
    @Inject
    constructor(
        private val outbox: OutboxDao,
        private val photos: PhotoQueueStatus,
        private val runState: SyncRunStateDao,
    ) {
        fun observe(): Flow<SyncStatus> =
            combine(
                outbox.observeCount(LIVE),
                outbox.observeCount(FAILED),
                photos.observePendingCount(),
                photos.observeFailedCount(),
                runState.observe(),
            ) { pending, failedOutbox, photoDepth, failedPhotos, run ->
                SyncStatus(
                    pendingCount = pending,
                    failedCount = failedOutbox + failedPhotos,
                    failedOutboxCount = failedOutbox,
                    failedPhotoCount = failedPhotos,
                    photoQueueDepth = photoDepth,
                    lastSyncedAt = run?.takeIf { it.lastError == null }?.lastRunAt,
                    lastPushAt = run?.lastPushAt,
                    lastError = run?.lastError,
                )
            }.distinctUntilChanged()

        private companion object {
            val LIVE = listOf(OutboxState.PENDING, OutboxState.IN_FLIGHT, OutboxState.HELD)
            val FAILED = listOf(OutboxState.FAILED)
        }
    }
