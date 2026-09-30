package dev.hho.android.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "conflict_record",
    indices = [Index(value = ["mutation_id", "entity_id", "field_name"], unique = true)],
)
data class ConflictRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "field_name") val fieldName: String,
    @ColumnInfo(name = "losing_value_json") val losingValueJson: String? = null,
    @ColumnInfo(name = "server_value_json") val serverValueJson: String? = null,
    @ColumnInfo(name = "detected_at") val detectedAt: Long,
    val origin: String,
)

object ConflictOrigin {
    const val LOCAL_PUSH: String = "LOCAL_PUSH"
    const val SERVER_LOG: String = "SERVER_LOG"
}

@Dao
interface ConflictRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(record: ConflictRecordEntity): Long

    @Query(
        "SELECT * FROM conflict_record WHERE mutation_id = :mutationId " +
            "AND entity_id = :entityId AND field_name = :fieldName",
    )
    suspend fun find(mutationId: String, entityId: String, fieldName: String): ConflictRecordEntity?

    @Query(
        "UPDATE conflict_record SET losing_value_json = :losing, server_value_json = :server " +
            "WHERE mutation_id = :mutationId AND entity_id = :entityId AND field_name = :fieldName",
    )
    suspend fun enrich(mutationId: String, entityId: String, fieldName: String, losing: String?, server: String?): Int

    @Query(
        "UPDATE conflict_record SET losing_value_json = COALESCE(:losing, losing_value_json), " +
            "server_value_json = COALESCE(:server, server_value_json), detected_at = :detectedAt, " +
            "origin = '${ConflictOrigin.SERVER_LOG}' " +
            "WHERE mutation_id = :mutationId AND entity_id = :entityId AND field_name = :fieldName",
    )
    suspend fun mergeServerLog(
        mutationId: String,
        entityId: String,
        fieldName: String,
        losing: String?,
        server: String?,
        detectedAt: Long,
    ): Int

    @Query("SELECT * FROM conflict_record ORDER BY detected_at DESC, id DESC")
    fun observeAll(): Flow<List<ConflictRecordEntity>>

    @Query("SELECT * FROM conflict_record ORDER BY detected_at DESC, id DESC")
    suspend fun getAll(): List<ConflictRecordEntity>

    @Query("SELECT COUNT(*) FROM conflict_record")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT name FROM (" +
            "SELECT name FROM item WHERE :entityType = 'item' AND id = :entityId " +
            "UNION ALL SELECT item.name AS name FROM stock_adjustment JOIN item ON item.id = stock_adjustment.item_id " +
            "WHERE :entityType = 'stock_adjustment' AND stock_adjustment.id = :entityId " +
            "UNION ALL SELECT item.name AS name FROM purchased_from_block JOIN item ON item.id = purchased_from_block.item_id " +
            "WHERE :entityType = 'purchased_from_block' AND purchased_from_block.id = :entityId " +
            "UNION ALL SELECT item.name AS name FROM item_identification JOIN item ON item.id = item_identification.item_id " +
            "WHERE :entityType = 'item_identification' AND item_identification.id = :entityId " +
            "UNION ALL SELECT item.name AS name FROM item_label JOIN item ON item.id = item_label.item_id " +
            "WHERE :entityType = 'item_label' AND item_label.id = :entityId" +
            ") LIMIT 1",
    )
    suspend fun itemNameFor(entityType: String, entityId: String): String?
}
