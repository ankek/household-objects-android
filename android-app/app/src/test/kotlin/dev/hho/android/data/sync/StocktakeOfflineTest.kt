package dev.hho.android.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.outbox.LedgerFakeServer
import dev.hho.android.data.outbox.OutboxOverlay
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.outbox.ledgerApiClient
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemLabelEntity
import dev.hho.android.data.room.LabelEntity
import dev.hho.android.data.room.LocationEntity
import dev.hho.android.data.room.OutboxState
import dev.hho.android.data.room.deleteHhoDatabaseFile
import dev.hho.android.data.room.fileHhoDatabase
import dev.hho.android.domain.EditRepository
import dev.hho.android.domain.EditResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Interceptor
import okhttp3.mockwebserver.MockWebServer
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StocktakeOfflineTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(120)

    private val server = MockWebServer()
    private val ledger = LedgerFakeServer()
    private val httpCalls = AtomicInteger()
    private val dbs = mutableListOf<HhoDatabase>()
    private val dbName = "stocktake-offline-${System.nanoTime()}"

    private val entityIds = UuidV7Generator(clock = { ID_EPOCH_MS }, random = Random(170_001))
    private val mutationIds = UuidV7Generator(clock = { ID_EPOCH_MS + 1 }, random = Random(170_002))
    private var tick = 1_000L

    @Before
    fun setUp() {
        deleteHhoDatabaseFile(dbName)
        server.dispatcher = ledger
    }

    @After
    fun tearDown() {
        dbs.forEach { runCatching { it.close() } }
        deleteHhoDatabaseFile(dbName)
        server.shutdown()
    }

    @Test
    fun `50 offline edits survive a process death and a lost response, applied exactly once`() =
        runBlocking {
            val expected = Model.seed()
            val edits = scenario()
            assertEquals(50, edits.size)
            edits.forEach { it.applyTo(expected) }

            val tripwire = Interceptor { chain ->
                httpCalls.incrementAndGet()
                chain.proceed(chain.request())
            }
            val api = ledgerApiClient(server, listOf(tripwire))

            var db = open()
            seedMirror(db)
            var syncTriggers = 0
            val firstHalf = editRepo(db, onSync = { syncTriggers++ })
            edits.take(25).forEach { it.perform(firstHalf) }
            val beforeDeath = db.outboxDao().getAllOrdered()
            assertEquals(25, beforeDeath.size)
            db.close()

            db = open()
            val survivors = db.outboxDao().getAllOrdered()
            assertEquals("no queued edit lost across the kill", beforeDeath.map { it.mutationId }, survivors.map { it.mutationId })
            assertTrue(survivors.all { it.state == OutboxState.PENDING })
            val secondHalf = editRepo(db, onSync = { syncTriggers++ })
            edits.drop(25).forEach { it.perform(secondHalf) }

            val queued = db.outboxDao().getAllOrdered()
            assertEquals("every edit is one queued mutation", 50, queued.size)
            assertEquals(50, queued.map { it.mutationId }.toSet().size)
            assertEquals(queued.map { it.seq }.sorted(), queued.map { it.seq })
            assertTrue(queued.all { it.state == OutboxState.PENDING })
            assertEquals("each edit asked for a sync", 50, syncTriggers)
            assertEquals("HhoApiClient must not be called offline (interceptor)", 0, httpCalls.get())
            assertEquals("no request reached the server", 0, server.requestCount)
            assertEquals(0, ledger.pushRequests)
            assertMirror(db, expected)
            expected.items.forEach { (id, m) ->
                assertEquals("derived quantity of $id", m.quantity, OutboxOverlay.derivedQuantity(db, id))
            }

            ledger.faults += LedgerFakeServer.Fault.APPLY_THEN_DROP_RESPONSE
            val first = OutboxSyncer(db, api, OutboxRepository(db, mutationIds, { tick++ }), clock = { tick++ }).push(DEVICE)
            assertTrue("lost response surfaces as Failed: $first", first is PushPhaseOutcome.Failed)
            assertEquals(1, ledger.pushRequests)
            assertEquals("the server already applied all 50", 50, ledger.appliedCount.size)
            assertTrue(ledger.appliedCount.values.all { it == 1 })
            val afterLoss = db.outboxDao().getAllOrdered()
            assertEquals("all rows survive the lost response", queued.map { it.mutationId }, afterLoss.map { it.mutationId })
            assertTrue(afterLoss.all { it.attemptCount == 1 && it.state == OutboxState.PENDING })
            db.close()

            db = open()
            val second = OutboxSyncer(db, api, OutboxRepository(db, mutationIds, { tick++ }), clock = { tick++ }).push(DEVICE)
            assertEquals(PushPhaseOutcome.Drained, second)

            assertTrue("outbox empty: ${db.outboxDao().getAllOrdered()}", db.outboxDao().getAllOrdered().isEmpty())
            assertEquals(2, ledger.pushRequests)
            assertEquals("replay = identical request bytes", ledger.requestBodies[0], ledger.requestBodies[1])
            assertEquals(queued.map { it.mutationId }, ledger.sentIds[1])
            assertEquals(queued.map { it.mutationId }.toSet(), ledger.appliedCount.keys)
            assertTrue("applied exactly once", ledger.appliedCount.values.all { it == 1 })
            assertEquals(queued.map { it.mutationId }.toSet(), ledger.skippedCount.keys)
            assertTrue("skipped exactly once", ledger.skippedCount.values.all { it == 1 })
            assertEquals(queued.map { it.mutationId }, ledger.applyOrder)

            val wire = Json.parseToJsonElement(ledger.requestBodies[0]).jsonObject
            assertPushRequestShape(wire)
            assertEquals(DEVICE, wire["device_id"]!!.jsonPrimitive.content)
            val serverSide = Model.seed()
            ledger.applyOrder.forEach { id ->
                val m = wire["mutations"]!!.jsonArray.map { it.jsonObject }.single { it["mutation_id"]!!.jsonPrimitive.content == id }
                serverSide.applyWire(m)
            }
            assertEquals("server state == independently computed expectation", expected.describe(), serverSide.describe())
            assertMirror(db, expected)

            val seedRequest = seedPushRequest()
            checkGolden("stocktake-seed-push-request.json", pretty(seedRequest))
            checkGolden("stocktake-push-request.json", pretty(wire))
            checkGolden("stocktake-expected-state.json", pretty(expected.toJson()))
        }

    private sealed interface Edit {
        suspend fun perform(repo: EditRepository)

        fun applyTo(model: Model)
    }

    private data class Adjust(val item: String, val delta: Long) : Edit {
        override suspend fun perform(repo: EditRepository) {
            repo.adjustStock(item, delta, reason = "stocktake").ok()
        }

        override fun applyTo(model: Model) {
            model.items.getValue(item).quantity += delta
        }
    }

    private data class Rename(val item: String, val name: String, val description: String) : Edit {
        override suspend fun perform(repo: EditRepository) {
            repo.editItem(item, name = name, description = description).ok()
        }

        override fun applyTo(model: Model) {
            val target = model.items.getValue(item)
            target.name = name
            target.description = description
        }
    }

    private data class Move(val item: String, val location: String) : Edit {
        override suspend fun perform(repo: EditRepository) {
            repo.moveItem(item, location).ok()
        }

        override fun applyTo(model: Model) {
            model.items.getValue(item).location = location
        }
    }

    private data class Attach(val item: String, val label: String) : Edit {
        override suspend fun perform(repo: EditRepository) {
            repo.attachLabel(item, label).ok()
        }

        override fun applyTo(model: Model) {
            check(model.edges.add(item to label)) { "scenario attaches an existing edge" }
        }
    }

    private data class Detach(val item: String, val label: String) : Edit {
        override suspend fun perform(repo: EditRepository) {
            repo.detachLabel(item, label).ok()
        }

        override fun applyTo(model: Model) {
            check(model.edges.remove(item to label)) { "scenario detaches a missing edge" }
        }
    }

    private fun scenario(): List<Edit> {
        val deltas = listOf(4L, -3, 7, -2, 5, -1, 3, -4, 6, 2, -5, 8, 1, -6, 9)
        val adjusts = deltas.mapIndexed { n, d -> Adjust(item(1 + n % 5), d) }
        val renames = (6..17).map { Rename(item(it), "Item ${two(it)} (counted)", "recounted ${two(it)}") }
        val moves = (18..27).map { Move(item(it), location(2 + it % 3)) }
        val attaches = listOf(28 to 3, 28 to 4, 29 to 3, 29 to 4, 30 to 3, 30 to 4, 1 to 1, 2 to 1)
            .map { (i, l) -> Attach(item(i), label(l)) }
        val detaches = SEED_EDGES.map { (i, l) -> Detach(item(i), label(l)) }
        val queues = listOf(adjusts, renames, moves, attaches, detaches).map { ArrayDeque(it) }
        return buildList {
            while (queues.any { it.isNotEmpty() }) queues.forEach { q -> q.removeFirstOrNull()?.let(::add) }
        }
    }

    private class ItemModel(var name: String, var description: String, var location: String?, var quantity: Long)

    private class Model(val items: MutableMap<String, ItemModel>, val edges: MutableSet<Pair<String, String>>) {
        fun applyWire(m: JsonObject) {
            val type = m["entity_type"]!!.jsonPrimitive.content
            val entity = m["entity_id"]!!.jsonPrimitive.content
            val op = m["op"]?.jsonPrimitive?.content ?: "upsert"
            val f = m["fields"]!!.jsonObject
            when (type) {
                "item" -> items.getValue(entity).apply {
                    f["name"]?.let { name = it.jsonPrimitive.content }
                    f["description"]?.let { description = it.jsonPrimitive.content }
                    f["location_id"]?.let { location = (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
                }
                "stock_adjustment" -> items.getValue(f["item_id"]!!.jsonPrimitive.content).quantity += f["delta"]!!.jsonPrimitive.longOrNull!!
                "item_label" -> {
                    val edge = f["item_id"]!!.jsonPrimitive.content to f["label_id"]!!.jsonPrimitive.content
                    if (op == "delete") check(edges.remove(edge)) else check(edges.add(edge))
                }
                else -> error("unexpected entity_type $type")
            }
        }

        fun describe(): String =
            items.toSortedMap().entries.joinToString("\n") { (id, m) -> "$id|${m.name}|${m.description}|${m.location}|${m.quantity}" } +
                "\n" + edges.sortedWith(compareBy({ it.first }, { it.second })).joinToString(",")

        fun toJson(): JsonObject =
            buildJsonObject {
                put(
                    "items",
                    JsonArray(
                        items.toSortedMap().map { (id, m) ->
                            buildJsonObject {
                                put("id", JsonPrimitive(id))
                                put("name", JsonPrimitive(m.name))
                                put("description", JsonPrimitive(m.description))
                                put("location_id", m.location?.let(::JsonPrimitive) ?: JsonNull)
                                put("quantity", JsonPrimitive(m.quantity))
                            }
                        },
                    ),
                )
                put(
                    "item_labels",
                    JsonArray(
                        edges.sortedWith(compareBy({ it.first }, { it.second })).map { (i, l) ->
                            buildJsonObject {
                                put("item_id", JsonPrimitive(i))
                                put("label_id", JsonPrimitive(l))
                            }
                        },
                    ),
                )
            }

        companion object {
            fun seed() = Model(
                (1..ITEM_COUNT).associate { item(it) to ItemModel("Item ${two(it)}", "seed", location(1), SEED_QUANTITY) }.toMutableMap(),
                SEED_EDGES.map { (i, l) -> item(i) to label(l) }.toMutableSet(),
            )
        }
    }

    private suspend fun seedMirror(db: HhoDatabase) {
        (1..4).forEach { db.locationDao().upsert(LocationEntity(location(it), 1, "Location $it", null, 1, 1, 1)) }
        (1..4).forEach { db.labelDao().upsert(LabelEntity(label(it), 1, "Label $it", "#00000$it", 1, 1, 1)) }
        (1..ITEM_COUNT).forEach {
            db.itemDao().upsert(
                ItemEntity(item(it), 1, "Item ${two(it)}", "seed", location(1), SEED_QUANTITY, "S${two(it)}", 1, 1, SEED_VERSION),
            )
        }
        SEED_EDGES.forEachIndexed { n, (i, l) -> db.itemLabelDao().upsert(ItemLabelEntity(edge(n + 1), 1, item(i), label(l))) }
    }

    private fun seedPushRequest(): JsonObject {
        val muts = ArrayList<JsonObject>()

        fun add(type: String, id: String, fields: Map<String, JsonElement>) {
            muts += buildJsonObject {
                put("mutation_id", JsonPrimitive(seedMutationId(muts.size + 1)))
                put("entity_type", JsonPrimitive(type))
                put("entity_id", JsonPrimitive(id))
                put("base_version", JsonPrimitive(0))
                put("fields", JsonObject(fields))
            }
        }
        (1..4).forEach { add("location", location(it), mapOf("name" to JsonPrimitive("Location $it"))) }
        (1..4).forEach { add("label", label(it), mapOf("name" to JsonPrimitive("Label $it"), "color" to JsonPrimitive("#00000$it"))) }
        (1..ITEM_COUNT).forEach {
            add(
                "item",
                item(it),
                mapOf(
                    "name" to JsonPrimitive("Item ${two(it)}"),
                    "description" to JsonPrimitive("seed"),
                    "location_id" to JsonPrimitive(location(1)),
                    "quantity" to JsonPrimitive(SEED_QUANTITY),
                ),
            )
        }
        SEED_EDGES.forEachIndexed { n, (i, l) ->
            add("item_label", edge(n + 1), mapOf("item_id" to JsonPrimitive(item(i)), "label_id" to JsonPrimitive(label(l))))
        }
        return buildJsonObject {
            put("device_id", JsonPrimitive("stocktake-seed-device"))
            put("mutations", JsonArray(muts))
        }
    }

    private suspend fun assertMirror(db: HhoDatabase, expected: Model) {
        val items = db.itemDao().observeAll().first().associateBy { it.id }
        assertEquals(expected.items.keys, items.keys)
        expected.items.forEach { (id, m) ->
            val row = items.getValue(id)
            assertEquals("name of $id", m.name, row.name)
            assertEquals("description of $id", m.description, row.description)
            assertEquals("location of $id", m.location, row.locationId)
        }
        val edges = (1..4).flatMap { l -> db.itemLabelDao().observeByLabelId(label(l)).first() }.map { it.itemId to it.labelId }
        assertEquals("label edges", expected.edges, edges.toSet())
        assertEquals("no duplicate edges", edges.size, edges.toSet().size)
    }

    private fun assertPushRequestShape(root: JsonObject) {
        assertEquals(setOf("device_id", "mutations"), root.keys)
        val closedSet = setOf("item", "stock_adjustment", "item_label")
        for (m in root["mutations"]!!.jsonArray.map { it.jsonObject }) {
            assertTrue(m.keys.containsAll(setOf("mutation_id", "entity_type", "entity_id", "base_version", "fields")))
            assertTrue("no unknown keys: ${m.keys}", m.keys.all { it in setOf("mutation_id", "entity_type", "entity_id", "base_version", "fields", "op") })
            assertTrue(m["base_version"]!!.jsonPrimitive.longOrNull != null)
            assertTrue(m["op"]?.jsonPrimitive?.content in setOf(null, "upsert", "delete"))
            val type = m["entity_type"]!!.jsonPrimitive.content
            assertTrue(type in closedSet)
            val f = m["fields"]!!.jsonObject
            when (type) {
                "item" -> {
                    assertTrue("item never carries quantity (A177)", "quantity" !in f)
                    assertTrue(f.keys.all { it in setOf("name", "description", "location_id") })
                }
                "stock_adjustment" -> assertTrue("item_id" in f && f["delta"]!!.jsonPrimitive.longOrNull != null)
                "item_label" -> {
                    assertEquals(setOf("item_id", "label_id"), f.keys)
                    if (m["op"]?.jsonPrimitive?.content != "delete") assertEquals(0L, m["base_version"]!!.jsonPrimitive.longOrNull)
                }
            }
        }
    }

    private fun checkGolden(name: String, actual: String) {
        val file = File(GOLDEN_DIR, name)
        if (UPDATE_GOLDEN) {
            file.parentFile!!.mkdirs()
            file.writeText(actual)
            return
        }
        assertTrue("golden $file is missing; regenerate deliberately with HHO_UPDATE_GOLDEN=1", file.isFile)
        assertEquals("golden $name differs from what the app now sends; if intended, regenerate with HHO_UPDATE_GOLDEN=1 and review", file.readText(), actual)
    }

    private fun pretty(e: JsonElement): String = PRETTY.encodeToString(JsonElement.serializer(), e) + "\n"

    private fun open(): HhoDatabase = fileHhoDatabase(dbName).also { dbs += it }

    private fun editRepo(db: HhoDatabase, onSync: () -> Unit = {}): EditRepository =
        EditRepository(db, OutboxRepository(db, mutationIds, { tick++ }), entityIds, onSync)

    private companion object {
        const val DEVICE = "stocktake-device-1"
        const val ID_EPOCH_MS = 1_780_000_000_000L
        const val ITEM_COUNT = 30
        const val SEED_QUANTITY = 10L
        const val SEED_VERSION = 1L
        val SEED_EDGES = listOf(28 to 1, 28 to 2, 29 to 1, 29 to 2, 30 to 1)
        val GOLDEN_DIR = File("src/test/resources/golden")
        val UPDATE_GOLDEN = System.getenv("HHO_UPDATE_GOLDEN") == "1" || System.getProperty("hho.updateGolden") == "true"
        val PRETTY = Json { prettyPrint = true; prettyPrintIndent = "  " }

        fun two(n: Int) = n.toString().padStart(2, '0')

        fun uuid(kind: Int, n: Int) = "00000000-0000-7000-8000-${kind.toString().padStart(4, '0')}${n.toString().padStart(8, '0')}"

        fun item(n: Int) = uuid(1, n)

        fun location(n: Int) = uuid(2, n)

        fun label(n: Int) = uuid(3, n)

        fun edge(n: Int) = uuid(4, n)

        fun seedMutationId(n: Int) = uuid(5, n)

        fun <T> EditResult<T>.ok(): T =
            (this as? EditResult.Success<T>)?.value ?: error("edit refused: $this")
    }
}
