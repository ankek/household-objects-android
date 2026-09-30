package dev.hho.android.data.attachments

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.room.AttachmentDao
import dev.hho.android.data.room.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

const val ATTACHMENT_CACHE_MAX_BYTES: Long = 200L * 1024 * 1024

class AttachmentNotCachedException(attachmentId: String) :
    Exception("No attachment metadata for id $attachmentId — nothing to resolve its item/hash from")

class AttachmentIntegrityException(attachmentId: String, reason: String) :
    Exception("Attachment $attachmentId failed integrity verification: $reason")

@Singleton
class AttachmentCache
    internal constructor(
        private val cacheDir: File,
        private val apiClient: HhoApiClient,
        private val attachmentDao: AttachmentDao,
        private val maxCacheBytes: Long,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
            apiClient: HhoApiClient,
            attachmentDao: AttachmentDao,
        ) : this(
            cacheDir = File(context.cacheDir, "attachments"),
            apiClient = apiClient,
            attachmentDao = attachmentDao,
            maxCacheBytes = ATTACHMENT_CACHE_MAX_BYTES,
        )

        private val tmpDir: File = File(cacheDir, ".tmp")

        private val locks = ConcurrentHashMap<String, Mutex>()

        private val evictionMutex = Mutex()

        init {
            cacheDir.mkdirs()
            tmpDir.mkdirs()
            tmpDir.listFiles()?.forEach { it.delete() }
        }

        suspend fun file(attachmentId: String): Result<File> =
            withContext(Dispatchers.IO) {
                cachedFile(attachmentId)?.let { return@withContext Result.success(it) }

                val mutex = locks.computeIfAbsent(attachmentId) { Mutex() }
                mutex.withLock {
                    cachedFile(attachmentId)?.let { return@withLock Result.success(it) }
                    fetchAndStore(attachmentId)
                }
            }

        private fun cachedFile(attachmentId: String): File? {
            val existing = File(cacheDir, attachmentId)
            if (!existing.isFile) return null
            existing.setLastModified(System.currentTimeMillis())
            return existing
        }

        private suspend fun fetchAndStore(attachmentId: String): Result<File> {
            val metadata =
                attachmentDao.getById(attachmentId)
                    ?: return Result.failure(AttachmentNotCachedException(attachmentId))

            val downloaded =
                apiClient.downloadAttachment(metadata.itemId, attachmentId).getOrElse {
                    return Result.failure(it)
                }

            return try {
                verifyIntegrity(downloaded, metadata)
                if (downloaded.length() > maxCacheBytes) {
                    Result.success(downloaded)
                } else {
                    Result.success(moveIntoCache(attachmentId, downloaded))
                }
            } catch (e: AttachmentIntegrityException) {
                downloaded.delete()
                Result.failure(e)
            } catch (e: IOException) {
                downloaded.delete()
                Result.failure(e)
            }
        }

        private fun verifyIntegrity(
            file: File,
            metadata: AttachmentEntity,
        ) {
            val actualSize = file.length()
            if (actualSize != metadata.sizeBytes) {
                throw AttachmentIntegrityException(
                    metadata.id,
                    "expected ${metadata.sizeBytes} bytes, got $actualSize",
                )
            }
            val actualSha256 = file.sha256Hex()
            if (!actualSha256.equals(metadata.sha256, ignoreCase = true)) {
                throw AttachmentIntegrityException(metadata.id, "sha256 mismatch")
            }
        }

        private suspend fun moveIntoCache(
            attachmentId: String,
            source: File,
        ): File {
            val staged = File.createTempFile("$attachmentId-", ".part", tmpDir)
            source.copyTo(staged, overwrite = true)
            source.delete()
            val target = File(cacheDir, attachmentId)
            Files.move(
                staged.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            evictionMutex.withLock { evictIfNeeded() }
            return target
        }

        private fun evictIfNeeded() {
            val files = cacheDir.listFiles { candidate -> candidate.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= maxCacheBytes) return
            for (candidate in files.sortedBy { it.lastModified() }) {
                if (total <= maxCacheBytes) break
                val size = candidate.length()
                if (candidate.delete()) total -= size
            }
        }
    }

private const val HASH_BUFFER_SIZE = 8192

private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(HASH_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}
