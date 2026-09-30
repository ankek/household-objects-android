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
import dev.hho.android.data.room.deleteHhoDatabaseFile
import dev.hho.android.data.room.fileHhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.flow.first
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
class ConvergenceTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private val server = MockWebServer()

    @Before
    fun setUp() = Unit

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("convergence-test", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    private fun apiClient(server: MockWebServer = this.server): HhoApiClient {
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

    private fun engineFor(
        db: HhoDatabase,
        server: MockWebServer = this.server,
    ): SyncEngine {
        val client = apiClient(server)
        return testSyncEngine(client, db)
    }

    private fun itemChangeJson(
        id: String,
        name: String,
        groupChangeSeq: Long = 1,
    ) = """
        {"entity_type":"item","id":"$id","group_change_seq":$groupChangeSeq,
         "data":{"id":"$id","name":"$name","created_at":1,"updated_at":1,"version":1}}
        """.trimIndent()

    private fun locationChangeJson(
        id: String,
        name: String,
        groupChangeSeq: Long = 1,
    ) = """
        {"entity_type":"location","id":"$id","group_change_seq":$groupChangeSeq,
         "data":{"id":"$id","name":"$name","created_at":1,"updated_at":1,"version":1}}
        """.trimIndent()

    private fun labelChangeJson(
        id: String,
        name: String,
        color: String,
        groupChangeSeq: Long = 1,
    ) = """
        {"entity_type":"label","id":"$id","group_change_seq":$groupChangeSeq,
         "data":{"id":"$id","name":"$name","color":"$color","created_at":1,"updated_at":1,"version":1}}
        """.trimIndent()

    private fun itemLabelChangeJson(
        id: String,
        itemId: String,
        labelId: String,
        groupChangeSeq: Long = 1,
    ) = """
        {"entity_type":"item_label","id":"$id","group_change_seq":$groupChangeSeq,
         "data":{"item_id":"$itemId","label_id":"$labelId"}}
        """.trimIndent()

    private fun warrantyBlockChangeJson(
        id: String,
        itemId: String,
        holder: String,
        groupChangeSeq: Long = 1,
    ) = """
        {"entity_type":"warranty_block","id":"$id","group_change_seq":$groupChangeSeq,
         "data":{"is_lifetime":false,"item_id":"$itemId","holder":"$holder","created_at":1,"updated_at":1,"version":1}}
        """.trimIndent()

    private fun tombstoneJson(
        entityType: String,
        id: String,
    ) = """{"entity_type":"$entityType","id":"$id","deleted_at":1}"""

    private fun pageJson(
        changes: List<String>,
        tombstones: List<String> = emptyList(),
        nextWatermark: Long,
        hasMore: Boolean,
    ) = """{"changes":[${changes.joinToString(",")}],"tombstones":[${tombstones.joinToString(",")}],"next_watermark":$nextWatermark,"has_more":$hasMore}"""

    private fun errorResponse() =
        MockResponse().setResponseCode(500).setBody(
            """{"type":"urn:hho:problem:internal","title":"Internal Server Error","status":500}""",
        )

    private fun initialPages(): List<String> =
        listOf(
            pageJson(changes = listOf(locationChangeJson("loc-1", "Loc 1")), nextWatermark = 10, hasMore = true),
            pageJson(
                changes = listOf(itemChangeJson("item-1", "Item 1"), itemChangeJson("item-2", "Item 2")),
                nextWatermark = 50,
                hasMore = true,
            ),
            pageJson(
                changes = listOf(itemChangeJson("item-3", "Item 3"), labelChangeJson("label-1", "Label 1", "red")),
                nextWatermark = 80,
                hasMore = true,
            ),
            pageJson(
                changes = listOf(itemLabelChangeJson("il-1", "item-1", "label-1")),
                nextWatermark = 100,
                hasMore = false,
            ),
        )

    private fun round1Pages(): List<String> =
        listOf(
            pageJson(changes = listOf(locationChangeJson("loc-2", "Loc 2")), nextWatermark = 101, hasMore = true),
            pageJson(changes = listOf(itemChangeJson("item-1", "Item 1 updated")), nextWatermark = 102, hasMore = true),
            pageJson(changes = listOf(warrantyBlockChangeJson("wb-1", "item-2", "Acme")), nextWatermark = 103, hasMore = true),
            pageJson(
                changes = emptyList(),
                tombstones = listOf(tombstoneJson("item", "item-3")),
                nextWatermark = 104,
                hasMore = false,
            ),
        )

    private fun round2Pages(): List<String> =
        listOf(
            pageJson(changes = listOf(itemChangeJson("item-4", "Item 4", groupChangeSeq = 555)), nextWatermark = 105, hasMore = true),
            pageJson(changes = listOf(itemChangeJson("item-5", "Item 5", groupChangeSeq = 555)), nextWatermark = 106, hasMore = true),
            pageJson(changes = listOf(itemLabelChangeJson("il-2", "item-4", "label-1")), nextWatermark = 107, hasMore = true),
            pageJson(changes = listOf(labelChangeJson("label-1", "Label 1", "blue")), nextWatermark = 108, hasMore = true),
            pageJson(
                changes = emptyList(),
                tombstones = listOf(tombstoneJson("location", "loc-1")),
                nextWatermark = 109,
                hasMore = false,
            ),
        )

    private fun finalStatePages(): List<String> =
        listOf(
            pageJson(
                changes = listOf(locationChangeJson("loc-2", "Loc 2"), itemChangeJson("item-1", "Item 1 updated")),
                nextWatermark = 40,
                hasMore = true,
            ),
            pageJson(
                changes = listOf(itemChangeJson("item-2", "Item 2"), itemChangeJson("item-4", "Item 4", groupChangeSeq = 555)),
                nextWatermark = 70,
                hasMore = true,
            ),
            pageJson(
                changes =
                    listOf(
                        itemChangeJson("item-5", "Item 5", groupChangeSeq = 555),
                        labelChangeJson("label-1", "Label 1", "blue"),
                    ),
                nextWatermark = 95,
                hasMore = true,
            ),
            pageJson(
                changes =
                    listOf(
                        itemLabelChangeJson("il-1", "item-1", "label-1"),
                        itemLabelChangeJson("il-2", "item-4", "label-1"),
                        warrantyBlockChangeJson("wb-1", "item-2", "Acme"),
                    ),
                nextWatermark = 109,
                hasMore = false,
            ),
        )

    private suspend fun buildFinalStateOracle(): HhoDatabase {
        val oracleServer = MockWebServer()
        try {
            finalStatePages().forEach { oracleServer.enqueue(MockResponse().setResponseCode(200).setBody(it)) }
            val oracleDb = inMemoryHhoDatabase()
            val result = engineFor(oracleDb, oracleServer).sync(deviceId = "device-oracle")
            check(result.isSuccess) { "oracle full pull failed: ${result.exceptionOrNull()}" }
            return oracleDb
        } finally {
            oracleServer.shutdown()
        }
    }

    private suspend fun assertMirrorsConverge(
        expected: HhoDatabase,
        actual: HhoDatabase,
    ) {
        for (id in listOf("item-1", "item-2", "item-4", "item-5")) {
            assertEquals("item $id", expected.itemDao().getById(id), actual.itemDao().getById(id))
        }
        assertNull("item-3 must be gone from the oracle", expected.itemDao().getById("item-3"))
        assertNull("item-3 must be gone from the actual mirror", actual.itemDao().getById("item-3"))

        assertEquals(expected.locationDao().getById("loc-2"), actual.locationDao().getById("loc-2"))
        assertNull("loc-1 must be gone from the oracle", expected.locationDao().getById("loc-1"))
        assertNull("loc-1 must be gone from the actual mirror", actual.locationDao().getById("loc-1"))

        assertEquals(expected.labelDao().getById("label-1"), actual.labelDao().getById("label-1"))

        assertEquals(
            expected.itemLabelDao().observeByLabelId("label-1").first().sortedBy { it.id },
            actual.itemLabelDao().observeByLabelId("label-1").first().sortedBy { it.id },
        )
        assertEquals(
            expected.warrantyBlockDao().getByItemId("item-2"),
            actual.warrantyBlockDao().getByItemId("item-2"),
        )

        assertEquals(expected.syncStateDao().get()?.watermark, actual.syncStateDao().get()?.watermark)
    }

    @Test
    fun `a full pull followed by incremental pulls across several rounds converges to the same mirror and watermark as a single full pull of the final state`() =
        runTest {
            val deviceA = inMemoryHhoDatabase()
            try {
                (initialPages() + round1Pages() + round2Pages() + finalStatePages()).forEach {
                    server.enqueue(MockResponse().setResponseCode(200).setBody(it))
                }

                val engineA = engineFor(deviceA)
                val initialResult = engineA.sync(deviceId = "device-a")
                assertTrue(initialResult.isSuccess)
                assertEquals(
                    SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.FirstSync),
                    initialResult.getOrNull(),
                )

                val round1Result = engineA.sync(deviceId = "device-a")
                assertTrue(round1Result.isSuccess)
                assertEquals(SyncRunOutcome.IncrementalPullRan, round1Result.getOrNull())

                val round2Result = engineA.sync(deviceId = "device-a")
                assertTrue(round2Result.isSuccess)
                assertEquals(SyncRunOutcome.IncrementalPullRan, round2Result.getOrNull())

                assertEquals(109L, deviceA.syncStateDao().get()?.watermark)

                val deviceB = inMemoryHhoDatabase()
                try {
                    val engineB = engineFor(deviceB)
                    val deviceBResult = engineB.sync(deviceId = "device-b")
                    assertTrue(deviceBResult.isSuccess)
                    assertEquals(
                        SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.FirstSync),
                        deviceBResult.getOrNull(),
                    )

                    assertEquals(4 + 4 + 5 + 4, server.requestCount)
                    assertMirrorsConverge(expected = deviceB, actual = deviceA)
                } finally {
                    deviceB.close()
                }
            } finally {
                deviceA.close()
            }
        }

    @Test
    fun `a simulated process death mid-incremental-pull persists exactly page N's watermark, resumes without re-fetching, and converges to the full-pull oracle`() =
        runTest {
            val dbName = "t158f-mid-incremental-${System.nanoTime()}"
            deleteHhoDatabaseFile(dbName)
            var db = fileHhoDatabase(dbName)
            try {
                initialPages().forEach { server.enqueue(MockResponse().setResponseCode(200).setBody(it)) }
                val initialResult = engineFor(db).sync(deviceId = "device-c")
                assertTrue(initialResult.isSuccess)
                repeat(initialPages().size) { server.takeRequest() }

                server.enqueue(MockResponse().setResponseCode(200).setBody(round1Pages()[0]))
                server.enqueue(MockResponse().setResponseCode(200).setBody(round1Pages()[1]))
                server.enqueue(errorResponse())

                val firstAttempt = engineFor(db).sync(deviceId = "device-c")

                assertTrue(firstAttempt.isFailure)
                assertTrue(firstAttempt.exceptionOrNull() is ApiError.Server)
                assertEquals(102L, db.syncStateDao().get()?.watermark)
                assertEquals("Loc 2", db.locationDao().getById("loc-2")?.name)
                assertEquals("Item 1 updated", db.itemDao().getById("item-1")?.name)
                assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":100"))
                assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":101"))
                assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":102"))

                db.close()
                db = fileHhoDatabase(dbName)

                assertEquals(102L, db.syncStateDao().get()?.watermark)
                assertEquals("Item 1 updated", db.itemDao().getById("item-1")?.name)

                server.enqueue(MockResponse().setResponseCode(200).setBody(round1Pages()[2]))
                server.enqueue(MockResponse().setResponseCode(200).setBody(round1Pages()[3]))

                val round1Resumed = engineFor(db).sync(deviceId = "device-c")

                assertTrue(round1Resumed.isSuccess)
                assertEquals(SyncRunOutcome.IncrementalPullRan, round1Resumed.getOrNull())
                assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":102"))
                assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":103"))

                assertEquals(104L, db.syncStateDao().get()?.watermark)
                assertEquals("Acme", db.warrantyBlockDao().getByItemId("item-2")?.holder)
                assertNull(db.itemDao().getById("item-3"))

                round2Pages().forEach { server.enqueue(MockResponse().setResponseCode(200).setBody(it)) }
                val round2Result = engineFor(db).sync(deviceId = "device-c")
                assertTrue(round2Result.isSuccess)
                repeat(round2Pages().size) { server.takeRequest() }

                val oracle = buildFinalStateOracle()
                try {
                    assertMirrorsConverge(expected = oracle, actual = db)
                } finally {
                    oracle.close()
                }
            } finally {
                db.close()
                deleteHhoDatabaseFile(dbName)
            }
        }

    @Test
    fun `a process death mid-full-pull leaves the empty mirror and absent watermark untouched, and SyncEngine converges via a fresh full pull on restart`() =
        runTest {
            val dbName = "t158f-mid-full-${System.nanoTime()}"
            deleteHhoDatabaseFile(dbName)
            var db = fileHhoDatabase(dbName)
            try {
                server.enqueue(MockResponse().setResponseCode(200).setBody(pageJson(changes = listOf(locationChangeJson("loc-x", "Loc X")), nextWatermark = 10, hasMore = true)))
                server.enqueue(errorResponse())

                val firstAttempt = engineFor(db).sync(deviceId = "device-d")

                assertTrue(firstAttempt.isFailure)
                assertTrue(firstAttempt.exceptionOrNull() is ApiError.Server)
                assertNull(db.locationDao().getById("loc-x"))
                assertNull(db.syncStateDao().get())
                server.takeRequest()
                server.takeRequest()

                db.close()
                db = fileHhoDatabase(dbName)

                assertNull(db.locationDao().getById("loc-x"))
                assertNull(db.syncStateDao().get())

                server.enqueue(MockResponse().setResponseCode(200).setBody(pageJson(changes = listOf(itemChangeJson("item-z", "Item Z")), nextWatermark = 5, hasMore = false)))

                val secondAttempt = engineFor(db).sync(deviceId = "device-d")

                assertTrue(secondAttempt.isSuccess)
                assertEquals(
                    SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.FirstSync),
                    secondAttempt.getOrNull(),
                )
                assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
                assertEquals("Item Z", db.itemDao().getById("item-z")?.name)
                assertEquals(5L, db.syncStateDao().get()?.watermark)
            } finally {
                db.close()
                deleteHhoDatabaseFile(dbName)
            }
        }

    @Test
    fun `a fresh device with no persisted watermark sends since 0 and is reported as FullPullRan(FirstSync), never an incremental pull`() =
        runTest {
            val db = inMemoryHhoDatabase()
            try {
                server.enqueue(MockResponse().setResponseCode(200).setBody(pageJson(changes = listOf(itemChangeJson("item-w", "Item W")), nextWatermark = 7, hasMore = false)))

                val result = engineFor(db).sync(deviceId = "device-e")

                assertTrue(result.isSuccess)
                assertEquals(SyncRunOutcome.FullPullRan(SyncRunOutcome.FullPullReason.FirstSync), result.getOrNull())
                assertTrue(server.takeRequest().body.readUtf8().contains("\"since\":0"))
                assertEquals("Item W", db.itemDao().getById("item-w")?.name)
                assertEquals(7L, db.syncStateDao().get()?.watermark)
            } finally {
                db.close()
            }
        }
}
