package dev.hho.android.data.photoqueue

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.OutboxMutationEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.PhotoState
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PhotoUploaderTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private val server = MockWebServer()
    private lateinit var db: HhoDatabase
    private lateinit var repo: PhotoQueueRepository
    private lateinit var uploader: PhotoUploader
    private lateinit var source: File
    private var now = 1_000L

    private val sha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        val tmp = TemporaryFolder().also { it.create() }
        source = tmp.newFile("cap.jpg").apply { writeText("abc") }
        repo = PhotoQueueRepository(tmp.newFolder("files"), db.photoQueueDao(), { now }) { it.inputStream() }
        uploader = PhotoUploader(repo, db.photoQueueDao(), db.outboxDao(), apiClient())
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun apiClient(): HhoApiClient {
        val prefs = File.createTempFile("photo-uploader", ".preferences_pb").also { it.deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { prefs })
        runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() } }
        val cv = ClientVersionInterceptor()
        val provider =
            object : DeviceTokenProvider {
                override suspend fun tokenFor(requestUrl: HttpUrl): String? = "tok"

                override suspend fun invalidate(
                    requestUrl: HttpUrl,
                    rejectedToken: String,
                ) = Unit
            }
        val http =
            NetworkModule.provideOkHttpClient(
                BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore)),
                AuthHeaderInterceptor(provider),
                cv,
            )
        return HhoApiClient(http, NetworkModule.provideProbeOkHttpClient(http, cv))
    }

    private fun attachmentJson(
        sha256: String = sha,
        size: Long = 3,
    ) = """{"id":"a-1","item_id":"item-1","category":"image","original_filename":"x.jpg","content_type":"image/jpeg",""" +
        """"size_bytes":$size,"sha256":"$sha256","created_at":1700000000000,"updated_at":1700000000001,"version":1,"has_thumbnail":true}"""

    private fun created() = MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody(attachmentJson())

    private fun list(vararg items: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
            .setBody("""{"attachments":[${items.joinToString(",")}]}""")

    private fun status(code: Int) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/problem+json")
            .setBody("""{"title":"x","status":$code}""")

    private suspend fun enqueue() = repo.enqueue("item-1", source)

    private suspend fun drain() = uploader.drain { now }

    private fun insertCreate(
        state: String = OutboxState.PENDING,
        baseVersion: Long = 0,
        entityType: String = "item",
        entityId: String = "item-1",
        op: String = "upsert",
    ) = runBlocking {
        db.outboxDao().insert(
            OutboxMutationEntity(
                mutationId = "m-${System.nanoTime()}",
                entityType = entityType,
                entityId = entityId,
                op = op,
                baseVersion = baseVersion,
                fieldsJson = "{}",
                state = state,
                createdAt = 1,
            ),
        )
    }

    @Test
    fun `201 deletes the entry and its file, sends the queue file name`() =
        runTest {
            val e = enqueue()
            server.enqueue(created())
            assertEquals(PhotoDrainOutcome.DRAINED, drain())
            assertNull(db.photoQueueDao().getById(e.id))
            assertFalse(File(e.filePath).exists())
            assertEquals(1, server.requestCount)
            val req = server.takeRequest()
            assertEquals("/api/v1/items/item-1/attachments", req.path)
            assertTrue(req.body.readUtf8().contains("filename=\"${File(e.filePath).name}\""))
        }

    @Test
    fun `network failure then reconcile hit does not upload again`() =
        runTest {
            val e = enqueue()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertEquals(PhotoDrainOutcome.RETRY_LATER, drain())
            val after = db.photoQueueDao().getById(e.id)!!
            assertEquals(PhotoState.QUEUED, after.state)
            assertEquals(1, after.attemptCount)

            now += 60_000
            server.enqueue(list(attachmentJson()))
            assertEquals(PhotoDrainOutcome.DRAINED, drain())
            assertNull(db.photoQueueDao().getById(e.id))
            assertFalse(File(e.filePath).exists())
            assertEquals("one upload + one list, no second upload", 2, server.requestCount)
            server.takeRequest()
            assertEquals("GET", server.takeRequest().method)
        }

    @Test
    fun `network failure then reconcile miss (same sha, different size) re-uploads`() =
        runTest {
            val e = enqueue()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            drain()
            now += 60_000
            server.enqueue(list(attachmentJson(size = 4), attachmentJson(sha256 = "00".repeat(32))))
            server.enqueue(created())
            assertEquals(PhotoDrainOutcome.DRAINED, drain())
            assertNull(db.photoQueueDao().getById(e.id))
            assertEquals(3, server.requestCount)
            assertEquals(listOf("POST", "GET", "POST"), List(3) { server.takeRequest().method })
        }

    @Test
    fun `entry found UPLOADING after process death reconciles first - hit`() =
        runTest {
            val e = enqueue()
            repo.markUploading(e.id)
            server.enqueue(list(attachmentJson()))
            assertEquals(PhotoDrainOutcome.DRAINED, drain())
            assertNull(db.photoQueueDao().getById(e.id))
            assertEquals(1, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
        }

    @Test
    fun `entry found UPLOADING after process death reconciles first - miss uploads`() =
        runTest {
            val e = enqueue()
            repo.markUploading(e.id)
            server.enqueue(list())
            server.enqueue(created())
            assertEquals(PhotoDrainOutcome.DRAINED, drain())
            assertNull(db.photoQueueDao().getById(e.id))
            assertEquals(listOf("GET", "POST"), List(2) { server.takeRequest().method })
        }

    @Test
    fun `404 eight times ends FAILED and keeps the file`() =
        runTest {
            val e = enqueue()
            repeat(8) {
                server.enqueue(status(404))
                drain()
                now += 2 * PHOTO_BACKOFF_CAP_MS
            }
            val after = db.photoQueueDao().getById(e.id)!!
            assertEquals(PhotoState.FAILED, after.state)
            assertEquals(8, after.attemptCount)
            assertTrue(File(e.filePath).exists())
            assertEquals(8, server.requestCount)
        }

    @Test
    fun `404 before the eighth attempt stays QUEUED with backoff`() =
        runTest {
            val e = enqueue()
            server.enqueue(status(404))
            assertEquals(PhotoDrainOutcome.RETRY_LATER, drain())
            val after = db.photoQueueDao().getById(e.id)!!
            assertEquals(PhotoState.QUEUED, after.state)
            assertEquals(now + photoBackoffMillis(1), after.nextAttemptAt)
            drain()
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `413 fails at once without retry`() =
        runTest {
            val e = enqueue()
            server.enqueue(status(413))
            assertEquals(PhotoDrainOutcome.DRAINED, drain())
            val after = db.photoQueueDao().getById(e.id)!!
            assertEquals(PhotoState.FAILED, after.state)
            assertEquals(1, after.attemptCount)
            assertTrue(File(e.filePath).exists())
        }

    @Test
    fun `other 4xx is terminal but 5xx is retryable`() =
        runTest {
            val bad = enqueue()
            server.enqueue(status(422))
            drain()
            assertEquals(PhotoState.FAILED, db.photoQueueDao().getById(bad.id)!!.state)
            val srv = enqueue()
            server.enqueue(status(503))
            drain()
            assertEquals(PhotoState.QUEUED, db.photoQueueDao().getById(srv.id)!!.state)
        }

    @Test
    fun `waits for a pending item create then uploads after it is acknowledged`() =
        runTest {
            val e = enqueue()
            val seq = insertCreate()
            drain()
            assertEquals(0, server.requestCount)
            val parked = db.photoQueueDao().getById(e.id)!!
            assertEquals(PhotoState.WAITING_PARENT, parked.state)
            assertEquals(0, parked.attemptCount)

            drain()
            assertEquals(0, server.requestCount)
            assertEquals(PhotoState.WAITING_PARENT, db.photoQueueDao().getById(e.id)!!.state)

            db.outboxDao().deleteBySeq(seq)
            server.enqueue(created())
            assertEquals(PhotoDrainOutcome.DRAINED, drain())
            assertNull(db.photoQueueDao().getById(e.id))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `hasPendingCreate matches only live item creates`() =
        runTest {
            val dao = db.outboxDao()
            assertFalse(dao.hasPendingCreate("item-1"))
            insertCreate(baseVersion = 3)
            insertCreate(entityType = "label")
            insertCreate(op = "delete")
            insertCreate(entityId = "other")
            insertCreate(state = OutboxState.FAILED)
            assertFalse(dao.hasPendingCreate("item-1"))
            for (s in listOf(OutboxState.PENDING, OutboxState.IN_FLIGHT, OutboxState.HELD)) {
                val seq = insertCreate(state = s)
                assertTrue(s, dao.hasPendingCreate("item-1"))
                dao.deleteBySeq(seq)
            }
        }

    @Test
    fun `401 stops the run without counting an attempt and leaves later entries untouched`() =
        runTest {
            val first = enqueue()
            now += 1
            val second = enqueue()
            server.enqueue(status(401))
            assertEquals(PhotoDrainOutcome.BLOCKED, drain())
            val a = db.photoQueueDao().getById(first.id)!!
            assertEquals(PhotoState.QUEUED, a.state)
            assertEquals(0, a.attemptCount)
            assertNull(a.nextAttemptAt)
            assertEquals(PhotoState.QUEUED, db.photoQueueDao().getById(second.id)!!.state)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `426 also blocks without counting an attempt`() =
        runTest {
            val e = enqueue()
            server.enqueue(
                MockResponse().setResponseCode(426).setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"type":"urn:hho:problem:upgrade-required","title":"Upgrade Required","status":426,"minimum_version":"9.9.9"}"""),
            )
            assertEquals(PhotoDrainOutcome.BLOCKED, drain())
            assertEquals(0, db.photoQueueDao().getById(e.id)!!.attemptCount)
            assertNotNull(db.photoQueueDao().getById(e.id))
        }
}
