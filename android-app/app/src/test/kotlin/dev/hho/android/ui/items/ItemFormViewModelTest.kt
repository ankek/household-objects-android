package dev.hho.android.ui.items

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.LocationEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.domain.EditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ItemFormViewModelTest {

    private lateinit var db: HhoDatabase
    private var syncCalls = 0
    private val viewModels = ViewModelTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        runBlocking {
            db.itemDao().upsert(ItemEntity("i1", 5, "Drill", "old", "loc-garage", 5, "AB12", 1, 1, 4))
            db.locationDao().upsertAll(
                listOf(
                    LocationEntity("loc-garage", 1, "Garage", null, 1, 1, 2),
                    LocationEntity("loc-shed", 1, "Shed", null, 1, 1, 1),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(): ItemFormViewModel {
        val ids = UuidV7Generator()
        val repo = EditRepository(db, OutboxRepository(db, ids), ids) { syncCalls++ }
        return viewModels.track(ItemFormViewModel(repo, db.itemDao(), db.locationDao()))
    }

    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) { while (!condition()) kotlinx.coroutines.delay(10) }
    }

    private fun outbox() = runBlocking { db.outboxDao().getAllOrdered() }

    private fun ItemFormViewModel.fill(name: String, description: String = "", location: String? = null) {
        onNameChange(name)
        onDescriptionChange(description)
        onLocationChange(location)
    }

    private fun str(m: LocalMutation, key: String) = (m.fields[key] as JsonPrimitive).content

    @Test
    fun createWithIdentifierAndLocationQueuesBothRowsInOneTransactionAndOneSync() {
        val vm = vm()
        vm.start(null)
        vm.fill("  Hammer ", " claw ", "loc-shed")
        vm.onIdentifierKindChange("serial")
        vm.onIdentifierValueChange(" SN-1 ")
        vm.submit()
        await { vm.state.value.savedItemId != null }

        val id = vm.state.value.savedItemId!!
        val rows = outbox()
        assertEquals(listOf("item", "item_identification"), rows.map { it.entityType })
        val item = LocalMutation.from(rows[0])
        assertEquals(id, rows[0].entityId)
        assertEquals(0L, item.baseVersion)
        assertEquals("Hammer", str(item, "name"))
        assertEquals("claw", str(item, "description"))
        assertEquals("loc-shed", str(item, "location_id"))
        val ident = LocalMutation.from(rows[1])
        assertEquals(0L, ident.baseVersion)
        assertEquals(id, str(ident, "item_id"))
        assertEquals("serial", str(ident, "kind"))
        assertEquals("SN-1", str(ident, "value"))
        assertEquals(1, syncCalls)
        assertEquals("Hammer", runBlocking { db.itemDao().getById(id) }!!.name)
    }

    @Test
    fun createWithNameOnlySendsNoDescriptionLocationOrIdentification() {
        val vm = vm()
        vm.start(null)
        vm.fill("Saw")
        vm.submit()
        await { vm.state.value.savedItemId != null }

        val row = outbox().single()
        assertEquals("item", row.entityType)
        assertEquals(setOf("name"), LocalMutation.from(row).fields.keys)
        assertEquals(1, syncCalls)
    }

    @Test
    fun failingIdentificationInsertRollsBackTheItemCreateToo() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_ident BEFORE INSERT ON item_identification BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val vm = vm()
        vm.start(null)
        vm.fill("Saw")
        vm.onIdentifierValueChange("X1")
        vm.submit()
        await { vm.state.value.error != null }

        assertEquals("Couldn't save the item. Please try again.", vm.state.value.error)
        assertNull(vm.state.value.savedItemId)
        assertTrue(outbox().isEmpty())
        assertEquals(0, syncCalls)
        assertEquals(1, runBlocking { db.itemDao().observeAll().first().size })
    }

    @Test
    fun blankNameIsRejectedInlineAndWritesNothing() {
        val vm = vm()
        vm.start(null)
        vm.fill("   ")
        vm.onIdentifierValueChange("X1")
        vm.submit()

        assertEquals("Name is required.", vm.state.value.nameError)
        assertTrue(!vm.state.value.submitting)
        assertTrue(outbox().isEmpty())
        assertEquals(0, syncCalls)
        vm.onNameChange("A")
        assertNull(vm.state.value.nameError)
    }

    @Test
    fun invalidIdentifierKindIsRejectedAndWritesNothing() {
        val vm = vm()
        vm.start(null)
        vm.fill("Saw")
        vm.onIdentifierKindChange("isbn")
        vm.onIdentifierValueChange("X1")
        vm.submit()

        assertNotNull(vm.state.value.identifierError)
        assertTrue(outbox().isEmpty())
        assertEquals(0, syncCalls)
    }

    @Test
    fun everyOfferedIdentificationKindIsAcceptedByTheRepository() {
        ITEM_FORM_IDENTIFICATION_KINDS.forEach { kind ->
            val vm = vm()
            vm.start(null)
            vm.fill("Item $kind")
            vm.onIdentifierKindChange(kind)
            vm.onIdentifierValueChange("v-$kind")
            vm.submit()
            await { vm.state.value.savedItemId != null }
        }
        assertEquals(ITEM_FORM_IDENTIFICATION_KINDS.size * 2, outbox().size)
    }

    @Test
    fun prefillPopulatesCreateStateAndIsSubmittedAsIdentification() {
        val vm = vm()
        vm.start(null, "serial", "SCAN-9")
        assertEquals("serial", vm.state.value.identifierKind)
        assertEquals("SCAN-9", vm.state.value.identifierValue)
        vm.onNameChange("Scanned thing")
        vm.submit()
        await { vm.state.value.savedItemId != null }
        val ident = LocalMutation.from(outbox().last())
        assertEquals("SCAN-9", str(ident, "value"))
        assertEquals("serial", str(ident, "kind"))
    }

    @Test
    fun prefillWithUnknownOrMissingKindFallsBack() {
        val a = vm().also { it.start(null, "weird", "V") }
        assertEquals("other", a.state.value.identifierKind)
        val b = vm().also { it.start(null, null, "V") }
        assertEquals(ITEM_FORM_DEFAULT_KIND, b.state.value.identifierKind)
    }

    @Test
    fun startIsIdempotentSoRecompositionKeepsUserInput() {
        val vm = vm()
        vm.start(null, "serial", "V")
        vm.onNameChange("Typed")
        vm.start(null, "model", "OTHER")
        assertEquals("Typed", vm.state.value.name)
        assertEquals("V", vm.state.value.identifierValue)
    }

    @Test
    fun editLoadsItemAndSendsOnlyChangedFieldsAtMirrorVersion() {
        val vm = vm()
        vm.start("i1")
        await { !vm.state.value.loading }
        assertEquals("Drill", vm.state.value.name)
        assertEquals("old", vm.state.value.description)
        assertEquals("loc-garage", vm.state.value.locationId)

        vm.onDescriptionChange("new text")
        vm.submit()
        await { vm.state.value.savedItemId != null }

        assertEquals("i1", vm.state.value.savedItemId)
        val row = outbox().single()
        val m = LocalMutation.from(row)
        assertEquals("i1", row.entityId)
        assertEquals(4L, m.baseVersion)
        assertEquals(setOf("description"), m.fields.keys)
        assertEquals("new text", str(m, "description"))
        assertEquals(1, syncCalls)
    }

    @Test
    fun editNameAndLocationChangeIsOneMutationAndClearingDescriptionSendsEmpty() {
        val vm = vm()
        vm.start("i1")
        await { !vm.state.value.loading }
        vm.onNameChange("Drill 2")
        vm.onDescriptionChange("")
        vm.onLocationChange(null)
        vm.submit()
        await { vm.state.value.savedItemId != null }

        val m = LocalMutation.from(outbox().single())
        assertEquals(4L, m.baseVersion)
        assertEquals("Drill 2", str(m, "name"))
        assertEquals("", str(m, "description"))
        assertEquals(JsonNull, m.fields["location_id"])
        assertEquals(1, syncCalls)
    }

    @Test
    fun editWithNoChangesWritesNothingAndStillCompletes() {
        val vm = vm()
        vm.start("i1")
        await { !vm.state.value.loading }
        vm.submit()
        await { vm.state.value.savedItemId != null }
        assertTrue(outbox().isEmpty())
        assertEquals(0, syncCalls)
    }

    @Test
    fun editWithBlankNameIsRejected() {
        val vm = vm()
        vm.start("i1")
        await { !vm.state.value.loading }
        vm.onNameChange(" ")
        vm.submit()
        assertEquals("Name is required.", vm.state.value.nameError)
        assertTrue(outbox().isEmpty())
    }

    @Test
    fun editOfMissingItemReportsNotFound() {
        val vm = vm()
        vm.start("ghost")
        await { vm.state.value.notFound }
        vm.submit()
        assertTrue(outbox().isEmpty())
    }

    @Test
    fun doubleSubmitCreatesOneItem() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val vm = vm()
        vm.start(null)
        vm.fill("Saw")
        vm.submit()
        assertTrue(vm.state.value.submitting)
        vm.submit()
        await {
            dispatcher.scheduler.advanceUntilIdle()
            vm.state.value.savedItemId != null
        }
        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, outbox().size)
        assertEquals(1, syncCalls)
    }
}
