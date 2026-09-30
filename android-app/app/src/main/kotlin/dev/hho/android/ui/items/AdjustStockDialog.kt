package dev.hho.android.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal val ADJUST_REASON_PRESETS = listOf("Used", "Restocked", "Lost", "Correction")

@Composable
fun AdjustStockDialog(
    state: AdjustStockState,
    onSubmit: (delta: Long?, reason: String, note: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deltaText by rememberSaveable { mutableStateOf("1") }
    var reason by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.completed) {
        if (state.completed) onDismiss()
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!state.submitting) onDismiss() },
        title = { Text("Adjust quantity") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { deltaText = step(deltaText, -1) },
                        enabled = !state.submitting,
                    ) { Text("-1") }
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { deltaText = it },
                        label = { Text("Change (+/-)") },
                        singleLine = true,
                        enabled = !state.submitting,
                        isError = state.deltaError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { deltaText = step(deltaText, 1) },
                        enabled = !state.submitting,
                    ) { Text("+1") }
                }
                state.deltaError?.let { ErrorLine(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADJUST_REASON_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = reason == preset,
                            onClick = { reason = preset },
                            enabled = !state.submitting,
                            label = { Text(preset) },
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    singleLine = true,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.generalError?.let { ErrorLine(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(deltaText.trim().toLongOrNull(), reason, note) },
                enabled = !state.submitting,
            ) { Text(if (state.submitting) "Saving..." else "Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.submitting) { Text("Cancel") }
        },
    )
}

@Composable
private fun ErrorLine(message: String) {
    Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

internal fun step(text: String, by: Long): String = ((text.trim().toLongOrNull() ?: 0L) + by).toString()
