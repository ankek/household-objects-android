package dev.hho.android.data.photoqueue

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.room.PhotoQueueDao
import dev.hho.android.data.room.PhotoQueueEntryEntity
import dev.hho.android.data.room.PhotoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

const val PHOTO_MAX_ATTEMPTS: Int = 8

const val PHOTO_BACKOFF_BASE_MS: Long = 30_000L

const val PHOTO_BACKOFF_CAP_MS: Long = 30L * 60 * 1000

fun photoBackoffMillis(attempt: Int): Long {
    require(attempt >= 1) { "attempt is 1-based" }
    val shift = (attempt - 1).coerceAtMost(20)
    return (PHOTO_BACKOFF_BASE_MS shl shift).coerceAtMost(PHOTO_BACKOFF_CAP_MS)
}

const val PHOTO_CATEGORY_IMAGE: String = "image"

@Singleton
class PhotoQueueRepository
    internal constructor(
        baseDir: File,
        private val dao: PhotoQueueDao,
        private val clock: () -> Long,
        private val openSource: (File) -> InputStream,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
            dao: PhotoQueueDao,
        ) : this(context.filesDir, dao, System::currentTimeMillis, { it.inputStream() })

        private val ids = UuidV7Generator(clock)
        private val dir: File = File(baseDir, "photo-queue")

        init {
            dir.mkdirs()
            dir.listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
        }

        suspend fun enqueue(
            itemId: String,
            sourceFile: File,
            category: String = PHOTO_CATEGORY_IMAGE,
        ): PhotoQueueEntryEntity =
            withContext(Dispatchers.IO) {
                val id = ids.generate()
                val staged = File(dir, id + PART_SUFFIX)
                val target = File(dir, id + FILE_SUFFIX)
                val (sha, size) =
                    try {
                        copyHashing(sourceFile, staged)
                    } catch (e: Throwable) {
                        staged.delete()
                        throw e
                    }
                try {
                    Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (e: Throwable) {
                    staged.delete()
                    throw e
                }
                val entry =
                    PhotoQueueEntryEntity(
                        id = id,
                        itemId = itemId,
                        filePath = target.absolutePath,
                        sha256 = sha,
                        sizeBytes = size,
                        category = category,
                        state = PhotoState.QUEUED,
                        createdAt = clock(),
                    )
                try {
                    dao.insert(entry)
                } catch (e: Throwable) {
                    target.delete()
                    throw e
                }
                entry
            }

        suspend fun nextDue(now: Long = clock()): PhotoQueueEntryEntity? {
            for (entry in dao.getDue(listOf(PhotoState.QUEUED), now)) {
                if (failIfFileMissing(entry)) continue
                return entry
            }
            return null
        }

        suspend fun markUploading(id: String): Boolean {
            val entry = dao.getById(id) ?: return false
            if (entry.state != PhotoState.QUEUED || failIfFileMissing(entry)) return false
            dao.updateProgress(id, PhotoState.UPLOADING, entry.attemptCount, null, entry.lastError)
            return true
        }

        suspend fun markUploaded(id: String) {
            val entry = dao.getById(id) ?: return
            dao.deleteById(id)
            withContext(Dispatchers.IO) { File(entry.filePath).delete() }
        }

        suspend fun markWaitingParent(id: String) {
            val entry = dao.getById(id) ?: return
            dao.updateProgress(id, PhotoState.WAITING_PARENT, entry.attemptCount, null, entry.lastError)
        }

        suspend fun releaseWaitingParent(): Int = dao.moveState(PhotoState.WAITING_PARENT, PhotoState.QUEUED)

        suspend fun recordFailure(
            id: String,
            error: String,
            retryable: Boolean,
            now: Long = clock(),
        ) {
            val entry = dao.getById(id) ?: return
            val attempts = entry.attemptCount + 1
            if (!retryable || attempts >= PHOTO_MAX_ATTEMPTS) {
                dao.updateProgress(id, PhotoState.FAILED, attempts, null, error)
            } else {
                dao.updateProgress(id, PhotoState.QUEUED, attempts, now + photoBackoffMillis(attempts), error)
            }
        }

        suspend fun resetUploadingToQueued(): Int = dao.moveState(PhotoState.UPLOADING, PhotoState.QUEUED)

        fun fileFor(entry: PhotoQueueEntryEntity): File = File(entry.filePath)

        fun observeEntries(): Flow<List<PhotoQueueEntryEntity>> = dao.observeAll()

        fun observeEntriesForItem(itemId: String): Flow<List<PhotoQueueEntryEntity>> = dao.observeByItem(itemId)

        fun observePendingCount(): Flow<Int> =
            dao.observeCount(listOf(PhotoState.QUEUED, PhotoState.UPLOADING, PhotoState.WAITING_PARENT))

        fun observeFailedCount(): Flow<Int> = dao.observeCount(listOf(PhotoState.FAILED))

        private suspend fun failIfFileMissing(entry: PhotoQueueEntryEntity): Boolean {
            if (File(entry.filePath).isFile) return false
            dao.updateProgress(
                entry.id,
                PhotoState.FAILED,
                entry.attemptCount,
                null,
                "Queued photo file is missing: ${File(entry.filePath).name}",
            )
            return true
        }

        private fun copyHashing(
            source: File,
            staged: File,
        ): Pair<String, Long> {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            openSource(source).use { input ->
                FileOutputStream(staged).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        total += read
                    }
                    out.flush()
                    out.fd.sync()
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } to total
        }

        private companion object {
            const val PART_SUFFIX = ".part"
            const val FILE_SUFFIX = ".jpg"
            const val BUFFER_SIZE = 8192
        }
    }
