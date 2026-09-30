package dev.hho.android.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxOverlay
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemLabelEntity
import dev.hho.android.data.room.LabelEntity
import dev.hho.android.data.room.LocationEntity
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.PurchasedFromBlockEntity
import dev.hho.android.data.room.deleteHhoDatabaseFile
import dev.hho.android.data.room.fileHhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EditRepositoryTest {
    private lateinit var db: HhoDatabase
    private lateinit var repo: EditRepository
    private val syncCalls = mutableListOf<Pair<Boolean, Int>>()
    private var syncThrows = false

    private fun repoFor(database: HhoDatabase, onSync: suspend () -> Unit = {}): EditRepository {
        val ids = UuidV7Generator()
        return EditRepository(database, OutboxRepository(database, ids), ids) {
            syncCalls += database.openHelper.writableDatabase.inTransaction() to
                database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM outbox_mutation").use { it.moveToFirst(); it.getInt(0) }
            if (syncThrows) error("scheduler down")
        }
    }

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = inMemoryHhoDatabase()
        repo = repoFor(db)
        seed(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(d: HhoDatabase) {
        d.itemDao().upsert(ItemEntity("i1", 5, "Drill", "old", null, 10, "AB12", 1, 1, 4))
        d.locationDao().upsert(LocationEntity("loc1", 1, "Garage", null, 1, 1, 1))
        d.labelDao().upsert(LabelEntity("lb1", 1, "Tools", "#fff", 1, 1, 1))
    }

    private suspend fun rows(d: HhoDatabase = db) = d.outboxDao().getAllOrdered()

    private fun OutboxMutationEntity.mutation() = LocalMutation.from(this)

    private fun <T> EditResult<T>.ok(): T = (this as EditResult.Success<T>).value

    private fun <T> EditResult<T>.err(): EditError = (this as EditResult.Failure).error

    @Test
    fun createItem_writesMirrorAndOutbox_baseZero_uuidV7() = kotlinx.coroutines.test.runTest {
        val id = repo.createItem("  Saw ", "hand saw", "loc1").ok()
        assertEquals('7', id[14])
        val item = db.itemDao().getById(id)!!
        assertEquals("Saw", item.name)
        assertEquals("loc1", item.locationId)
        val m = rows().single().mutation()
        assertEquals(LocalMutation("item", id, "upsert", 0, m.fields), m)
        assertEquals(setOf("name", "description", "location_id"), m.fields.keys)
        assertEquals(1, syncCalls.size)
    }

    @Test
    fun editItem_usesMirrorVersionAsBase_neverSendsQuantity() = runTest {
        repo.editItem("i1", name = "Drill 2", description = "new").ok()
        assertEquals("Drill 2", db.itemDao().getById("i1")!!.name)
        val m = rows().single().mutation()
        assertEquals(4L, m.baseVersion)
        assertEquals(setOf("name", "description"), m.fields.keys)
        assertEquals(10L, db.itemDao().getById("i1")!!.quantity)
    }

    @Test
    fun moveItem_setsAndClearsLocation() = runTest {
        repo.moveItem("i1", "loc1").ok()
        assertEquals("loc1", db.itemDao().getById("i1")!!.locationId)
        repo.moveItem("i1", null).ok()
        assertNull(db.itemDao().getById("i1")!!.locationId)
        assertEquals(2, syncCalls.size)
        assertEquals(kotlinx.serialization.json.JsonNull, rows().single().mutation().fields["location_id"])
    }

    @Test
    fun adjustStock_derivesQuantity_andNeverEmitsItemQuantity() = runTest {
        val id = repo.adjustStock("i1", -3, reason = "used", note = "n").ok()
        assertEquals(10L, db.itemDao().getById("i1")!!.quantity)
        assertEquals(7L, OutboxOverlay.derivedQuantity(db, "i1"))
        val m = rows().single().mutation()
        assertEquals("stock_adjustment", m.entityType)
        assertEquals(id, m.entityId)
        assertFalse("quantity" in m.fields)
        assertEquals(JsonPrimitive(-3L), m.fields["delta"])
        assertEquals(setOf("item_id", "delta", "reason", "note"), m.fields.keys)
        assertEquals(-3L, db.stockAdjustmentDao().observeByItemId("i1").first().single().delta)
    }

    @Test
    fun attachAndDetachLabel() = runTest {
        val edge = repo.attachLabel("i1", "lb1").ok()
        assertEquals(listOf(ItemLabelEntity(edge, 0, "i1", "lb1")), db.itemLabelDao().observeByItemId("i1").first())
        assertEquals(edge, repo.attachLabel("i1", "lb1").ok())
        assertEquals(1, rows().size)
        assertEquals(1, syncCalls.size)

        repo.detachLabel("i1", "lb1").ok()
        assertTrue(db.itemLabelDao().observeByItemId("i1").first().isEmpty())
        val del = rows().last().mutation()
        assertEquals("delete", del.op)
        assertEquals(edge, del.entityId)
        assertEquals("delete addresses the edge by item_id/label_id (openapi SyncMutation.fields)", setOf("item_id", "label_id"), del.fields.keys)
        assertEquals(2, syncCalls.size)
        assertTrue(repo.detachLabel("i1", "lb1").err() is EditError.NotFound)
    }

    @Test
    fun addIdentification_writesBoth() = runTest {
        val id = repo.addIdentification("i1", "serial", " SN-1 ").ok()
        assertEquals("SN-1", db.itemIdentificationDao().observeByItemId("i1").first().single().value)
        val m = rows().single().mutation()
        assertEquals(id, m.entityId)
        assertEquals(0L, m.baseVersion)
        assertEquals(setOf("item_id", "kind", "value"), m.fields.keys)
    }

    @Test
    fun stampPurchase_createsThenUpdatesWithBlockVersion() = runTest {
        val id = repo.stampPurchase("i1", vendor = "Acme", purchasedOn = LocalDate.of(2026, 1, 2), purchasePriceMinor = 1999).ok()
        assertEquals(0L, rows().single().mutation().baseVersion)
        val block = db.purchasedFromBlockDao().getByItemId("i1")!!
        assertEquals(LocalDate.of(2026, 1, 2), block.purchasedOn)

        db.outboxDao().getAllOrdered().forEach { db.outboxDao().deleteBySeq(it.seq) }
        db.purchasedFromBlockDao().upsert(block.copy(version = 6))
        assertEquals(id, repo.stampPurchase("i1", notes = "gift").ok())
        val m = rows().single().mutation()
        assertEquals(6L, m.baseVersion)
        assertEquals(setOf("item_id", "notes"), m.fields.keys)
        assertEquals("Acme", db.purchasedFromBlockDao().getByItemId("i1")!!.vendor)
        assertEquals("gift", db.purchasedFromBlockDao().getByItemId("i1")!!.notes)
    }

    @Test
    fun validation_failsBeforeAnyWrite_noSync() = runTest {
        val before = db.itemDao().getById("i1")
        assertTrue(repo.createItem("  ").err() is EditError.Invalid)
        assertTrue(repo.createItem("x", locationId = "nope").err() is EditError.NotFound)
        assertTrue(repo.editItem("i1", name = " ").err() is EditError.Invalid)
        assertTrue(repo.editItem("i1").err() is EditError.Invalid)
        assertTrue(repo.editItem("ghost", name = "x").err() is EditError.NotFound)
        assertTrue(repo.moveItem("i1", "nope").err() is EditError.NotFound)
        assertTrue(repo.adjustStock("i1", 0).err() is EditError.Invalid)
        assertTrue(repo.adjustStock("ghost", 1).err() is EditError.NotFound)
        assertTrue(repo.attachLabel("i1", "nope").err() is EditError.NotFound)
        assertTrue(repo.attachLabel("ghost", "lb1").err() is EditError.NotFound)
        assertTrue(repo.addIdentification("i1", "bogus", "v").err() is EditError.Invalid)
        assertTrue(repo.addIdentification("i1", "serial", " ").err() is EditError.Invalid)
        assertTrue(repo.stampPurchase("i1").err() is EditError.Invalid)
        assertTrue(repo.stampPurchase("ghost", vendor = "v").err() is EditError.NotFound)
        assertTrue(rows().isEmpty())
        assertEquals(before, db.itemDao().getById("i1"))
        assertEquals(1, db.itemDao().observeAll().first().size)
        assertTrue(syncCalls.isEmpty())
    }

    @Test
    fun failingEnqueue_rollsBackMirror_everyOperation() = runTest {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_enqueue BEFORE INSERT ON outbox_mutation BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val results = listOf(
            repo.createItem("Saw"),
            repo.editItem("i1", name = "Changed"),
            repo.moveItem("i1", "loc1"),
            repo.adjustStock("i1", 2),
            repo.attachLabel("i1", "lb1"),
            repo.addIdentification("i1", "serial", "S"),
            repo.stampPurchase("i1", vendor = "V"),
        )
        results.forEach { assertTrue(it.err() is EditError.StorageFailed) }
        assertEquals(listOf(ItemEntity("i1", 5, "Drill", "old", null, 10, "AB12", 1, 1, 4)), db.itemDao().observeAll().first())
        assertTrue(db.stockAdjustmentDao().observeByItemId("i1").first().isEmpty())
        assertTrue(db.itemLabelDao().observeByItemId("i1").first().isEmpty())
        assertTrue(db.itemIdentificationDao().observeByItemId("i1").first().isEmpty())
        assertNull(db.purchasedFromBlockDao().getByItemId("i1"))
        assertTrue(rows().isEmpty())
        assertTrue(syncCalls.isEmpty())
    }

    @Test
    fun observersSeeOptimisticChange() = runTest {
        val seenInitial = CompletableDeferred<Unit>()
        val renamed = withContext(Dispatchers.Default) {
            val watcher = async {
                db.itemDao().observeById("i1").onEach { seenInitial.complete(Unit) }.first { it?.name == "Renamed" }
            }
            withTimeout(5_000) { seenInitial.await() }
            repo.editItem("i1", name = "Renamed").ok()
            withTimeout(5_000) { watcher.await() }
        }
        assertEquals("Renamed", renamed!!.name)
    }

    @Test
    fun syncNow_calledOnceAfterCommit_outsideTransaction() = runTest {
        repo.adjustStock("i1", 1).ok()
        assertEquals(listOf(false to 1), syncCalls)
    }

    @Test
    fun schedulerFailure_doesNotFailTheCommittedEdit() = runTest {
        syncThrows = true
        repo.editItem("i1", name = "Kept").ok()
        assertEquals("Kept", db.itemDao().getById("i1")!!.name)
        assertEquals(1, rows().size)
    }

    @Test
    fun killBetweenCommitAndSync_losesNothing() = runTest {
        val name = "edit-repo-kill"
        deleteHhoDatabaseFile(name)
        var file = fileHhoDatabase(name)
        try {
            seed(file)
            syncThrows = true
            val killed = repoFor(file)
            val adjId = killed.adjustStock("i1", 4).ok()
            val newId = killed.createItem("Persisted").ok()
            file.close()

            file = fileHhoDatabase(name)
            assertEquals(listOf("stock_adjustment", "item"), rows(file).map { it.entityType })
            assertNotNull(file.stockAdjustmentDao().observeByItemId("i1").first().firstOrNull { it.id == adjId })
            assertEquals("Persisted", file.itemDao().getById(newId)!!.name)
            assertEquals(14L, OutboxOverlay.derivedQuantity(file, "i1"))
        } finally {
            file.close()
            deleteHhoDatabaseFile(name)
        }
    }

    @Test
    fun batch_commitsAllEdits_syncsOnceAfterCommit() = runTest {
        val r = repo.batch {
            adjustStock("i1", 2, "r", null)
            adjustStock("i1", 3, "r", null)
            EditResult.Success("done")
        }
        assertEquals("done", r.ok())
        assertEquals(2, rows().size)
        assertEquals(15L, OutboxOverlay.derivedQuantity(db, "i1"))
        assertEquals(listOf(false to 2), syncCalls)
    }

    @Test
    fun batch_failureRollsBackEarlierEdits_noSync() = runTest {
        val r = repo.batch {
            adjustStock("i1", 2, "r", null)
            adjustStock("ghost", 1, "r", null)
        }
        assertTrue(r.err() is EditError.NotFound)
        assertTrue(rows().isEmpty())
        assertTrue(db.stockAdjustmentDao().observeByItemId("i1").first().isEmpty())
        assertTrue(syncCalls.isEmpty())
    }

    @Test
    fun batch_thatWritesNothing_doesNotSync() = runTest {
        repo.batch { EditResult.Success(Unit) }.ok()
        assertTrue(syncCalls.isEmpty())
    }
}
