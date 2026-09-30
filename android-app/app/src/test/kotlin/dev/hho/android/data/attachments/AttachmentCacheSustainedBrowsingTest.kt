package dev.hho.android.data.attachments

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.room.AttachmentEntity
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AttachmentCacheSustainedBrowsingTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private val server = MockWebServer()
    private lateinit var db: HhoDatabase
    private lateinit var scratchDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        scratchDir =
            File.createTempFile("attachment-cache-sustained-test", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
        cacheDir = File(scratchDir, "attachments")
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
        scratchDir.deleteRecursively()
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("attachment-cache-sustained-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun apiClient(): HhoApiClient {
        val dataStore = tempDataStore()
        runBlocking {
            dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() }
        }
        val clientVersionInterceptor = ClientVersionInterceptor()
        val authHeaderInterceptor =
            AuthHeaderInterceptor(
                object : DeviceTokenProvider {
                    override suspend fun tokenFor(requestUrl: HttpUrl): String? = "device-token"

                    override suspend fun invalidate(
                        requestUrl: HttpUrl,
                        rejectedToken: String,
                    ) = Unit
                },
            )
        val baseUrlInterceptor = BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore))
        val httpClient =
            NetworkModule.provideOkHttpClient(baseUrlInterceptor, authHeaderInterceptor, clientVersionInterceptor)
        val probeHttpClient = NetworkModule.provideProbeOkHttpClient(httpClient, clientVersionInterceptor)
        return HhoApiClient(httpClient, probeHttpClient)
    }

    private fun sha256Hex(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.US_ASCII))
            .joinToString(separator = "") { "%02x".format(it) }

    private fun sha256Hex(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString(separator = "") { "%02x".format(it) }

    private fun seedAttachment(
        id: String,
        content: String,
        itemId: String = "item-1",
    ) = runBlocking {
        db.attachmentDao().upsert(
            AttachmentEntity(
                id = id,
                groupChangeSeq = 1L,
                itemId = itemId,
                category = "general",
                originalFilename = "$id.bin",
                contentType = "application/octet-stream",
                sizeBytes = content.length.toLong(),
                sha256 = sha256Hex(content),
                hasThumbnail = false,
                createdAt = 1L,
                updatedAt = 1L,
                version = 1L,
            ),
        )
    }

    private fun cache(maxCacheBytes: Long): AttachmentCache =
        AttachmentCache(
            cacheDir = cacheDir,
            apiClient = apiClient(),
            attachmentDao = db.attachmentDao(),
            maxCacheBytes = maxCacheBytes,
        )

    private fun contentOfSize(
        seedValue: Long,
        size: Int,
    ): String {
        val random = Random(seedValue)
        return String(CharArray(size) { 'a' + random.nextInt(26) })
    }

    private fun totalCacheBytes(): Long = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun tmpDirIsEmpty(): Boolean = File(cacheDir, ".tmp").listFiles().orEmpty().isEmpty()

    private fun assertBoundHolds(bound: Long) {
        assertTrue("cache dir exceeded its bound: ${totalCacheBytes()} > $bound", totalCacheBytes() <= bound)
        assertTrue(".tmp/ left a staged file behind", tmpDirIsEmpty())
    }

    private class ByIdDispatcher(private val contentById: Map<String, String>) : Dispatcher() {
        val requestCounts = ConcurrentHashMap<String, Int>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val id = request.path.orEmpty().substringAfterLast('/')
            requestCounts.merge(id, 1, Int::plus)
            val content = contentById[id] ?: return MockResponse().setResponseCode(404)
            return MockResponse().setResponseCode(200).setBody(content)
        }
    }

    @Test
    fun `sustained browsing across sequential, hot-set and randomized access keeps the bound after every view`() =
        runTest {
            val bound = 20_000L
            val count = 64
            val ids = (0 until count).map { "sb-$it" }
            val contentById =
                ids.mapIndexed { i, id ->
                    val size = if (i % 8 == 0) (bound - 200).toInt() else 200 + (i * 37) % 2800
                    id to contentOfSize(seedValue = i.toLong(), size = size)
                }.toMap()
            contentById.forEach { (id, content) -> seedAttachment(id, content) }
            server.dispatcher = ByIdDispatcher(contentById)
            val attachmentCache = cache(maxCacheBytes = bound)

            val hotSet = ids.take(6)
            val hotSetRevisits = List(8) { hotSet }.flatten()
            val random = Random(20260928L)
            val randomPass = List(40) { ids[random.nextInt(ids.size)] }
            val browsingSequence = ids + hotSetRevisits + randomPass

            for (id in browsingSequence) {
                val result = attachmentCache.file(id)
                assertTrue("view of $id failed: ${result.exceptionOrNull()}", result.isSuccess)
                assertBoundHolds(bound)
            }
        }

    @Test
    fun `a small hot set re-viewed frequently is never re-fetched, even as other attachments churn through the cache`() =
        runTest {
            val bound = 6_000L
            val hotIds = listOf("hot-0", "hot-1")
            val hotContent = hotIds.associateWith { id -> contentOfSize(seedValue = id.hashCode().toLong(), size = 300) }
            val churnIds = (0 until 40).map { "churn-$it" }
            val churnContent =
                churnIds.associateWith { id -> contentOfSize(seedValue = id.hashCode().toLong(), size = 500) }
            val contentById = hotContent + churnContent
            contentById.forEach { (id, content) -> seedAttachment(id, content) }
            val dispatcher = ByIdDispatcher(contentById)
            server.dispatcher = dispatcher
            val attachmentCache = cache(maxCacheBytes = bound)

            hotIds.forEach { assertTrue(attachmentCache.file(it).isSuccess) }

            for (churnId in churnIds) {
                assertTrue(attachmentCache.file(churnId).isSuccess)
                Thread.sleep(MTIME_TICK_MS)
                hotIds.forEach {
                    assertTrue(attachmentCache.file(it).isSuccess)
                    Thread.sleep(MTIME_TICK_MS)
                }
            }

            hotIds.forEach { id ->
                assertEquals("hot id $id was re-fetched", 1, dispatcher.requestCounts[id])
            }
        }

    @Test
    fun `concurrent sustained browsing across overlapping ids leaves the bound intact and every cached file valid`() =
        runTest {
            val bound = 15_000L
            val count = 30
            val ids = (0 until count).map { "cc-$it" }
            val contentById =
                ids.mapIndexed { i, id ->
                    id to contentOfSize(seedValue = i.toLong() + 1_000, size = 300 + (i * 53) % 1_200)
                }.toMap()
            contentById.forEach { (id, content) -> seedAttachment(id, content) }
            server.dispatcher = ByIdDispatcher(contentById)
            val attachmentCache = cache(maxCacheBytes = bound)

            val workers =
                (0 until 8).map { workerIndex ->
                    async(Dispatchers.Default) {
                        val random = Random(workerIndex * 97L + 3)
                        repeat(25) {
                            val id = ids[random.nextInt(ids.size)]
                            attachmentCache.file(id).getOrThrow()
                        }
                    }
                }
            workers.awaitAll()

            assertBoundHolds(bound)
            cacheDir.listFiles { candidate -> candidate.isFile }.orEmpty().forEach { cached ->
                val expected = contentById.getValue(cached.name)
                assertEquals(
                    "cached file ${cached.name} does not match its sha256 — a torn write?",
                    sha256Hex(expected),
                    sha256Hex(cached),
                )
            }
        }

    @Test
    fun `an oversize attachment interleaved with normal browsing is served every time without ever counting toward the bound`() =
        runTest {
            val bound = 5_000L
            val bigId = "oversize"
            val bigContent = contentOfSize(seedValue = 999L, size = (bound + 1_000).toInt())
            val normalIds = (0 until 10).map { "normal-$it" }
            val normalContent =
                normalIds.associateWith { id -> contentOfSize(seedValue = id.hashCode().toLong(), size = 400) }
            val contentById = normalContent + (bigId to bigContent)
            contentById.forEach { (id, content) -> seedAttachment(id, content) }
            val dispatcher = ByIdDispatcher(contentById)
            server.dispatcher = dispatcher
            val attachmentCache = cache(maxCacheBytes = bound)

            val sequence = normalIds.flatMapIndexed { i, id -> if (i % 2 == 0) listOf(id, bigId) else listOf(id) }
            for (id in sequence) {
                val result = attachmentCache.file(id)
                assertTrue("view of $id failed: ${result.exceptionOrNull()}", result.isSuccess)
                assertEquals(contentById.getValue(id), result.getOrThrow().readText())
                assertBoundHolds(bound)
                assertFalse(File(cacheDir, bigId).exists())
            }

            assertEquals(5, dispatcher.requestCounts[bigId])
        }

    @Test
    fun `the production default bound is A168's 200 MiB`() {
        assertEquals(209_715_200L, ATTACHMENT_CACHE_MAX_BYTES)
        assertEquals(200L * 1024 * 1024, ATTACHMENT_CACHE_MAX_BYTES)
    }

    @Test
    fun `views over a set larger than capacity keep exactly the five most recently used and refetch evicted ones lazily`() =
        runTest {
            val entrySize = 1_000
            val capacity = 5
            val bound = (entrySize * capacity).toLong()
            val ids = (0 until 12).map { "lru-$it" }
            val contentById = ids.mapIndexed { i, id -> id to contentOfSize(seedValue = 500L + i, size = entrySize) }.toMap()
            assertTrue("total size must exceed the bound", contentById.values.sumOf { it.length } > bound)
            contentById.forEach { (id, content) -> seedAttachment(id, content) }
            val dispatcher = ByIdDispatcher(contentById)
            server.dispatcher = dispatcher
            val attachmentCache = cache(maxCacheBytes = bound)

            val random = Random(161L)
            val sequence = ids + listOf(ids[7]) + ids.take(3) + List(120) { ids[random.nextInt(ids.size)] }

            val model = LinkedHashSet<String>()
            val expectedRequests = HashMap<String, Int>()
            for (id in sequence) {
                val hit = model.remove(id)
                model.add(id)
                if (!hit) expectedRequests.merge(id, 1, Int::plus)
                while (model.size > capacity) model.remove(model.first())

                val result = attachmentCache.file(id)
                assertTrue("view of $id failed: ${result.exceptionOrNull()}", result.isSuccess)
                assertEquals(contentById.getValue(id), result.getOrThrow().readText())
                Thread.sleep(MTIME_TICK_MS)

                assertBoundHolds(bound)
                val onDisk = cacheDir.listFiles { f -> f.isFile }.orEmpty().map { it.name }.toSet()
                assertEquals("on-disk set diverged from the LRU model after viewing $id", model.toSet(), onDisk)
                assertEquals(
                    "request counts diverged (hit/miss mismatch) after viewing $id",
                    expectedRequests.toMap(),
                    dispatcher.requestCounts.toMap(),
                )
            }

            assertTrue("no id was ever refetched", dispatcher.requestCounts.values.any { it > 1 })
            assertEquals(capacity, model.size)
        }

    private companion object {
        const val MTIME_TICK_MS = 15L
    }
}
