package dev.hho.android.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.connected.collect { onConnected() }
    }

    ConnectScreenContent(
        uiState = uiState,
        onUrlChanged = viewModel::onUrlChanged,
        onConnectClicked = viewModel::onConnectClicked,
        modifier = modifier,
    )
}

@Composable
private fun ConnectScreenContent(
    uiState: ConnectUiState,
    onUrlChanged: (String) -> Unit,
    onConnectClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Connect to your HHO instance",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Enter the address of your self-hosted server, e.g. https://hho.example.com " +
                "or http://192.168.1.10:7745.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = uiState.urlInput,
            onValueChange = onUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Instance URL") },
            singleLine = true,
            isError = uiState.errorMessage != null,
            enabled = !uiState.isChecking,
        )
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onConnectClicked,
            enabled = !uiState.isChecking && uiState.urlInput.isNotBlank(),
        ) {
            if (uiState.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(if (uiState.isChecking) "Connecting…" else "Connect")
        }
    }
}
