package dev.hho.android.ui.items

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
internal fun ItemFormScreen(
    itemId: String?,
    onSaved: (itemId: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    prefillIdentifierKind: String? = null,
    prefillIdentifierValue: String? = null,
    viewModel: ItemFormViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.start(itemId, prefillIdentifierKind, prefillIdentifierValue) }
    val state by viewModel.state.collectAsState()
    val locations by viewModel.locations.collectAsState()
    LaunchedEffect(state.savedItemId) { state.savedItemId?.let(onSaved) }
    ItemFormContent(
        state = state,
        locations = locations,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onLocationChange = viewModel::onLocationChange,
        onKindChange = viewModel::onIdentifierKindChange,
        onIdentifierChange = viewModel::onIdentifierValueChange,
        onSubmit = viewModel::submit,
        onCancel = onCancel,
        modifier = modifier,
    )
}

@Composable
private fun ItemFormContent(
    state: ItemFormState,
    locations: List<LocationOption>,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLocationChange: (String?) -> Unit,
    onKindChange: (String) -> Unit,
    onIdentifierChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingLocation by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onCancel, enabled = !state.submitting) { Text("Cancel") }
        Text(if (state.isEdit) "Edit item" else "New item", style = MaterialTheme.typography.headlineSmall)
        when {
            state.loading -> CircularProgressIndicator()
            state.notFound -> Text("This item couldn't be found. It may have been deleted, or this device hasn't synced it yet.")
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Name (required)") },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    enabled = !state.submitting,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                val locationName = locations.firstOrNull { it.id == state.locationId }?.path ?: "No location"
                OutlinedButton(onClick = { pickingLocation = true }, enabled = !state.submitting) {
                    Text("Location: $locationName")
                }
                if (!state.isEdit) {
                    Text("Identifier (optional)", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ITEM_FORM_IDENTIFICATION_KINDS.forEach { kind ->
                            FilterChip(
                                selected = state.identifierKind == kind,
                                onClick = { onKindChange(kind) },
                                enabled = !state.submitting,
                                label = { Text(kind.replace('_', ' ')) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.identifierValue,
                        onValueChange = onIdentifierChange,
                        label = { Text("Identifier value") },
                        isError = state.identifierError != null,
                        supportingText = state.identifierError?.let { { Text(it) } },
                        singleLine = true,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = onSubmit, enabled = !state.submitting) {
                    Text(if (state.submitting) "Saving..." else if (state.isEdit) "Save" else "Create")
                }
            }
        }
    }
    if (pickingLocation) {
        LocationPicker(
            options = ItemEditOptions(locations = locations, currentLocationId = state.locationId),
            state = ItemEditState(),
            onSelect = {
                onLocationChange(it)
                pickingLocation = false
            },
            onDismiss = { pickingLocation = false },
            title = "Choose location",
            confirmLabel = "Choose",
        )
    }
}
