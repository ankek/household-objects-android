package dev.hho.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onViewItems: () -> Unit,
    onReceiveDelivery: () -> Unit,
    onSyncStatus: () -> Unit,
    modifier: Modifier = Modifier,
    syncProblem: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Logged in.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onViewItems) {
            Text("View items")
        }
        Button(onClick = onReceiveDelivery) {
            Text("Receive delivery")
        }
        Button(onClick = onSyncStatus) {
            Text(if (syncProblem) "Sync status (!)" else "Sync status")
        }
    }
}
