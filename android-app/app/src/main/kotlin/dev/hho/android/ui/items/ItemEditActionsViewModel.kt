package dev.hho.android.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.ItemLabelDao
import dev.hho.android.data.room.LabelDao
import dev.hho.android.data.room.LabelEntity
import dev.hho.android.data.room.LocationDao
import dev.hho.android.data.room.LocationEntity
import dev.hho.android.domain.EditError
import dev.hho.android.domain.EditRepository
import dev.hho.android.domain.EditResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class LocationOption(val id: String, val path: String)

internal data class LabelOption(val id: String, val name: String, val color: String)

internal data class ItemEditOptions(
    val locations: List<LocationOption> = emptyList(),
    val currentLocationId: String? = null,
    val attachedLabels: List<LabelOption> = emptyList(),
    val availableLabels: List<LabelOption> = emptyList(),
)

internal data class ItemEditState(
    val submitting: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
internal class ItemEditActionsViewModel
    @Inject
    constructor(
        private val edits: EditRepository,
        private val itemDao: ItemDao,
        private val itemLabelDao: ItemLabelDao,
        private val labelDao: LabelDao,
        private val locationDao: LocationDao,
    ) : ViewModel() {

        private val itemIdFlow = MutableStateFlow<String?>(null)
        private val _state = MutableStateFlow(ItemEditState())
        val state: StateFlow<ItemEditState> = _state.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val options: StateFlow<ItemEditOptions> =
            itemIdFlow
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest(::observeOptions)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ItemEditOptions())

        fun load(itemId: String) {
            itemIdFlow.value = itemId
        }

        fun move(itemId: String, locationId: String?) =
            run(closeOnSuccess = true) { edits.moveItem(itemId, locationId) }

        fun attach(itemId: String, labelId: String) =
            run { edits.attachLabel(itemId, labelId) }

        fun detach(itemId: String, labelId: String) =
            run { edits.detachLabel(itemId, labelId) }

        fun reset() {
            if (!_state.value.submitting) _state.value = ItemEditState()
        }

        private fun run(closeOnSuccess: Boolean = false, action: suspend () -> EditResult<*>) {
            if (_state.value.submitting) return
            _state.value = ItemEditState(submitting = true)
            viewModelScope.launch {
                val result = action()
                _state.update {
                    when (result) {
                        is EditResult.Success -> ItemEditState(completed = closeOnSuccess)
                        is EditResult.Failure -> ItemEditState(error = message(result.error))
                    }
                }
            }
        }

        private fun message(error: EditError): String =
            when (error) {
                is EditError.NotFound -> when (error.kind) {
                    "location" -> "That location is no longer on this device."
                    "label", "item_label" -> "That label is no longer on this device."
                    else -> "This item is no longer on this device."
                }
                is EditError.Invalid -> error.message
                is EditError.StorageFailed -> "Couldn't save the change. Please try again."
            }

        private fun observeOptions(itemId: String): Flow<ItemEditOptions> =
            combine(
                itemDao.observeById(itemId),
                itemLabelDao.observeByItemId(itemId),
                labelDao.observeAll(),
                locationDao.observeAll(),
            ) { item, edges, labels, locations ->
                val attachedIds = edges.mapTo(HashSet()) { it.labelId }
                val (attached, available) = labels.map { it.toOption() }.partition { it.id in attachedIds }
                ItemEditOptions(
                    locations = locationOptions(locations),
                    currentLocationId = item?.locationId,
                    attachedLabels = attached,
                    availableLabels = available,
                )
            }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun LabelEntity.toOption() = LabelOption(id, name, color)

internal fun locationOptions(locations: List<LocationEntity>): List<LocationOption> {
    val byId = locations.associateBy { it.id }
    return locations
        .map { location ->
            val names = ArrayDeque<String>()
            val seen = HashSet<String>()
            var cursor: LocationEntity? = location
            while (cursor != null && seen.add(cursor.id)) {
                names.addFirst(cursor.name)
                cursor = cursor.parentId?.let(byId::get)
            }
            LocationOption(location.id, names.joinToString(" / "))
        }
        .sortedBy { it.path.lowercase() }
}
