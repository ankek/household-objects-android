package dev.hho.android.ui.receiving

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hho.android.data.receiving.DiscrepancySummary
import dev.hho.android.data.receiving.LineStatus
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ReceivingSessionEntity
import dev.hho.android.ui.scan.CameraPermissionDenied
import dev.hho.android.ui.scan.ScanCameraPreview
import dev.hho.android.ui.scan.rememberCameraPermission

@Composable
internal fun ReceivingScreen(
    onBack: () -> Unit,
    onCheckedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceivingViewModel = hiltViewModel(),
) {
    DisposableEffect(viewModel) { onDispose { viewModel.showSessions() } }
    val mode by viewModel.mode.collectAsState()
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        when (mode) {
            ReceivingMode.Sessions -> SessionList(viewModel, onBack)
            ReceivingMode.Header -> HeaderForm(viewModel)
            is ReceivingMode.Session -> SessionPage(viewModel, onCheckedIn)
        }
    }
}

@Composable
private fun SessionList(viewModel: ReceivingViewModel, onBack: () -> Unit) {
    val sessions by viewModel.openSessions.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Button(onClick = viewModel::newSession) { Text("New") }
    }
    Text("Receive delivery", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
    if (sessions.isEmpty()) {
        Text("No open deliveries. Tap New to start one.", style = MaterialTheme.typography.bodyMedium)
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sessions, key = { it.id }) { session ->
            Column(
                Modifier.fillMaxWidth().clickable { viewModel.openSession(session.id) }.padding(vertical = 8.dp),
            ) {
                Text(session.vendor, style = MaterialTheme.typography.titleMedium)
                Text(session.subtitle(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun ReceivingSessionEntity.subtitle(): String =
    listOfNotNull(orderReference, purchasedOn?.toString()).joinToString(" - ").ifEmpty { "No order reference" }

@Composable
private fun HeaderForm(viewModel: ReceivingViewModel) {
    val state by viewModel.header.collectAsState()
    var vendor by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    Text("New delivery", style = MaterialTheme.typography.titleLarge)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = vendor,
            onValueChange = { vendor = it },
            label = { Text("Vendor (required)") },
            isError = state.vendorError != null,
            supportingText = state.vendorError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = order,
            onValueChange = { order = it },
            label = { Text("Order reference") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Purchased on (YYYY-MM-DD)") },
            isError = state.dateError != null,
            supportingText = state.dateError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::showSessions) { Text("Cancel") }
            Button(
                onClick = { viewModel.createSession(vendor, order, date) },
                enabled = !state.submitting,
            ) { Text("Start receiving") }
        }
    }
}

@Composable
private fun ColumnScope.SessionPage(viewModel: ReceivingViewModel, onCheckedIn: () -> Unit) {
    val state = viewModel.session.collectAsState().value
    if (state == null) {
        Text("This delivery is no longer available.")
        OutlinedButton(onClick = viewModel::showSessions) { Text("Back") }
        return
    }
    val latestOnCheckedIn by rememberUpdatedState(onCheckedIn)
    LaunchedEffect(state.checkedIn) { if (state.checkedIn) latestOnCheckedIn() }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberCameraPermission()

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::showSessions) { Text("Back") }
        if (state.isOpen) {
            Button(onClick = viewModel::openPicker) { Text("Add expected") }
            if (state.scanning) {
                OutlinedButton(onClick = { viewModel.setScanning(false) }) { Text("Stop scan") }
            } else {
                Button(onClick = { viewModel.setScanning(true) }) { Text("Scan") }
            }
        }
    }
    Text(state.session.vendor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
    Text(state.session.subtitle() + " - " + state.session.status.lowercase(), style = MaterialTheme.typography.bodySmall)
    SummaryBlock(state.summary)
    if (state.isOpen && state.scanning) {
        if (camera.granted) {
            ScanCameraPreview(
                isPaused = { viewModel.paused },
                onBarcodes = viewModel::onBarcodes,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        } else {
            CameraPermissionDenied(onRetry = camera.request, onOpenSettings = camera.openSettings)
        }
    }
    state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }
    state.checkInError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.lines, key = { it.itemId }) { line ->
            LineRow(
                line = line,
                editable = state.isOpen,
                onIncrement = { viewModel.increment(line.itemId) },
                onDecrement = { viewModel.decrement(line.itemId) },
                onEdit = { editing = line.itemId },
            )
        }
    }
    if (state.isOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.navigationBarsPadding()) {
            Button(onClick = viewModel::requestCheckIn, enabled = !state.submitting) { Text("Check in") }
            OutlinedButton(onClick = viewModel::requestCancel) { Text("Cancel session") }
        }
    }

    if (state.pickerOpen) ItemPicker(viewModel, state)
    editing?.let { id ->
        state.lines.firstOrNull { it.itemId == id }?.let { line ->
            EditLineDialog(
                line = line,
                onSave = { received, expected ->
                    received?.let { viewModel.setReceived(id, it) }
                    if (expected != null && line.discrepancy.expectedQty != null) viewModel.setExpected(id, expected)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
    state.pendingUnexpected?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUnexpected,
            title = { Text("Add ${pending.name} as unexpected?") },
            text = { Text("It is not on this delivery's expected list. It will be counted and checked in anyway.") },
            confirmButton = { Button(onClick = viewModel::confirmUnexpected) { Text("Add") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUnexpected) { Text("Skip") } },
        )
    }
    state.choices?.let { choices ->
        AlertDialog(
            onDismissRequest = viewModel::dismissChoices,
            title = { Text("${choices.size} items match") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { item ->
                        OutlinedButton(onClick = { viewModel.choose(item.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismissChoices) { Text("Keep scanning") } },
        )
    }
    if (state.confirmingCheckIn) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCheckIn,
            title = { Text("Check in this delivery?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryBlock(state.summary)
                    Text(
                        if (state.summary.totalReceived == 0) {
                            "Nothing has been counted yet."
                        } else {
                            "Counted quantities are added to stock and the vendor is recorded on each item."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmCheckIn, enabled = !state.submitting && state.summary.totalReceived > 0) {
                    Text("Check in")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissCheckIn) { Text("Keep counting") } },
        )
    }
    if (state.confirmingCancel) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCancel,
            title = { Text("Cancel this session?") },
            text = { Text("Nothing is added to stock. The counts stay on this device but the delivery closes.") },
            confirmButton = { Button(onClick = viewModel::confirmCancel) { Text("Cancel session") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCancel) { Text("Keep") } },
        )
    }
}

@Composable
private fun SummaryBlock(summary: DiscrepancySummary) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            "Received ${summary.totalReceived} of ${summary.totalExpected} expected",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Matched ${summary.matchedCount}  Short ${summary.shortLines.size}  " +
                "Over ${summary.overLines.size}  Unexpected ${summary.unexpectedLines.size}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LineRow(
    line: ReceivingLineUi,
    editable: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEdit: () -> Unit,
) {
    val d = line.discrepancy
    Column(Modifier.fillMaxWidth()) {
        Text(line.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "Expected ${d.expectedQty ?: "-"} / received ${d.receivedQty} (${d.status.label()})",
            style = MaterialTheme.typography.bodySmall,
        )
        if (editable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecrement, enabled = d.receivedQty > 0) { Text("-1") }
                OutlinedButton(onClick = onIncrement) { Text("+1") }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }
    }
}

private fun LineStatus.label(): String =
    when (this) {
        LineStatus.MATCHED -> "matched"
        LineStatus.SHORT -> "short"
        LineStatus.OVER -> "over"
        LineStatus.UNEXPECTED -> "unexpected"
    }

@Composable
private fun EditLineDialog(
    line: ReceivingLineUi,
    onSave: (received: Int?, expected: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var received by remember { mutableStateOf(line.discrepancy.receivedQty.toString()) }
    var expected by remember { mutableStateOf(line.discrepancy.expectedQty?.toString().orEmpty()) }
    val receivedValue = received.trim().toIntOrNull()?.takeIf { it >= 0 }
    val expectedValue = expected.trim().toIntOrNull()?.takeIf { it >= 1 }
    val hasExpected = line.discrepancy.expectedQty != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = received,
                    onValueChange = { received = it },
                    label = { Text("Received") },
                    isError = receivedValue == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (hasExpected) {
                    OutlinedTextField(
                        value = expected,
                        onValueChange = { expected = it },
                        label = { Text("Expected") },
                        isError = expectedValue == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(receivedValue, expectedValue) },
                enabled = receivedValue != null && (!hasExpected || expectedValue != null),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemPicker(viewModel: ReceivingViewModel, state: ReceivingSessionUiState) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.pickerResults.collectAsState()
    ModalBottomSheet(onDismissRequest = viewModel::closePicker) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search items") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(Modifier.height(320.dp)) {
                items(results, key = ItemEntity::id) { item ->
                    val expected = state.lines.firstOrNull { it.itemId == item.id }?.discrepancy?.expectedQty
                    Column(Modifier.fillMaxWidth().clickable { viewModel.addExpected(item) }.padding(vertical = 8.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        if (expected != null) Text("Expected: $expected", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(onClick = viewModel::closePicker, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
