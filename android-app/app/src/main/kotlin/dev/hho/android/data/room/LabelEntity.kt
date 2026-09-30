package dev.hho.android.data.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import dev.hho.android.data.apiclient.SyncChange
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "label")
data class LabelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    val name: String,
    val color: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val version: Long,
)

fun SyncChange.LabelChange.toEntity(): LabelEntity =
    LabelEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        name = data.name,
        color = data.color,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface LabelDao {

    @Upsert
    suspend fun upsert(entity: LabelEntity)

    @Upsert
    suspend fun upsertAll(entities: List<LabelEntity>)

    @Query("DELETE FROM label WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM label WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM label")
    suspend fun clearAll()

    @Query("SELECT * FROM label ORDER BY name")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM label WHERE id = :id")
    suspend fun getById(id: String): LabelEntity?
}
