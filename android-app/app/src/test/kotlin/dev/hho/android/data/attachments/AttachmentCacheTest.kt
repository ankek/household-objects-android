package dev.hho.android.data.attachments

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.room.AttachmentDao
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AttachmentCacheTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(15)

    private val server = MockWebServer()
    private lateinit var db: HhoDatabase
    private lateinit var scratchDir: File

    @org.junit.Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        scratchDir = File.createTempFile("attachment-cache-test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
        scratchDir.deleteRecursively()
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("attachment-cache-test", ".preferences_pb")
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

    private fun seedAttachment(
        id: String,
        content: String,
        itemId: String = "item-1",
        sha256: String = sha256Hex(content),
        sizeBytes: Long = content.length.toLong(),
    ) = runBlocking {
        db.attachmentDao().upsert(
            AttachmentEntity(
                id = id,
                groupChangeSeq = 1L,
                itemId = itemId,
                category = "general",
                originalFilename = "$id.bin",
                contentType = "application/octet-stream",
                sizeBytes = sizeBytes,
                sha256 = sha256,
                hasThumbnail = false,
                createdAt = 1L,
                updatedAt = 1L,
                version = 1L,
            ),
        )
    }

    private fun cache(maxCacheBytes: Long = ATTACHMENT_CACHE_MAX_BYTES): AttachmentCache =
        AttachmentCache(
            cacheDir = File(scratchDir, "attachments"),
            apiClient = apiClient(),
            attachmentDao = db.attachmentDao(),
            maxCacheBytes = maxCacheBytes,
        )

    @Test
    fun `first view fetches the bytes over the network, a second view of the same id serves the cached copy`() =
        runTest {
            val content = "hello attachment bytes"
            seedAttachment(id = "a1", content = content)
            server.enqueue(MockResponse().setResponseCode(200).setBody(content))
            val attachmentCache = cache()

            val first = attachmentCache.file("a1")
            assertTrue(first.isSuccess)
            assertEquals(content, first.getOrThrow().readText())
            assertEquals(1, server.requestCount)

            val second = attachmentCache.file("a1")
            assertTrue(second.isSuccess)
            assertEquals(content, second.getOrThrow().readText())
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `the download request targets the attachment's own item and id`() =
        runTest {
            val content = "x"
            seedAttachment(id = "att-99", content = content, itemId = "item-77")
            server.enqueue(MockResponse().setResponseCode(200).setBody(content))

            cache().file("att-99")

            assertEquals("/api/v1/items/item-77/attachments/att-99", server.takeRequest().path)
        }

    @Test
    fun `a body whose sha256 does not match the mirrored metadata is not cached`() =
        runTest {
            val content = "corrupted-on-the-wire"
            seedAttachment(id = "a2", content = content, sha256 = "0".repeat(64))
            server.enqueue(MockResponse().setResponseCode(200).setBody(content))
            val attachmentCache = cache()

            val result = attachmentCache.file("a2")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is AttachmentIntegrityException)
            assertFalse(File(scratchDir, "attachments/a2").exists())
            assertTrue(File(scratchDir, "attachments/.tmp").listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `a body whose length does not match the mirrored size is not cached`() =
        runTest {
            val content = "short"
            seedAttachment(id = "a3", content = content, sizeBytes = 999L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(content))
            val attachmentCache = cache()

            val result = attachmentCache.file("a3")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is AttachmentIntegrityException)
            assertFalse(File(scratchDir, "attachments/a3").exists())
        }

    @Test
    fun `a network failure leaves nothing cached and surfaces as an ApiError`() =
        runTest {
            seedAttachment(id = "a4", content = "irrelevant")
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"type":"urn:hho:problem:internal","title":"boom","status":500}"""))
            val attachmentCache = cache()

            val result = attachmentCache.file("a4")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ApiError.Server)
            assertFalse(File(scratchDir, "attachments/a4").exists())
        }

    @Test
    fun `no metadata row for the id fails without ever calling the network`() =
        runTest {
            val attachmentCache = cache()

            val result = attachmentCache.file("does-not-exist")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is AttachmentNotCachedException)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `two concurrent requests for the same id produce exactly one fetch`() =
        runTest {
            val content = "shared-bytes"
            seedAttachment(id = "a5", content = content)
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(content)
                    .setBodyDelay(300, TimeUnit.MILLISECONDS),
            )
            val attachmentCache = cache()

            val first = async(Dispatchers.Default) { attachmentCache.file("a5") }
            val second = async(Dispatchers.Default) { attachmentCache.file("a5") }
            val (r1, r2) = awaitAll(first, second)

            assertTrue(r1.isSuccess)
            assertTrue(r2.isSuccess)
            assertEquals(content, (r1.getOrThrow() as File).readText())
            assertEquals(content, (r2.getOrThrow() as File).readText())
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `eviction removes the least-recently-used entry, not simply the oldest write`() =
        runTest {
            val contentA = "0123456789"
            val contentB = "abcdefghij"
            val contentC = "ABCDEFGHIJ"
            seedAttachment(id = "lru-a", content = contentA)
            seedAttachment(id = "lru-b", content = contentB)
            seedAttachment(id = "lru-c", content = contentC)
            val attachmentCache = cache(maxCacheBytes = 25)

            server.enqueue(MockResponse().setResponseCode(200).setBody(contentA))
            attachmentCache.file("lru-a")
            Thread.sleep(10)
            server.enqueue(MockResponse().setResponseCode(200).setBody(contentB))
            attachmentCache.file("lru-b")
            Thread.sleep(10)
            attachmentCache.file("lru-a")
            Thread.sleep(10)
            server.enqueue(MockResponse().setResponseCode(200).setBody(contentC))
            attachmentCache.file("lru-c")

            assertTrue(File(scratchDir, "attachments/lru-a").exists())
            assertFalse(File(scratchDir, "attachments/lru-b").exists())
            assertTrue(File(scratchDir, "attachments/lru-c").exists())
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `an attachment larger than the whole bound is served without being cached`() =
        runTest {
            val content = "this-is-longer-than-the-tiny-bound"
            seedAttachment(id = "big", content = content)
            server.enqueue(MockResponse().setResponseCode(200).setBody(content))
            val attachmentCache = cache(maxCacheBytes = 4)

            val result = attachmentCache.file("big")

            assertTrue(result.isSuccess)
            assertEquals(content, result.getOrThrow().readText())
            assertFalse(File(scratchDir, "attachments/big").exists())
        }

    @Test
    fun `a fresh AttachmentCache instance over the same directory rebuilds its index from disk, without re-fetching`() =
        runTest {
            val content = "durable-bytes"
            seedAttachment(id = "a6", content = content)
            server.enqueue(MockResponse().setResponseCode(200).setBody(content))
            val client = apiClient()
            val cacheDir = File(scratchDir, "attachments")
            val first = AttachmentCache(cacheDir, client, db.attachmentDao(), ATTACHMENT_CACHE_MAX_BYTES)
            assertTrue(first.file("a6").isSuccess)
            assertEquals(1, server.requestCount)

            val restarted = AttachmentCache(cacheDir, client, db.attachmentDao(), ATTACHMENT_CACHE_MAX_BYTES)
            val result = restarted.file("a6")

            assertTrue(result.isSuccess)
            assertEquals(content, result.getOrThrow().readText())
            assertEquals(1, server.requestCount)
            assertNotEquals(first, restarted)
        }
}
