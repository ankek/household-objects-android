package dev.hho.android.ui.items

import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemCustomFieldEntity
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.ItemLabelEntity
import dev.hho.android.data.room.LabelEntity
import dev.hho.android.data.room.LocationEntity
import dev.hho.android.data.room.PurchasedFromBlockEntity
import dev.hho.android.data.room.SoldToBlockEntity
import dev.hho.android.data.room.StockAdjustmentEntity
import dev.hho.android.data.room.WarrantyBlockEntity
import dev.hho.android.testing.AirplaneModeRule
import dev.hho.android.MainActivity
import dev.hho.android.testing.NetworkTripwire
import dev.hho.android.ui.theme.HhoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OfflineItemsInstrumentedTest {

    private val hiltRule = HiltAndroidRule(this)
    private val airplane = AirplaneModeRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(hiltRule).around(airplane).around(compose)

    @Inject lateinit var db: HhoDatabase

    @Inject lateinit var tripwire: NetworkTripwire

    @Before
    fun seed() {
        hiltRule.inject()
        runBlocking { seedFixtures() }
        compose.activityRule.scenario.onActivity { activity ->
            val host = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ComposeView
            host.setContent {
            HhoTheme(darkTheme = false) {
                var selected by rememberSaveable { mutableStateOf<String?>(null) }
                val id = selected
                if (id == null) {
                    ItemListScreen(onItemClick = { selected = it })
                } else {
                    ItemDetailScreen(itemId = id, onBack = { selected = null })
                }
            }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun assertNoNetworkAttempted() {
        assertEquals("network requests were attempted while offline", emptyList<String>(), tripwire.requests.toList())
    }

    @Test
    fun deviceIsActuallyOffline() {
        assertFalse("airplane mode did not take the device offline", airplane.hasInternet())
    }

    @Test
    fun listShowsAllSeededItems() {
        compose.onNodeWithText("Cordless Drill").assertExists()
        compose.onNodeWithText("Garden Hose").assertExists()
        compose.onNodeWithText("Stepladder").assertExists()
        compose.onNodeWithText("Qty 3 · DR-1").assertExists()
    }

    @Test
    fun searchByNameFilters() {
        search("hose")
        compose.onNodeWithText("Garden Hose").assertExists()
        compose.onAllNodesWithText("Cordless Drill").assertCountEquals(0)
        compose.onAllNodesWithText("Stepladder").assertCountEquals(0)
    }

    @Test
    fun searchByIdentifierValueFilters() {
        search("SN-DRILL")
        compose.onNodeWithText("Cordless Drill").assertExists()
        compose.onAllNodesWithText("Garden Hose").assertCountEquals(0)
        compose.onAllNodesWithText("Stepladder").assertCountEquals(0)
    }

    @Test
    fun searchWithNoMatchShowsNoMatchesState() {
        search("zzz-nothing")
        compose.onNodeWithText("No items, short codes, or scanned values match \"zzz-nothing\".").assertExists()
        compose.onAllNodesWithText("Cordless Drill").assertCountEquals(0)
    }

    @Test
    fun detailShowsCoreFieldsLocationAndLabels() {
        openDrill()
        shown("A cordless drill")
        shown("Quantity: 3")
        shown("Short code: DR-1")
        shown("Location: Garage")
        shown("Labels: Power tools, Fragile")
    }

    @Test
    fun detailShowsWarrantySoldToAndPurchasedFromBlocks() {
        openDrill()
        shown("Warranty")
        shown("Holder: Jane Doe")
        shown("Provider: Acme Warranties")
        shown("Starts: 2024-01-01")
        shown("Expires: 2026-01-01")
        shown("Sold to")
        shown("Sale price (minor units): 5000")
        shown("Buyer: Alex Buyer")
        shown("Purchased from")
        shown("Purchase price (minor units): 12000")
        shown("Vendor: Hardware Store")
        shown("Order reference: PO-42")
    }

    @Test
    fun detailShowsIdentifiersAndEveryCustomFieldType() {
        openDrill()
        shown("Identifiers")
        shown("barcode: 0123456789012")
        shown("serial: SN-DRILL-77")
        shown("Custom fields")
        shown("Colour: Red")
        shown("Voltage: 18.5")
        shown("Cordless: Yes")
        shown("Bought: 2023-03-01")
    }

    @Test
    fun detailShowsStockHistory() {
        openDrill()
        shown("Stock history")
        shown("+5 → 5 (initial)")
        shown("-2 → 3 (loan)")
        shown("Note: lent to a neighbour")
    }

    @Test
    fun richItemLastStockRowIsReachableByScrolling() {
        compose.onNode(hasText("Server Rack")).performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText("Short code: RACK-9").fetchSemanticsNodes().isNotEmpty()
        }
        shown("Note: adjustment note 30")
        shown("+1 → 30 (adjust 30)")
        shown("rack-id-1: RACK-VAL-1")
    }

    @Test
    fun backFromDetailReturnsToList() {
        openDrill()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Garden Hose").assertExists()
        compose.onNodeWithText("Stepladder").assertExists()
    }

    @Test
    fun itemWithoutBlocksOmitsThem() {
        compose.onNodeWithText("Stepladder").performClick()
        compose.onNodeWithText("Back").assertExists()
        compose.onAllNodesWithText("Warranty").assertCountEquals(0)
        compose.onAllNodesWithText("Identifiers").assertCountEquals(0)
    }

    private fun shown(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun search(query: String) {
        compose.onNode(hasSetTextAction()).performTextInput(query)
        compose.waitForIdle()
    }

    private fun openDrill() {
        compose.onNode(hasText("Cordless Drill")).performClick()
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText("Location: Garage").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private suspend fun seedFixtures() {
        db.locationDao().upsert(LocationEntity("loc-1", 1L, "Garage", null, 1L, 1L, 1L))
        db.labelDao().upsert(LabelEntity("label-1", 1L, "Power tools", "#ffffff", 1L, 1L, 1L))
        db.labelDao().upsert(LabelEntity("label-2", 1L, "Fragile", "#ffffff", 1L, 1L, 1L))
        db.itemDao().upsertAll(
            listOf(
                item("item-1", "Cordless Drill", locationId = "loc-1", quantity = 3L, shortCode = "DR-1", description = "A cordless drill"),
                item("item-2", "Garden Hose", quantity = 1L, shortCode = "GH-2", description = null),
                item("item-4", "Server Rack", quantity = 30L, shortCode = "RACK-9", description = null),
                item("item-3", "Stepladder", quantity = null, shortCode = null, description = null),
            ),
        )
        db.itemLabelDao().upsert(ItemLabelEntity("il-1", 1L, "item-1", "label-1"))
        db.itemLabelDao().upsert(ItemLabelEntity("il-2", 1L, "item-1", "label-2"))
        db.warrantyBlockDao().upsert(
            WarrantyBlockEntity(
                "w-1", 1L, "item-1", false, "Jane Doe", "Acme Warranties",
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1), "Keep receipt", 1L, 1L, 1L,
            ),
        )
        db.soldToBlockDao().upsert(
            SoldToBlockEntity("s-1", 1L, "item-1", 5_000L, "Alex Buyer", LocalDate.of(2024, 6, 1), "Yard sale", 1L, 1L, 1L),
        )
        db.purchasedFromBlockDao().upsert(
            PurchasedFromBlockEntity(
                "p-1", 1L, "item-1", 12_000L, "Hardware Store", LocalDate.of(2023, 3, 1), "PO-42", "On sale", 1L, 1L, 1L,
            ),
        )
        db.itemIdentificationDao().upsert(identifier("id-1", "item-1", "barcode", "0123456789012"))
        db.itemIdentificationDao().upsert(identifier("id-2", "item-1", "serial", "SN-DRILL-77"))
        db.itemIdentificationDao().upsert(identifier("id-3", "item-2", "barcode", "9998887776665"))
        db.itemCustomFieldDao().upsert(customField("cf-1", "Colour", "text", textValue = "Red"))
        db.itemCustomFieldDao().upsert(customField("cf-2", "Voltage", "number", numberValue = BigDecimal("18.5")))
        db.itemCustomFieldDao().upsert(customField("cf-3", "Cordless", "boolean", boolValue = true))
        db.itemCustomFieldDao().upsert(customField("cf-4", "Bought", "date", dateValue = LocalDate.of(2023, 3, 1)))
        for (n in 1..RICH_ROWS) {
            db.itemIdentificationDao().upsert(identifier("rid-$n", "item-4", "rack-id-$n", "RACK-VAL-$n"))
            db.itemCustomFieldDao().upsert(customField("rcf-$n", "Field $n", "text", textValue = "Value $n", itemId = "item-4"))
            db.stockAdjustmentDao().upsert(
                StockAdjustmentEntity("rsa-$n", 1L, "item-4", 1L, n.toLong(), "adjust $n", "adjustment note $n", n.toLong(), n.toLong(), 1L),
            )
        }
        db.stockAdjustmentDao().upsert(stock("sa-1", 5L, 5L, "initial", null, 1L))
        db.stockAdjustmentDao().upsert(stock("sa-2", -2L, 3L, "loan", "lent to a neighbour", 2L))
    }

    private fun item(id: String, name: String, locationId: String? = null, quantity: Long?, shortCode: String?, description: String?) =
        ItemEntity(id, 1L, name, description, locationId, quantity, shortCode, 1L, 1L, 1L)

    private fun identifier(id: String, itemId: String, kind: String, value: String) =
        ItemIdentificationEntity(id, 1L, itemId, kind, value, 1L, 1L, 1L)

    private fun customField(
        id: String,
        name: String,
        type: String,
        textValue: String? = null,
        numberValue: BigDecimal? = null,
        boolValue: Boolean? = null,
        dateValue: LocalDate? = null,
        itemId: String = "item-1",
    ) = ItemCustomFieldEntity(
        id, 1L, itemId, name, type, null, textValue, numberValue, boolValue, dateValue, 1L, 1L, 1L,
    )

    private fun stock(id: String, delta: Long, resulting: Long, reason: String?, note: String?, at: Long) =
        StockAdjustmentEntity(id, 1L, "item-1", delta, resulting, reason, note, at, at, 1L)

    private companion object {
        const val WAIT_MILLIS = 10_000L
        const val RICH_ROWS = 30
    }
}
