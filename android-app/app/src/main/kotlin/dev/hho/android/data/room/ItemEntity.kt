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

@Entity(tableName = "item", indices = [Index("location_id")])
data class ItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "location_id") val locationId: String?,
    val quantity: Long?,
    @ColumnInfo(name = "short_code") val shortCode: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    val version: Long?,
)

fun SyncChange.ItemChange.toEntity(): ItemEntity =
    ItemEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        name = data.name,
        description = data.description,
        locationId = data.locationId,
        quantity = data.quantity,
        shortCode = data.shortCode,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface ItemDao {

    @Upsert
    suspend fun upsert(entity: ItemEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ItemEntity>)

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM item WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM item")
    suspend fun clearAll()

    @Query("SELECT * FROM item ORDER BY name")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM item WHERE id = :id")
    fun observeById(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun getById(id: String): ItemEntity?

    @Query("SELECT * FROM item WHERE location_id = :locationId ORDER BY name")
    fun observeByLocation(locationId: String): Flow<List<ItemEntity>>

    @Query(
        """
        SELECT DISTINCT item.* FROM item
        LEFT JOIN item_identification ON item_identification.item_id = item.id
        WHERE item.name LIKE :likePattern ESCAPE '\'
           OR item.short_code LIKE :likePattern ESCAPE '\'
           OR item_identification.value LIKE :likePattern ESCAPE '\'
        ORDER BY item.name
        """,
    )
    fun search(likePattern: String): Flow<List<ItemEntity>>

    @Query(
        """
        SELECT DISTINCT item.* FROM item
        INNER JOIN item_identification ON item_identification.item_id = item.id
        WHERE item_identification.value = :value
        """,
    )
    suspend fun findByIdentifierValue(value: String): List<ItemEntity>
}
