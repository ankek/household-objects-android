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

@Entity(tableName = "attachment", indices = [Index("item_id")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_change_seq") val groupChangeSeq: Long,
    @ColumnInfo(name = "item_id") val itemId: String,
    val category: String,
    @ColumnInfo(name = "original_filename") val originalFilename: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val sha256: String,
    @ColumnInfo(name = "has_thumbnail") val hasThumbnail: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val version: Long,
)

fun SyncChange.AttachmentChange.toEntity(): AttachmentEntity =
    AttachmentEntity(
        id = id,
        groupChangeSeq = groupChangeSeq,
        itemId = data.itemId,
        category = data.category.value,
        originalFilename = data.originalFilename,
        contentType = data.contentType,
        sizeBytes = data.sizeBytes,
        sha256 = data.sha256,
        hasThumbnail = data.hasThumbnail,
        createdAt = data.createdAt,
        updatedAt = data.updatedAt,
        version = data.version,
    )

@Dao
interface AttachmentDao {

    @Upsert
    suspend fun upsert(entity: AttachmentEntity)

    @Upsert
    suspend fun upsertAll(entities: List<AttachmentEntity>)

    @Query("DELETE FROM attachment WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM attachment WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM attachment")
    suspend fun clearAll()

    @Query("SELECT * FROM attachment WHERE item_id = :itemId")
    fun observeByItemId(itemId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachment WHERE id = :id")
    suspend fun getById(id: String): AttachmentEntity?
}
