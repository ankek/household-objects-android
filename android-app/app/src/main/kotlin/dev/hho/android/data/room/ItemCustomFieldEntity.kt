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
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "item_custom_field", indices = [Index("item_id")])
data class ItemCustomFieldEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    @ColumnInfo(name = "item_id") val itemId: String?,
    val name: String,
    @ColumnInfo(name = "field_type") val fieldType: String,
    @ColumnInfo(name = "field_def_id") val fieldDefId: String?,
    @ColumnInfo(name = "text_value") val textValue: String?,
    @ColumnInfo(name = "number_value") val numberValue: BigDecimal?,
    @ColumnInfo(name = "bool_value") val boolValue: Boolean?,
    @ColumnInfo(name = "date_value") val dateValue: LocalDate?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    val version: Long?,
)

fun SyncChange.ItemCustomFieldChange.toEntity(): ItemCustomFieldEntity =
    ItemCustomFieldEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        itemId = data.itemId,
        name = data.name,
        fieldType = data.fieldType.value,
        fieldDefId = data.fieldDefId,
        textValue = data.textValue,
        numberValue = data.numberValue,
        boolValue = data.boolValue,
        dateValue = data.dateValue,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface ItemCustomFieldDao {

    @Upsert
    suspend fun upsert(entity: ItemCustomFieldEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ItemCustomFieldEntity>)

    @Query("DELETE FROM item_custom_field WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM item_custom_field WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM item_custom_field")
    suspend fun clearAll()

    @Query("SELECT * FROM item_custom_field WHERE item_id = :itemId")
    fun observeByItemId(itemId: String): Flow<List<ItemCustomFieldEntity>>
}
