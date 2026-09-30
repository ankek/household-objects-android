package dev.hho.android.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.LocationDao
import dev.hho.android.domain.EditError
import dev.hho.android.domain.EditRepository
import dev.hho.android.domain.EditResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal val ITEM_FORM_IDENTIFICATION_KINDS: List<String> = listOf("serial", "model", "asset_tag", "barcode", "other")

internal const val ITEM_FORM_DEFAULT_KIND = "barcode"

internal data class ItemFormState(
    val itemId: String? = null,
    val loading: Boolean = false,
    val notFound: Boolean = false,
    val name: String = "",
    val description: String = "",
    val locationId: String? = null,
    val identifierKind: String = ITEM_FORM_DEFAULT_KIND,
    val identifierValue: String = "",
    val nameError: String? = null,
    val identifierError: String? = null,
    val error: String? = null,
    val submitting: Boolean = false,
    val savedItemId: String? = null,
) {
    val isEdit: Boolean get() = itemId != null
}

@HiltViewModel
internal class ItemFormViewModel
    @Inject
    constructor(
        private val edits: EditRepository,
        private val itemDao: ItemDao,
        locationDao: LocationDao,
    ) : ViewModel() {

        private val _state = MutableStateFlow(ItemFormState())
        val state: StateFlow<ItemFormState> = _state.asStateFlow()

        val locations: StateFlow<List<LocationOption>> =
            locationDao.observeAll()
                .map(::locationOptions)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

        private var started = false
        private var original: OriginalItem? = null

        private data class OriginalItem(val name: String, val description: String?, val locationId: String?)

        fun start(itemId: String?, prefillKind: String? = null, prefillValue: String? = null) {
            if (started) return
            started = true
            if (itemId == null) {
                _state.value = ItemFormState(
                    identifierKind = prefillKind?.takeIf { it in ITEM_FORM_IDENTIFICATION_KINDS }
                        ?: if (prefillKind == null) ITEM_FORM_DEFAULT_KIND else "other",
                    identifierValue = prefillValue.orEmpty(),
                )
                return
            }
            _state.value = ItemFormState(itemId = itemId, loading = true)
            viewModelScope.launch {
                val item = itemDao.getById(itemId)
                if (item == null) {
                    _state.value = ItemFormState(itemId = itemId, notFound = true)
                } else {
                    original = OriginalItem(item.name, item.description, item.locationId)
                    _state.value = ItemFormState(
                        itemId = itemId,
                        name = item.name,
                        description = item.description.orEmpty(),
                        locationId = item.locationId,
                    )
                }
            }
        }

        fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, error = null) }

        fun onDescriptionChange(value: String) = _state.update { it.copy(description = value, error = null) }

        fun onLocationChange(locationId: String?) = _state.update { it.copy(locationId = locationId, error = null) }

        fun onIdentifierKindChange(kind: String) = _state.update { it.copy(identifierKind = kind, identifierError = null, error = null) }

        fun onIdentifierValueChange(value: String) = _state.update { it.copy(identifierValue = value, identifierError = null, error = null) }

        fun submit() {
            val s = _state.value
            if (s.submitting || s.loading || s.notFound || s.savedItemId != null) return
            val nameError = if (s.name.isBlank()) "Name is required." else null
            val identifier = s.identifierValue.trim()
            val identifierError = when {
                s.isEdit || identifier.isEmpty() -> null
                s.identifierKind !in ITEM_FORM_IDENTIFICATION_KINDS -> "Choose an identifier type."
                else -> null
            }
            if (nameError != null || identifierError != null) {
                _state.update { it.copy(nameError = nameError, identifierError = identifierError, error = null) }
                return
            }
            _state.update { it.copy(submitting = true, nameError = null, identifierError = null, error = null) }
            viewModelScope.launch {
                val result = if (s.itemId == null) create(s, identifier) else edit(s.itemId, s)
                _state.update {
                    when (result) {
                        is EditResult.Success -> it.copy(submitting = false, savedItemId = result.value)
                        is EditResult.Failure -> failed(it, result.error)
                    }
                }
            }
        }

        private suspend fun create(s: ItemFormState, identifier: String): EditResult<String> =
            edits.batch {
                val created = createItem(s.name, s.description.trim().ifEmpty { null }, s.locationId)
                if (created is EditResult.Success && identifier.isNotEmpty()) {
                    val ident = addIdentification(created.value, s.identifierKind, identifier)
                    if (ident is EditResult.Failure) return@batch ident
                }
                created
            }

        private suspend fun edit(itemId: String, s: ItemFormState): EditResult<String> {
            val o = original ?: return EditResult.Failure(EditError.NotFound("item", itemId))
            val name = s.name.trim().takeIf { it != o.name }
            val description = s.description.takeIf { it.trim() != o.description.orEmpty().trim() }?.trim()
            val moved = s.locationId != o.locationId
            if (name == null && description == null && !moved) return EditResult.Success(itemId)
            return when (
                val r = edits.batch {
                    updateItem(itemId, name, description, setLocation = moved, locationId = s.locationId)
                }
            ) {
                is EditResult.Success -> EditResult.Success(itemId)
                is EditResult.Failure -> r
            }
        }

        private fun failed(s: ItemFormState, error: EditError): ItemFormState =
            when {
                error is EditError.Invalid && error.field == "name" -> s.copy(submitting = false, nameError = "Name is required.")
                error is EditError.Invalid && (error.field == "kind" || error.field == "value") ->
                    s.copy(submitting = false, identifierError = error.message)
                error is EditError.Invalid -> s.copy(submitting = false, error = error.message)
                error is EditError.NotFound -> s.copy(
                    submitting = false,
                    error = when (error.kind) {
                        "location" -> "That location is no longer on this device."
                        else -> "This item is no longer on this device."
                    },
                )
                else -> s.copy(submitting = false, error = "Couldn't save the item. Please try again.")
            }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
