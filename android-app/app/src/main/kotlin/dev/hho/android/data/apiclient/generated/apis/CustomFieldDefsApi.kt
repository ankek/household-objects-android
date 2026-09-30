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

import dev.hho.android.data.apiclient.generated.models.CustomFieldDef
import dev.hho.android.data.apiclient.generated.models.CustomFieldDefCreateRequest
import dev.hho.android.data.apiclient.generated.models.CustomFieldDefListResponse
import dev.hho.android.data.apiclient.generated.models.CustomFieldDefUpdateRequest
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

open class CustomFieldDefsApi(basePath: kotlin.String = defaultBasePath, client: Call.Factory = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.BASE_URL_KEY, "/api/v1")
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun createCustomFieldDef(customFieldDefCreateRequest: CustomFieldDefCreateRequest, xHHOClientVersion: kotlin.String? = null) : CustomFieldDef {
        val localVarResponse = createCustomFieldDefWithHttpInfo(customFieldDefCreateRequest = customFieldDefCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as CustomFieldDef
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
    fun createCustomFieldDefWithHttpInfo(customFieldDefCreateRequest: CustomFieldDefCreateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<CustomFieldDef?> {
        val localVariableConfig = createCustomFieldDefRequestConfig(customFieldDefCreateRequest = customFieldDefCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<CustomFieldDefCreateRequest, CustomFieldDef>(
            localVariableConfig
        )
    }

    fun createCustomFieldDefRequestConfig(customFieldDefCreateRequest: CustomFieldDefCreateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<CustomFieldDefCreateRequest> {
        val localVariableBody = customFieldDefCreateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/custom-field-defs",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteCustomFieldDef(fieldDefID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteCustomFieldDefWithHttpInfo(fieldDefID = fieldDefID, xHHOClientVersion = xHHOClientVersion)

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
    fun deleteCustomFieldDefWithHttpInfo(fieldDefID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteCustomFieldDefRequestConfig(fieldDefID = fieldDefID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteCustomFieldDefRequestConfig(fieldDefID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/custom-field-defs/{fieldDefID}".replace("{"+"fieldDefID"+"}", encodeURIComponent(fieldDefID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun listCustomFieldDefs(xHHOClientVersion: kotlin.String? = null) : CustomFieldDefListResponse {
        val localVarResponse = listCustomFieldDefsWithHttpInfo(xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as CustomFieldDefListResponse
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
    fun listCustomFieldDefsWithHttpInfo(xHHOClientVersion: kotlin.String?) : ApiResponse<CustomFieldDefListResponse?> {
        val localVariableConfig = listCustomFieldDefsRequestConfig(xHHOClientVersion = xHHOClientVersion)

        return request<Unit, CustomFieldDefListResponse>(
            localVariableConfig
        )
    }

    fun listCustomFieldDefsRequestConfig(xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/custom-field-defs",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateCustomFieldDef(fieldDefID: kotlin.String, customFieldDefUpdateRequest: CustomFieldDefUpdateRequest, xHHOClientVersion: kotlin.String? = null) : CustomFieldDef {
        val localVarResponse = updateCustomFieldDefWithHttpInfo(fieldDefID = fieldDefID, customFieldDefUpdateRequest = customFieldDefUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as CustomFieldDef
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
    fun updateCustomFieldDefWithHttpInfo(fieldDefID: kotlin.String, customFieldDefUpdateRequest: CustomFieldDefUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<CustomFieldDef?> {
        val localVariableConfig = updateCustomFieldDefRequestConfig(fieldDefID = fieldDefID, customFieldDefUpdateRequest = customFieldDefUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<CustomFieldDefUpdateRequest, CustomFieldDef>(
            localVariableConfig
        )
    }

    fun updateCustomFieldDefRequestConfig(fieldDefID: kotlin.String, customFieldDefUpdateRequest: CustomFieldDefUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<CustomFieldDefUpdateRequest> {
        val localVariableBody = customFieldDefUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/custom-field-defs/{fieldDefID}".replace("{"+"fieldDefID"+"}", encodeURIComponent(fieldDefID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    private fun encodeURIComponent(uriComponent: kotlin.String): kotlin.String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
