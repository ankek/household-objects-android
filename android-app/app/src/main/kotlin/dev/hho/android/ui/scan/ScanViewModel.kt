package dev.hho.android.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.scanner.DecodedBarcode
import dev.hho.android.ui.deeplink.parseItemDeepLink
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanUiState {
    data object Scanning : ScanUiState

    data class Sheet(val value: String, val resolution: ScanResolution) : ScanUiState
}

sealed interface ScanEvent {
    data class CreateItem(
        val prefillIdentifierKind: String,
        val prefillIdentifierValue: String,
    ) : ScanEvent
}

const val SCAN_PREFILL_KIND = "barcode"

val ScanResolution.NoMatch.canCreate: Boolean get() = parseItemDeepLink(value) == null

@HiltViewModel
class ScanViewModel
    @Inject
    constructor(
        private val resolver: ScanResolver,
    ) : ViewModel() {
        internal var nowMillis: () -> Long = System::currentTimeMillis

        private val mutableState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
        val uiState: StateFlow<ScanUiState> = mutableState.asStateFlow()

        private val eventChannel = Channel<ScanEvent>(Channel.BUFFERED)

        val events: Flow<ScanEvent> = eventChannel.receiveAsFlow()

        @Volatile private var resolving = false
        @Volatile private var lastSeenValue: String? = null
        private var lastSeenAt = 0L

        val paused: Boolean get() = resolving || mutableState.value !is ScanUiState.Scanning

        fun onBarcodes(barcodes: List<DecodedBarcode>) {
            val value = barcodes.firstOrNull()?.value ?: return
            if (paused) return
            val now = nowMillis()
            val repeat = value == lastSeenValue && now - lastSeenAt < REPEAT_SUPPRESSION_MILLIS
            lastSeenValue = value
            lastSeenAt = now
            if (repeat) return
            resolving = true
            viewModelScope.launch {
                try {
                    mutableState.value = ScanUiState.Sheet(value, resolver.resolve(value))
                } finally {
                    resolving = false
                }
            }
        }

        fun dismissSheet() {
            lastSeenAt = nowMillis()
            mutableState.value = ScanUiState.Scanning
        }

        fun createItem() {
            val sheet = mutableState.value as? ScanUiState.Sheet ?: return
            val noMatch = sheet.resolution as? ScanResolution.NoMatch ?: return
            if (!noMatch.canCreate) return
            dismissSheet()
            eventChannel.trySend(ScanEvent.CreateItem(SCAN_PREFILL_KIND, noMatch.value))
        }

        fun choose(itemId: String) {
            val sheet = mutableState.value as? ScanUiState.Sheet ?: return
            val multiple = sheet.resolution as? ScanResolution.Multiple ?: return
            val item = multiple.items.firstOrNull { it.id == itemId } ?: return
            mutableState.value = sheet.copy(resolution = ScanResolution.Single(item))
        }

        companion object {
            const val REPEAT_SUPPRESSION_MILLIS = 3_000L
        }
    }
