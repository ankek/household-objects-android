package dev.hho.android.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OutboxMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HhoDatabase::class.java,
    )

    private fun indexNames(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Set<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$table'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    @Test
    fun migration1To2_keepsV1Data_andCreatesOutboxWithIndexes() {
        helper.createDatabase(DB, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO item (id, group_change_seq, name, version) VALUES ('item-1', 7, 'Hammer', 3)",
            )
            v1.execSQL("INSERT INTO sync_state (id, watermark, lastSyncedAt) VALUES (0, 42, 1000)")
        }

        val v2 = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        v2.query("SELECT name, version, group_change_seq FROM item WHERE id = 'item-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Hammer", c.getString(0))
            assertEquals(3, c.getInt(1))
            assertEquals(7, c.getInt(2))
        }
        v2.query("SELECT watermark FROM sync_state WHERE id = 0").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42L, c.getLong(0))
        }
        v2.query("SELECT COUNT(*) FROM outbox_mutation").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        assertEquals(
            setOf(
                "index_outbox_mutation_mutation_id",
                "index_outbox_mutation_state_seq",
                "index_outbox_mutation_entity_type_entity_id",
            ),
            indexNames(v2, "outbox_mutation"),
        )
        v2.close()
    }

    @Test
    fun migration1To2_createsEveryPhase4TableWithItsIndexes() {
        helper.createDatabase(DB, 1).close()
        val v2 = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        val expected = mapOf(
            "receiving_session" to setOf("index_receiving_session_status"),
            "receiving_line" to emptySet(),
            "photo_queue_entry" to setOf(
                "index_photo_queue_entry_state_created_at",
                "index_photo_queue_entry_item_id",
            ),
            "conflict_record" to setOf("index_conflict_record_mutation_id_entity_id_field_name"),
            "sync_run_state" to emptySet(),
        )
        for ((table, indexes) in expected) {
            v2.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue("$table missing", c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            assertEquals(table, indexes, indexNames(v2, table).filterNot { it.startsWith("sqlite_autoindex") }.toSet())
        }
        v2.execSQL("INSERT INTO sync_run_state (id) VALUES (0)")
        v2.query("SELECT reconcile_pending FROM sync_run_state WHERE id = 0").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        v2.execSQL(
            "INSERT INTO conflict_record (mutation_id, entity_type, entity_id, field_name, detected_at, origin) " +
                "VALUES ('m', 'item', 'e', 'name', 1, 'LOCAL_PUSH')",
        )
        val dup = runCatching {
            v2.execSQL(
                "INSERT INTO conflict_record (mutation_id, entity_type, entity_id, field_name, detected_at, origin) " +
                    "VALUES ('m', 'item', 'e', 'name', 2, 'SERVER_LOG')",
            )
        }
        assertTrue("unique (mutation_id, entity_id, field_name) not enforced", dup.exceptionOrNull() is android.database.sqlite.SQLiteConstraintException)
        v2.close()
    }

    @Test
    fun migratedDatabase_opensThroughRoom_andOutboxIsUsable() = runTest {
        helper.createDatabase(DB, 1).close()
        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).close()

        val room = androidx.room.Room.databaseBuilder(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            HhoDatabase::class.java,
            DB,
        ).addMigrations(MIGRATION_1_2).build()
        try {
            val seq = room.outboxDao().insert(sample("m-1", "e-1"))
            assertEquals(1L, seq)
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}

internal fun sample(mutationId: String, entityId: String, state: String = OutboxState.PENDING) =
    OutboxMutationEntity(
        mutationId = mutationId,
        entityType = "item",
        entityId = entityId,
        op = "upsert",
        baseVersion = 0,
        fieldsJson = "{}",
        state = state,
        createdAt = 1L,
    )
