package dev.hho.android.data.apiclient

import dev.hho.android.data.apiclient.generated.apis.AttachmentsApi
import dev.hho.android.data.apiclient.generated.apis.AuthApi
import dev.hho.android.data.apiclient.generated.apis.StatusApi
import dev.hho.android.data.apiclient.generated.apis.SyncApi
import dev.hho.android.data.apiclient.generated.infrastructure.ApiResponse
import dev.hho.android.data.apiclient.generated.infrastructure.ClientError
import dev.hho.android.data.apiclient.generated.infrastructure.ClientException
import dev.hho.android.data.apiclient.generated.infrastructure.ResponseType
import dev.hho.android.data.apiclient.generated.infrastructure.Serializer
import dev.hho.android.data.apiclient.generated.infrastructure.ServerError
import dev.hho.android.data.apiclient.generated.infrastructure.ServerException
import dev.hho.android.data.apiclient.generated.infrastructure.Success
import dev.hho.android.data.apiclient.generated.infrastructure.Response as GeneratedResponse
import dev.hho.android.data.apiclient.generated.models.DeviceTokenIssueRequest
import dev.hho.android.data.apiclient.generated.models.DeviceTokenIssueResponse
import dev.hho.android.data.apiclient.generated.models.LoginRequest
import dev.hho.android.data.apiclient.generated.models.LoginResponse
import dev.hho.android.data.apiclient.generated.models.Problem
import dev.hho.android.data.apiclient.generated.models.StatusResponse
import dev.hho.android.data.apiclient.generated.models.SyncConflictLogResponse
import dev.hho.android.data.apiclient.generated.models.SyncPullRequest
import dev.hho.android.data.apiclient.generated.models.SyncPushResponse
import dev.hho.android.data.apiclient.generated.models.UpgradeRequiredProblem
import dev.hho.android.data.network.AuthHeaderInterceptor
import dev.hho.android.data.network.NoInstanceUrlConfiguredException
import dev.hho.android.di.ProbeHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val PLACEHOLDER_BASE_URL = "http://placeholder.invalid/api/v1"

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private const val SESSION_COOKIE_NAME = "hho_session"

data class LoginOutcome(
    val response: LoginResponse,
    val sessionCookie: String,
)

@Singleton
class HhoApiClient
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
        @ProbeHttpClient private val probeHttpClient: OkHttpClient,
    ) {
        private val statusApi = StatusApi(PLACEHOLDER_BASE_URL, httpClient)
        private val authApi = AuthApi(PLACEHOLDER_BASE_URL, httpClient)
        private val attachmentsApi = AttachmentsApi(PLACEHOLDER_BASE_URL, httpClient)
        private val syncApi = SyncApi(PLACEHOLDER_BASE_URL, httpClient)

        suspend fun getStatus(): Result<StatusResponse> = apiCall { statusApi.getStatus() }

        suspend fun probeInstance(candidateBaseUrl: HttpUrl): Result<StatusResponse> =
            apiCall {
                val probeBasePath = candidateBaseUrl.newBuilder().addPathSegments("api/v1").build().toString()
                StatusApi(probeBasePath, probeHttpClient).getStatus()
            }

        suspend fun login(
            username: String,
            password: String,
        ): Result<LoginOutcome> =
            apiCall {
                val httpResponse = authApi.loginWithHttpInfo(LoginRequest(username = username, password = password), null)
                val body = httpResponse.bodyOrThrow()
                val sessionCookie = httpResponse.headers.sessionCookieValue()
                    ?: throw MissingSessionCookieException()
                LoginOutcome(response = body, sessionCookie = sessionCookie)
            }

        suspend fun issueDeviceToken(
            deviceLabel: String,
            sessionCookie: String,
        ): Result<DeviceTokenIssueResponse> =
            apiCall {
                val cookieScopedClient =
                    httpClient.newBuilder()
                        .apply { interceptors().removeAll { it is AuthHeaderInterceptor } }
                        .addInterceptor { chain ->
                            chain.proceed(chain.request().newBuilder().header("Cookie", sessionCookie).build())
                        }
                        .build()
                AuthApi(PLACEHOLDER_BASE_URL, cookieScopedClient)
                    .issueDeviceToken(DeviceTokenIssueRequest(deviceLabel = deviceLabel))
            }

        suspend fun downloadAttachment(
            itemId: String,
            attachmentId: String,
        ): Result<File> =
            apiCall {
                attachmentsApi.downloadItemAttachment(itemID = itemId, attachmentID = attachmentId)
            }

        suspend fun uploadAttachment(
            itemId: String,
            category: AttachmentCategory,
            file: File,
        ): Result<AttachmentInfo> =
            apiCall {
                attachmentsApi
                    .uploadItemAttachment(itemID = itemId, category = category.toGenerated(), file = file)
                    .toInfo()
            }

        suspend fun listAttachments(itemId: String): Result<List<AttachmentInfo>> =
            apiCall {
                attachmentsApi.listItemAttachments(itemID = itemId).attachments.map { it.toInfo() }
            }

        suspend fun syncPull(
            deviceId: String,
            since: Long,
            limit: Int,
        ): Result<SyncPullOutcome> =
            apiCall {
                val requestJson =
                    Serializer.kotlinxSerializationJson.encodeToString(
                        SyncPullRequest(deviceId = deviceId, since = since, limit = limit),
                    )
                val request =
                    Request.Builder()
                        .url(PLACEHOLDER_BASE_URL.toHttpUrl().newBuilder().addPathSegments("sync/pull").build())
                        .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                        .header("Accept", "application/json, application/problem+json")
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw httpStatusToApiError(response.code, bodyString)
                    }
                    SyncChangeDecoder.decodePullResponse(
                        Serializer.kotlinxSerializationJson.parseToJsonElement(bodyString),
                    )
                }
            }

        suspend fun syncPush(
            deviceId: String,
            mutations: List<PushMutation>,
        ): Result<SyncPushOutcome> =
            apiCall {
                val requestJson = encodePushRequest(deviceId, mutations)
                val request =
                    Request.Builder()
                        .url(PLACEHOLDER_BASE_URL.toHttpUrl().newBuilder().addPathSegments("sync/push").build())
                        .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                        .header("Accept", "application/json, application/problem+json")
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (response.code == 400) {
                        val detail =
                            runCatching { Serializer.kotlinxSerializationJson.decodeFromString<Problem>(bodyString).detail }.getOrNull()
                        if (detail != null && detail.startsWith(SyncPushDetailParser.STRUCTURAL_PREFIX)) {
                            return@use SyncPushDetailParser.parse(detail)
                        }
                    }
                    if (!response.isSuccessful) {
                        throw httpStatusToApiError(response.code, bodyString)
                    }
                    val decoded = Serializer.kotlinxSerializationJson.decodeFromString<SyncPushResponse>(bodyString)
                    SyncPushOutcome.Success(decoded.applied, decoded.skipped, decoded.conflicts, decoded.newWatermark)
                }
            }

        suspend fun getConflicts(
            after: String? = null,
            limit: Int = 50,
        ): Result<SyncConflictLogResponse> = apiCall { syncApi.listSyncConflicts(limit = limit, after = after) }

        private suspend fun <T> apiCall(block: () -> T): Result<T> =
            withContext(Dispatchers.IO) {
                try {
                    Result.success(block())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Result.failure(e.toApiError())
                }
            }
    }

private fun encodePushRequest(
    deviceId: String,
    mutations: List<PushMutation>,
): String {
    val root: JsonObject =
        buildJsonObject {
            put("device_id", deviceId)
            put(
                "mutations",
                JsonArray(
                    mutations.map { m ->
                        buildJsonObject {
                            put("mutation_id", m.mutationId)
                            put("entity_type", m.entityType)
                            put("entity_id", m.entityId)
                            put("base_version", m.baseVersion)
                            put("fields", JsonObject(m.fields))
                            m.op?.let { put("op", JsonPrimitive(it)) }
                        }
                    },
                ),
            )
        }
    return root.toString()
}

private fun Throwable.toApiError(): ApiError =
    when (this) {
        is ApiError -> this
        is NoInstanceUrlConfiguredException -> ApiError.NoInstanceConfigured(this)
        is ClientException -> toApiError()
        is ServerException -> ApiError.Server(statusCode, problemDetailOf(response), this)
        is MissingSessionCookieException -> ApiError.UnexpectedPayload(message ?: toString(), this)
        is UnknownSyncEntityTypeException -> ApiError.UnexpectedPayload(message ?: toString(), this)
        is MalformedSyncPullResponseException -> ApiError.UnexpectedPayload(message ?: toString(), this)
        is SerializationException -> ApiError.UnexpectedPayload(message ?: "malformed response body", this)
        is UnsupportedOperationException -> ApiError.UnexpectedPayload(message ?: "unsupported response content type", this)
        is IOException -> ApiError.Network(this)
        else -> ApiError.Unknown(this)
    }

private fun ClientException.toApiError(): ApiError {
    if (statusCode == 426) {
        upgradeRequiredProblemOf(response)?.let { return ApiError.UpgradeRequired(it.minimumVersion, it.detail, this) }
    }
    val detail = problemDetailOf(response)
    return when (statusCode) {
        401 -> ApiError.Unauthorized(this)
        404 -> ApiError.NotFound(this)
        413 -> ApiError.PayloadTooLarge(detail, this)
        409 -> ApiError.Conflict(detail, this)
        else -> ApiError.Validation(statusCode, detail, this)
    }
}

private fun httpStatusToApiError(
    statusCode: Int,
    rawBody: String,
): ApiError {
    if (statusCode == 426) {
        runCatching { Serializer.kotlinxSerializationJson.decodeFromString<UpgradeRequiredProblem>(rawBody) }
            .getOrNull()
            ?.let { return ApiError.UpgradeRequired(it.minimumVersion, it.detail) }
    }
    val detail = runCatching { Serializer.kotlinxSerializationJson.decodeFromString<Problem>(rawBody).detail }.getOrNull()
    return when (statusCode) {
        401 -> ApiError.Unauthorized()
        404 -> ApiError.NotFound()
        413 -> ApiError.PayloadTooLarge(detail)
        409 -> ApiError.Conflict(detail)
        in 500..599 -> ApiError.Server(statusCode, detail)
        else -> ApiError.Validation(statusCode, detail)
    }
}

private fun upgradeRequiredProblemOf(response: GeneratedResponse?): UpgradeRequiredProblem? {
    val rawBody =
        when (response) {
            is ClientError<*> -> response.body as? String
            is ServerError<*> -> response.body as? String
            else -> null
        } ?: return null
    return runCatching { Serializer.kotlinxSerializationJson.decodeFromString<UpgradeRequiredProblem>(rawBody) }.getOrNull()
}

private fun problemDetailOf(response: GeneratedResponse?): String? {
    val rawBody =
        when (response) {
            is ClientError<*> -> response.body as? String
            is ServerError<*> -> response.body as? String
            else -> null
        } ?: return null
    return runCatching { Serializer.kotlinxSerializationJson.decodeFromString<Problem>(rawBody).detail }.getOrNull()
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> ApiResponse<T?>.bodyOrThrow(): T =
    when (responseType) {
        ResponseType.Success -> (this as Success<*>).data as T
        ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
        ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
        ResponseType.ClientError -> {
            val error = this as ClientError<*>
            throw ClientException("Client error : ${error.statusCode} ${error.message.orEmpty()}", error.statusCode, this)
        }
        ResponseType.ServerError -> {
            val error = this as ServerError<*>
            throw ServerException("Server error : ${error.statusCode} ${error.message.orEmpty()} ${error.body}", error.statusCode, this)
        }
    }

private fun Map<String, List<String>>.sessionCookieValue(): String? =
    entries
        .firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }
        ?.value
        ?.map { it.substringBefore(';') }
        ?.firstOrNull { it.startsWith("$SESSION_COOKIE_NAME=") }

private class MissingSessionCookieException :
    Exception("Login succeeded but the response carried no $SESSION_COOKIE_NAME session cookie")
