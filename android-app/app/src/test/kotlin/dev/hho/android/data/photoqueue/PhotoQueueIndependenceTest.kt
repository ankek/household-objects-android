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
import dev.hho.android.data.outbox.LocalMutation
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.PhotoState
import dev.hho.android.data.room.deleteHhoDatabaseFile
import dev.hho.android.data.room.fileHhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.data.sync.OutboxSyncer
import dev.hho.android.data.sync.PushPhaseOutcome
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
class PhotoQueueIndependenceTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val tmp = TemporaryFolder().also { it.create() }
    private val server = MockWebServer()
    private val dbs = mutableListOf<HhoDatabase>()
    private val dbName = "photo-independence-${System.nanoTime()}"
    private var now = 1_000L

    private val storedAttachments = mutableListOf<String>()
    private var postCount = 0
    private var pushCount = 0
    private var photoPostMode: (Int) -> MockResponse? = { null }
    private var pushMode: () -> MockResponse? = { null }

    private lateinit var source: File
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        source = tmp.newFile("cap.jpg").apply { writeText("abc") }
        filesDir = tmp.newFolder("files")
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.endsWith("/sync/push") -> {
                            pushCount++
                            pushMode() ?: applyAll(request)
                        }
                        path.endsWith("/attachments") && request.method == "POST" -> {
                            postCount++
                            val custom = photoPostMode(postCount)
                            if (custom != null) return custom
                            store()
                            json(201, storedAttachments.last())
                        }
                        path.endsWith("/attachments") && request.method == "GET" ->
                            json(200, """{"attachments":[${storedAttachments.joinToString(",")}]}""")
                        else -> json(404, """{"title":"x","status":404}""")
                    }
                }
            }
    }

    @After
    fun tearDown() {
        dbs.forEach { runCatching { it.close() } }
        deleteHhoDatabaseFile(dbName)
        server.shutdown()
    }

    @Test
    fun `data sync and data outbox never reference the photoqueue package`() {
        val roots = listOf("data/sync", "data/outbox").map { File("src/main/kotlin/dev/hho/android/$it") }
        val files = roots.flatMap { r -> r.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        assertTrue(
            "Expected Kotlin sources under ${roots.map { it.absolutePath }} but found ${files.size}; " +
                "the scan would otherwise pass vacuously.",
            files.size >= 5,
        )
        val offenders =
            files.filter { f -> PHOTOQUEUE_REF.containsMatchIn(stripComments(f.readText())) }
                .map { it.path }
        if (offenders.isNotEmpty()) {
            fail(
                "data/sync and data/outbox must never import or reference dev.hho.android.data.photoqueue " +
                    "(FR-122: the queues are independent; use an interface declared in data/sync and " +
                    "implemented in data/photoqueue). Offending files: $offenders",
            )
        }
    }

    @Test
    fun `a failing photo upload does not stop an entity sync`() =
        runBlocking {
            val c = components(inMemoryHhoDatabase())
            c.photoRepo.enqueue("item-1", source)
            c.outbox.enqueue(itemEdit("a"))
            photoPostMode = { json(503, """{"title":"x","status":503}""") }

            assertEquals(PhotoDrainOutcome.RETRY_LATER, c.uploader.drain { now })
            assertEquals(PushPhaseOutcome.Drained, c.syncer.push("dev-1"))

            assertTrue("entity outbox drained", c.db.outboxDao().getAllOrdered().isEmpty())
            assertEquals(1, pushCount)
            assertEquals(listOf(PhotoState.QUEUED), photoStates(c.db))
        }

    @Test
    fun `a failing entity push does not stop photo uploads`() =
        runBlocking {
            val c = components(inMemoryHhoDatabase())
            val entry = c.photoRepo.enqueue("item-1", source)
            c.outbox.enqueue(itemEdit("a"))
            pushMode = { json(503, """{"title":"x","status":503}""") }

            assertTrue(c.syncer.push("dev-1") is PushPhaseOutcome.Failed)
            assertEquals(PhotoDrainOutcome.DRAINED, c.uploader.drain { now })

            assertEquals("one attachment stored", 1, storedAttachments.size)
            assertTrue("photo row gone", photoStates(c.db).isEmpty())
            assertTrue("photo file gone", !File(entry.filePath).exists())
            assertEquals("entity row stays queued for retry", 1, c.db.outboxDao().getAllOrdered().size)
        }

    @Test
    fun `kill and reopen keeps queued photos and recovers an UPLOADING entry`() =
        runBlocking {
            deleteHhoDatabaseFile(dbName)
            val before = components(fileHhoDatabase(dbName))
            val queued = before.photoRepo.enqueue("item-1", source)
            val inFlight = before.photoRepo.enqueue("item-1", source)
            assertTrue(before.photoRepo.markUploading(inFlight.id))
            before.db.close()

            val after = components(fileHhoDatabase(dbName))
            val rows = after.db.photoQueueDao().getByStates(ALL_STATES)
            assertEquals(setOf(queued.id, inFlight.id), rows.map { it.id }.toSet())
            assertEquals(PhotoState.UPLOADING, rows.first { it.id == inFlight.id }.state)
            assertTrue(File(queued.filePath).isFile && File(inFlight.filePath).isFile)

            assertEquals(PhotoDrainOutcome.DRAINED, after.uploader.drain { now })

            assertEquals("both uploaded after recovery", 2, storedAttachments.size)
            assertTrue(after.db.photoQueueDao().getByStates(ALL_STATES).isEmpty())
            assertTrue(!File(queued.filePath).exists() && !File(inFlight.filePath).exists())
        }

    @Test
    fun `a lost 201 does not duplicate the attachment`() =
        runBlocking {
            val c = components(inMemoryHhoDatabase())
            val entry = c.photoRepo.enqueue("item-1", source)
            photoPostMode = { n ->
                if (n == 1) store()
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            }

            assertEquals(PhotoDrainOutcome.RETRY_LATER, c.uploader.drain { now })
            val postsAfterLoss = postCount
            assertEquals("server holds the attachment", 1, storedAttachments.size)
            assertEquals(PhotoState.QUEUED, c.db.photoQueueDao().getById(entry.id)?.state)

            photoPostMode = { fail("a second POST was sent after a lost 201"); null }
            now += photoBackoffMillis(1) + 1
            assertEquals(PhotoDrainOutcome.DRAINED, c.uploader.drain { now })

            assertEquals("no POST in the reconciling drain", postsAfterLoss, postCount)
            assertEquals("exactly one attachment on the server", 1, storedAttachments.size)
            assertTrue(photoStates(c.db).isEmpty())
            assertTrue(!File(entry.filePath).exists())
        }

    private class Components(
        val db: HhoDatabase,
        val photoRepo: PhotoQueueRepository,
        val uploader: PhotoUploader,
        val outbox: OutboxRepository,
        val syncer: OutboxSyncer,
    )

    private fun components(db: HhoDatabase): Components {
        dbs += db
        val api = apiClient()
        val photoRepo = PhotoQueueRepository(filesDir, db.photoQueueDao(), { now }) { it.inputStream() }
        val outbox = OutboxRepository(db, clock = { now++ })
        return Components(
            db,
            photoRepo,
            PhotoUploader(photoRepo, db.photoQueueDao(), db.outboxDao(), api),
            outbox,
            OutboxSyncer(db, api, outbox, clock = { now++ }),
        )
    }

    private suspend fun photoStates(db: HhoDatabase) =
        db.photoQueueDao().getByStates(ALL_STATES).map { it.state }

    private fun itemEdit(id: String) =
        LocalMutation("item", id, "upsert", 1, Json.parseToJsonElement("""{"name":"N"}""").jsonObject)

    private fun store() {
        storedAttachments +=
            """{"id":"a-${storedAttachments.size}","item_id":"item-1","category":"image","original_filename":"x.jpg",""" +
                """"content_type":"image/jpeg","size_bytes":3,"sha256":"$SHA_ABC","created_at":1700000000000,""" +
                """"updated_at":1700000000001,"version":1,"has_thumbnail":true}"""
    }

    private fun applyAll(request: RecordedRequest): MockResponse {
        val muts = Json.parseToJsonElement(request.body.readUtf8()).jsonObject["mutations"]!!.jsonArray.map { it.jsonObject }
        val applied =
            muts.joinToString(",") { m ->
                """{"mutation_id":"${m["mutation_id"]!!.jsonPrimitive.content}","entity_type":"${m["entity_type"]!!.jsonPrimitive.content}",""" +
                    """"entity_id":"${m["entity_id"]!!.jsonPrimitive.content}","version":10}"""
            }
        return json(200, """{"applied":[$applied],"skipped":[],"conflicts":[],"new_watermark":99}""")
    }

    private fun json(
        status: Int,
        body: String,
    ) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private fun apiClient(): HhoApiClient {
        val prefs = File.createTempFile("photo-independence", ".preferences_pb").also { it.deleteOnExit() }
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

    private fun stripComments(text: String): String =
        text.replace(Regex("/\\*[\\s\\S]*?\\*/"), "").lines().joinToString("\n") { it.substringBefore("//") }

    private companion object {
        val ALL_STATES = listOf(PhotoState.QUEUED, PhotoState.UPLOADING, PhotoState.WAITING_PARENT, PhotoState.FAILED)
        const val SHA_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val PHOTOQUEUE_REF = Regex("""dev\.hho\.android\.data\.photoqueue""")
    }
}
