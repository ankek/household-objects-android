package dev.hho.android.data.apiclient

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.File

class HhoApiClientAttachmentsTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(15)

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun apiClient(): HhoApiClient {
        val prefs = File.createTempFile("hho-att-test", ".preferences_pb").also { it.deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { prefs })
        runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() } }
        val cv = ClientVersionInterceptor()
        val provider =
            object : DeviceTokenProvider {
                override suspend fun tokenFor(requestUrl: HttpUrl): String? = "tok-123"

                override suspend fun invalidate(
                    requestUrl: HttpUrl,
                    rejectedToken: String,
                ) = Unit
            }
        val http = NetworkModule.provideOkHttpClient(BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore)), AuthHeaderInterceptor(provider), cv)
        return HhoApiClient(http, NetworkModule.provideProbeOkHttpClient(http, cv))
    }

    private val payload = ByteArray(300_000) { (it * 31 % 251).toByte() }

    private fun payloadFile(name: String = "receipt.jpg"): File {
        val dir = File.createTempFile("hho-att", "dir").also { it.delete(); it.mkdirs(); it.deleteOnExit() }
        return File(dir, name).also { it.writeBytes(payload); it.deleteOnExit() }
    }

    private fun attachmentJson(
        id: String = "a-1",
        sha: String = "ab".repeat(32),
        size: Long = payload.size.toLong(),
    ) = """{"id":"$id","item_id":"item-9","category":"receipt","original_filename":"receipt.jpg","content_type":"image/jpeg",""" +
        """"size_bytes":$size,"sha256":"$sha","created_at":1700000000000,"updated_at":1700000000001,"version":1,"has_thumbnail":true}"""

    private fun problem(status: Int) =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/problem+json")
            .setBody("""{"type":"about:blank","title":"t","status":$status}""")

    @Test
    fun `upload sends exact multipart request and decodes 201`() {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody(attachmentJson()))
        val result = runBlocking { apiClient().uploadAttachment("item-9", AttachmentCategory.RECEIPT, payloadFile()) }

        val info = result.getOrThrow()
        assertEquals("a-1", info.id)
        assertEquals("receipt", info.category)
        assertEquals("ab".repeat(32), info.sha256)
        assertEquals(payload.size.toLong(), info.sizeBytes)
        assertTrue(info.hasThumbnail)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/items/item-9/attachments", req.path)
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
        assertNotNull(req.getHeader("X-HHO-Client-Version"))
        val contentType = req.getHeader("Content-Type")!!
        assertTrue(contentType, contentType.startsWith("multipart/form-data; boundary="))
        val boundary = contentType.substringAfter("boundary=")

        val body = req.body.readByteArray()
        val text = String(body, Charsets.ISO_8859_1)
        val parts = text.split("--$boundary").drop(1).filter { it.startsWith("\r\n") }
        assertEquals(2, parts.size)
        val categoryPart = parts.single { it.contains("""name="category"""") }
        assertTrue(categoryPart.contains("\r\n\r\nreceipt\r\n"))
        assertTrue(!categoryPart.contains("filename="))
        val filePart = parts.single { it.contains("""name="file"""") }
        assertTrue(filePart.contains("""filename="receipt.jpg""""))
        assertTrue(filePart.contains("Content-Type: image/jpeg"))
        val headerEnd = filePart.indexOf("\r\n\r\n") + 4
        val fileBytes = filePart.substring(headerEnd, filePart.length - 2).toByteArray(Charsets.ISO_8859_1)
        assertArrayEquals(payload, fileBytes)
    }

    @Test
    fun `upload 404 maps to NotFound`() {
        server.enqueue(problem(404))
        val error = runBlocking { apiClient().uploadAttachment("nope", AttachmentCategory.IMAGE, payloadFile()) }.exceptionOrNull()
        assertTrue(error.toString(), error is ApiError.NotFound)
    }

    @Test
    fun `upload 413 maps to distinguishable PayloadTooLarge`() {
        server.enqueue(problem(413))
        val error = runBlocking { apiClient().uploadAttachment("item-9", AttachmentCategory.IMAGE, payloadFile()) }.exceptionOrNull()
        assertTrue(error.toString(), error is ApiError.PayloadTooLarge)
        assertEquals(413, (error as ApiError.Validation).status)
    }

    @Test
    fun `upload 500 maps to Server`() {
        server.enqueue(problem(500))
        val error = runBlocking { apiClient().uploadAttachment("item-9", AttachmentCategory.IMAGE, payloadFile()) }.exceptionOrNull()
        assertTrue(error.toString(), error is ApiError.Server)
        assertEquals(500, (error as ApiError.Server).status)
    }

    @Test
    fun `upload network failure maps to Network`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val error = runBlocking { apiClient().uploadAttachment("item-9", AttachmentCategory.IMAGE, payloadFile()) }.exceptionOrNull()
        assertTrue(error.toString(), error is ApiError.Network)
    }

    @Test
    fun `list decodes sha256 and size and hits the list path`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("""{"attachments":[${attachmentJson("a-1", "cd".repeat(32), 42)},${attachmentJson("a-2", "ef".repeat(32), 7)}]}"""),
        )
        val list = runBlocking { apiClient().listAttachments("item-9") }.getOrThrow()
        assertEquals(listOf("a-1", "a-2"), list.map { it.id })
        assertEquals("cd".repeat(32), list[0].sha256)
        assertEquals(42L, list[0].sizeBytes)
        assertEquals(7L, list[1].sizeBytes)

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/v1/items/item-9/attachments", req.path)
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
        assertNotNull(req.getHeader("X-HHO-Client-Version"))
    }

    @Test
    fun `list empty and 404`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("""{"attachments":[]}"""))
        assertTrue(runBlocking { apiClient().listAttachments("item-9") }.getOrThrow().isEmpty())
        server.enqueue(problem(404))
        assertTrue(runBlocking { apiClient().listAttachments("x") }.exceptionOrNull() is ApiError.NotFound)
    }
}
