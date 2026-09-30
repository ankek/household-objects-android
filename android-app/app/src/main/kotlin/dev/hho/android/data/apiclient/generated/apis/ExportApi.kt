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

open class ExportApi(basePath: kotlin.String = defaultBasePath, client: Call.Factory = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.BASE_URL_KEY, "/api/v1")
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun exportBoM(locationId: kotlin.collections.List<kotlin.String>? = null, labelId: kotlin.collections.List<kotlin.String>? = null, itemId: kotlin.collections.List<kotlin.String>? = null, xHHOClientVersion: kotlin.String? = null) : java.io.File {
        val localVarResponse = exportBoMWithHttpInfo(locationId = locationId, labelId = labelId, itemId = itemId, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as java.io.File
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
    fun exportBoMWithHttpInfo(locationId: kotlin.collections.List<kotlin.String>?, labelId: kotlin.collections.List<kotlin.String>?, itemId: kotlin.collections.List<kotlin.String>?, xHHOClientVersion: kotlin.String?) : ApiResponse<java.io.File?> {
        val localVariableConfig = exportBoMRequestConfig(locationId = locationId, labelId = labelId, itemId = itemId, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, java.io.File>(
            localVariableConfig
        )
    }

    fun exportBoMRequestConfig(locationId: kotlin.collections.List<kotlin.String>?, labelId: kotlin.collections.List<kotlin.String>?, itemId: kotlin.collections.List<kotlin.String>?, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf<kotlin.String, kotlin.collections.List<kotlin.String>>()
            .apply {
                if (locationId != null) {
                    put("location_id", toMultiValue(locationId.toList(), "multi"))
                }
                if (labelId != null) {
                    put("label_id", toMultiValue(labelId.toList(), "multi"))
                }
                if (itemId != null) {
                    put("item_id", toMultiValue(itemId.toList(), "multi"))
                }
            }
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/export/bom",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun exportItemsCSV(xHHOClientVersion: kotlin.String? = null) : java.io.File {
        val localVarResponse = exportItemsCSVWithHttpInfo(xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as java.io.File
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
    fun exportItemsCSVWithHttpInfo(xHHOClientVersion: kotlin.String?) : ApiResponse<java.io.File?> {
        val localVariableConfig = exportItemsCSVRequestConfig(xHHOClientVersion = xHHOClientVersion)

        return request<Unit, java.io.File>(
            localVariableConfig
        )
    }

    fun exportItemsCSVRequestConfig(xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/export/items.csv",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    private fun encodeURIComponent(uriComponent: kotlin.String): kotlin.String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
