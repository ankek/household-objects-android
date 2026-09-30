package dev.hho.android.ui.syncstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.room.ConflictRecordDao
import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.OutboxDao
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.PhotoQueueDao
import dev.hho.android.data.sync.SyncScheduler
import dev.hho.android.data.sync.SyncStatus
import dev.hho.android.data.sync.SyncStatusRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

internal enum class SyncHeadline { UP_TO_DATE, PENDING, PROBLEM }

internal data class FailedMutationUi(
    val mutationId: String,
    val label: String,
    val error: String?,
    val at: Long,
)

internal data class FailedPhotoUi(
    val id: String,
    val itemName: String,
    val error: String?,
    val attempts: Int,
)

internal data class SyncStatusUi(
    val status: SyncStatus = SyncStatus(),
    val failedMutations: List<FailedMutationUi> = emptyList(),
    val failedPhotos: List<FailedPhotoUi> = emptyList(),
) {
    val headline: SyncHeadline
        get() = when {
            status.hasProblem -> SyncHeadline.PROBLEM
            status.pendingCount > 0 -> SyncHeadline.PENDING
            else -> SyncHeadline.UP_TO_DATE
        }
}

@HiltViewModel
internal class SyncStatusViewModel
    internal constructor(
        repository: SyncStatusRepository,
        outbox: OutboxDao,
        photos: PhotoQueueDao,
        private val itemDao: ItemDao,
        conflicts: ConflictRecordDao,
        private val syncNow: () -> Unit,
    ) : ViewModel() {
        @Inject
        constructor(
            repository: SyncStatusRepository,
            outbox: OutboxDao,
            photos: PhotoQueueDao,
            itemDao: ItemDao,
            conflicts: ConflictRecordDao,
            scheduler: SyncScheduler,
        ) : this(repository, outbox, photos, itemDao, conflicts, scheduler::syncNow)

        val conflictCount: StateFlow<Int> =
            conflicts.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

        val state: StateFlow<SyncStatusUi> =
            combine(repository.observe(), outbox.observeFailed(), photos.observeFailed()) { status, mutations, failedPhotos ->
                SyncStatusUi(
                    status = status,
                    failedMutations = mutations.map { it.toUi() },
                    failedPhotos = failedPhotos.map { FailedPhotoUi(it.id, itemName(it.itemId), it.lastError, it.attemptCount) },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SyncStatusUi())

        private val _syncing = MutableStateFlow(false)

        val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

        fun onSyncNow() {
            if (!_syncing.compareAndSet(expect = false, update = true)) return
            viewModelScope.launch {
                try {
                    syncNow()
                    delay(CLICK_GUARD_MILLIS)
                } finally {
                    _syncing.value = false
                }
            }
        }

        private suspend fun OutboxMutationEntity.toUi(): FailedMutationUi {
            val itemId = if (entityType == "item") entityId else fieldItemId(fieldsJson)
            val name = itemId?.let { itemDao.getById(it)?.name }
            val target = name ?: itemId ?: entityId
            return FailedMutationUi(mutationId, "$entityType: $target", lastError, lastAttemptAt ?: createdAt)
        }

        private suspend fun itemName(itemId: String): String = itemDao.getById(itemId)?.name ?: itemId

        private fun fieldItemId(json: String): String? =
            runCatching {
                (Json.parseToJsonElement(json).jsonObject["item_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            }.getOrNull()

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val CLICK_GUARD_MILLIS = 1_500L
        }
    }
