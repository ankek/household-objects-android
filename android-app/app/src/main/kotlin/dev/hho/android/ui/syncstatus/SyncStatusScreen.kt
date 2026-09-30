package dev.hho.android.ui.syncstatus

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
internal fun SyncStatusScreen(
    onBack: () -> Unit,
    onConflictLog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val conflictCount by viewModel.conflictCount.collectAsState()
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("Back") } }
        item { Text("Sync status", style = MaterialTheme.typography.headlineSmall) }
        item { Text(headlineText(ui), style = MaterialTheme.typography.titleMedium) }
        item { Text(lastSyncText(ui.status.lastSyncedAt, System.currentTimeMillis())) }
        item { Text("Photos waiting to upload: ${ui.status.photoQueueDepth}") }
        ui.status.lastError?.let { error ->
            item { Text("Last error: $error", color = MaterialTheme.colorScheme.error) }
        }
        item {
            Button(onClick = viewModel::onSyncNow, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
                Text("Sync now")
            }
        }
        item {
            OutlinedButton(onClick = onConflictLog, modifier = Modifier.fillMaxWidth()) {
                Text("Conflict log ($conflictCount)")
            }
        }
        item { Text("Failures", style = MaterialTheme.typography.titleMedium) }
        if (ui.failedMutations.isEmpty() && ui.failedPhotos.isEmpty()) {
            item { Text("No failures.") }
        }
        items(ui.failedMutations, key = { "m-${it.mutationId}" }) { m ->
            Column {
                Text("Edit not synced - ${m.label}", style = MaterialTheme.typography.bodyLarge)
                Text(m.error ?: "Unknown error", style = MaterialTheme.typography.bodyMedium)
                Text(absoluteTime(m.at), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(ui.failedPhotos, key = { "p-${it.id}" }) { p ->
            Column {
                Text("Photo not uploaded - ${p.itemName}", style = MaterialTheme.typography.bodyLarge)
                Text(p.error ?: "Unknown error", style = MaterialTheme.typography.bodyMedium)
                Text("Attempts: ${p.attempts}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun headlineText(ui: SyncStatusUi): String =
    when (ui.headline) {
        SyncHeadline.UP_TO_DATE -> "Up to date"
        SyncHeadline.PENDING -> "${ui.status.pendingCount} pending"
        SyncHeadline.PROBLEM -> "Needs attention (${ui.status.failedCount} failed, ${ui.status.pendingCount} pending)"
    }

private fun lastSyncText(at: Long?, now: Long): String =
    if (at == null) {
        "Last sync: never"
    } else {
        val relative = DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS)
        "Last sync: $relative (${absoluteTime(at)})"
    }

private fun absoluteTime(at: Long): String =
    DateUtils.formatDateTime(
        null,
        at,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
    )
