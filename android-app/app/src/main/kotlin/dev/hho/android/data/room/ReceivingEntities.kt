package dev.hho.android.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(
    tableName = "receiving_session",
    indices = [Index(value = ["status"])],
)
data class ReceivingSessionEntity(
    @PrimaryKey val id: String,
    val vendor: String,
    @ColumnInfo(name = "order_reference") val orderReference: String? = null,
    @ColumnInfo(name = "purchased_on") val purchasedOn: LocalDate? = null,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)

object ReceivingStatus {
    const val OPEN: String = "OPEN"
    const val COMPLETED: String = "COMPLETED"
    const val CANCELLED: String = "CANCELLED"
}

@Entity(
    tableName = "receiving_line",
    primaryKeys = ["session_id", "item_id"],
)
data class ReceivingLineEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "expected_qty") val expectedQty: Int? = null,
    @ColumnInfo(name = "received_qty") val receivedQty: Int = 0,
)

@Dao
interface ReceivingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: ReceivingSessionEntity)

    @Update
    suspend fun updateSession(session: ReceivingSessionEntity)

    @Query("SELECT * FROM receiving_session WHERE id = :id")
    suspend fun getSession(id: String): ReceivingSessionEntity?

    @Query("SELECT * FROM receiving_session WHERE status = :status ORDER BY created_at DESC")
    fun observeByStatus(status: String): Flow<List<ReceivingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLine(line: ReceivingLineEntity)

    @Query("SELECT * FROM receiving_line WHERE session_id = :sessionId ORDER BY item_id ASC")
    suspend fun getLines(sessionId: String): List<ReceivingLineEntity>

    @Query("SELECT * FROM receiving_line WHERE session_id = :sessionId ORDER BY item_id ASC")
    fun observeLines(sessionId: String): Flow<List<ReceivingLineEntity>>

    @Query(
        "UPDATE receiving_line SET received_qty = received_qty + :delta " +
            "WHERE session_id = :sessionId AND item_id = :itemId",
    )
    suspend fun addReceived(sessionId: String, itemId: String, delta: Int): Int

    @Query(
        "UPDATE receiving_line SET received_qty = MAX(0, received_qty + :delta) " +
            "WHERE session_id = :sessionId AND item_id = :itemId " +
            "AND EXISTS (SELECT 1 FROM receiving_session WHERE id = :sessionId AND status = 'OPEN')",
    )
    suspend fun adjustReceived(sessionId: String, itemId: String, delta: Int): Int

    @Query("SELECT * FROM receiving_session WHERE id = :id")
    fun observeSession(id: String): Flow<ReceivingSessionEntity?>

    @Query("SELECT * FROM receiving_line WHERE session_id = :sessionId AND item_id = :itemId")
    suspend fun getLine(sessionId: String, itemId: String): ReceivingLineEntity?

    @Query(
        "UPDATE receiving_line SET received_qty = :qty " +
            "WHERE session_id = :sessionId AND item_id = :itemId",
    )
    suspend fun setReceived(sessionId: String, itemId: String, qty: Int): Int

    @Query("DELETE FROM receiving_line WHERE session_id = :sessionId")
    suspend fun deleteLines(sessionId: String)

    @Query("DELETE FROM receiving_session WHERE id = :id")
    suspend fun deleteSession(id: String)
}
