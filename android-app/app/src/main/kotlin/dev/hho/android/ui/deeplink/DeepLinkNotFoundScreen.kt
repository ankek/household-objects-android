package dev.hho.android.ui.deeplink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeepLinkNotFoundScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Item not found", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This link does not match any item synced to this device. It may belong to another " +
                "household, have been deleted, or not have synced yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onBack) { Text("Back to items") }
    }
}
