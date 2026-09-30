package dev.hho.android.e2e

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.auth.DeviceTokenProvider
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.BaseUrlInterceptor
import dev.hho.android.data.network.ClientVersionInterceptor
import dev.hho.android.data.network.InstanceBaseUrlResolver
import dev.hho.android.data.settings.SettingsKeys
import dev.hho.android.di.NetworkModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume
import org.junit.rules.ExternalResource
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class RealServerHarness : ExternalResource(), AutoCloseable {
    private var process: Process? = null
    private var shutdownHook: Thread? = null
    private lateinit var workDir: File
    private lateinit var logFile: File
    private val json = Json { ignoreUnknownKeys = true }
    private val readbackHttp = OkHttpClient()

    @Volatile
    private var deviceToken: String? = null

    lateinit var baseUrl: HttpUrl
        private set

    val username: String = "owner"
    val password: String = "correct-horse-battery-staple"

    lateinit var groupId: String
        private set

    val pid: Long get() = pidOf(checkNotNull(process) { "harness not started" })

    val serverLog: File get() = logFile

    fun newApiClient(): HhoApiClient {
        val token = checkNotNull(deviceToken) { "harness not started" }
        return buildClient(baseUrl, token)
    }

    override fun before() {
        start()
    }

    override fun after() {
        close()
    }

    fun start() {
        val bin = resolveBinary()
        Assume.assumeTrue(SKIP_MESSAGE, bin != null)
        workDir = File.createTempFile("hho-e2e-", "").also {
            it.delete()
            check(it.mkdirs()) { "cannot create $it" }
        }
        val dataDir = File(workDir, "data").also { it.mkdirs() }
        logFile = File(workDir, "server.log")
        try {
            launch(checkNotNull(bin), dataDir)
            bootstrap()
        } catch (t: Throwable) {
            val tail = runCatching { logFile.readText().takeLast(4000) }.getOrDefault("")
            close()
            throw AssertionError("real-server harness failed to start: ${t.message}\n--- server log tail ---\n$tail", t)
        }
    }

    private fun launch(bin: File, dataDir: File) {
        var lastFailure: Throwable? = null
        repeat(LAUNCH_ATTEMPTS) {
            val port = freePort()
            val pb = ProcessBuilder(bin.absolutePath, "serve", "--addr", "127.0.0.1:$port")
                .redirectErrorStream(true)
                .redirectOutput(logFile)
            pb.environment().apply {
                keys.filter { it.startsWith("HHO_") }.forEach { remove(it) }
                put("HHO_DATA_DIR", dataDir.absolutePath)
            }
            val p = pb.start()
            process = p
            registerShutdownHook(p)
            baseUrl = "http://127.0.0.1:$port/".toHttpUrlOrThrow()
            try {
                awaitReady(p)
                return
            } catch (t: Throwable) {
                lastFailure = t
                terminate(p)
                process = null
            }
        }
        throw IllegalStateException("server did not become ready after $LAUNCH_ATTEMPTS attempts", lastFailure)
    }

    private fun awaitReady(p: Process) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(READY_TIMEOUT_SECONDS)
        val statusUrl = baseUrl.resolve("api/v1/status")!!
        while (System.nanoTime() < deadline) {
            check(p.isAlive) { "server exited early with code ${p.exitValue()}" }
            val ok = runCatching {
                readbackHttp.newCall(Request.Builder().url(statusUrl).build()).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) return
            Thread.sleep(POLL_MILLIS)
        }
        error("no 200 from $statusUrl within ${READY_TIMEOUT_SECONDS}s")
    }

    private fun bootstrap() {
        val register = post(
            "api/v1/auth/register",
            buildJsonObject {
                put("username", username)
                put("password", password)
            },
            token = null,
        )
        check(register.first == 201) { "register answered ${register.first}" }
        groupId = json.parseToJsonElement(register.second).jsonObject.getValue("group_id").jsonPrimitive.content

        val bootstrapClient = buildClient(baseUrl, token = null)
        runBlocking {
            val login = bootstrapClient.login(username, password).getOrThrow()
            val issued = bootstrapClient.issueDeviceToken("e2e-harness", login.sessionCookie).getOrThrow()
            deviceToken = issued.token
        }
    }

    fun seedItem(
        name: String,
        quantity: Long = 0,
        description: String? = null,
    ): JsonObject {
        val (code, body) = post(
            "api/v1/items",
            buildJsonObject {
                put("name", name)
                put("quantity", quantity)
                if (description != null) put("description", description)
            },
            token = deviceToken,
        )
        check(code == 201) { "createItem answered $code" }
        return json.parseToJsonElement(body).jsonObject
    }

    fun readItem(id: String): JsonObject? {
        val (code, body) = get("api/v1/items/$id")
        if (code == 404) return null
        check(code == 200) { "getItem answered $code" }
        return json.parseToJsonElement(body).jsonObject
    }

    fun listItems(): List<JsonObject> {
        val (code, body) = get("api/v1/items?limit=200")
        check(code == 200) { "listItems answered $code" }
        val parsed = json.parseToJsonElement(body)
        val array: JsonArray = if (parsed is JsonArray) parsed else parsed.jsonObject.getValue("items").jsonArray
        return array.map { it.jsonObject }
    }

    private fun get(path: String): Pair<Int, String> {
        val req = Request.Builder().url(baseUrl.resolve(path)!!)
            .header("Authorization", "Bearer ${checkNotNull(deviceToken)}")
            .build()
        return readbackHttp.newCall(req).execute().use { it.code to it.body!!.string() }
    }

    private fun post(
        path: String,
        body: JsonObject,
        token: String?,
    ): Pair<Int, String> {
        val req = Request.Builder().url(baseUrl.resolve(path)!!)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return readbackHttp.newCall(req).execute().use { it.code to it.body!!.string() }
    }

    override fun close() {
        process?.let { terminate(it) }
        process = null
        shutdownHook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
        shutdownHook = null
        if (::workDir.isInitialized) workDir.deleteRecursively()
        readbackHttp.dispatcher.executorService.shutdown()
        readbackHttp.connectionPool.evictAll()
    }

    private fun registerShutdownHook(p: Process) {
        shutdownHook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
        val hook = Thread { kill(p) }
        Runtime.getRuntime().addShutdownHook(hook)
        shutdownHook = hook
    }

    private fun terminate(p: Process) {
        p.destroy()
        if (!p.waitFor(GRACEFUL_STOP_SECONDS, TimeUnit.SECONDS)) kill(p)
        p.waitFor(GRACEFUL_STOP_SECONDS, TimeUnit.SECONDS)
    }

    private fun kill(p: Process) {
        descendantsOf(p).forEach { runCatching { it.javaClass.getMethod("destroyForcibly").invoke(it) } }
        p.destroyForcibly()
    }

    companion object {
        const val SKIP_MESSAGE = "HHO_SERVER_BIN not set: real-server e2e tests skipped (see T170g)"

        private const val READY_TIMEOUT_SECONDS = 30L
        private const val GRACEFUL_STOP_SECONDS = 5L
        private const val POLL_MILLIS = 100L
        private const val LAUNCH_ATTEMPTS = 3

        fun resolveBinary(): File? {
            val raw = System.getenv("HHO_SERVER_BIN")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("HHO_SERVER_BIN")?.takeIf { it.isNotBlank() }
            return raw?.let(::File)?.takeIf { it.isFile && it.canExecute() }
        }

        private fun pidOf(p: Process): Long = Process::class.java.getMethod("pid").invoke(p) as Long

        private fun descendantsOf(p: Process): List<Any> =
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val stream = Process::class.java.getMethod("descendants").invoke(p) as java.util.stream.Stream<Any>
                stream.toList()
            }.getOrDefault(emptyList())

        private fun freePort(): Int =
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

        private fun String.toHttpUrlOrThrow(): HttpUrl = HttpUrl.Builder().apply {
            val u = java.net.URI(this@toHttpUrlOrThrow)
            scheme(u.scheme).host(u.host).port(u.port)
        }.build()

        private fun buildClient(
            baseUrl: HttpUrl,
            token: String?,
        ): HhoApiClient {
            val file = File.createTempFile("hho-e2e-prefs", ".preferences_pb").also { it.deleteOnExit() }
            val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
            runBlocking { dataStore.edit { it[SettingsKeys.INSTANCE_BASE_URL] = baseUrl.toString() } }
            val cv = ClientVersionInterceptor()
            val provider = object : DeviceTokenProvider {
                override suspend fun tokenFor(requestUrl: HttpUrl): String? = token

                override suspend fun invalidate(
                    requestUrl: HttpUrl,
                    rejectedToken: String,
                ) = Unit
            }
            val http = NetworkModule.provideOkHttpClient(
                BaseUrlInterceptor(InstanceBaseUrlResolver(dataStore)),
                AuthHeaderInterceptor(provider),
                cv,
            )
            return HhoApiClient(http, NetworkModule.provideProbeOkHttpClient(http, cv))
        }
    }
}
