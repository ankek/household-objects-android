@file:Suppress(
    "ArrayInDataClass",
    "DuplicatedCode",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "RemoveRedundantCallsOfConversionMethods",
    "REDUNDANT_CALL_OF_CONVERSION_METHOD",
    "RedundantUnitReturnType",
    "RemoveEmptyClassBody",
    "UnnecessaryVariable",
    "UnusedImport",
    "UnnecessaryVariable",
    "unused"
)

package dev.hho.android.data.apiclient.generated.apis

import java.io.IOException
import okhttp3.Call
import okhttp3.HttpUrl

import dev.hho.android.data.apiclient.generated.models.Label
import dev.hho.android.data.apiclient.generated.models.LabelCreateRequest
import dev.hho.android.data.apiclient.generated.models.LabelListResponse
import dev.hho.android.data.apiclient.generated.models.LabelUpdateRequest
import dev.hho.android.data.apiclient.generated.models.Problem
import dev.hho.android.data.apiclient.generated.models.UpgradeRequiredProblem

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.hho.android.data.apiclient.generated.infrastructure.ApiClient
import dev.hho.android.data.apiclient.generated.infrastructure.ApiResponse
import dev.hho.android.data.apiclient.generated.infrastructure.ClientException
import dev.hho.android.data.apiclient.generated.infrastructure.ClientError
import dev.hho.android.data.apiclient.generated.infrastructure.ServerException
import dev.hho.android.data.apiclient.generated.infrastructure.ServerError
import dev.hho.android.data.apiclient.generated.infrastructure.MultiValueMap
import dev.hho.android.data.apiclient.generated.infrastructure.PartConfig
import dev.hho.android.data.apiclient.generated.infrastructure.RequestConfig
import dev.hho.android.data.apiclient.generated.infrastructure.RequestMethod
import dev.hho.android.data.apiclient.generated.infrastructure.ResponseType
import dev.hho.android.data.apiclient.generated.infrastructure.Success
import dev.hho.android.data.apiclient.generated.infrastructure.toMultiValue
import dev.hho.android.data.apiclient.generated.infrastructure.Serializer

open class LabelsApi(basePath: kotlin.String = defaultBasePath, client: Call.Factory = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.BASE_URL_KEY, "/api/v1")
        }
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun attachItemLabel(itemID: kotlin.String, labelID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = attachItemLabelWithHttpInfo(itemID = itemID, labelID = labelID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> Unit
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    @Throws(IllegalStateException::class, IOException::class)
    fun attachItemLabelWithHttpInfo(itemID: kotlin.String, labelID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = attachItemLabelRequestConfig(itemID = itemID, labelID = labelID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun attachItemLabelRequestConfig(itemID: kotlin.String, labelID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/items/{itemID}/labels/{labelID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"labelID"+"}", encodeURIComponent(labelID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun createLabel(labelCreateRequest: LabelCreateRequest, xHHOClientVersion: kotlin.String? = null) : Label {
        val localVarResponse = createLabelWithHttpInfo(labelCreateRequest = labelCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as Label
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun createLabelWithHttpInfo(labelCreateRequest: LabelCreateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<Label?> {
        val localVariableConfig = createLabelRequestConfig(labelCreateRequest = labelCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<LabelCreateRequest, Label>(
            localVariableConfig
        )
    }

    fun createLabelRequestConfig(labelCreateRequest: LabelCreateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<LabelCreateRequest> {
        val localVariableBody = labelCreateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/labels",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteLabel(labelID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteLabelWithHttpInfo(labelID = labelID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> Unit
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    @Throws(IllegalStateException::class, IOException::class)
    fun deleteLabelWithHttpInfo(labelID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteLabelRequestConfig(labelID = labelID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteLabelRequestConfig(labelID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/labels/{labelID}".replace("{"+"labelID"+"}", encodeURIComponent(labelID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun detachItemLabel(itemID: kotlin.String, labelID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = detachItemLabelWithHttpInfo(itemID = itemID, labelID = labelID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> Unit
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    @Throws(IllegalStateException::class, IOException::class)
    fun detachItemLabelWithHttpInfo(itemID: kotlin.String, labelID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = detachItemLabelRequestConfig(itemID = itemID, labelID = labelID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun detachItemLabelRequestConfig(itemID: kotlin.String, labelID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/items/{itemID}/labels/{labelID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"labelID"+"}", encodeURIComponent(labelID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun listItemLabels(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : LabelListResponse {
        val localVarResponse = listItemLabelsWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as LabelListResponse
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun listItemLabelsWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<LabelListResponse?> {
        val localVariableConfig = listItemLabelsRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, LabelListResponse>(
            localVariableConfig
        )
    }

    fun listItemLabelsRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/labels".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun listLabels(xHHOClientVersion: kotlin.String? = null) : LabelListResponse {
        val localVarResponse = listLabelsWithHttpInfo(xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as LabelListResponse
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun listLabelsWithHttpInfo(xHHOClientVersion: kotlin.String?) : ApiResponse<LabelListResponse?> {
        val localVariableConfig = listLabelsRequestConfig(xHHOClientVersion = xHHOClientVersion)

        return request<Unit, LabelListResponse>(
            localVariableConfig
        )
    }

    fun listLabelsRequestConfig(xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/labels",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateLabel(labelID: kotlin.String, labelUpdateRequest: LabelUpdateRequest, xHHOClientVersion: kotlin.String? = null) : Label {
        val localVarResponse = updateLabelWithHttpInfo(labelID = labelID, labelUpdateRequest = labelUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as Label
            ResponseType.Informational -> throw UnsupportedOperationException("Client does not support Informational responses.")
            ResponseType.Redirection -> throw UnsupportedOperationException("Client does not support Redirection responses.")
            ResponseType.ClientError -> {
                val localVarError = localVarResponse as ClientError<*>
                throw ClientException("Client error : ${localVarError.statusCode} ${localVarError.message.orEmpty()}", localVarError.statusCode, localVarResponse)
            }
            ResponseType.ServerError -> {
                val localVarError = localVarResponse as ServerError<*>
                throw ServerException("Server error : ${localVarError.statusCode} ${localVarError.message.orEmpty()} ${localVarError.body}", localVarError.statusCode, localVarResponse)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class)
    fun updateLabelWithHttpInfo(labelID: kotlin.String, labelUpdateRequest: LabelUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<Label?> {
        val localVariableConfig = updateLabelRequestConfig(labelID = labelID, labelUpdateRequest = labelUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<LabelUpdateRequest, Label>(
            localVariableConfig
        )
    }

    fun updateLabelRequestConfig(labelID: kotlin.String, labelUpdateRequest: LabelUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<LabelUpdateRequest> {
        val localVariableBody = labelUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/labels/{labelID}".replace("{"+"labelID"+"}", encodeURIComponent(labelID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    private fun encodeURIComponent(uriComponent: kotlin.String): kotlin.String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
