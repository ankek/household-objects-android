package dev.hho.android.ui.scan

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.items.ItemSearch
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.scanner.BarcodeFormat
import dev.hho.android.data.scanner.DecodedBarcode
import dev.hho.android.ui.deeplink.DeepLinkResolver
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.domain.EditRepository
import dev.hho.android.ui.items.StockActionsViewModel
import dev.hho.android.ui.items.ViewModelTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ScanViewModelTest {
    private lateinit var db: HhoDatabase
    private lateinit var vm: ScanViewModel
    private var clock = 0L
    private var syncCalls = 0
    private val viewModels = ViewModelTracker()

    private fun awaitSheet(): ScanUiState.Sheet = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withTimeout(10_000) {
            val sheet = vm.uiState.first { it is ScanUiState.Sheet } as ScanUiState.Sheet
            vm.viewModelScope.coroutineContext.job.children.forEach { it.join() }
            sheet
        }
    }

    private fun code(v: String) = listOf(DecodedBarcode(v, BarcodeFormat.QR_CODE))

    @Before fun setUp() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        db.itemDao().upsert(
            ItemEntity("a", 1, "Saw", null, null, null, null, null, null, null),
        )
        db.itemIdentificationDao().upsert(
            ItemIdentificationEntity("i1", 1L, "a", "barcode", "123", 1L, 1L, 1L),
        )
        vm = viewModels.track(
            ScanViewModel(ScanResolver(DeepLinkResolver(db.itemDao()), ItemSearch(db.itemDao()), db.itemDao())),
        )
        vm.nowMillis = { clock }
    }

    @After fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun startsScanningNotPaused() {
        assertEquals(ScanUiState.Scanning, vm.uiState.value)
        assertFalse(vm.paused)
    }

    @Test fun matchOpensSheetAndPausesAnalysis() {
        vm.onBarcodes(code("123"))
        val sheet = awaitSheet()
        assertEquals("123", sheet.value)
        assertTrue(sheet.resolution is ScanResolution.Single)
        assertTrue(vm.paused)
    }

    @Test fun noMatchOpensSheetWithNoMatch() {
        vm.onBarcodes(code("nope"))
        assertEquals(ScanResolution.NoMatch("nope"), awaitSheet().resolution)
    }

    @Test fun detectionsWhileSheetOpenAreDropped() {
        vm.onBarcodes(code("123"))
        val first = awaitSheet()
        vm.onBarcodes(code("nope"))
        assertEquals(first, vm.uiState.value)
    }

    @Test fun sameCodeStillInFrameDoesNotReopenAfterDismiss() {
        vm.onBarcodes(code("123"))
        awaitSheet()
        vm.dismissSheet()
        assertFalse(vm.paused)
        clock += 1_000
        vm.onBarcodes(code("123"))
        assertEquals(ScanUiState.Scanning, vm.uiState.value)
        assertFalse("a suppressed repeat must not start a resolve", vm.paused)
        clock += 2_500
        vm.onBarcodes(code("123"))
        assertEquals(ScanUiState.Scanning, vm.uiState.value)
        assertFalse("a suppressed repeat must not start a resolve", vm.paused)
    }

    @Test fun sameCodeReopensAfterLeavingFrameForWindow() {
        vm.onBarcodes(code("123"))
        awaitSheet()
        vm.dismissSheet()
        clock += ScanViewModel.REPEAT_SUPPRESSION_MILLIS + 1
        vm.onBarcodes(code("123"))
        awaitSheet()
    }

    @Test fun differentCodeOpensImmediatelyAfterDismiss() {
        vm.onBarcodes(code("123"))
        awaitSheet()
        vm.dismissSheet()
        vm.onBarcodes(code("nope"))
        awaitSheet()
    }

    @Test fun chooseNarrowsMultipleToSingle() = kotlinx.coroutines.runBlocking {
        db.itemDao().upsert(ItemEntity("b", 1, "Hammer", null, null, null, null, null, null, null))
        db.itemIdentificationDao().upsert(ItemIdentificationEntity("i2", 1L, "b", "barcode", "123", 1L, 1L, 1L))
        vm.onBarcodes(code("123"))
        assertTrue(awaitSheet().resolution is ScanResolution.Multiple)
        vm.choose("b")
        val single = (vm.uiState.value as ScanUiState.Sheet).resolution as ScanResolution.Single
        assertEquals("b", single.item.id)
    }

    private fun <T> next(flow: kotlinx.coroutines.flow.Flow<T>): T = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withTimeout(10_000) { flow.first() }
    }

    @Test fun createItemEmitsPrefillWithRawScannedValueAndClosesSheet() {
        vm.onBarcodes(code("  4006381333931 "))
        awaitSheet()
        vm.createItem()
        assertEquals(
            ScanEvent.CreateItem("barcode", "  4006381333931 "),
            next(vm.events),
        )
        assertEquals(ScanUiState.Scanning, vm.uiState.value)
        assertFalse(vm.paused)
    }

    @Test fun createItemIsIgnoredForUnresolvedHhoLabel() {
        val url = "https://hho.example.com/i/0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"
        vm.onBarcodes(code(url))
        val sheet = awaitSheet()
        assertFalse((sheet.resolution as ScanResolution.NoMatch).canCreate)
        vm.createItem()
        assertEquals(sheet, vm.uiState.value)
        assertTrue(kotlinx.coroutines.runBlocking { vm.events.isEmptyNow() })
    }

    @Test fun createItemIsIgnoredOnMatchSheet() {
        vm.onBarcodes(code("123"))
        val sheet = awaitSheet()
        vm.createItem()
        assertEquals(sheet, vm.uiState.value)
        assertTrue(kotlinx.coroutines.runBlocking { vm.events.isEmptyNow() })
    }

    @Test fun adjustQuantityFromMatchedSheetQueuesAdjustmentThenResumesScanning() {
        vm.onBarcodes(code("123"))
        val item = (awaitSheet().resolution as ScanResolution.Single).item
        val ids = UuidV7Generator()
        val stock = viewModels.track(
            StockActionsViewModel(EditRepository(db, OutboxRepository(db, ids), ids) { syncCalls++ }),
        )
        assertTrue(vm.paused)
        stock.submit(item.id, 2L, "Used", "")
        val done = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(10_000) { stock.state.first { it.completed } }
        }
        assertTrue(done.completed)
        assertEquals(1, syncCalls)
        assertEquals(1, kotlinx.coroutines.runBlocking { db.outboxDao().getAllOrdered().size })
        vm.dismissSheet()
        assertFalse(vm.paused)
    }

    @Test fun adjustQuantityAfterChoosingFromMultipleTargetsChosenItem() = kotlinx.coroutines.runBlocking {
        db.itemDao().upsert(ItemEntity("b", 1, "Hammer", null, null, null, null, null, null, null))
        db.itemIdentificationDao().upsert(ItemIdentificationEntity("i2", 1L, "b", "barcode", "123", 1L, 1L, 1L))
        vm.onBarcodes(code("123"))
        awaitSheet()
        vm.choose("b")
        val target = ((vm.uiState.value as ScanUiState.Sheet).resolution as ScanResolution.Single).item.id
        val ids = UuidV7Generator()
        val stock = viewModels.track(
            StockActionsViewModel(EditRepository(db, OutboxRepository(db, ids), ids) { syncCalls++ }),
        )
        stock.submit(target, -1L, "", "")
        kotlinx.coroutines.withTimeout(10_000) { stock.state.first { it.completed } }
        val fields = dev.hho.android.data.outbox.LocalMutation.from(db.outboxDao().getAllOrdered().single()).fields
        assertEquals("b", (fields["item_id"] as kotlinx.serialization.json.JsonPrimitive).content)
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<*>.isEmptyNow(): Boolean =
    kotlinx.coroutines.withTimeoutOrNull(200) { first() } == null
