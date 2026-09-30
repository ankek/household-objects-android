package dev.hho.android.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import dev.hho.android.data.apiclient.SyncChange
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "stock_adjustment", indices = [Index("item_id")])
data class StockAdjustmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    @ColumnInfo(name = "item_id") val itemId: String?,
    val delta: Long,
    @ColumnInfo(name = "resulting_quantity") val resultingQuantity: Long,
    val reason: String?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    val version: Long?,
)

fun SyncChange.StockAdjustmentChange.toEntity(): StockAdjustmentEntity =
    StockAdjustmentEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        itemId = data.itemId,
        delta = data.delta,
        resultingQuantity = data.resultingQuantity,
        reason = data.reason,
        note = data.note,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface StockAdjustmentDao {

    @Upsert
    suspend fun upsert(entity: StockAdjustmentEntity)

    @Upsert
    suspend fun upsertAll(entities: List<StockAdjustmentEntity>)

    @Query("DELETE FROM stock_adjustment WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM stock_adjustment WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM stock_adjustment")
    suspend fun clearAll()

    @Query("SELECT * FROM stock_adjustment WHERE item_id = :itemId ORDER BY created_at ASC")
    fun observeByItemId(itemId: String): Flow<List<StockAdjustmentEntity>>
}
