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

@Entity(
    tableName = "item_label",
    indices = [Index("item_id"), Index("label_id")],
)
data class ItemLabelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "label_id") val labelId: String,
)

fun SyncChange.ItemLabelChange.toEntity(): ItemLabelEntity =
    ItemLabelEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        itemId = data.itemId,
        labelId = data.labelId,
    )

@Dao
interface ItemLabelDao {

    @Upsert
    suspend fun upsert(entity: ItemLabelEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ItemLabelEntity>)

    @Query("DELETE FROM item_label WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM item_label WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM item_label")
    suspend fun clearAll()

    @Query("SELECT * FROM item_label WHERE item_id = :itemId")
    fun observeByItemId(itemId: String): Flow<List<ItemLabelEntity>>

    @Query("SELECT * FROM item_label WHERE label_id = :labelId")
    fun observeByLabelId(labelId: String): Flow<List<ItemLabelEntity>>
}
