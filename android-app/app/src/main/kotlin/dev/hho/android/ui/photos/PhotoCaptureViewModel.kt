package dev.hho.android.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.photoqueue.PhotoCapture
import dev.hho.android.data.photoqueue.PhotoQueueRepository
import dev.hho.android.data.photoqueue.PhotoScheduler
import dev.hho.android.data.room.PhotoQueueEntryEntity
import dev.hho.android.data.room.PhotoState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class PhotoCaptureUiState(
    val submitting: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
internal class PhotoCaptureViewModel
    internal constructor(
        val capture: PhotoCapture,
        private val queue: PhotoQueueRepository,
        private val schedule: () -> Unit,
    ) : ViewModel() {
        @Inject
        constructor(
            capture: PhotoCapture,
            queue: PhotoQueueRepository,
            scheduler: PhotoScheduler,
        ) : this(capture, queue, scheduler::schedule)

        private val _state = MutableStateFlow(PhotoCaptureUiState())
        val state: StateFlow<PhotoCaptureUiState> = _state.asStateFlow()

        fun shoot(itemId: String) {
            var claimed = false
            _state.update {
                claimed = !it.submitting && !it.done
                if (claimed) it.copy(submitting = true, error = null) else it
            }
            if (!claimed) return
            viewModelScope.launch {
                var temp: java.io.File? = null
                try {
                    temp = capture.capture()
                    queue.enqueue(itemId, temp)
                    schedule()
                    _state.update { it.copy(submitting = false, done = true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update {
                        it.copy(submitting = false, error = "Couldn't save the photo. Try again.")
                    }
                } finally {
                    temp?.delete()
                }
            }
        }

        fun cameraFailed() {
            _state.update { it.copy(error = "The camera could not be started on this device.") }
        }

        override fun onCleared() {
            capture.unbind()
        }
    }

internal data class PhotoBadge(
    val queued: Int = 0,
    val uploading: Int = 0,
    val failed: Int = 0,
) {
    fun summary(): String? {
        val parts =
            listOfNotNull(
                queued.takeIf { it > 0 }?.let { "$it queued" },
                uploading.takeIf { it > 0 }?.let { "$it uploading" },
                failed.takeIf { it > 0 }?.let { "$it failed" },
            )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Photos: ")
    }

    companion object {
        fun from(entries: List<PhotoQueueEntryEntity>) =
            PhotoBadge(
                queued = entries.count { it.state == PhotoState.QUEUED || it.state == PhotoState.WAITING_PARENT },
                uploading = entries.count { it.state == PhotoState.UPLOADING },
                failed = entries.count { it.state == PhotoState.FAILED },
            )
    }
}

@HiltViewModel
internal class ItemPhotoBadgeViewModel
    @Inject
    constructor(
        private val queue: PhotoQueueRepository,
    ) : ViewModel() {
        private val itemId = MutableStateFlow<String?>(null)

        @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val badge: StateFlow<PhotoBadge> =
            itemId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else queue.observeEntriesForItem(id)
                }.map { PhotoBadge.from(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoBadge())

        fun load(id: String) {
            itemId.value = id
        }
    }
