package dev.hho.android.data.outbox

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy

internal class LedgerFakeServer : Dispatcher() {
    enum class Fault { NONE, SERVER_ERROR_500, APPLY_THEN_DROP_RESPONSE }

    private val ledger = HashSet<String>()

    val appliedCount = HashMap<String, Int>()

    val applyOrder = ArrayList<String>()

    val entityState = HashMap<String, MutableMap<String, String>>()

    val versions = HashMap<String, Long>()

    val baseVersions = HashMap<String, Long>()

    val poisonedEntityIds = HashSet<String>()
    var pushRequests = 0
        private set
    val faults = ArrayDeque<Fault>()

    val batchSizes = ArrayList<Int>()

    val sentIds = ArrayList<List<String>>()

    val requestBodies = ArrayList<String>()

    val skippedCount = HashMap<String, Int>()

    val opById = HashMap<String, String>()

    val fieldsById = HashMap<String, JsonObject>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.path.orEmpty().endsWith("/sync/push").not()) return json(404, """{"title":"x","status":404}""")
        pushRequests++
        val fault = faults.removeFirstOrNull() ?: Fault.NONE
        if (fault == Fault.SERVER_ERROR_500) {
            return json(500, """{"type":"about:blank","title":"boom","status":500}""")
        }
        val bodyText = request.body.readUtf8()
        requestBodies += bodyText
        val muts = Json.parseToJsonElement(bodyText).jsonObject["mutations"]!!.jsonArray.map { it.jsonObject }
        batchSizes += muts.size
        sentIds += muts.map { it["mutation_id"]!!.jsonPrimitive.content }
        muts.forEachIndexed { i, m ->
            if (m["entity_id"]!!.jsonPrimitive.content in poisonedEntityIds) {
                val id = m["mutation_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(400)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody(
                        """{"type":"about:blank","title":"Bad Request","status":400,""" +
                            """"detail":"mutations[$i] (mutation_id=\"$id\"): unsupported field"}""",
                    )
            }
        }
        val applied = ArrayList<String>()
        val skipped = ArrayList<String>()
        for (m in muts) {
            val id = m["mutation_id"]!!.jsonPrimitive.content
            val type = m["entity_type"]!!.jsonPrimitive.content
            val entity = m["entity_id"]!!.jsonPrimitive.content
            if (!ledger.add(id)) {
                skippedCount.merge(id, 1, Int::plus)
                skipped += """{"mutation_id":"$id"}"""
                continue
            }
            val key = "$type/$entity"
            val version = (versions[key] ?: 0L) + 1
            versions[key] = version
            appliedCount.merge(id, 1, Int::plus)
            applyOrder += id
            baseVersions[id] = m["base_version"]!!.jsonPrimitive.content.toLong()
            val fields: JsonObject = m["fields"]!!.jsonObject
            opById[id] = m["op"]?.jsonPrimitive?.content ?: "upsert"
            fieldsById[id] = fields
            val state = entityState.getOrPut(key) { HashMap() }
            fields.forEach { (k, v) -> state[k] = v.jsonPrimitive.content }
            applied += """{"mutation_id":"$id","entity_type":"$type","entity_id":"$entity","version":$version}"""
        }
        val response = json(
            200,
            """{"applied":[${applied.joinToString(",")}],"skipped":[${skipped.joinToString(",")}],""" +
                """"conflicts":[],"new_watermark":99}""",
        )
        return if (fault == Fault.APPLY_THEN_DROP_RESPONSE) {
            response.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        } else {
            response
        }
    }

    private fun json(status: Int, body: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
}
