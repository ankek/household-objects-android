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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.File

class HhoApiClientSyncPushTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(15)

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun apiClient(): HhoApiClient {
        val file = File.createTempFile("hho-push-test", ".preferences_pb").also { it.deleteOnExit() }
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = server.url("/").toString() } }
        val cv = ClientVersionInterceptor()
        val provider =
            object : DeviceTokenProvider {
                override suspend fun tokenFor(requestUrl: HttpUrl): String? = null

                override suspend fun invalidate(
                    requestUrl: HttpUrl,
                    rejectedToken: String,
                ) = Unit
            }
        val http = NetworkModule.provideOkHttpClient(BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore)), AuthHeaderInterceptor(provider), cv)
        return HhoApiClient(http, NetworkModule.provideProbeOkHttpClient(http, cv))
    }

    private fun problem(
        status: Int,
        detail: String,
    ) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"type":"about:blank","title":"t","status":$status,"detail":${JsonPrimitive(detail)}}""")

    private val mutation =
        PushMutation(
            mutationId = "m-1",
            entityType = "item",
            entityId = "e-1",
            baseVersion = 3,
            fields =
                mapOf(
                    "name" to JsonPrimitive("Drill"),
                    "quantity" to JsonPrimitive(4),
                    "notes" to JsonNull,
                    "archived" to JsonPrimitive(false),
                ),
        )

    @Test
    fun `200 mixed applied skipped conflicts decodes and sends the exact request JSON`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"applied":[{"mutation_id":"m-1","entity_type":"item","entity_id":"e-1","version":4}],""" +
                        """"skipped":[{"mutation_id":"m-2"}],""" +
                        """"conflicts":[{"mutation_id":"m-3","entity_type":"item","entity_id":"e-3","field_name":"name"}],""" +
                        """"new_watermark":42}""",
                ),
            )

            val outcome = apiClient().syncPush("dev-1", listOf(mutation, mutation.copy(mutationId = "m-9", op = "delete"))).getOrThrow()

            outcome as SyncPushOutcome.Success
            assertEquals(listOf("m-1"), outcome.applied.map { it.mutationId })
            assertEquals(4L, outcome.applied.single().version)
            assertEquals(listOf("m-2"), outcome.skipped.map { it.mutationId })
            assertEquals("name", outcome.conflicts.single().fieldName)
            assertEquals(42L, outcome.newWatermark)

            val recorded = server.takeRequest()
            assertEquals("/api/v1/sync/push", recorded.path)
            assertEquals("POST", recorded.method)
            val expected =
                Json.parseToJsonElement(
                    """{"device_id":"dev-1","mutations":[""" +
                        """{"mutation_id":"m-1","entity_type":"item","entity_id":"e-1","base_version":3,""" +
                        """"fields":{"name":"Drill","quantity":4,"notes":null,"archived":false}},""" +
                        """{"mutation_id":"m-9","entity_type":"item","entity_id":"e-1","base_version":3,""" +
                        """"fields":{"name":"Drill","quantity":4,"notes":null,"archived":false},"op":"delete"}]}""",
                )
            assertEquals(expected, Json.parseToJsonElement(recorded.body.readUtf8()))
        }

    @Test
    fun `200 with empty lists decodes to an empty Success`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"applied":[],"skipped":[],"conflicts":[],"new_watermark":0}"""))

            val outcome = apiClient().syncPush("d", emptyList()).getOrThrow() as SyncPushOutcome.Success

            assertTrue(outcome.applied.isEmpty() && outcome.skipped.isEmpty() && outcome.conflicts.isEmpty())
        }

    @Test
    fun `structural 400 with the pinned detail yields index and mutation id`() =
        runBlocking {
            val detail = """mutations[2] (mutation_id="abc-123"): entity_type "bogus" is not a known entity type"""
            server.enqueue(problem(400, detail))

            val outcome = apiClient().syncPush("d", listOf(mutation)).getOrThrow() as SyncPushOutcome.StructuralRejection

            assertEquals(2, outcome.index)
            assertEquals("abc-123", outcome.mutationId)
            assertEquals(detail, outcome.detail)
        }

    @Test
    fun `structural 400 with an escaped quote in the id unescapes it`() =
        runBlocking {
            server.enqueue(problem(400, """mutations[0] (mutation_id="a\"b"): mutation_id is bad"""))

            val outcome = apiClient().syncPush("d", listOf(mutation)).getOrThrow() as SyncPushOutcome.StructuralRejection

            assertEquals(0, outcome.index)
            assertEquals("a\"b", outcome.mutationId)
        }

    @Test
    fun `structural 400 with an unparseable detail yields null index and id`() =
        runBlocking {
            server.enqueue(problem(400, "mutations[oops] something else entirely"))

            val outcome = apiClient().syncPush("d", listOf(mutation)).getOrThrow() as SyncPushOutcome.StructuralRejection

            assertNull(outcome.index)
            assertNull(outcome.mutationId)
            assertEquals("mutations[oops] something else entirely", outcome.detail)
        }

    @Test
    fun `non-structural 400 maps to ApiError Validation not StructuralRejection`() =
        runBlocking {
            server.enqueue(problem(400, "device_id is required"))

            val error = apiClient().syncPush("", listOf(mutation)).exceptionOrNull() as ApiError.Validation

            assertEquals(400, error.status)
            assertEquals("device_id is required", error.detail)
        }

    @Test
    fun `401 maps to Unauthorized`() =
        runBlocking {
            server.enqueue(problem(401, "nope"))
            assertTrue(apiClient().syncPush("d", listOf(mutation)).exceptionOrNull() is ApiError.Unauthorized)
        }

    @Test
    fun `426 maps to UpgradeRequired carrying the minimum version`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(426).setBody(
                    """{"type":"urn:hho:problem:upgrade-required","title":"Upgrade Required","status":426,"minimum_version":"2.4.0"}""",
                ),
            )

            val error = apiClient().syncPush("d", listOf(mutation)).exceptionOrNull() as ApiError.UpgradeRequired

            assertEquals("2.4.0", error.minimumVersion)
        }

    @Test
    fun `500 maps to Server`() =
        runBlocking {
            server.enqueue(problem(500, "boom"))

            val error = apiClient().syncPush("d", listOf(mutation)).exceptionOrNull() as ApiError.Server

            assertEquals(500, error.status)
        }

    @Test
    fun `200 with a malformed body maps to UnexpectedPayload`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{not json"))
            assertTrue(apiClient().syncPush("d", listOf(mutation)).exceptionOrNull() is ApiError.UnexpectedPayload)
        }

    @Test
    fun `200 with a JSON body missing required fields maps to UnexpectedPayload`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"applied":[]}"""))
            assertTrue(apiClient().syncPush("d", listOf(mutation)).exceptionOrNull() is ApiError.UnexpectedPayload)
        }

    @Test
    fun `A145 evidence - generated SyncMutation cannot encode its free-form fields`() {
        val generated =
            dev.hho.android.data.apiclient.generated.models.SyncMutation(
                mutationId = "m",
                entityType = "item",
                entityId = "e",
                baseVersion = 0,
                fields = JsonObject(mapOf("name" to JsonPrimitive("x"))),
            )
        val failed =
            runCatching {
                dev.hho.android.data.apiclient.generated.infrastructure.Serializer.kotlinxSerializationJson
                    .encodeToString(dev.hho.android.data.apiclient.generated.models.SyncMutation.serializer(), generated)
            }.isFailure
        assertTrue("generated SyncMutation unexpectedly encodes; hand-written encoder no longer needed", failed)
    }
}
