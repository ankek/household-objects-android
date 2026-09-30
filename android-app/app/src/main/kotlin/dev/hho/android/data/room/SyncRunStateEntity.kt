package dev.hho.android.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_run_state")
data class SyncRunStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long? = null,
    @ColumnInfo(name = "last_push_at") val lastPushAt: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "last_error_at") val lastErrorAt: Long? = null,
    @ColumnInfo(name = "conflict_cursor") val conflictCursor: String? = null,
    @ColumnInfo(name = "reconcile_pending", defaultValue = "0") val reconcilePending: Boolean = false,
) {
    companion object {
        const val SINGLETON_ID: Int = 0
    }
}

@Dao
interface SyncRunStateDao {

    @Query("SELECT * FROM sync_run_state WHERE id = ${SyncRunStateEntity.SINGLETON_ID}")
    suspend fun get(): SyncRunStateEntity?

    @Query("SELECT * FROM sync_run_state WHERE id = ${SyncRunStateEntity.SINGLETON_ID}")
    fun observe(): Flow<SyncRunStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncRunStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(state: SyncRunStateEntity)

    @Query("UPDATE sync_run_state SET last_run_at = :at, last_error = NULL, last_error_at = NULL WHERE id = ${SyncRunStateEntity.SINGLETON_ID}")
    suspend fun setRunSucceeded(at: Long)

    @Query(
        "UPDATE sync_run_state SET last_run_at = :at, last_error = :message, last_error_at = :at, " +
            "reconcile_pending = MAX(reconcile_pending, :owed) WHERE id = ${SyncRunStateEntity.SINGLETON_ID}",
    )
    suspend fun setRunFailed(at: Long, message: String, owed: Boolean)

    @Query("UPDATE sync_run_state SET last_push_at = :at WHERE id = ${SyncRunStateEntity.SINGLETON_ID}")
    suspend fun setLastPushAt(at: Long)

    @Query("UPDATE sync_run_state SET reconcile_pending = :owed WHERE id = ${SyncRunStateEntity.SINGLETON_ID}")
    suspend fun setReconcilePending(owed: Boolean)

    @Query("UPDATE sync_run_state SET conflict_cursor = :cursor WHERE id = ${SyncRunStateEntity.SINGLETON_ID}")
    suspend fun setConflictCursor(cursor: String?)
}
