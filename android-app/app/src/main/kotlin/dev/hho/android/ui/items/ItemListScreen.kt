package dev.hho.android.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ItemListScreen(
    onItemClick: (String) -> Unit,
    onScan: () -> Unit = {},
    onNewItem: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ItemListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.queryState.collectAsState()
    ItemListScreenContent(
        uiState = uiState,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onItemClick = onItemClick,
        onScan = onScan,
        onNewItem = onNewItem,
        modifier = modifier,
    )
}

@Composable
private fun ItemListScreenContent(
    uiState: ItemListUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onItemClick: (String) -> Unit,
    onScan: () -> Unit,
    onNewItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onScan) {
                Text("Scan barcode or QR label")
            }
            Button(onClick = onNewItem) { Text("New item") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            label = { Text("Search items, short codes, or scanned values") },
            singleLine = true,
        )
        ItemListBody(uiState = uiState, onItemClick = onItemClick, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ItemListBody(
    uiState: ItemListUiState,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is ItemListUiState.Loading ->
            Box(modifier = modifier) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

        is ItemListUiState.Empty ->
            Box(modifier = modifier) {
                Text(
                    text = "Nothing has synced to this device yet. Items will appear here once " +
                        "this device finishes its first sync with your instance.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

        is ItemListUiState.NoMatches ->
            Box(modifier = modifier) {
                Text(
                    text = "No items, short codes, or scanned values match \"${uiState.query}\".",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

        is ItemListUiState.Data ->
            LazyColumn(modifier = modifier) {
                items(items = uiState.items, key = { it.id }) { row ->
                    ItemRowContent(row = row, onClick = { onItemClick(row.id) })
                    HorizontalDivider()
                }
            }
    }
}

@Composable
private fun ItemRowContent(
    row: ItemRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = row.name, style = MaterialTheme.typography.titleMedium)
        val subtitle = itemRowSubtitle(row)
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun itemRowSubtitle(row: ItemRow): String =
    buildString {
        if (row.quantity != null) {
            append("Qty ${row.quantity}")
        }
        if (row.shortCode != null) {
            if (isNotEmpty()) append(" · ")
            append(row.shortCode)
        }
    }
