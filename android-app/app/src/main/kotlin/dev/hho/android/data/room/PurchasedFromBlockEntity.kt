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

@Entity(tableName = "purchased_from_block", indices = [Index("item_id")])
data class PurchasedFromBlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    @ColumnInfo(name = "item_id") val itemId: String?,
    @ColumnInfo(name = "purchase_price_minor") val purchasePriceMinor: Long,
    val vendor: String?,
    @ColumnInfo(name = "purchased_on") val purchasedOn: LocalDate?,
    @ColumnInfo(name = "order_reference") val orderReference: String?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    val version: Long?,
)

fun SyncChange.PurchasedFromBlockChange.toEntity(): PurchasedFromBlockEntity =
    PurchasedFromBlockEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        itemId = data.itemId,
        purchasePriceMinor = data.purchasePriceMinor,
        vendor = data.vendor,
        purchasedOn = data.purchasedOn,
        orderReference = data.orderReference,
        notes = data.notes,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface PurchasedFromBlockDao {

    @Upsert
    suspend fun upsert(entity: PurchasedFromBlockEntity)

    @Upsert
    suspend fun upsertAll(entities: List<PurchasedFromBlockEntity>)

    @Query("DELETE FROM purchased_from_block WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM purchased_from_block WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM purchased_from_block")
    suspend fun clearAll()

    @Query("SELECT version FROM purchased_from_block WHERE id = :id")
    suspend fun versionById(id: String): Long?

    @Query("SELECT * FROM purchased_from_block WHERE item_id = :itemId LIMIT 1")
    suspend fun getByItemId(itemId: String): PurchasedFromBlockEntity?

    @Query("SELECT * FROM purchased_from_block WHERE item_id = :itemId LIMIT 1")
    fun observeByItemId(itemId: String): Flow<PurchasedFromBlockEntity?>
}
