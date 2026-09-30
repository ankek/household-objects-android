package dev.hho.android.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.room.ConflictOrigin
import dev.hho.android.data.room.ConflictRecordEntity
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.SyncRunStateEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ConflictLogSyncTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private lateinit var db: HhoDatabase
    private lateinit var sync: ConflictLogSync
    private val server = MockWebServer()

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        sync = ConflictLogSync(apiClient(), db.conflictRecordDao(), SyncRunRecorder(db.syncRunStateDao()))
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun apiClient(): HhoApiClient {
        val file = File.createTempFile("hho-conflict-test", ".preferences_pb").also { it.deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() } }
        val cv = ClientVersionInterceptor()
        val provider = object : DeviceTokenProvider {
            override suspend fun tokenFor(requestUrl: HttpUrl): String? = "tok-secret"
            override suspend fun invalidate(requestUrl: HttpUrl, rejectedToken: String) = Unit
        }
        val http = NetworkModule.provideOkHttpClient(BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore)), AuthHeaderInterceptor(provider), cv)
        return HhoApiClient(http, NetworkModule.provideProbeOkHttpClient(http, cv))
    }

    private fun entry(
        id: String,
        at: Long,
        mutation: String? = "m-$id",
        entity: String = "e-$id",
        field: String = "name",
        server: String? = "\"S$id\"",
        losing: String? = "\"L$id\"",
    ): String {
        fun q(v: String?) = v?.let { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" } ?: "null"
        return """{"id":"$id","mutation_id":${q(mutation)},"entity_type":"item","entity_id":"$entity","field_name":"$field",""" +
            """"server_value":${q(server)},"losing_client_value":${q(losing)},"detected_at":$at}"""
    }

    private fun page(cursor: String?, vararg entries: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
            .setBody("""{"conflicts":[${entries.joinToString(",")}],"next_cursor":${cursor?.let { "\"$it\"" } ?: "null"}}""")

    private fun problem(status: Int) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"t","status":$status,"detail":"x"}""")

    private suspend fun watermark() = db.syncRunStateDao().get()?.conflictCursor

    @Test
    fun `first sync pages through every page newest first and stores the watermark`() = runBlocking {
        server.enqueue(page("c1", entry("d", 400), entry("c", 300)))
        server.enqueue(page("c2", entry("b", 200)))
        server.enqueue(page(null, entry("a", 100)))
        val r = sync.sync(pageSize = 2).getOrThrow()
        assertEquals(ConflictLogSyncResult(4, 4, 0, true), r)
        assertEquals(4, db.conflictRecordDao().getAll().size)
        assertTrue(db.conflictRecordDao().getAll().all { it.origin == ConflictOrigin.SERVER_LOG })
        assertEquals("400", watermark())
        val r1 = server.takeRequest()
        assertNull(r1.requestUrl!!.queryParameter("after"))
        assertEquals("2", r1.requestUrl!!.queryParameter("limit"))
        assertEquals("c1", server.takeRequest().requestUrl!!.queryParameter("after"))
        assertEquals("c2", server.takeRequest().requestUrl!!.queryParameter("after"))
    }

    @Test
    fun `second sync fetches only newer entries and stops at the known one`() = runBlocking {
        server.enqueue(page("c1", entry("b", 200)))
        server.enqueue(page(null, entry("a", 100)))
        sync.sync(pageSize = 1).getOrThrow()
        server.takeRequest(); server.takeRequest()
        server.enqueue(page("n1", entry("e", 400), entry("d", 300)))
        server.enqueue(page("n2", entry("b", 200), entry("a", 100)))
        val r = sync.sync(pageSize = 2).getOrThrow()
        assertEquals(2, r.inserted)
        assertEquals(1, r.merged)
        assertEquals(2, server.requestCount - 2)
        assertEquals(4, db.conflictRecordDao().getAll().size)
        assertEquals("400", watermark())
    }

    @Test
    fun `an up to date sync with a full log stops after the tie page and inserts nothing`() = runBlocking {
        server.enqueue(page(null, entry("a", 100)))
        sync.sync().getOrThrow()
        server.enqueue(page("older", entry("a", 100)))
        server.enqueue(page(null, entry("0", 50)))
        val r = sync.sync().getOrThrow()
        assertEquals(2, server.requestCount - 1)
        
        assertEquals(0, r.inserted)
        assertEquals(1, db.conflictRecordDao().getAll().size)
    }

    @Test
    fun `merges into an existing LOCAL_PUSH row without a duplicate`() = runBlocking {
        db.conflictRecordDao().insertIgnore(
            ConflictRecordEntity(
                mutationId = "m-x", entityType = "item", entityId = "e-x", fieldName = "name",
                losingValueJson = "\"mine\"", serverValueJson = null, detectedAt = 50, origin = ConflictOrigin.LOCAL_PUSH,
            ),
        )
        server.enqueue(page(null, entry("x", 500, server = "\"theirs\"", losing = null)))
        val r = sync.sync().getOrThrow()
        assertEquals(1, r.merged)
        assertEquals(0, r.inserted)
        val rows = db.conflictRecordDao().getAll()
        assertEquals(1, rows.size)
        assertEquals("\"theirs\"", rows[0].serverValueJson)
        assertEquals("\"mine\"", rows[0].losingValueJson)
        assertEquals(500L, rows[0].detectedAt)
        assertEquals(ConflictOrigin.SERVER_LOG, rows[0].origin)
    }

    @Test
    fun `null mutation_id entries are idempotent and distinct per log id`() = runBlocking {
        val a = entry("a", 100, mutation = null, entity = "same", field = "_entity", server = null, losing = null)
        val b = entry("b", 100, mutation = null, entity = "same", field = "_entity", server = null, losing = null)
        server.enqueue(page(null, b, a))
        sync.sync().getOrThrow()
        server.enqueue(page(null, b, a))
        val r = sync.sync().getOrThrow()
        assertEquals(0, r.inserted)
        assertEquals(2, r.merged)
        val rows = db.conflictRecordDao().getAll()
        assertEquals(2, rows.size)
        assertEquals(setOf("server:a", "server:b"), rows.map { it.mutationId }.toSet())
        assertTrue(rows.all { it.serverValueJson == null && it.losingValueJson == null })
    }

    @Test
    fun `explicit nulls and JSON text values decode and are stored verbatim`() = runBlocking {
        server.enqueue(page(null, entry("a", 100, server = "{\"k\":[1,2]}", losing = null)))
        sync.sync().getOrThrow()
        val row = db.conflictRecordDao().getAll().single()
        assertEquals("{\"k\":[1,2]}", row.serverValueJson)
        assertNull(row.losingValueJson)
    }

    @Test
    fun `401 and 5xx map to ApiError and do not advance the stored position`() = runBlocking {
        server.enqueue(page(null, entry("a", 100)))
        sync.sync().getOrThrow()
        server.enqueue(problem(401))
        assertTrue(sync.sync().exceptionOrNull() is ApiError.Unauthorized)
        server.enqueue(problem(501))
        assertTrue(sync.sync().exceptionOrNull() is ApiError.Server)
        server.enqueue(page("c", entry("z", 900)))
        server.enqueue(problem(503))
        assertTrue(sync.sync(pageSize = 1).isFailure)
        assertEquals("100", watermark())
        server.enqueue(page("c", entry("z", 900)))
        server.enqueue(page(null, entry("a", 100)))
        val r = sync.sync(pageSize = 1).getOrThrow()
        assertEquals(0, r.inserted)
        assertEquals("900", watermark())
        assertEquals(2, db.conflictRecordDao().getAll().size)
    }

    @Test
    fun `hitting the page cap keeps the rows but does not advance the watermark`() = runBlocking {
        server.enqueue(page("c1", entry("b", 200)))
        server.enqueue(page("c2", entry("a", 100)))
        val r = sync.sync(pageSize = 1, maxPages = 2).getOrThrow()
        assertFalse(r.complete)
        assertNull(watermark())
        assertEquals(2, db.conflictRecordDao().getAll().size)
    }

    @Test
    fun `request carries limit, bearer auth and the client version header`() = runBlocking {
        server.enqueue(page(null))
        sync.sync(pageSize = 7).getOrThrow()
        val req = server.takeRequest()
        assertEquals("/api/v1/sync/conflicts", req.requestUrl!!.encodedPath)
        assertEquals("GET", req.method)
        assertEquals("7", req.requestUrl!!.queryParameter("limit"))
        assertEquals("Bearer tok-secret", req.getHeader("Authorization"))
        assertTrue(req.getHeader("X-HHO-Client-Version") != null)
        assertNull(watermark())
    }

    @Test
    fun `advancing the watermark preserves the other sync run state`() = runBlocking {
        db.syncRunStateDao().upsert(SyncRunStateEntity(lastRunAt = 5, lastError = "boom"))
        server.enqueue(page(null, entry("a", 100)))
        sync.sync().getOrThrow()
        val s = db.syncRunStateDao().get()!!
        assertEquals(5L, s.lastRunAt)
        assertEquals("boom", s.lastError)
        assertEquals("100", s.conflictCursor)
    }
}
