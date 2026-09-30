package dev.hho.android.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.domain.EditResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TwoDeviceConcurrentEditTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(180)

    @get:Rule(order = 1)
    val server = RealServerHarness()

    private lateinit var a: TwoDeviceSupportDevice
    private lateinit var b: TwoDeviceSupportDevice

    @Before
    fun setUp() {
        a = TwoDeviceSupportDevice(server, "device-a")
        b = TwoDeviceSupportDevice(server, "device-b")
    }

    @After
    fun tearDown() {
        a.close()
        b.close()
    }

    private fun serverField(id: String, field: String) =
        server.readItem(id)!!.getValue(field).jsonPrimitive.content

    private fun <T> EditResult<T>.ok() = assertTrue("edit failed: $this", this is EditResult.Success)

    @Test
    fun differentFieldEdits_bothSurvive_andBothMirrorsConverge() = runBlocking {
        val id = server.seedItem("Original", quantity = 1, description = "orig-desc").getValue("id").jsonPrimitive.content
        a.sync(); b.sync()

        a.edits.editItem(id, name = "A-name").ok()
        b.edits.editItem(id, description = "B-desc").ok()
        a.sync(); b.sync(); a.sync()

        assertEquals("A-name", serverField(id, "name"))
        assertEquals("B-desc", serverField(id, "description"))
        for (d in listOf(a, b)) {
            val row = checkNotNull(d.db.itemDao().getById(id))
            assertEquals("${d.name} name", "A-name", row.name)
            assertEquals("${d.name} description", "B-desc", row.description)
            assertTrue("${d.name} conflicts", d.db.conflictRecordDao().getAll().isEmpty())
            assertEquals("${d.name} outbox", 0, d.pending())
        }
    }

    @Test
    fun sameFieldEdit_serverWins_loserGetsConflictRecord_andCorrectedMirror() = runBlocking {
        val id = server.seedItem("Original", quantity = 1).getValue("id").jsonPrimitive.content
        a.sync(); b.sync()

        a.edits.editItem(id, name = "A-name").ok()
        b.edits.editItem(id, name = "B-name").ok()
        assertEquals("B's optimistic apply", "B-name", b.db.itemDao().getById(id)?.name)

        a.sync()
        b.sync()

        assertEquals("A-name", serverField(id, "name"))
        val rows = b.db.conflictRecordDao().getAll().filter { it.entityId == id && it.fieldName == "name" }
        assertEquals(1, rows.size)
        assertEquals("\"B-name\"", rows.single().losingValueJson)
        assertEquals("\"A-name\"", rows.single().serverValueJson)
        assertEquals("A-name", b.db.itemDao().getById(id)?.name)
        assertEquals(0, b.pending())
        a.sync()
        assertEquals("A-name", a.db.itemDao().getById(id)?.name)
        assertEquals(0, a.pending())
        val bMutations = rows.map { it.mutationId }.toSet()
        assertTrue(a.db.conflictRecordDao().getAll().all { it.mutationId in bMutations })
    }

    @Test
    fun stockAdjustmentsFromBothDevices_bothApply_noDoubleCount() = runBlocking {
        val id = server.seedItem("Screws", quantity = 10).getValue("id").jsonPrimitive.content
        a.sync(); b.sync()

        a.edits.adjustStock(id, 2).ok()
        b.edits.adjustStock(id, -1).ok()
        a.sync(); b.sync(); a.sync(); b.sync()

        assertEquals("11", serverField(id, "quantity"))
        assertEquals(11L, a.db.itemDao().getById(id)?.quantity)
        assertEquals(11L, b.db.itemDao().getById(id)?.quantity)
        assertTrue(a.db.conflictRecordDao().getAll().isEmpty())
        assertTrue(b.db.conflictRecordDao().getAll().isEmpty())
    }

    @Test
    fun moveOnOneDevice_andLabelAttachOnAnother_bothSurvive() = runBlocking {
        val seeder = ReferenceDataSeeder(server)
        val loc = seeder.createLocation("Garage")
        val label = seeder.createLabel("Tools")
        val id = server.seedItem("Drill", quantity = 1).getValue("id").jsonPrimitive.content
        a.sync(); b.sync()

        a.edits.moveItem(id, loc).ok()
        b.edits.attachLabel(id, label).ok()
        a.sync(); b.sync(); a.sync()

        val item = server.readItem(id)!!
        assertEquals(loc, item.getValue("location_id").jsonPrimitive.content)
        for (d in listOf(a, b)) {
            assertEquals("${d.name} location", loc, d.db.itemDao().getById(id)?.locationId)
            val labels = d.db.itemLabelDao().observeByItemId(id).first()
            assertEquals("${d.name} labels", listOf(label), labels.map { it.labelId })
            assertTrue(d.db.conflictRecordDao().getAll().isEmpty())
        }
    }
}
