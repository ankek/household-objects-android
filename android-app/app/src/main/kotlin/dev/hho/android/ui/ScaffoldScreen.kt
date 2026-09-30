package dev.hho.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ScaffoldScreen(
    viewModel: ScaffoldViewModel = hiltViewModel(),
) {
    ScaffoldScreenContent(state = viewModel.state)
}

@Composable
private fun ScaffoldScreenContent(
    state: ScaffoldUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "HHO",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Scaffold only — no features yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${state.applicationId} ${state.versionName} (minSdk ${state.minSdk})",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
