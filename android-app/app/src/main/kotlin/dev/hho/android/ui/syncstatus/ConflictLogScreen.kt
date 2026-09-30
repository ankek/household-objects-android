package dev.hho.android.ui.syncstatus

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
internal fun ConflictLogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConflictLogViewModel = hiltViewModel(),
) {
    val rows by viewModel.state.collectAsState()
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("Back") } }
        item { Text("Conflict log", style = MaterialTheme.typography.headlineSmall) }
        if (rows.isEmpty()) item { Text("No conflicts.") }
        items(rows, key = { it.id }) { c ->
            Column {
                Text("${c.entityLabel} - ${c.fieldLabel}", style = MaterialTheme.typography.bodyLarge)
                Text("Rejected value: ${c.rejectedValue}", style = MaterialTheme.typography.bodyMedium)
                Text("Kept (server) value: ${c.keptValue}", style = MaterialTheme.typography.bodyMedium)
                Text(c.originLabel, style = MaterialTheme.typography.bodySmall)
                Text(
                    DateUtils.formatDateTime(
                        null,
                        c.detectedAt,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
