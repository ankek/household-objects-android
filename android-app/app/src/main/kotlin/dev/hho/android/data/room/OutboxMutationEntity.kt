package dev.hho.android.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "outbox_mutation",
    indices = [
        Index(value = ["mutation_id"], unique = true),
        Index(value = ["state", "seq"]),
        Index(value = ["entity_type", "entity_id"]),
    ],
)
data class OutboxMutationEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val op: String,
    @ColumnInfo(name = "base_version") val baseVersion: Long,
    @ColumnInfo(name = "fields_json") val fieldsJson: String,
    val state: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

object OutboxState {
    const val PENDING: String = "PENDING"
    const val IN_FLIGHT: String = "IN_FLIGHT"
    const val HELD: String = "HELD"
    const val FAILED: String = "FAILED"
}

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(mutation: OutboxMutationEntity): Long

    @Query("SELECT * FROM outbox_mutation ORDER BY seq ASC")
    suspend fun getAllOrdered(): List<OutboxMutationEntity>

    @Query("SELECT * FROM outbox_mutation WHERE state = :state ORDER BY seq ASC LIMIT :limit")
    suspend fun getByState(state: String, limit: Int = Int.MAX_VALUE): List<OutboxMutationEntity>

    @Query("SELECT * FROM outbox_mutation WHERE entity_type = :entityType AND entity_id = :entityId ORDER BY seq ASC")
    suspend fun getForEntity(entityType: String, entityId: String): List<OutboxMutationEntity>

    @Query("SELECT * FROM outbox_mutation WHERE mutation_id = :mutationId")
    suspend fun getByMutationId(mutationId: String): OutboxMutationEntity?

    @Query("UPDATE outbox_mutation SET state = :state WHERE seq = :seq")
    suspend fun updateState(seq: Long, state: String)

    @Query("DELETE FROM outbox_mutation WHERE seq = :seq")
    suspend fun deleteBySeq(seq: Long)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM outbox_mutation WHERE entity_type = 'item' AND entity_id = :itemId " +
            "AND op = 'upsert' AND base_version = 0 AND state IN ('PENDING', 'IN_FLIGHT', 'HELD'))",
    )
    suspend fun hasPendingCreate(itemId: String): Boolean

    @Query("SELECT * FROM outbox_mutation WHERE state IN ('PENDING', 'IN_FLIGHT', 'HELD') ORDER BY seq ASC")
    suspend fun getLive(): List<OutboxMutationEntity>

    @Query(
        "SELECT * FROM outbox_mutation WHERE entity_type = :entityType AND entity_id = :entityId " +
            "AND state != 'FAILED' ORDER BY seq ASC",
    )
    suspend fun getLiveForEntity(entityType: String, entityId: String): List<OutboxMutationEntity>

    @Query("UPDATE outbox_mutation SET fields_json = :fieldsJson WHERE seq = :seq")
    suspend fun updateFields(seq: Long, fieldsJson: String)

    @Query("UPDATE outbox_mutation SET state = :state, base_version = :baseVersion WHERE seq = :seq")
    suspend fun updateStateAndBase(seq: Long, state: String, baseVersion: Long)

    @Query(
        "UPDATE outbox_mutation SET state = 'IN_FLIGHT', last_attempt_at = :now " +
            "WHERE mutation_id IN (:mutationIds) AND state = 'PENDING'",
    )
    suspend fun markInFlight(mutationIds: List<String>, now: Long)

    @Query("UPDATE outbox_mutation SET state = 'PENDING' WHERE state = 'IN_FLIGHT'")
    suspend fun resetInFlightToPending(): Int

    @Query("UPDATE outbox_mutation SET state = 'FAILED', last_error = :error WHERE mutation_id = :mutationId")
    suspend fun markFailed(mutationId: String, error: String): Int

    @Query(
        "UPDATE outbox_mutation SET attempt_count = attempt_count + 1, last_error = :error, " +
            "last_attempt_at = :now WHERE mutation_id = :mutationId",
    )
    suspend fun recordAttempt(mutationId: String, error: String?, now: Long): Int

    @Query("SELECT COUNT(*) FROM outbox_mutation WHERE state IN (:states)")
    fun observeCount(states: List<String>): Flow<Int>

    @Query("SELECT * FROM outbox_mutation WHERE state = 'FAILED' ORDER BY seq ASC")
    fun observeFailed(): Flow<List<OutboxMutationEntity>>
}
