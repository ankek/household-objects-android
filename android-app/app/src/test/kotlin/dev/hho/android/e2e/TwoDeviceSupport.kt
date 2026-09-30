package dev.hho.android.e2e

import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.data.sync.SyncEngine
import dev.hho.android.data.sync.testSyncEngine
import dev.hho.android.domain.EditRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class TwoDeviceSupportDevice(
    server: RealServerHarness,
    val name: String,
) {
    val db: HhoDatabase = inMemoryHhoDatabase()
    val outbox = OutboxRepository(db)
    private val engine: SyncEngine = testSyncEngine(server.newApiClient(), db, outbox, withConflictLog = true)
    private val ids = UuidV7Generator()

    val edits = EditRepository(db, outbox, ids) {}

    suspend fun sync() = engine.sync(name).also { check(it.isSuccess) { "$name sync failed: ${it.exceptionOrNull()}" } }.getOrThrow()

    suspend fun pending(): Int = outbox.observePendingCount().first()

    fun close() = db.close()
}

internal class ReferenceDataSeeder(private val server: RealServerHarness) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val token: String = runBlocking {
        val client = server.newApiClient()
        val login = client.login(server.username, server.password).getOrThrow()
        client.issueDeviceToken("e2e-seeder", login.sessionCookie).getOrThrow().token
    }

    fun createLocation(name: String): String = create("api/v1/locations", buildJsonObject { put("name", name) })

    fun createLabel(name: String): String =
        create("api/v1/labels", buildJsonObject { put("name", name); put("color", "#336699") })

    private fun create(path: String, body: kotlinx.serialization.json.JsonObject): String {
        val req = Request.Builder().url(server.baseUrl.resolve(path)!!)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(req).execute().use {
            val text = it.body!!.string()
            check(it.code == 201) { "POST $path answered ${it.code}" }
            json.parseToJsonElement(text).jsonObject.getValue("id").jsonPrimitive.content
        }.also { http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }
}
