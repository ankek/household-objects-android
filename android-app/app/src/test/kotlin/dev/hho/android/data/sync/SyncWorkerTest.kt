package dev.hho.android.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceIdProvider
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
import dev.hho.android.domain.AuthRepository
import dev.hho.android.domain.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class SyncWorkerTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(15)

    private val server = MockWebServer()
    private lateinit var db: HhoDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()

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
        val file = File.createTempFile("sync-worker-test", ".preferences_pb")
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

    private fun realSyncEngine(): SyncEngine {
        val client = apiClient()
        return testSyncEngine(client, db)
    }

    private class FakeAuthRepository(initial: AuthState) : AuthRepository {
        private val state = MutableStateFlow(initial)
        override val authState: Flow<AuthState> = state

        override suspend fun login(
            username: String,
            password: String,
        ): Result<Unit> = error("SyncWorkerTest: login() is not exercised by SyncWorker")

        override suspend fun logout() = error("SyncWorkerTest: logout() is not exercised by SyncWorker")
    }

    private class FakeDeviceIdProvider(private val id: String) : DeviceIdProvider {
        var callCount = 0
            private set

        override suspend fun deviceId(): String {
            callCount++
            return id
        }
    }

    private fun buildWorker(
        syncEngine: SyncEngine,
        deviceIdProvider: DeviceIdProvider,
        authRepository: AuthRepository,
    ): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        SyncWorker(appContext, workerParameters, syncEngine, deviceIdProvider, authRepository, SyncRunRecorder(db.syncRunStateDao()))
                },
            )
            .build()

    private fun itemPageJson(
        id: String,
        nextWatermark: Long,
        hasMore: Boolean,
    ) = """
        {
          "changes": [
            {"entity_type":"item","id":"$id","group_change_seq":1,
             "data":{"id":"$id","name":"Item $id","created_at":1,"updated_at":1,"version":1}}
          ],
          "tombstones": [],
          "next_watermark": $nextWatermark,
          "has_more": $hasMore
        }
        """.trimIndent()

    private fun cursorTooOldJson() = """{"cursor_too_old":true}"""

    @Test
    fun `logged out - no-op success with no network call and no device id lookup`() =
        runTest {
            val deviceIdProvider = FakeDeviceIdProvider("device-unused")
            val worker = buildWorker(realSyncEngine(), deviceIdProvider, FakeAuthRepository(AuthState.LoggedOut))

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(0, server.requestCount)
            assertEquals(0, deviceIdProvider.callCount)
        }

    @Test
    fun `logged in - delegates to the real SyncEngine (device id forwarded, mirror actually updated), returns success`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 5, hasMore = false)))
            val worker =
                buildWorker(realSyncEngine(), FakeDeviceIdProvider("device-xyz"), FakeAuthRepository(AuthState.LoggedIn))

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, server.requestCount)
            assertEquals("Item item-1", db.itemDao().getById("item-1")?.name)
            assertEquals(5L, db.syncStateDao().get()?.watermark)
        }

    @Test
    fun `logged in - the resolved device id is sent as the sync request's device_id`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-1", nextWatermark = 5, hasMore = false)))
            val worker =
                buildWorker(realSyncEngine(), FakeDeviceIdProvider("device-xyz"), FakeAuthRepository(AuthState.LoggedIn))

            worker.doWork()

            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"device_id\":\"device-xyz\""))
        }

    @Test
    fun `cursor_too_old triggers SyncEngine's internal full re-pull and still returns success, never retry`() =
        runTest {
            runBlocking {
                db.itemDao().upsert(
                    ItemEntity(
                        id = "item-old",
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
                db.syncStateDao().upsert(SyncStateEntity(watermark = 5L, lastSyncedAt = 1L))
            }
            server.enqueue(MockResponse().setResponseCode(200).setBody(cursorTooOldJson()))
            server.enqueue(MockResponse().setResponseCode(200).setBody(itemPageJson("item-new", nextWatermark = 20, hasMore = false)))
            val worker =
                buildWorker(realSyncEngine(), FakeDeviceIdProvider("device-xyz"), FakeAuthRepository(AuthState.LoggedIn))

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(2, server.requestCount)
            assertNull(db.itemDao().getById("item-old"))
            assertEquals("Item item-new", db.itemDao().getById("item-new")?.name)
        }

    @Test
    fun `a server failure returns retry, not failure or success`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(500).setBody(
                    """{"type":"urn:hho:problem:internal","title":"Internal Server Error","status":500}""",
                ),
            )
            val worker =
                buildWorker(realSyncEngine(), FakeDeviceIdProvider("device-xyz"), FakeAuthRepository(AuthState.LoggedIn))

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    private suspend fun queueOneEdit() =
        dev.hho.android.data.outbox.OutboxRepository(db).enqueue(
            dev.hho.android.data.outbox.LocalMutation(
                "item", "a", "upsert", 1,
                kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("New"))
                },
            ),
        )

    private fun worker() =
        buildWorker(realSyncEngine(), FakeDeviceIdProvider("device-xyz"), FakeAuthRepository(AuthState.LoggedIn))

    @Test
    fun `a failed push (5xx) returns retry and never pulls`() =
        runTest {
            queueOneEdit()
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"type":"about:blank","title":"t","status":500}"""))

            assertEquals(ListenableWorker.Result.retry(), worker().doWork())
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a 401 on push is success (no retry spin), rows stay queued, nothing pulled`() =
        runTest {
            queueOneEdit()
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"about:blank","title":"t","status":401}"""))

            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals(1, server.requestCount)
            assertEquals(1, db.outboxDao().getAllOrdered().size)
        }

    @Test
    fun `a 426 on push is success (no retry spin) and the minimum version is recorded`() =
        runTest {
            queueOneEdit()
            server.enqueue(
                MockResponse().setResponseCode(426).setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"type":"about:blank","title":"t","status":426,"detail":"old","minimum_version":"9.9.9"}"""),
            )

            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals(1, server.requestCount)
            assertTrue(db.syncRunStateDao().get()!!.lastError!!.contains("9.9.9"))
        }

    @Test
    fun `an unexpected crash inside the engine is recorded and retried`() =
        runTest {
            val crashing = testSyncEngine(apiClient(), db, push = { throw IllegalStateException("kaput") })
            val worker =
                buildWorker(crashing, FakeDeviceIdProvider("device-xyz"), FakeAuthRepository(AuthState.LoggedIn))

            assertEquals(ListenableWorker.Result.retry(), worker.doWork())
            assertEquals("IllegalStateException", db.syncRunStateDao().get()!!.lastError)
        }

    @Test
    fun `a clean run leaves last_run_at set and no error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(itemPageJson("a", 3, false)))
            val worker =
                buildWorker(realSyncEngine(), FakeDeviceIdProvider("device-xyz"), FakeAuthRepository(AuthState.LoggedIn))

            assertEquals(ListenableWorker.Result.success(), worker.doWork())
            val row = db.syncRunStateDao().get()!!
            assertEquals(1_000L, row.lastRunAt)
            assertEquals(null, row.lastError)
        }
}
