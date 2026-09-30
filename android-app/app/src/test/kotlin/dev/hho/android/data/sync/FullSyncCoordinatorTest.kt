package dev.hho.android.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.apiclient.ApiError
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.apiclient.UnknownSyncEntityTypeException
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
class FullSyncCoordinatorTest {

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
        val file = File.createTempFile("full-sync-coordinator-test", ".preferences_pb")
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

    private fun coordinator(): FullSyncCoordinator = FullSyncCoordinator(apiClient(), db)

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

    private fun emptyPageJson(
        nextWatermark: Long,
        hasMore: Boolean,
    ) = """{"changes":[],"tombstones":[],"next_watermark":$nextWatermark,"has_more":$hasMore}"""

    private fun unknownTombstonePageJson(
        nextWatermark: Long,
    ) = """
        {
          "changes": [],
          "tombstones": [{"entity_type":"widget","id":"whatever","deleted_at":1}],
          "next_watermark": $nextWatermark,
          "has_more": false
        }
        """.trimIndent()

    private fun seedPreviousSuccessfulPull(
        itemId: String = "item-old",
        watermark: Long = 5L,
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
        db.syncStateDao().upsert(SyncStateEntity(watermark = watermark, lastSyncedAt = 1L))
    }

    @Test
    fun `pullFull loops until has_more is false, applying every page and advancing since from each page's own next_watermark`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(locationPageJson("loc-1", nextWatermark = 10, hasMore = true)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 20, hasMore = true)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(emptyPageJson(nextWatermark = 30, hasMore = false)))

            val result = coordinator().pullFull(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertEquals(3, server.requestCount)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":10"))
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":20"))
            assertEquals("Loc loc-1", db.locationDao().getById("loc-1")?.name)
            assertEquals("Item item-1", db.itemDao().getById("item-1")?.name)
            assertEquals(30L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `a single-page full pull (has_more false immediately) issues exactly one request`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 5, hasMore = false)))

            val result = coordinator().pullFull(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertEquals(1, server.requestCount)
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `a full pull always starts at since 0, even when a previous watermark is already stored locally`() =
        runTest {
            seedPreviousSuccessfulPull(watermark = 999L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 5, hasMore = false)))

            coordinator().pullFull(deviceId = "device-1")

            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
        }

    @Test
    fun `a successful full pull discards mirror rows the incoming pages never mention`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-stale", watermark = 1L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 5, hasMore = false)))

            val result = coordinator().pullFull(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertNull(db.itemDao().getById("item-stale"))
            assertEquals("Item item-1", db.itemDao().getById("item-1")?.name)
        }

    @Test
    fun `an apply-time failure on a later page rolls back the whole pull, including pages already applied in this same transaction, and never advances the watermark`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(locationPageJson("loc-1", nextWatermark = 10, hasMore = true)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 20, hasMore = true)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(unknownTombstonePageJson(nextWatermark = 30)))

            val result = coordinator().pullFull(deviceId = "device-1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnknownSyncEntityTypeException)
            assertNull(db.locationDao().getById("loc-1"))
            assertNull(db.itemDao().getById("item-1"))
            assertEquals("Old item", db.itemDao().getById("item-old")?.name)
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `a fetch-time failure on a later page never opens a transaction at all -- the prior mirror and watermark are untouched`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(locationPageJson("loc-1", nextWatermark = 10, hasMore = true)))
            server.enqueue(
                MockResponse().setResponseCode(500).setBody(
                    """{"type":"urn:hho:problem:internal","title":"Internal Server Error","status":500}""",
                ),
            )

            val result = coordinator().pullFull(deviceId = "device-1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ApiError.Server)
            assertEquals(2, server.requestCount)
            assertNull(db.locationDao().getById("loc-1"))
            assertEquals("Old item", db.itemDao().getById("item-old")?.name)
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `cursor_too_old on a full pull's own since fails as drift and touches nothing`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"cursor_too_old":true}"""))

            val result = coordinator().pullFull(deviceId = "device-1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnexpectedCursorTooOldDuringFullPullException)
            assertEquals("Old item", db.itemDao().getById("item-old")?.name)
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }
}
