package dev.hho.android.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hho.android.ui.photos.ItemPhotoBadgeViewModel
import dev.hho.android.ui.photos.PhotoBadge

private const val UNKNOWN_TYPED_VALUE_PLACEHOLDER = "(unrenderable value)"

@Composable
internal fun ItemDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    onAddPhoto: () -> Unit = {},
    viewModel: ItemDetailViewModel = hiltViewModel(),
    stockViewModel: StockActionsViewModel = hiltViewModel(),
    editViewModel: ItemEditActionsViewModel = hiltViewModel(),
    photoBadgeViewModel: ItemPhotoBadgeViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
        editViewModel.load(itemId)
        photoBadgeViewModel.load(itemId)
    }
    val uiState by viewModel.uiState.collectAsState()
    val stockState by stockViewModel.state.collectAsState()
    val editState by editViewModel.state.collectAsState()
    val editOptions by editViewModel.options.collectAsState()
    val photoBadge by photoBadgeViewModel.badge.collectAsState()
    var adjusting by remember { mutableStateOf(false) }
    var movingLocation by remember { mutableStateOf(false) }
    var editingLabels by remember { mutableStateOf(false) }
    ItemDetailScreenContent(
        uiState = uiState,
        onBack = onBack,
        onEdit = onEdit,
        onAddPhoto = onAddPhoto,
        photoBadge = photoBadge,
        onAdjustQuantity = {
            stockViewModel.reset()
            adjusting = true
        },
        onMoveLocation = {
            editViewModel.reset()
            movingLocation = true
        },
        onEditLabels = {
            editViewModel.reset()
            editingLabels = true
        },
        modifier = modifier,
    )
    if (movingLocation) {
        LocationPicker(
            options = editOptions,
            state = editState,
            onSelect = { locationId -> editViewModel.move(itemId, locationId) },
            onDismiss = {
                movingLocation = false
                editViewModel.reset()
            },
        )
    }
    if (editingLabels) {
        LabelEditor(
            options = editOptions,
            state = editState,
            onAttach = { labelId -> editViewModel.attach(itemId, labelId) },
            onDetach = { labelId -> editViewModel.detach(itemId, labelId) },
            onDismiss = {
                editingLabels = false
                editViewModel.reset()
            },
        )
    }
    if (adjusting) {
        AdjustStockDialog(
            state = stockState,
            onSubmit = { delta, reason, note -> stockViewModel.submit(itemId, delta, reason, note) },
            onDismiss = {
                adjusting = false
                stockViewModel.reset()
            },
        )
    }
}

@Composable
private fun ItemDetailScreenContent(
    uiState: ItemDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddPhoto: () -> Unit,
    photoBadge: PhotoBadge,
    onAdjustQuantity: () -> Unit,
    onMoveLocation: () -> Unit,
    onEditLabels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            if (uiState is ItemDetailUiState.Data) {
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }
        when (uiState) {
            is ItemDetailUiState.Loading ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is ItemDetailUiState.NotFound ->
                Text(
                    text = "This item couldn't be found. It may have been deleted, or this " +
                        "device hasn't synced it yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )

            is ItemDetailUiState.Data ->
                ItemDetailBody(
                    item = uiState.item,
                    onAddPhoto = onAddPhoto,
                    photoBadge = photoBadge,
                    onAdjustQuantity = onAdjustQuantity,
                    onMoveLocation = onMoveLocation,
                    onEditLabels = onEditLabels,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                )
        }
    }
}

@Composable
private fun ItemDetailBody(
    item: ItemDetailData,
    onAddPhoto: () -> Unit,
    photoBadge: PhotoBadge,
    onAdjustQuantity: () -> Unit,
    onMoveLocation: () -> Unit,
    onEditLabels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = item.name, style = MaterialTheme.typography.headlineSmall)
        item.description?.let { description ->
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        item.quantity?.let { quantity ->
            Text(text = "Quantity: $quantity", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onAdjustQuantity) { Text("Adjust quantity") }
        item.shortCode?.let { shortCode ->
            Text(text = "Short code: $shortCode", style = MaterialTheme.typography.bodyMedium)
        }
        item.locationName?.let { locationName ->
            Text(text = "Location: $locationName", style = MaterialTheme.typography.bodyMedium)
        }
        if (item.labelNames.isNotEmpty()) {
            Text(text = "Labels: ${item.labelNames.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMoveLocation) { Text("Move location") }
            OutlinedButton(onClick = onEditLabels) { Text("Edit labels") }
        }
        OutlinedButton(onClick = onAddPhoto) { Text("Add photo") }
        photoBadge.summary()?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (photoBadge.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        item.warranty?.let { warranty -> WarrantySection(warranty) }
        item.soldTo?.let { soldTo -> SoldToSection(soldTo) }
        item.purchasedFrom?.let { purchasedFrom -> PurchasedFromSection(purchasedFrom) }
        if (item.identifiers.isNotEmpty()) {
            IdentifiersSection(item.identifiers)
        }
        if (item.customFields.isNotEmpty()) {
            CustomFieldsSection(item.customFields)
        }
        if (item.stockAdjustments.isNotEmpty()) {
            StockHistorySection(item.stockAdjustments)
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun WarrantySection(warranty: WarrantyBlockData) {
    DetailSection(title = "Warranty") {
        Text(
            text = if (warranty.isLifetime) "Lifetime" else "Limited",
            style = MaterialTheme.typography.bodyMedium,
        )
        warranty.holder?.let { Text("Holder: $it", style = MaterialTheme.typography.bodyMedium) }
        warranty.provider?.let { Text("Provider: $it", style = MaterialTheme.typography.bodyMedium) }
        warranty.startsOn?.let { Text("Starts: $it", style = MaterialTheme.typography.bodyMedium) }
        warranty.expiresOn?.let { Text("Expires: $it", style = MaterialTheme.typography.bodyMedium) }
        warranty.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SoldToSection(soldTo: SoldToBlockData) {
    DetailSection(title = "Sold to") {
        Text(text = "Sale price (minor units): ${soldTo.salePriceMinor}", style = MaterialTheme.typography.bodyMedium)
        soldTo.buyerName?.let { Text("Buyer: $it", style = MaterialTheme.typography.bodyMedium) }
        soldTo.soldOn?.let { Text("Sold on: $it", style = MaterialTheme.typography.bodyMedium) }
        soldTo.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun PurchasedFromSection(purchasedFrom: PurchasedFromBlockData) {
    DetailSection(title = "Purchased from") {
        Text(
            text = "Purchase price (minor units): ${purchasedFrom.purchasePriceMinor}",
            style = MaterialTheme.typography.bodyMedium,
        )
        purchasedFrom.vendor?.let { Text("Vendor: $it", style = MaterialTheme.typography.bodyMedium) }
        purchasedFrom.purchasedOn?.let { Text("Purchased on: $it", style = MaterialTheme.typography.bodyMedium) }
        purchasedFrom.orderReference?.let { Text("Order reference: $it", style = MaterialTheme.typography.bodyMedium) }
        purchasedFrom.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun IdentifiersSection(identifiers: List<IdentifierData>) {
    DetailSection(title = "Identifiers") {
        identifiers.forEach { identifier ->
            Text(
                text = "${identifier.kind}: ${identifier.value}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CustomFieldsSection(customFields: List<CustomFieldData>) {
    DetailSection(title = "Custom fields") {
        customFields.forEach { field ->
            Text(
                text = "${field.name}: ${field.value.render()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StockHistorySection(stockAdjustments: List<StockAdjustmentData>) {
    DetailSection(title = "Stock history") {
        stockAdjustments.forEach { adjustment ->
            val sign = if (adjustment.delta >= 0) "+" else ""
            val reasonSuffix = adjustment.reason?.let { " ($it)" }.orEmpty()
            Text(
                text = "$sign${adjustment.delta} → ${adjustment.resultingQuantity}$reasonSuffix",
                style = MaterialTheme.typography.bodyMedium,
            )
            adjustment.note?.let { Text("Note: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun CustomFieldValue?.render(): String =
    when (this) {
        is CustomFieldValue.Text -> value
        is CustomFieldValue.Number -> value.toPlainString()
        is CustomFieldValue.Bool -> if (value) "Yes" else "No"
        is CustomFieldValue.Date -> value.toString()
        null -> UNKNOWN_TYPED_VALUE_PLACEHOLDER
    }
