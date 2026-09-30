package dev.hho.android.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.domain.EditRepository
import dev.hho.android.domain.EditResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
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
class StocktakeRealServerTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.seconds(240)

    @get:Rule(order = 1)
    val server = RealServerHarness()

    private lateinit var a: TwoDeviceSupportDevice
    private lateinit var b: TwoDeviceSupportDevice
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        a = TwoDeviceSupportDevice(server, "stocktake-device-a")
        b = TwoDeviceSupportDevice(server, "stocktake-device-b")
    }

    @After
    fun tearDown() {
        a.close()
        b.close()
    }

    private sealed interface Edit {
        suspend fun perform(repo: EditRepository): EditResult<*>
    }

    private class Adjust(val item: String, val delta: Long, val reason: String?) : Edit {
        override suspend fun perform(repo: EditRepository) = repo.adjustStock(item, delta, reason)
    }

    private class Rename(val item: String, val name: String?, val description: String?) : Edit {
        override suspend fun perform(repo: EditRepository) = repo.editItem(item, name, description)
    }

    private class Move(val item: String, val location: String?) : Edit {
        override suspend fun perform(repo: EditRepository) = repo.moveItem(item, location)
    }

    private class Attach(val item: String, val label: String) : Edit {
        override suspend fun perform(repo: EditRepository) = repo.attachLabel(item, label)
    }

    private class Detach(val item: String, val label: String) : Edit {
        override suspend fun perform(repo: EditRepository) = repo.detachLabel(item, label)
    }

    private fun golden(name: String): JsonObject =
        json.parseToJsonElement(File(GOLDEN_DIR, name).also { assertTrue("golden $it missing", it.isFile) }.readText()).jsonObject

    private fun String.str(key: String) = jsonOf(this).getValue(key).jsonPrimitive.content

    private fun jsonOf(s: String) = json.parseToJsonElement(s).jsonObject

    private fun editsFromGolden(push: JsonObject): List<Edit> =
        push.getValue("mutations").jsonArray.map { it.jsonObject }.map { m ->
            val f = m.getValue("fields").jsonObject
            val op = m["op"]?.jsonPrimitive?.content ?: "upsert"
            when (val type = m.getValue("entity_type").jsonPrimitive.content) {
                "stock_adjustment" -> Adjust(
                    f.getValue("item_id").jsonPrimitive.content,
                    f.getValue("delta").jsonPrimitive.longOrNull!!,
                    f["reason"]?.jsonPrimitive?.content,
                )
                "item" -> {
                    val id = m.getValue("entity_id").jsonPrimitive.content
                    if ("location_id" in f) {
                        assertEquals("a move carries only the location", setOf("location_id"), f.keys)
                        Move(id, (f.getValue("location_id") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content)
                    } else {
                        Rename(id, f["name"]?.jsonPrimitive?.content, f["description"]?.jsonPrimitive?.content)
                    }
                }
                "item_label" -> {
                    val item = f.getValue("item_id").jsonPrimitive.content
                    val label = f.getValue("label_id").jsonPrimitive.content
                    if (op == "delete") Detach(item, label) else Attach(item, label)
                }
                else -> error("unexpected entity_type $type in the golden")
            }
        }

    @Test
    fun `50 stocktake edits persist on the real server, second sync is a no-op, second device converges`() = runBlocking {
        val seedRequest = golden("stocktake-seed-push-request.json")
        val push = golden("stocktake-push-request.json")
        val expected = golden("stocktake-expected-state.json")
        val edits = editsFromGolden(push)
        assertEquals(50, edits.size)
        assertEquals(
            "golden edit mix",
            listOf(15, 12, 10, 8, 5),
            listOf(
                edits.count { it is Adjust },
                edits.count { it is Rename },
                edits.count { it is Move },
                edits.count { it is Attach },
                edits.count { it is Detach },
            ),
        )

        val seedQty = seedRequest.getValue("mutations").jsonArray.map { it.jsonObject }
            .filter { it.getValue("entity_type").jsonPrimitive.content == "item" }
            .associate { it.getValue("entity_id").jsonPrimitive.content to it.getValue("fields").jsonObject.getValue("quantity").jsonPrimitive.long() }
        assertEquals(30, seedQty.size)
        val expectedQty = seedQty.toMutableMap()
        edits.filterIsInstance<Adjust>().forEach { expectedQty[it.item] = expectedQty.getValue(it.item) + it.delta }
        val goldenItems = expected.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(
            "golden expected-state quantities == seed + sum(delta)",
            expectedQty,
            goldenItems.associate { it.getValue("id").jsonPrimitive.content to it.getValue("quantity").jsonPrimitive.long() },
        )

        val rest = RestReadback(server)
        try {
            val seeded = rest.push(seedRequest)
            assertEquals("seed applied", seedRequest.getValue("mutations").jsonArray.size, seeded.getValue("applied").jsonArray.size)
            assertTrue("seed has no conflicts: $seeded", seeded.getValue("conflicts").jsonArray.isEmpty())
            val seedState = rest.snapshot()
            assertEquals(seedQty, seedState.quantities())

            a.sync()
            assertEquals(30, a.db.itemDao().observeAll().first().size)
            for (e in edits) {
                val r = e.perform(a.edits)
                assertTrue("edit failed: $r", r is EditResult.Success<*>)
            }
            assertEquals("50 queued mutations", 50, a.pending())
            assertEquals("nothing reached the server yet", seedState.text, rest.snapshot().text)
            a.sync()
            assertEquals("outbox drained", 0, a.pending())

            val after = rest.snapshot()
            assertEquals("readback quantities == seed + sum(delta)", expectedQty, after.quantities())
            assertEquals(30, after.items.size)
            for (g in goldenItems) {
                val id = g.getValue("id").jsonPrimitive.content
                val row = after.items.getValue(id)
                assertEquals("name of $id", g.getValue("name").jsonPrimitive.content, row.getValue("name").jsonPrimitive.content)
                assertEquals("description of $id", g.getValue("description").jsonPrimitive.content, row.getValue("description").jsonPrimitive.content)
                assertEquals("location of $id", g.getValue("location_id").jsonPrimitive.content, row.getValue("location_id").jsonPrimitive.content)
            }
            val goldenEdges = expected.getValue("item_labels").jsonArray.map { it.jsonObject }
                .map { it.getValue("item_id").jsonPrimitive.content to it.getValue("label_id").jsonPrimitive.content }.toSet()
            assertEquals("label edges", goldenEdges, after.edges)
            assertTrue("edits changed the server", after.text != seedState.text)
            assertTrue(edits.filterIsInstance<Rename>().all { after.items.getValue(it.item).getValue("name").jsonPrimitive.content == it.name })
            assertTrue(edits.filterIsInstance<Move>().all { after.items.getValue(it.item).getValue("location_id").jsonPrimitive.content == it.location })

            val mirrorA = mirror(a.db)
            a.sync()
            assertEquals("second sync changed nothing on the server", after.text, rest.snapshot().text)
            assertEquals(0, a.pending())
            assertEquals("second sync changed nothing in the mirror", mirrorA, mirror(a.db))
            assertTrue("GET /sync/conflicts is empty: ${rest.conflicts()}", rest.conflicts().isEmpty())
            assertTrue(a.db.conflictRecordDao().getAll().isEmpty())

            b.sync()
            assertEquals(0, b.pending())
            assertTrue(b.db.conflictRecordDao().getAll().isEmpty())
            val mirrorB = mirror(b.db)
            assertEquals("device B == device A", mirrorA, mirrorB)
            assertEquals("device B == server readback", after.text, rest.snapshot().text)
            assertEquals(expectedQty, mirrorB.items.mapValues { it.value.quantity!! })
            assertEquals(goldenEdges, mirrorB.edges)
            for (g in goldenItems) {
                val row = mirrorB.items.getValue(g.getValue("id").jsonPrimitive.content)
                assertEquals(g.getValue("name").jsonPrimitive.content, row.name)
                assertEquals(g.getValue("description").jsonPrimitive.content, row.description)
                assertEquals(g.getValue("location_id").jsonPrimitive.content, row.locationId)
            }
            b.sync()
            assertEquals(mirrorB, mirror(b.db))
        } finally {
            rest.close()
        }
    }

    private data class Mirror(
        val items: Map<String, dev.hho.android.data.room.ItemEntity>,
        val edges: Set<Pair<String, String>>,
        val labels: Set<String>,
        val locations: Set<String>,
    )

    private suspend fun mirror(db: HhoDatabase): Mirror {
        val items = db.itemDao().observeAll().first().associateBy { it.id }
        val labels = db.labelDao().observeAll().first()
        val edges = labels.flatMap { l -> db.itemLabelDao().observeByLabelId(l.id).first() }.map { it.itemId to it.labelId }
        return Mirror(
            items,
            edges.toSet().also { assertEquals("no duplicate edges", edges.size, it.size) },
            labels.map { it.id }.toSet(),
            db.locationDao().observeAll().first().map { it.id }.toSet(),
        )
    }

    private fun JsonElement.long() = jsonPrimitive.longOrNull!!

    private fun kotlinx.serialization.json.JsonPrimitive.long() = longOrNull!!

    private class Snapshot(val items: Map<String, JsonObject>, val edges: Set<Pair<String, String>>) {
        val text: String = items.toSortedMap().values.joinToString("\n") { it.toString() } + "\n" + edges.sortedBy { it.first + it.second }

        fun quantities() = items.mapValues { it.value.getValue("quantity").jsonPrimitive.longOrNull!! }
    }

    private class RestReadback(private val server: RealServerHarness) : AutoCloseable {
        private val http = OkHttpClient()
        private val json = Json { ignoreUnknownKeys = true }
        private val token: String = runBlocking {
            val client = server.newApiClient()
            val login = client.login(server.username, server.password).getOrThrow()
            client.issueDeviceToken("e2e-stocktake-readback", login.sessionCookie).getOrThrow().token
        }

        private fun call(path: String, body: JsonObject? = null): JsonObject {
            val req = Request.Builder().url(server.baseUrl.resolve(path)!!)
                .header("Authorization", "Bearer $token")
                .apply { if (body != null) post(body.toString().toRequestBody("application/json".toMediaType())) }
                .build()
            return http.newCall(req).execute().use {
                val text = it.body!!.string()
                check(it.code == 200) { "${req.method} $path answered ${it.code}" }
                json.parseToJsonElement(text).jsonObject
            }
        }

        fun push(body: JsonObject) = call("api/v1/sync/push", body)

        fun conflicts(): List<JsonElement> = call("api/v1/sync/conflicts?limit=200").getValue("conflicts").jsonArray

        fun snapshot(): Snapshot {
            val items = call("api/v1/items?limit=200").getValue("items").jsonArray.map { it.jsonObject }
                .associateBy { it.getValue("id").jsonPrimitive.content }
            val edges = items.keys.flatMap { id ->
                call("api/v1/items/$id/labels").getValue("labels").jsonArray.map { id to it.jsonObject.getValue("id").jsonPrimitive.content }
            }.toSet()
            return Snapshot(items, edges)
        }

        override fun close() {
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
        }
    }

    private companion object {
        val GOLDEN_DIR = File("src/test/resources/golden")
    }
}
