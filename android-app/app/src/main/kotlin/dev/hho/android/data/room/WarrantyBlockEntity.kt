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

@Entity(tableName = "warranty_block", indices = [Index("item_id")])
data class WarrantyBlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    @ColumnInfo(name = "item_id") val itemId: String?,
    @ColumnInfo(name = "is_lifetime") val isLifetime: Boolean,
    val holder: String?,
    val provider: String?,
    @ColumnInfo(name = "starts_on") val startsOn: LocalDate?,
    @ColumnInfo(name = "expires_on") val expiresOn: LocalDate?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    val version: Long?,
)

fun SyncChange.WarrantyBlockChange.toEntity(): WarrantyBlockEntity =
    WarrantyBlockEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        itemId = data.itemId,
        isLifetime = data.isLifetime,
        holder = data.holder,
        provider = data.provider,
        startsOn = data.startsOn,
        expiresOn = data.expiresOn,
        notes = data.notes,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface WarrantyBlockDao {

    @Upsert
    suspend fun upsert(entity: WarrantyBlockEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WarrantyBlockEntity>)

    @Query("DELETE FROM warranty_block WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM warranty_block WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM warranty_block")
    suspend fun clearAll()

    @Query("SELECT * FROM warranty_block WHERE item_id = :itemId LIMIT 1")
    suspend fun getByItemId(itemId: String): WarrantyBlockEntity?

    @Query("SELECT * FROM warranty_block WHERE item_id = :itemId LIMIT 1")
    fun observeByItemId(itemId: String): Flow<WarrantyBlockEntity?>
}
