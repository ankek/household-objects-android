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
import java.time.LocalDate

@Entity(tableName = "sold_to_block", indices = [Index("item_id")])
data class SoldToBlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    @ColumnInfo(name = "item_id") val itemId: String?,
    @ColumnInfo(name = "sale_price_minor") val salePriceMinor: Long,
    @ColumnInfo(name = "buyer_name") val buyerName: String?,
    @ColumnInfo(name = "sold_on") val soldOn: LocalDate?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    val version: Long?,
)

fun SyncChange.SoldToBlockChange.toEntity(): SoldToBlockEntity =
    SoldToBlockEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        itemId = data.itemId,
        salePriceMinor = data.salePriceMinor,
        buyerName = data.buyerName,
        soldOn = data.soldOn,
        notes = data.notes,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface SoldToBlockDao {

    @Upsert
    suspend fun upsert(entity: SoldToBlockEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SoldToBlockEntity>)

    @Query("DELETE FROM sold_to_block WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sold_to_block WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM sold_to_block")
    suspend fun clearAll()

    @Query("SELECT * FROM sold_to_block WHERE item_id = :itemId LIMIT 1")
    suspend fun getByItemId(itemId: String): SoldToBlockEntity?

    @Query("SELECT * FROM sold_to_block WHERE item_id = :itemId LIMIT 1")
    fun observeByItemId(itemId: String): Flow<SoldToBlockEntity?>
}
