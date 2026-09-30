package dev.hho.android.data.photoqueue

import dev.hho.android.data.sync.PhotoQueueStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class PhotoQueueStatusSource
    @Inject
    constructor(
        private val queue: PhotoQueueRepository,
    ) : PhotoQueueStatus {
        override fun observePendingCount(): Flow<Int> = queue.observePendingCount()

        override fun observeFailedCount(): Flow<Int> = queue.observeFailedCount()
    }
