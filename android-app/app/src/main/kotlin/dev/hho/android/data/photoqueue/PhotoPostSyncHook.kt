package dev.hho.android.data.photoqueue

import dev.hho.android.data.sync.PostSyncHook
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PhotoPostSyncHook
    @Inject
    constructor(
        private val queue: PhotoQueueRepository,
        private val scheduler: PhotoScheduler,
    ) : PostSyncHook {
        override suspend fun onSyncSucceeded() {
            queue.releaseWaitingParent()
            scheduler.schedule()
        }
    }
