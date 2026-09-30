package dev.hho.android.ui.receiving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.items.ItemSearch
import dev.hho.android.data.receiving.DiscrepancySummary
import dev.hho.android.data.receiving.LineDiscrepancy
import dev.hho.android.data.receiving.ReceivingException
import dev.hho.android.data.receiving.ReceivingRepository
import dev.hho.android.data.receiving.ScanOutcome
import dev.hho.android.data.receiving.toDiscrepancy
import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ReceivingSessionEntity
import dev.hho.android.data.room.ReceivingStatus
import dev.hho.android.data.scanner.DecodedBarcode
import dev.hho.android.domain.CheckInResult
import dev.hho.android.domain.EditError
import dev.hho.android.domain.ReceivingCheckIn
import dev.hho.android.ui.scan.ScanResolution
import dev.hho.android.ui.scan.ScanResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

internal sealed interface ReceivingMode {
    data object Sessions : ReceivingMode

    data object Header : ReceivingMode

    data class Session(val id: String) : ReceivingMode
}

internal data class HeaderState(
    val vendorError: String? = null,
    val dateError: String? = null,
    val submitting: Boolean = false,
)

internal data class PendingItem(val itemId: String, val name: String)

internal data class ReceivingLineUi(val name: String, val discrepancy: LineDiscrepancy) {
    val itemId: String get() = discrepancy.itemId
}

internal data class ReceivingSessionUiState(
    val session: ReceivingSessionEntity,
    val lines: List<ReceivingLineUi>,
    val summary: DiscrepancySummary,
    val scanning: Boolean = false,
    val pickerOpen: Boolean = false,
    val pendingUnexpected: PendingItem? = null,
    val choices: List<ItemEntity>? = null,
    val confirmingCheckIn: Boolean = false,
    val confirmingCancel: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
    val checkInError: String? = null,
    val checkedIn: Boolean = false,
) {
    val isOpen: Boolean get() = session.status == ReceivingStatus.OPEN
}

private data class Transient(
    val message: String? = null,
    val scanning: Boolean = false,
    val pickerOpen: Boolean = false,
    val pendingUnexpected: PendingItem? = null,
    val choices: List<ItemEntity>? = null,
    val confirmingCheckIn: Boolean = false,
    val confirmingCancel: Boolean = false,
    val submitting: Boolean = false,
    val checkInError: String? = null,
    val checkedIn: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class ReceivingViewModel
    @Inject
    constructor(
        private val repo: ReceivingRepository,
        private val checkIn: ReceivingCheckIn,
        private val resolver: ScanResolver,
        private val itemSearch: ItemSearch,
        private val itemDao: ItemDao,
    ) : ViewModel() {
        internal var nowMillis: () -> Long = System::currentTimeMillis

        private val _mode = MutableStateFlow<ReceivingMode>(ReceivingMode.Sessions)
        val mode: StateFlow<ReceivingMode> = _mode.asStateFlow()

        private val _header = MutableStateFlow(HeaderState())
        val header: StateFlow<HeaderState> = _header.asStateFlow()

        private val transient = MutableStateFlow(Transient())

        val openSessions: StateFlow<List<ReceivingSessionEntity>> =
            repo.observeOpenSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val session: StateFlow<ReceivingSessionUiState?> =
            _mode.flatMapLatest { mode ->
                if (mode is ReceivingMode.Session) sessionFlow(mode.id) else flowOf(null)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        val pickerResults: StateFlow<List<ItemEntity>> =
            _query.flatMapLatest { itemSearch.results(it) }
                .map { it.take(PICKER_LIMIT) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        @Volatile private var resolving = false
        @Volatile private var lastSeenValue: String? = null
        private var lastSeenAt = 0L

        private val sessionId: String? get() = (_mode.value as? ReceivingMode.Session)?.id

        private fun sessionFlow(id: String) =
            combine(repo.observeSession(id), repo.observeLines(id), transient) { session, lines, t ->
                session?.let {
                    val named = lines.map { line ->
                        ReceivingLineUi(itemDao.getById(line.itemId)?.name ?: line.itemId, line.toDiscrepancy())
                    }
                    ReceivingSessionUiState(
                        session = it,
                        lines = named,
                        summary = DiscrepancySummary(named.map { l -> l.discrepancy }),
                        scanning = t.scanning,
                        pickerOpen = t.pickerOpen,
                        pendingUnexpected = t.pendingUnexpected,
                        choices = t.choices,
                        confirmingCheckIn = t.confirmingCheckIn,
                        confirmingCancel = t.confirmingCancel,
                        submitting = t.submitting,
                        message = t.message,
                        checkInError = t.checkInError,
                        checkedIn = t.checkedIn,
                    )
                }
            }

        val paused: Boolean
            get() = resolving || transient.value.let {
                !it.scanning || it.pendingUnexpected != null || it.choices != null || it.pickerOpen ||
                    it.confirmingCheckIn || it.confirmingCancel || it.submitting
            }

        fun showSessions() {
            resetScan()
            transient.value = Transient()
            _header.value = HeaderState()
            _query.value = ""
            _mode.value = ReceivingMode.Sessions
        }

        fun newSession() {
            _header.value = HeaderState()
            _mode.value = ReceivingMode.Header
        }

        fun openSession(id: String) {
            resetScan()
            transient.value = Transient()
            _query.value = ""
            _mode.value = ReceivingMode.Session(id)
        }

        fun createSession(vendor: String, orderReference: String, purchasedOn: String) {
            if (_header.value.submitting) return
            val vendorError = if (vendor.isBlank()) "Enter the vendor." else null
            var date: LocalDate? = null
            var dateError: String? = null
            if (purchasedOn.isNotBlank()) {
                try {
                    date = LocalDate.parse(purchasedOn.trim())
                } catch (e: DateTimeParseException) {
                    dateError = "Use the format YYYY-MM-DD, for example 2026-03-04."
                }
            }
            if (vendorError != null || dateError != null) {
                _header.value = HeaderState(vendorError = vendorError, dateError = dateError)
                return
            }
            _header.value = HeaderState(submitting = true)
            viewModelScope.launch {
                try {
                    val created = repo.startSession(vendor, orderReference.ifBlank { null }, date)
                    _header.value = HeaderState()
                    openSession(created.id)
                } catch (e: ReceivingException) {
                    _header.value = HeaderState(vendorError = e.message)
                }
            }
        }

        fun setQuery(query: String) {
            _query.value = query
        }

        fun openPicker() = transient.update { it.copy(pickerOpen = true, message = null) }

        fun closePicker() {
            _query.value = ""
            transient.update { it.copy(pickerOpen = false) }
        }

        fun addExpected(item: ItemEntity) {
            val id = sessionId ?: return
            val existing = session.value?.lines?.firstOrNull { it.itemId == item.id }
            if (existing?.discrepancy?.expectedQty != null) {
                say("${item.name} is already on the expected list.")
                return
            }
            guarded {
                repo.addExpected(id, item.id, 1)
                say("Added ${item.name}.")
            }
        }

        fun setReceived(itemId: String, quantity: Int) {
            val id = sessionId ?: return
            guarded { repo.setReceived(id, itemId, quantity) }
        }

        fun setExpected(itemId: String, quantity: Int) {
            val id = sessionId ?: return
            guarded { repo.addExpected(id, itemId, quantity) }
        }

        fun increment(itemId: String) = adjustReceived(itemId, +1)

        fun decrement(itemId: String) = adjustReceived(itemId, -1)

        private fun adjustReceived(itemId: String, by: Int) {
            val id = sessionId ?: return
            guarded { repo.adjustReceived(id, itemId, by) }
        }

        fun setScanning(on: Boolean) {
            if (!on) resetScan()
            transient.update { it.copy(scanning = on, message = null) }
        }

        fun onBarcodes(barcodes: List<DecodedBarcode>) {
            val value = barcodes.firstOrNull()?.value ?: return
            val id = sessionId ?: return
            if (paused) return
            val now = nowMillis()
            val repeat = value == lastSeenValue && now - lastSeenAt < REPEAT_SUPPRESSION_MILLIS
            lastSeenValue = value
            lastSeenAt = now
            if (repeat) return
            resolving = true
            viewModelScope.launch {
                try {
                    when (val resolution = resolver.resolve(value)) {
                        is ScanResolution.Single -> record(id, resolution.item)
                        is ScanResolution.Multiple -> transient.update { it.copy(choices = resolution.items) }
                        is ScanResolution.NoMatch -> say("No item found for ${resolution.value}.")
                    }
                } finally {
                    resolving = false
                }
            }
        }

        fun choose(itemId: String) {
            val id = sessionId ?: return
            val item = transient.value.choices?.firstOrNull { it.id == itemId } ?: return
            transient.update { it.copy(choices = null) }
            viewModelScope.launch { record(id, item) }
        }

        fun dismissChoices() {
            lastSeenAt = nowMillis()
            transient.update { it.copy(choices = null) }
        }

        fun confirmUnexpected() {
            val id = sessionId ?: return
            val pending = transient.value.pendingUnexpected ?: return
            transient.update { it.copy(pendingUnexpected = null) }
            guarded {
                repo.confirmUnexpected(id, pending.itemId)
                say("Added ${pending.name} as unexpected.")
            }
        }

        fun dismissUnexpected() {
            lastSeenAt = nowMillis()
            transient.update { it.copy(pendingUnexpected = null) }
        }

        private suspend fun record(sessionId: String, item: ItemEntity) {
            try {
                when (val outcome = repo.recordScan(sessionId, item.id)) {
                    is ScanOutcome.Counted ->
                        say("Counted ${item.name}: ${outcome.line.receivedQty} received.")
                    is ScanOutcome.Unexpected ->
                        transient.update { it.copy(pendingUnexpected = PendingItem(item.id, item.name)) }
                }
            } catch (e: ReceivingException) {
                say(e.message ?: "Could not record the scan.")
            }
        }

        private fun resetScan() {
            lastSeenValue = null
            lastSeenAt = 0L
        }

        fun requestCheckIn() = transient.update { it.copy(confirmingCheckIn = true, checkInError = null) }

        fun dismissCheckIn() = transient.update { if (it.submitting) it else it.copy(confirmingCheckIn = false) }

        fun confirmCheckIn() {
            val id = sessionId ?: return
            var started = false
            transient.update {
                started = !it.submitting
                if (started) it.copy(submitting = true, checkInError = null) else it
            }
            if (!started) return
            viewModelScope.launch {
                var ok = false
                val error: String? = try {
                    when (val result = checkIn.checkIn(id)) {
                        is CheckInResult.Completed -> { ok = true; null }
                        is CheckInResult.AlreadyCompleted -> "This delivery was already checked in."
                        is CheckInResult.Failed ->
                            "Check-in failed and nothing was changed: ${describe(result.error)} " +
                                "Fix the lines and try again."
                    }
                } catch (e: ReceivingException) {
                    e.message ?: "Check-in was refused."
                }
                transient.update {
                    it.copy(submitting = false, confirmingCheckIn = false, checkedIn = ok, checkInError = error)
                }
            }
        }

        private fun describe(error: EditError): String =
            when (error) {
                is EditError.Invalid -> error.message
                is EditError.NotFound -> "an item is no longer on this device."
                is EditError.StorageFailed -> "the device could not save the change."
            }

        fun requestCancel() = transient.update { it.copy(confirmingCancel = true) }

        fun dismissCancel() = transient.update { it.copy(confirmingCancel = false) }

        fun confirmCancel() {
            val id = sessionId ?: return
            viewModelScope.launch {
                try {
                    repo.cancel(id)
                    showSessions()
                } catch (e: ReceivingException) {
                    transient.update { it.copy(confirmingCancel = false, message = e.message) }
                }
            }
        }

        private fun say(message: String) = transient.update { it.copy(message = message) }

        private fun guarded(block: suspend () -> Unit) {
            viewModelScope.launch {
                try {
                    block()
                } catch (e: ReceivingException) {
                    say(e.message ?: "The change was refused.")
                }
            }
        }

        companion object {
            const val REPEAT_SUPPRESSION_MILLIS = 3_000L
            const val PICKER_LIMIT = 50
        }
    }
