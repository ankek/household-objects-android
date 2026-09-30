package dev.hho.android.data.photoqueue

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.PhotoQueueDao
import dev.hho.android.data.room.PhotoState
import dev.hho.android.data.room.inMemoryHhoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PhotoQueueRepositoryTest {
    private lateinit var db: HhoDatabase
    private lateinit var dao: PhotoQueueDao
    private lateinit var base: File
    private lateinit var source: File
    private var now = 1_000L

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        dao = db.photoQueueDao()
        val tmp = TemporaryFolder().also { it.create() }
        base = tmp.newFolder("files")
        source = tmp.newFile("cap.jpg").apply { writeText("abc") }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repo(
        d: PhotoQueueDao = dao,
        open: (File) -> InputStream = { it.inputStream() },
    ) = PhotoQueueRepository(base, d, { now }, open)

    private val queueDir get() = File(base, "photo-queue")

    @Test
    fun enqueueCopiesAndHashesAgainstKnownSha256() =
        runTest {
            val e = repo().enqueue("item-1", source)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", e.sha256)
            assertEquals(3L, e.sizeBytes)
            assertEquals(PhotoState.QUEUED, e.state)
            assertEquals("abc", File(e.filePath).readText())
            assertTrue(File(e.filePath).parentFile == queueDir)
            assertTrue("source is caller-owned", source.exists())
            assertEquals(e, dao.getById(e.id))
        }

    @Test
    fun midCopyFailureLeavesNoFileAndNoRow() =
        runTest {
            val failing = { f: File ->
                object : InputStream() {
                    var n = 0

                    override fun read(): Int = throw IOException("boom")

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (n++ == 0) {
                            b[off] = 1
                            return 1
                        }
                        throw IOException("boom")
                    }
                }
            }
            try {
                repo(open = failing).enqueue("i", source)
                fail("expected IOException")
            } catch (_: IOException) {
            }
            assertEquals(emptyList<String>(), queueDir.list()!!.toList())
            assertTrue(dao.getAllOrdered().isEmpty())
        }

    @Test
    fun rowInsertFailureRemovesCopiedFile() =
        runTest {
            val broken =
                object : PhotoQueueDao by dao {
                    override suspend fun insert(entry: dev.hho.android.data.room.PhotoQueueEntryEntity) =
                        throw IllegalStateException("db down")
                }
            try {
                repo(broken).enqueue("i", source)
                fail("expected failure")
            } catch (_: IllegalStateException) {
            }
            assertEquals(0, queueDir.list()!!.size)
        }

    @Test
    fun staleStagingFilesAreSweptOnConstruction() {
        queueDir.mkdirs()
        File(queueDir, "x.part").writeText("junk")
        repo()
        assertFalse(File(queueDir, "x.part").exists())
    }

    @Test
    fun transitionsAndUploadedDeletesRowAndFile() =
        runTest {
            val r = repo()
            val e = r.enqueue("i", source)
            assertEquals(e, r.nextDue())
            assertTrue(r.markUploading(e.id))
            assertEquals(PhotoState.UPLOADING, dao.getById(e.id)!!.state)
            assertFalse("not QUEUED any more", r.markUploading(e.id))
            assertNull(r.nextDue())
            r.markWaitingParent(e.id)
            assertEquals(PhotoState.WAITING_PARENT, dao.getById(e.id)!!.state)
            assertNull(r.nextDue())
            assertEquals(1, r.releaseWaitingParent())
            assertEquals(PhotoState.QUEUED, dao.getById(e.id)!!.state)
            assertTrue(r.markUploading(e.id))
            r.markUploaded(e.id)
            assertNull(dao.getById(e.id))
            assertFalse(File(e.filePath).exists())
        }

    @Test
    fun backoffScheduleValues() {
        val expected = listOf(30_000L, 60_000, 120_000, 240_000, 480_000, 960_000, 1_800_000, 1_800_000)
        assertEquals(expected, (1..8).map { photoBackoffMillis(it) })
        assertEquals(1_800_000L, photoBackoffMillis(500))
    }

    @Test
    fun retryableFailureSchedulesBackoffAndHidesUntilDue() =
        runTest {
            val r = repo()
            val e = r.enqueue("i", source)
            r.recordFailure(e.id, "503", retryable = true, now = 10_000)
            var row = dao.getById(e.id)!!
            assertEquals(1, row.attemptCount)
            assertEquals(PhotoState.QUEUED, row.state)
            assertEquals(40_000L, row.nextAttemptAt)
            assertEquals("503", row.lastError)
            assertNull(r.nextDue(39_999))
            assertNotNull(r.nextDue(40_000))
            r.recordFailure(e.id, "503", true, now = 40_000)
            row = dao.getById(e.id)!!
            assertEquals(100_000L, row.nextAttemptAt)
        }

    @Test
    fun eighthAttemptFailsAndKeepsFile() =
        runTest {
            val r = repo()
            val e = r.enqueue("i", source)
            repeat(7) { r.recordFailure(e.id, "5xx", true, now = 0) }
            assertEquals(PhotoState.QUEUED, dao.getById(e.id)!!.state)
            r.recordFailure(e.id, "5xx", true, now = 0)
            val row = dao.getById(e.id)!!
            assertEquals(PhotoState.FAILED, row.state)
            assertEquals(8, row.attemptCount)
            assertNull(row.nextAttemptAt)
            assertTrue(File(e.filePath).exists())
            assertNull(r.nextDue(Long.MAX_VALUE))
        }

    @Test
    fun nonRetryable413FailsImmediately() =
        runTest {
            val r = repo()
            val e = r.enqueue("i", source)
            r.recordFailure(e.id, "413 too large", retryable = false)
            val row = dao.getById(e.id)!!
            assertEquals(PhotoState.FAILED, row.state)
            assertEquals(1, row.attemptCount)
            assertEquals("413 too large", row.lastError)
            assertTrue(File(e.filePath).exists())
        }

    @Test
    fun missingFileBecomesFailedNotCrash() =
        runTest {
            val r = repo()
            val e = r.enqueue("i", source)
            val e2 = r.enqueue("i", source)
            File(e.filePath).delete()
            assertEquals(e2.id, r.nextDue()!!.id)
            val row = dao.getById(e.id)!!
            assertEquals(PhotoState.FAILED, row.state)
            assertTrue(row.lastError!!.contains("missing"))
            File(e2.filePath).delete()
            assertFalse(r.markUploading(e2.id))
            assertEquals(PhotoState.FAILED, dao.getById(e2.id)!!.state)
        }

    @Test
    fun processDeathResetReturnsUploadingToQueuedKeepingAttempts() =
        runTest {
            val r = repo()
            val e = r.enqueue("i", source)
            r.recordFailure(e.id, "x", true, now = 0)
            dao.updateProgress(e.id, PhotoState.UPLOADING, 1, null, "x")
            assertEquals(1, r.resetUploadingToQueued())
            val row = dao.getById(e.id)!!
            assertEquals(PhotoState.QUEUED, row.state)
            assertEquals(1, row.attemptCount)
        }

    @Test
    fun nextDueIsOldestFirstAndObserversReport() =
        runTest {
            val r = repo()
            now = 1
            val a = r.enqueue("i", source)
            now = 2
            val b = r.enqueue("i", source)
            assertEquals(a.id, r.nextDue()!!.id)
            r.recordFailure(a.id, "x", false)
            assertEquals(b.id, r.nextDue()!!.id)
            assertEquals(listOf(a.id, b.id), r.observeEntries().first().map { it.id })
            assertEquals(1, r.observePendingCount().first())
            assertEquals(1, r.observeFailedCount().first())
        }
}
