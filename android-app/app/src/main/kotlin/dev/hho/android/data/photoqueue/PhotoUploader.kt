package dev.hho.android.data.photoqueue

import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.AttachmentCategory
import dev.hho.android.data.apiclient.AttachmentInfo
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.room.OutboxDao
import dev.hho.android.data.room.PhotoQueueDao
import dev.hho.android.data.room.PhotoQueueEntryEntity
import dev.hho.android.data.room.PhotoState
import javax.inject.Inject

enum class PhotoDrainOutcome {
    DRAINED,

    RETRY_LATER,

    BLOCKED,
}

open class PhotoUploader
    @Inject
    constructor(
        private val repository: PhotoQueueRepository,
        private val photoDao: PhotoQueueDao,
        private val outboxDao: OutboxDao,
        private val api: HhoApiClient,
    ) {
        open suspend fun drain(clock: () -> Long = System::currentTimeMillis): PhotoDrainOutcome {
            repository.releaseWaitingParent()

            for (orphan in photoDao.getByStates(listOf(PhotoState.UPLOADING))) {
                when (val r = reconcile(orphan)) {
                    ReconcileResult.Hit -> repository.markUploaded(orphan.id)
                    ReconcileResult.Miss -> Unit
                    is ReconcileResult.Failed -> {
                        if (!r.error.isBlocking()) return PhotoDrainOutcome.RETRY_LATER
                        repository.resetUploadingToQueued()
                        return PhotoDrainOutcome.BLOCKED
                    }
                }
            }
            repository.resetUploadingToQueued()

            var transientFailure = false
            while (true) {
                val entry = repository.nextDue(clock()) ?: break
                if (outboxDao.hasPendingCreate(entry.itemId)) {
                    repository.markWaitingParent(entry.id)
                    continue
                }
                if (!repository.markUploading(entry.id)) continue
                when (process(entry, clock)) {
                    Step.Done -> Unit
                    Step.Transient -> transientFailure = true
                    Step.Blocked -> {
                        repository.resetUploadingToQueued()
                        return PhotoDrainOutcome.BLOCKED
                    }
                }
            }
            val waiting = photoDao.getByStates(listOf(PhotoState.QUEUED)).isNotEmpty()
            return if (transientFailure || waiting) PhotoDrainOutcome.RETRY_LATER else PhotoDrainOutcome.DRAINED
        }

        private suspend fun process(
            entry: PhotoQueueEntryEntity,
            clock: () -> Long,
        ): Step {
            if (entry.attemptCount > 0) {
                when (val r = reconcile(entry)) {
                    ReconcileResult.Hit -> {
                        repository.markUploaded(entry.id)
                        return Step.Done
                    }
                    ReconcileResult.Miss -> Unit
                    is ReconcileResult.Failed -> return fail(entry, r.error, clock)
                }
            }
            val category = AttachmentCategory.entries.firstOrNull { it.wire == entry.category } ?: AttachmentCategory.IMAGE
            val result = api.uploadAttachment(entry.itemId, category, repository.fileFor(entry))
            val error = result.exceptionOrNull() ?: run {
                repository.markUploaded(entry.id)
                return Step.Done
            }
            return fail(entry, error, clock)
        }

        private suspend fun fail(
            entry: PhotoQueueEntryEntity,
            error: Throwable,
            clock: () -> Long,
        ): Step {
            if (error.isBlocking()) return Step.Blocked
            val retryable =
                when (error) {
                    is ApiError.PayloadTooLarge -> false
                    is ApiError.Validation, is ApiError.Conflict -> false
                    else -> true
                }
            repository.recordFailure(entry.id, describe(error), retryable, clock())
            return if (retryable) Step.Transient else Step.Done
        }

        private suspend fun reconcile(entry: PhotoQueueEntryEntity): ReconcileResult {
            val list = api.listAttachments(entry.itemId)
            val error = list.exceptionOrNull()
            if (error != null) return ReconcileResult.Failed(error)
            val hit = list.getOrThrow().any { it.matches(entry) }
            return if (hit) ReconcileResult.Hit else ReconcileResult.Miss
        }

        private fun AttachmentInfo.matches(entry: PhotoQueueEntryEntity): Boolean =
            sha256.equals(entry.sha256, ignoreCase = true) && sizeBytes == entry.sizeBytes

        private fun Throwable.isBlocking(): Boolean =
            this is ApiError.Unauthorized || this is ApiError.UpgradeRequired || this is ApiError.NoInstanceConfigured

        private fun describe(error: Throwable): String = (error as? ApiError)?.message ?: error.javaClass.simpleName

        private sealed interface ReconcileResult {
            data object Hit : ReconcileResult

            data object Miss : ReconcileResult

            data class Failed(val error: Throwable) : ReconcileResult
        }

        private enum class Step { Done, Transient, Blocked }
    }
