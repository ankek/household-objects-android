package dev.hho.android.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.items.ItemSearch
import dev.hho.android.data.outbox.PendingDeltas
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.ItemEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ItemListViewModel
    @Inject
    constructor(
        private val itemSearch: ItemSearch,
        private val db: HhoDatabase,
    ) : ViewModel() {

        private val query = MutableStateFlow("")

        val queryState: StateFlow<String> = query.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<ItemListUiState> =
            query
                .flatMapLatest { currentQuery ->
                    combine(itemSearch.results(currentQuery), PendingDeltas.observe(db)) { entities, deltas ->
                        entities.toUiState(currentQuery, deltas)
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = ItemListUiState.Loading,
                )

        fun onQueryChange(newQuery: String) {
            query.value = newQuery
        }
    }

private fun List<ItemEntity>.toUiState(currentQuery: String, deltas: Map<String, Long>): ItemListUiState = when {
    isNotEmpty() -> ItemListUiState.Data(map { it.toRow(deltas[it.id] ?: 0L) })
    currentQuery.isBlank() -> ItemListUiState.Empty
    else -> ItemListUiState.NoMatches(currentQuery)
}

private const val STOP_TIMEOUT_MILLIS = 5_000L

data class ItemRow(
    val id: String,
    val name: String,
    val quantity: Long?,
    val shortCode: String?,
)

private fun ItemEntity.toRow(pendingDelta: Long): ItemRow =
    ItemRow(
        id = id,
        name = name,
        quantity = if (quantity == null && pendingDelta == 0L) null else (quantity ?: 0L) + pendingDelta,
        shortCode = shortCode,
    )

sealed interface ItemListUiState {
    data object Loading : ItemListUiState

    data object Empty : ItemListUiState

    data class NoMatches(val query: String) : ItemListUiState

    data class Data(val items: List<ItemRow>) : ItemListUiState
}
