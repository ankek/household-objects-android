package dev.hho.android.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun filterLocations(options: List<LocationOption>, query: String): List<LocationOption> {
    val needle = query.trim()
    return if (needle.isEmpty()) options else options.filter { it.path.contains(needle, ignoreCase = true) }
}

@Composable
internal fun LocationPicker(
    options: ItemEditOptions,
    state: ItemEditState,
    onSelect: (locationId: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Move to location",
    confirmLabel: String = "Move",
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(options.currentLocationId) }

    LaunchedEffect(state.completed) {
        if (state.completed) onDismiss()
    }
    val visible = filterLocations(options.locations, query)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!state.submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search locations") },
                    singleLine = true,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item(key = "none") {
                        LocationRow("No location", selected == null, !state.submitting) { selected = null }
                    }
                    items(visible, key = { it.id }) { option ->
                        LocationRow(option.path, selected == option.id, !state.submitting) { selected = option.id }
                    }
                }
                if (visible.isEmpty() && query.isNotBlank()) {
                    Text("No locations match.", style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }, enabled = !state.submitting) {
                Text(if (state.submitting) "Saving..." else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.submitting) { Text("Cancel") }
        },
    )
}

@Composable
private fun LocationRow(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(text, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
