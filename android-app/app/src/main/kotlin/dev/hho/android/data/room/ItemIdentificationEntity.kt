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
    tableName = "item_identification",
    indices = [Index("item_id"), Index("value")],
)
data class ItemIdentificationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    @ColumnInfo(name = "item_id") val itemId: String?,
    val kind: String,
    val value: String,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    val version: Long?,
)

fun SyncChange.ItemIdentificationChange.toEntity(): ItemIdentificationEntity =
    ItemIdentificationEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        itemId = data.itemId,
        kind = data.kind.value,
        value = data.value,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface ItemIdentificationDao {

    @Upsert
    suspend fun upsert(entity: ItemIdentificationEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ItemIdentificationEntity>)

    @Query("DELETE FROM item_identification WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM item_identification WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM item_identification")
    suspend fun clearAll()

    @Query("SELECT * FROM item_identification WHERE item_id = :itemId")
    fun observeByItemId(itemId: String): Flow<List<ItemIdentificationEntity>>

    @Query("SELECT * FROM item_identification WHERE value = :value")
    suspend fun findByValue(value: String): List<ItemIdentificationEntity>
}
