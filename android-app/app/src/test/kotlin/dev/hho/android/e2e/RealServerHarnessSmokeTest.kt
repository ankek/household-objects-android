package dev.hho.android.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.sync.testSyncEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RealServerHarnessSmokeTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(90)

    @get:Rule(order = 1)
    val server = RealServerHarness()

    private lateinit var db: HhoDatabase

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedRoundTripsThroughIndependentReadback() {
        val seeded = server.seedItem("Cordless drill", quantity = 3)
        val id = seeded.getValue("id").jsonPrimitive.content

        val read = checkNotNull(server.readItem(id))
        assertEquals("Cordless drill", read.getValue("name").jsonPrimitive.content)
        assertEquals("3", read.getValue("quantity").jsonPrimitive.content)
        assertTrue(server.listItems().any { it.getValue("id").jsonPrimitive.content == id })
        assertNull(server.readItem("does-not-exist"))
    }

    @Test
    fun syncPullMirrorsSeededItemIntoRoom() =
        runBlocking {
            val id = server.seedItem("Step ladder", quantity = 1).getValue("id").jsonPrimitive.content

            val engine = testSyncEngine(server.newApiClient(), db)
            val outcome = engine.sync("e2e-device-a")
            assertTrue("sync failed: ${outcome.exceptionOrNull()}", outcome.isSuccess)

            val mirrored = checkNotNull(db.itemDao().getById(id))
            assertEquals("Step ladder", mirrored.name)
            assertEquals(1L, mirrored.quantity)
        }
}
