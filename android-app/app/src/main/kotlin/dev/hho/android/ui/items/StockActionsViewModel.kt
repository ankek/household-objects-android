package dev.hho.android.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.domain.EditError
import dev.hho.android.domain.EditRepository
import dev.hho.android.domain.EditResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class StockActionsViewModel
    @Inject
    constructor(
        private val edits: EditRepository,
    ) : ViewModel() {

        private val _state = MutableStateFlow(AdjustStockState())
        val state: StateFlow<AdjustStockState> = _state.asStateFlow()

        fun submit(itemId: String, delta: Long?, reason: String, note: String) {
            if (_state.value.submitting) return
            if (delta == null) {
                _state.value = AdjustStockState(deltaError = "Enter a whole number, for example 3 or -2.")
                return
            }
            _state.value = AdjustStockState(submitting = true)
            viewModelScope.launch {
                val result = edits.adjustStock(
                    itemId = itemId,
                    delta = delta,
                    reason = reason.trim().ifEmpty { null },
                    note = note.trim().ifEmpty { null },
                )
                _state.update { toState(result) }
            }
        }

        fun reset() {
            if (!_state.value.submitting) _state.value = AdjustStockState()
        }

        private fun toState(result: EditResult<String>): AdjustStockState =
            when (result) {
                is EditResult.Success -> AdjustStockState(completed = true)
                is EditResult.Failure -> when (val error = result.error) {
                    is EditError.Invalid ->
                        if (error.field == "delta") {
                            AdjustStockState(deltaError = "The change must not be zero.")
                        } else {
                            AdjustStockState(generalError = error.message)
                        }
                    is EditError.NotFound ->
                        AdjustStockState(generalError = "This item is no longer on this device.")
                    is EditError.StorageFailed ->
                        AdjustStockState(generalError = "Couldn't save the change. Please try again.")
                }
            }
    }

data class AdjustStockState(
    val submitting: Boolean = false,
    val deltaError: String? = null,
    val generalError: String? = null,
    val completed: Boolean = false,
)
