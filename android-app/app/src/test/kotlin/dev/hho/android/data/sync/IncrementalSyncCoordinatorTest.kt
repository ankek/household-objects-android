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
class IncrementalSyncCoordinatorTest {

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
        val file = File.createTempFile("incremental-sync-coordinator-test", ".preferences_pb")
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

    private fun coordinator(): IncrementalSyncCoordinator = IncrementalSyncCoordinator(apiClient(), db)

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

    private fun unknownTombstonePageJson(
        nextWatermark: Long,
        hasMore: Boolean = false,
    ) = """
        {
          "changes": [],
          "tombstones": [{"entity_type":"widget","id":"whatever","deleted_at":1}],
          "next_watermark": $nextWatermark,
          "has_more": $hasMore
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
    fun `pullIncremental starts at the persisted watermark, not zero, and merges onto the existing mirror rather than replacing it`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-new", nextWatermark = 10, hasMore = false)))

            val result = coordinator().pullIncremental(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertEquals(IncrementalPullOutcome.Applied, result.getOrNull())
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":5"))
            assertEquals("Old item", db.itemDao().getById("item-old")?.name)
            assertEquals("Item item-new", db.itemDao().getById("item-new")?.name)
            assertEquals(10L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `an incremental pull with no persisted watermark starts at since 0`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 5, hasMore = false)))

            val result = coordinator().pullIncremental(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `incremental pull commits each page's changes and watermark separately, never batching two pages into one transaction`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 0L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(locationPageJson("loc-1", nextWatermark = 10, hasMore = true)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 20, hasMore = false)))

            val result = coordinator().pullIncremental(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertEquals(2, server.requestCount)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":10"))
            assertEquals("Loc loc-1", db.locationDao().getById("loc-1")?.name)
            assertEquals("Item item-1", db.itemDao().getById("item-1")?.name)
            assertEquals(20L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `a simulated process death between page N's commit and page N+1's fetch resumes the next call at exactly page N's watermark`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 0L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(locationPageJson("loc-1", nextWatermark = 10, hasMore = true)))
            server.enqueue(
                MockResponse().setResponseCode(500).setBody(
                    """{"type":"urn:hho:problem:internal","title":"Internal Server Error","status":500}""",
                ),
            )

            val firstCallResult = coordinator().pullIncremental(deviceId = "device-1")

            assertTrue(firstCallResult.isFailure)
            assertTrue(firstCallResult.exceptionOrNull() is ApiError.Server)
            assertEquals("Loc loc-1", db.locationDao().getById("loc-1")?.name)
            assertEquals(10L, db.syncStateDao().get()?.watermark)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":10"))

            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 20, hasMore = false)))

            val secondCallResult = coordinator().pullIncremental(deviceId = "device-1")

            assertTrue(secondCallResult.isSuccess)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":10"))
            assertEquals("Item item-1", db.itemDao().getById("item-1")?.name)
            assertEquals(20L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `an apply-time failure on page N rolls back only page N, leaving every earlier page's own committed transaction intact`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 0L)
            server.enqueue(MockResponse().setResponseCode(200).setBody(locationPageJson("loc-1", nextWatermark = 10, hasMore = true)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(unknownTombstonePageJson(nextWatermark = 20, hasMore = false)))

            val result = coordinator().pullIncremental(deviceId = "device-1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnknownSyncEntityTypeException)
            assertEquals("Loc loc-1", db.locationDao().getById("loc-1")?.name)
            assertEquals(10L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `cursor_too_old on the persisted watermark is reported as CursorTooOld and touches neither the mirror nor the watermark`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"cursor_too_old":true}"""))

            val result = coordinator().pullIncremental(deviceId = "device-1")

            assertTrue(result.isSuccess)
            assertEquals(IncrementalPullOutcome.CursorTooOld, result.getOrNull())
            assertEquals("Old item", db.itemDao().getById("item-old")?.name)
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `a fetch-time failure on the first page never opens a transaction -- the prior mirror and watermark are untouched`() =
        runTest {
            seedPreviousSuccessfulPull(itemId = "item-old", watermark = 5L)
            server.enqueue(
                MockResponse().setResponseCode(500).setBody(
                    """{"type":"urn:hho:problem:internal","title":"Internal Server Error","status":500}""",
                ),
            )

            val result = coordinator().pullIncremental(deviceId = "device-1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ApiError.Server)
            assertEquals("Old item", db.itemDao().getById("item-old")?.name)
            assertNull(db.locationDao().getById("loc-1"))
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }
}
