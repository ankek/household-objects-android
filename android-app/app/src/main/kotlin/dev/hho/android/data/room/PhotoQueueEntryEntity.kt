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
    tableName = "photo_queue_entry",
    indices = [
        Index(value = ["state", "created_at"]),
        Index(value = ["item_id"]),
    ],
)
data class PhotoQueueEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    val sha256: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val category: String,
    val state: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

object PhotoState {
    const val QUEUED: String = "QUEUED"
    const val UPLOADING: String = "UPLOADING"
    const val WAITING_PARENT: String = "WAITING_PARENT"
    const val FAILED: String = "FAILED"
}

@Dao
interface PhotoQueueDao {

    @Insert
    suspend fun insert(entry: PhotoQueueEntryEntity)

    @Query("SELECT * FROM photo_queue_entry WHERE id = :id")
    suspend fun getById(id: String): PhotoQueueEntryEntity?

    @Query("SELECT * FROM photo_queue_entry ORDER BY created_at ASC, id ASC")
    suspend fun getAllOrdered(): List<PhotoQueueEntryEntity>

    @Query("SELECT * FROM photo_queue_entry WHERE state IN (:states) ORDER BY created_at ASC, id ASC")
    suspend fun getByStates(states: List<String>): List<PhotoQueueEntryEntity>

    @Query(
        "SELECT * FROM photo_queue_entry WHERE state IN (:states) " +
            "AND (next_attempt_at IS NULL OR next_attempt_at <= :now) ORDER BY created_at ASC, id ASC",
    )
    suspend fun getDue(states: List<String>, now: Long): List<PhotoQueueEntryEntity>

    @Query(
        "UPDATE photo_queue_entry SET state = :state, attempt_count = :attemptCount, " +
            "next_attempt_at = :nextAttemptAt, last_error = :lastError WHERE id = :id",
    )
    suspend fun updateProgress(id: String, state: String, attemptCount: Int, nextAttemptAt: Long?, lastError: String?)

    @Query("DELETE FROM photo_queue_entry WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM photo_queue_entry ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<PhotoQueueEntryEntity>>

    @Query("SELECT * FROM photo_queue_entry WHERE item_id = :itemId ORDER BY created_at ASC, id ASC")
    fun observeByItem(itemId: String): Flow<List<PhotoQueueEntryEntity>>

    @Query("UPDATE photo_queue_entry SET state = :to, next_attempt_at = NULL WHERE state = :from")
    suspend fun moveState(from: String, to: String): Int

    @Query("SELECT COUNT(*) FROM photo_queue_entry WHERE state IN (:states)")
    fun observeCount(states: List<String>): Flow<Int>

    @Query("SELECT * FROM photo_queue_entry WHERE state = 'FAILED' ORDER BY created_at ASC, id ASC")
    fun observeFailed(): Flow<List<PhotoQueueEntryEntity>>
}
