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

@Entity(tableName = "location", indices = [Index("parent_id")])
data class LocationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val version: Long,
)

fun SyncChange.LocationChange.toEntity(): LocationEntity =
    LocationEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        name = data.name,
        parentId = data.parentId,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface LocationDao {

    @Upsert
    suspend fun upsert(entity: LocationEntity)

    @Upsert
    suspend fun upsertAll(entities: List<LocationEntity>)

    @Query("DELETE FROM location WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM location WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM location")
    suspend fun clearAll()

    @Query("SELECT * FROM location ORDER BY name")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM location WHERE id = :id")
    suspend fun getById(id: String): LocationEntity?

    @Query("SELECT * FROM location WHERE parent_id = :parentId ORDER BY name")
    fun observeByParentId(parentId: String): Flow<List<LocationEntity>>
}
