package dev.hho.android.data.sync

import dev.hho.android.data.room.SyncRunStateDao
import dev.hho.android.data.room.SyncRunStateEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRunRecorder
    internal constructor(
        private val dao: SyncRunStateDao,
        private val clock: () -> Long,
    ) {
        @Inject
        constructor(dao: SyncRunStateDao) : this(dao, System::currentTimeMillis)

        suspend fun reconcileOwed(): Boolean = dao.get()?.reconcilePending == true

        suspend fun setReconcilePending(owed: Boolean) {
            ensureRow()
            dao.setReconcilePending(owed)
        }

        suspend fun recordPushReachedServer() {
            ensureRow()
            dao.setLastPushAt(clock())
        }

        suspend fun recordSuccess() {
            ensureRow()
            dao.setRunSucceeded(clock())
        }

        suspend fun recordFailure(
            message: String?,
            reconcileOwed: Boolean = false,
        ) {
            ensureRow()
            dao.setRunFailed(clock(), message ?: "sync failed", reconcileOwed)
        }

        suspend fun conflictWatermark(): Long? = dao.get()?.conflictCursor?.toLongOrNull()

        suspend fun advanceConflictWatermark(watermark: Long) {
            ensureRow()
            dao.setConflictCursor(watermark.toString())
        }

        private suspend fun ensureRow() = dao.insertIfAbsent(SyncRunStateEntity())
    }
