package dev.hho.android.ui.scan

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hho.android.ui.items.AdjustStockDialog
import dev.hho.android.ui.items.StockActionsViewModel

@Composable
internal fun ScanScreen(
    onViewItem: (String) -> Unit,
    onCreateItem: (prefillKind: String, prefillValue: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
    cameraViewModel: ScanCameraViewModel = hiltViewModel(),
    stockViewModel: StockActionsViewModel = hiltViewModel(),
) {
    val camera = rememberCameraPermission()

    val state by viewModel.uiState.collectAsState()
    val stockState by stockViewModel.state.collectAsState()
    var adjustingItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val latestOnCreateItem by rememberUpdatedState(onCreateItem)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanEvent.CreateItem ->
                    latestOnCreateItem(event.prefillIdentifierKind, event.prefillIdentifierValue)
            }
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("Back") }
        if (camera.granted) {
            ScanCameraPreview(
                isPaused = { viewModel.paused },
                onBarcodes = viewModel::onBarcodes,
                modifier = Modifier.fillMaxSize(),
                cameraViewModel = cameraViewModel,
            )
        } else {
            CameraPermissionDenied(
                onRetry = camera.request,
                onOpenSettings = camera.openSettings,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    (state as? ScanUiState.Sheet)?.let { sheet ->
        ScanActionSheet(
            sheet = sheet,
            onViewItem = { id ->
                viewModel.dismissSheet()
                onViewItem(id)
            },
            onAdjust = { id ->
                stockViewModel.reset()
                adjustingItemId = id
            },
            onCreate = viewModel::createItem,
            onChoose = viewModel::choose,
            onDismiss = viewModel::dismissSheet,
        )
    }
    adjustingItemId?.let { id ->
        AdjustStockDialog(
            state = stockState,
            onSubmit = { delta, reason, note -> stockViewModel.submit(id, delta, reason, note) },
            onDismiss = {
                val saved = stockState.completed
                adjustingItemId = null
                stockViewModel.reset()
                if (saved) viewModel.dismissSheet()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanActionSheet(
    sheet: ScanUiState.Sheet,
    onViewItem: (String) -> Unit,
    onAdjust: (String) -> Unit,
    onCreate: () -> Unit,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val resolution = sheet.resolution) {
                is ScanResolution.Single -> {
                    Text(resolution.item.name, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onViewItem(resolution.item.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("View item")
                    }
                    OutlinedButton(onClick = { onAdjust(resolution.item.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Adjust quantity")
                    }
                }
                is ScanResolution.Multiple -> {
                    Text("${resolution.items.size} items match ${sheet.value}", style = MaterialTheme.typography.titleMedium)
                    resolution.items.forEach { item ->
                        OutlinedButton(onClick = { onChoose(item.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.name)
                        }
                    }
                }
                is ScanResolution.NoMatch -> {
                    Text("No item found for ${resolution.value}", style = MaterialTheme.typography.titleMedium)
                    if (resolution.canCreate) {
                        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create item") }
                    } else {
                        Text(
                            "This looks like an HHO label for an item that is not on this device yet. " +
                                "Sync, then scan again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Keep scanning") }
        }
    }
}
