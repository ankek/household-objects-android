package dev.hho.android.ui.items

import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.hho.android.MainActivity
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.testing.AirplaneModeRule
import dev.hho.android.testing.NetworkTripwire
import dev.hho.android.ui.theme.HhoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OfflineEditsInstrumentedTest {

    private val hiltRule = HiltAndroidRule(this)
    private val airplane = AirplaneModeRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(hiltRule).around(airplane).around(compose)

    @Inject lateinit var db: HhoDatabase

    @Inject lateinit var tripwire: NetworkTripwire

    private sealed interface Screen {
        data object List : Screen
        data class Detail(val id: String) : Screen
        data class Form(val id: String?, val visit: Int) : Screen
    }

    @Before
    fun seed() {
        hiltRule.inject()
        runBlocking {
            db.itemDao().upsertAll(
                listOf(ItemEntity(DRILL, 1L, "Cordless Drill", "A cordless drill", null, 3L, "DR-1", 1L, 1L, 1L)),
            )
        }
        compose.activityRule.scenario.onActivity { activity ->
            val host = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ComposeView
            host.setContent {
                HhoTheme(darkTheme = false) {
                    var screen by remember { mutableStateOf<Screen>(Screen.List) }
                    var visits by remember { mutableIntStateOf(0) }
                    when (val s = screen) {
                        Screen.List -> ItemListScreen(
                            onItemClick = { screen = Screen.Detail(it) },
                            onNewItem = { screen = Screen.Form(null, ++visits) },
                        )
                        is Screen.Detail -> ItemDetailScreen(
                            itemId = s.id,
                            onBack = { screen = Screen.List },
                            onEdit = { screen = Screen.Form(s.id, ++visits) },
                        )
                        is Screen.Form -> ItemFormScreen(
                            itemId = s.id,
                            viewModel = hiltViewModel(key = "form-${s.visit}"),
                            onSaved = { screen = Screen.Detail(it) },
                            onCancel = { screen = if (s.id == null) Screen.List else Screen.Detail(s.id) },
                        )
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
    fun editAdjustAndCreateWorkOfflineAndQueueDistinctMutations() {
        assertTrue("device is not offline", !airplane.hasInternet())

        compose.onNodeWithText("Cordless Drill").performClick()
        awaitText("Quantity: 3")
        compose.onNodeWithText("Edit").performClick()
        awaitText("Edit item")
        compose.onNode(hasSetTextAction() and hasText("Name (required)")).performTextReplacement("Impact Driver")
        compose.onNodeWithText("Save").performClick()
        awaitText("Impact Driver")
        shown("Impact Driver")

        compose.onNodeWithText("Adjust quantity").performClick()
        compose.onNodeWithText("+1").performClick()
        compose.onNodeWithText("+1").performClick()
        compose.onNodeWithText("Confirm").performClick()
        awaitText("Quantity: 6")
        shown("Quantity: 6")

        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("New item").performClick()
        awaitText("Identifier (optional)")
        compose.onNode(hasSetTextAction() and hasText("Name (required)")).performTextInput("Label Maker")
        compose.onNodeWithText("barcode").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Identifier value")).performScrollTo().performTextInput("4006381333931")
        compose.onNodeWithText("Create").performScrollTo().performClick()
        awaitText("Label Maker")
        shown("Label Maker")
        shown("barcode: 4006381333931")

        val rows = runBlocking { db.outboxDao().getAllOrdered() }
        assertEquals(4, rows.size)
        assertEquals(listOf("item", "stock_adjustment", "item", "item_identification"), rows.map { it.entityType })
        assertEquals(DRILL, rows[0].entityId)
        assertEquals("mutation_ids must be distinct", 4, rows.map { it.mutationId }.toSet().size)
        assertEquals(listOf("upsert", "upsert", "upsert", "upsert"), rows.map { it.op })
        assertEquals("creates are based on version 0", listOf(0L, 0L, 0L), listOf(rows[1], rows[2], rows[3]).map { it.baseVersion })
        assertTrue(rows[0].fieldsJson.contains("Impact Driver"))
        assertTrue(rows[1].fieldsJson.contains("\"delta\""))
        assertTrue(rows[3].fieldsJson.contains("4006381333931"))
        assertTrue("no item mutation may carry quantity", rows.filter { it.entityType == "item" }.none { it.fieldsJson.contains("quantity") })
    }

    private fun awaitText(text: String) {
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shown(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val DRILL = "item-1"
        const val WAIT_MILLIS = 10_000L
    }
}
