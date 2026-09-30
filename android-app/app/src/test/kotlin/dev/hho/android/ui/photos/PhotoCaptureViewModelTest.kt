package dev.hho.android.ui.photos

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.photoqueue.PhotoCapture
import dev.hho.android.data.photoqueue.PhotoQueueRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.PhotoQueueDao
import dev.hho.android.data.room.PhotoQueueEntryEntity
import dev.hho.android.data.room.PhotoState
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.ui.items.ViewModelTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

private class FakeCapture(private val dir: File) : PhotoCapture {
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0
    var lastFile: File? = null
    var unbound = false

    override suspend fun bind(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit

    override fun unbind() {
        unbound = true
    }

    override suspend fun capture(): File {
        calls++
        gate?.await()
        failure?.let { throw it }
        return File(dir, "cap-$calls.jpg").also {
            it.writeText("jpeg-bytes")
            lastFile = it
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PhotoCaptureViewModelTest {
    private lateinit var db: HhoDatabase
    private lateinit var base: File
    private lateinit var capDir: File
    private lateinit var fake: FakeCapture
    private var scheduled = 0
    private val viewModels = ViewModelTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryHhoDatabase()
        val tmp = TemporaryFolder().also { it.create() }
        base = tmp.newFolder("files")
        capDir = tmp.newFolder("cache")
        fake = FakeCapture(capDir)
    }

    @After
    fun tearDown() {
        viewModels.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun repo(dao: PhotoQueueDao = db.photoQueueDao()) =
        PhotoQueueRepository(base, dao, { 1_000L }) { it.inputStream() }

    private fun vm(queue: PhotoQueueRepository = repo()) =
        viewModels.track(PhotoCaptureViewModel(fake, queue) { scheduled++ })

    private fun awaitState(vm: PhotoCaptureViewModel, pred: (PhotoCaptureUiState) -> Boolean) = runBlocking {
        val deadline = System.currentTimeMillis() + 5_000
        while (!pred(vm.state.value) && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertTrue("state: ${vm.state.value}", pred(vm.state.value))
    }

    private fun entries() = runBlocking { db.photoQueueDao().getAllOrdered() }

    @Test
    fun captureSuccessQueuesOneEntrySchedulesOnceAndDeletesTemp() {
        val v = vm()
        v.shoot("item-1")
        awaitState(v) { it.done }
        val e = entries().single()
        assertEquals("item-1", e.itemId)
        assertEquals(PhotoState.QUEUED, e.state)
        assertEquals(
            java.security.MessageDigest.getInstance("SHA-256").digest("jpeg-bytes".toByteArray())
                .joinToString("") { "%02x".format(it) },
            e.sha256,
        )
        assertEquals(1, scheduled)
        assertFalse("temp must be deleted", fake.lastFile!!.exists())
        assertTrue(File(e.filePath).isFile)
        assertNull(v.state.value.error)
    }

    @Test
    fun captureFailureQueuesNothingAndShowsError() {
        fake.failure = IOException("boom")
        val v = vm()
        v.shoot("item-1")
        awaitState(v) { it.error != null }
        assertTrue(entries().isEmpty())
        assertEquals(0, scheduled)
        assertFalse(v.state.value.submitting)
        assertFalse(v.state.value.done)
    }

    @Test
    fun enqueueFailureDeletesTempAndShowsError() {
        val real = db.photoQueueDao()
        val failing =
            object : PhotoQueueDao by real {
                override suspend fun insert(entry: PhotoQueueEntryEntity) = throw IOException("disk full")
            }
        val v = vm(repo(failing))
        v.shoot("item-1")
        awaitState(v) { it.error != null }
        assertFalse("temp must be deleted", fake.lastFile!!.exists())
        assertTrue(entries().isEmpty())
        assertEquals(0, scheduled)
        assertTrue(File(base, "photo-queue").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun doubleSubmitWhileInFlightCapturesOnce() {
        fake.gate = CompletableDeferred()
        val v = vm()
        v.shoot("item-1")
        v.shoot("item-1")
        assertEquals(1, fake.calls)
        assertTrue(v.state.value.submitting)
        fake.gate!!.complete(Unit)
        awaitState(v) { it.done }
        v.shoot("item-1")
        assertEquals(1, fake.calls)
        assertEquals(1, entries().size)
        assertEquals(1, scheduled)
    }

    @Test
    fun retryIsAllowedAfterFailure() {
        fake.failure = IOException("boom")
        val v = vm()
        v.shoot("item-1")
        awaitState(v) { it.error != null }
        fake.failure = null
        v.shoot("item-1")
        awaitState(v) { it.done }
        assertEquals(1, entries().size)
        assertNull(v.state.value.error)
    }

    @Test
    fun badgeCountsArePerItem() = runTest {
        val dao = db.photoQueueDao()
        fun e(id: String, item: String, state: String) =
            PhotoQueueEntryEntity(id, item, "/x/$id", "s", 1, "image", state, createdAt = 1)
        dao.insert(e("a", "i1", PhotoState.QUEUED))
        dao.insert(e("b", "i1", PhotoState.WAITING_PARENT))
        dao.insert(e("c", "i1", PhotoState.UPLOADING))
        dao.insert(e("d", "i1", PhotoState.FAILED))
        dao.insert(e("z", "i2", PhotoState.QUEUED))
        val badge = viewModels.track(ItemPhotoBadgeViewModel(repo()))
        badge.load("i1")
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { badge.badge.collect { } }
        val deadline = System.currentTimeMillis() + 5_000
        while (badge.badge.value.failed == 0 && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertEquals(PhotoBadge(queued = 2, uploading = 1, failed = 1), badge.badge.value)
        val b = repo().observeEntriesForItem("i1").first().let { PhotoBadge.from(it) }
        assertEquals(PhotoBadge(queued = 2, uploading = 1, failed = 1), b)
        assertEquals("Photos: 2 queued, 1 uploading, 1 failed", b.summary())
        assertEquals(PhotoBadge(queued = 1), PhotoBadge.from(repo().observeEntriesForItem("i2").first()))
        assertNull(PhotoBadge.from(repo().observeEntriesForItem("none").first()).summary())
        collector.cancel()
    }
}
