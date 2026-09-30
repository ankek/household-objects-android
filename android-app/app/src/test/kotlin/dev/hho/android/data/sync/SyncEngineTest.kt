package dev.hho.android.data.sync

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
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.SyncStateEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
class SyncEngineTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(15)

    private val server = MockWebServer()
    private lateinit var db: HhoDatabase

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("sync-engine-test", ".preferences_pb")
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

    private fun engine(): SyncEngine {
        val client = apiClient()
        return testSyncEngine(client, db)
    }

    private fun locationPageJson(
        id: String,
        nextWatermark: Long,
        hasMore: Boolean,
    ) = """
        {
          "changes": [
            {"entity_type":"location","id":"$id","group_change_seq":1,
             "data":{"id":"$id","name":"Loc $id","created_at":1,"updated_at":1,"version":1}}
          ],
          "tombstones": [],
          "next_watermark": $nextWatermark,
          "has_more": $hasMore
        }
        """.trimIndent()

    private fun itemPageJson(
        id: String,
        nextWatermark: Long,
        hasMore: Boolean,
    ) = """
        {
          "changes": [
            {"entity_type":"item","id":"$id","group_change_seq":2,
             "data":{"id":"$id","name":"Item $id","created_at":1,"updated_at":1,"version":1}}
          ],
          "tombstones": [],
          "next_watermark": $nextWatermark,
          "has_more": $hasMore
        }
        """.trimIndent()

    private fun cursorTooOldJson() = """{"cursor_too_old":true}"""

    private fun seedPreviousSuccessfulPull(
        itemId: String = "item-old",
        watermark: Long? = 5L,
    ) = runBlocking {
        db.itemDao().upsert(
            ItemEntity(
                id = itemId,
                groupChangeSeq = 1L,
                name = "Old item",
                description = null,
                locationId = null,
                quantity = 1L,
                shortCode = null,
                createdAt = 1L,
                updatedAt = 1L,
                version = 1L,
            ),
        )
        if (watermark != null) {
            db.syncStateDao().upsert(SyncStateEntity(watermark = watermark, lastSyncedAt = 1L))
        }
    }

    @Test
    fun `no persisted watermark routes through a full pull, and a stale local row absent from the server is gone afterward`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-stale", watermark = null)
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 5, hasMore = false)))

            val result = engine().sync(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertEquals(
                SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.FirstSync),
                result.getOrNull(),
            )
            assertEquals(1, server.requestCount)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
            assertNull(db.itemDao().getById("item-stale"))
            assertEquals("Item item-1", db.itemDao().getById("item-1")?.name)
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `a persisted watermark routes through an incremental pull`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-new", nextWatermark = 10, hasMore = false)))

            val result = engine().sync(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertEquals(SyncRunOutcome.IncrementalPullRan, result.getOrNull())
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":5"))
            assertEquals("Old item", db.itemDao().getById("item-old")?.name)
            assertEquals("Item item-new", db.itemDao().getById("item-new")?.name)
            assertEquals(10L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `cursor_too_old on the incremental path triggers an immediate full re-pull from since 0 in the same call, discarding rows the server has purged`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(cursorTooOldJson()))
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-new", nextWatermark = 20, hasMore = false)))

            val result = engine().sync(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertEquals(
                SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.CursorTooOldRecovery),
                result.getOrNull(),
            )
            assertEquals(2, server.requestCount)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":5"))
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
            assertNull(db.itemDao().getById("item-old"))
            assertEquals("Item item-new", db.itemDao().getById("item-new")?.name)
            assertEquals(20L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `cursor_too_old recovery, then a fetch failure mid the resulting full re-pull, leaves the previous mirror and watermark fully intact`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(cursorTooOldJson()))
            server.enqueue(MockResponse().setResponseCode(200).setBody(locationPageJson("loc-1", nextWatermark = 10, hasMore = true)))
            server.enqueue(
                MockResponse().setResponseCode(500).setBody(
                    """{"type":"urn:hho:problem:internal","title":"Internal Server Error","status":500}""",
                ),
            )

            val result = engine().sync(deviceId = "device-1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ApiError.Server)
            assertNull(db.locationDao().getById("loc-1"))
            assertEquals("Old item", db.itemDao().getById("item-old")?.name)
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }
}
