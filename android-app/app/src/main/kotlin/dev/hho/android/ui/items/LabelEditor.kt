package dev.hho.android.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelEditor(
    options: ItemEditOptions,
    state: ItemEditState,
    onAttach: (labelId: String) -> Unit,
    onDetach: (labelId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text("Edit labels") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Attached", style = MaterialTheme.typography.titleSmall)
                if (options.attachedLabels.isEmpty()) {
                    Text("No labels attached.", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.attachedLabels.forEach { label ->
                            InputChip(
                                selected = true,
                                onClick = { onDetach(label.id) },
                                enabled = !state.submitting,
                                label = { Text(label.name) },
                                trailingIcon = { Text("x") },
                            )
                        }
                    }
                }
                Text("Add a label", style = MaterialTheme.typography.titleSmall)
                if (options.availableLabels.isEmpty()) {
                    Text("No other labels available.", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.availableLabels.forEach { label ->
                            AssistChip(
                                onClick = { onAttach(label.id) },
                                enabled = !state.submitting,
                                label = { Text("+ ${label.name}") },
                            )
                        }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
