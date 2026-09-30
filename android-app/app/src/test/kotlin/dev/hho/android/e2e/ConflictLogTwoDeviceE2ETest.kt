package dev.hho.android.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.ConflictOrigin
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.sync.SyncRunOutcome
import dev.hho.android.data.sync.testSyncEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
class ConflictLogTwoDeviceE2ETest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(120)

    @get:Rule(order = 1)
    val server = RealServerHarness()

    private lateinit var dbA: HhoDatabase
    private lateinit var dbB: HhoDatabase

    @Before
    fun setUp() {
        dbA = inMemoryHhoDatabase()
        dbB = inMemoryHhoDatabase()
    }

    @After
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    @Test
    fun sameFieldConflictPushedByTwoDevices_landsInConflictRecordWithBothValues_afterOneRun() = runBlocking {
        val id = server.seedItem("Original", quantity = 1).getValue("id").jsonPrimitive.content
        val outboxA = OutboxRepository(dbA)
        val outboxB = OutboxRepository(dbB)
        val engineA = testSyncEngine(server.newApiClient(), dbA, outboxA, withConflictLog = true)
        val engineB = testSyncEngine(server.newApiClient(), dbB, outboxB, withConflictLog = true)

        assertTrue(engineA.sync("device-a").isSuccess)
        assertTrue(engineB.sync("device-b").isSuccess)
        outboxA.enqueue(rename(id, checkNotNull(dbA.itemDao().getById(id)?.version), "A-name"))
        val queuedB = outboxB.enqueue(rename(id, checkNotNull(dbB.itemDao().getById(id)?.version), "B-name"))

        val a = engineA.sync("device-a")
        assertTrue("A sync failed: ${a.exceptionOrNull()}", a.isSuccess)
        assertEquals("A-name", server.readItem(id)!!.getValue("name").jsonPrimitive.content)
        assertTrue(dbA.conflictRecordDao().getAll().isEmpty())

        val b = engineB.sync("device-b")
        assertEquals(SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.ConflictReconcile), b.getOrNull())

        val row = dbB.conflictRecordDao().getAll().single { it.entityId == id && it.fieldName == "name" }
        assertEquals(queuedB.mutationId, row.mutationId)
        assertEquals("\"B-name\"", row.losingValueJson)
        assertEquals("\"A-name\"", row.serverValueJson)
        assertEquals(ConflictOrigin.SERVER_LOG, row.origin)
        assertEquals("A-name", dbB.itemDao().getById(id)?.name)
    }

    private fun rename(id: String, base: Long, name: String) =
        LocalMutation("item", id, "upsert", base, buildJsonObject { put("name", JsonPrimitive(name)) })
}
