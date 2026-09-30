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
import dev.hho.android.data.apiclient.generated.models.ReportItemCountByLocationResponse
import dev.hho.android.data.apiclient.generated.models.ReportPurchasesResponse
import dev.hho.android.data.apiclient.generated.models.ReportValuationResponse
import dev.hho.android.data.apiclient.generated.models.ReportWarrantyExpiringResponse
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

open class ReportsApi(basePath: kotlin.String = defaultBasePath, client: Call.Factory = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.BASE_URL_KEY, "/api/v1")
        }
    }

     enum class FormatGetReportItemCountByLocation(val value: kotlin.String) {
         @SerialName(value = "json") Json("json"),
         @SerialName(value = "csv") Csv("csv");

        override fun toString(): kotlin.String = "$value"
     }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getReportItemCountByLocation(format: FormatGetReportItemCountByLocation? = FormatGetReportItemCountByLocation.Json, xHHOClientVersion: kotlin.String? = null) : ReportItemCountByLocationResponse {
        val localVarResponse = getReportItemCountByLocationWithHttpInfo(format = format, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as ReportItemCountByLocationResponse
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
    fun getReportItemCountByLocationWithHttpInfo(format: FormatGetReportItemCountByLocation?, xHHOClientVersion: kotlin.String?) : ApiResponse<ReportItemCountByLocationResponse?> {
        val localVariableConfig = getReportItemCountByLocationRequestConfig(format = format, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, ReportItemCountByLocationResponse>(
            localVariableConfig
        )
    }

    fun getReportItemCountByLocationRequestConfig(format: FormatGetReportItemCountByLocation?, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf<kotlin.String, kotlin.collections.List<kotlin.String>>()
            .apply {
                if (format != null) {
                    put("format", listOf(format.value))
                }
            }
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/reports/item-count-by-location",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

     enum class FormatGetReportPurchases(val value: kotlin.String) {
         @SerialName(value = "json") Json("json"),
         @SerialName(value = "csv") Csv("csv");

        override fun toString(): kotlin.String = "$value"
     }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getReportPurchases(from: kotlin.String, to: kotlin.String, format: FormatGetReportPurchases? = FormatGetReportPurchases.Json, xHHOClientVersion: kotlin.String? = null) : ReportPurchasesResponse {
        val localVarResponse = getReportPurchasesWithHttpInfo(from = from, to = to, format = format, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as ReportPurchasesResponse
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
    fun getReportPurchasesWithHttpInfo(from: kotlin.String, to: kotlin.String, format: FormatGetReportPurchases?, xHHOClientVersion: kotlin.String?) : ApiResponse<ReportPurchasesResponse?> {
        val localVariableConfig = getReportPurchasesRequestConfig(from = from, to = to, format = format, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, ReportPurchasesResponse>(
            localVariableConfig
        )
    }

    fun getReportPurchasesRequestConfig(from: kotlin.String, to: kotlin.String, format: FormatGetReportPurchases?, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf<kotlin.String, kotlin.collections.List<kotlin.String>>()
            .apply {
                if (format != null) {
                    put("format", listOf(format.value))
                }
                put("from", listOf(from.toString()))
                put("to", listOf(to.toString()))
            }
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/reports/purchases",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

     enum class GroupByGetReportValuation(val value: kotlin.String) {
         @SerialName(value = "location") Location("location"),
         @SerialName(value = "label") Label("label");

        override fun toString(): kotlin.String = "$value"
     }

     enum class FormatGetReportValuation(val value: kotlin.String) {
         @SerialName(value = "json") Json("json"),
         @SerialName(value = "csv") Csv("csv");

        override fun toString(): kotlin.String = "$value"
     }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getReportValuation(groupBy: GroupByGetReportValuation, format: FormatGetReportValuation? = FormatGetReportValuation.Json, xHHOClientVersion: kotlin.String? = null) : ReportValuationResponse {
        val localVarResponse = getReportValuationWithHttpInfo(groupBy = groupBy, format = format, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as ReportValuationResponse
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
    fun getReportValuationWithHttpInfo(groupBy: GroupByGetReportValuation, format: FormatGetReportValuation?, xHHOClientVersion: kotlin.String?) : ApiResponse<ReportValuationResponse?> {
        val localVariableConfig = getReportValuationRequestConfig(groupBy = groupBy, format = format, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, ReportValuationResponse>(
            localVariableConfig
        )
    }

    fun getReportValuationRequestConfig(groupBy: GroupByGetReportValuation, format: FormatGetReportValuation?, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf<kotlin.String, kotlin.collections.List<kotlin.String>>()
            .apply {
                if (format != null) {
                    put("format", listOf(format.value))
                }
                put("group_by", listOf(groupBy.value))
            }
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/reports/valuation",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

     enum class FormatGetReportWarrantyExpiring(val value: kotlin.String) {
         @SerialName(value = "json") Json("json"),
         @SerialName(value = "csv") Csv("csv");

        override fun toString(): kotlin.String = "$value"
     }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getReportWarrantyExpiring(format: FormatGetReportWarrantyExpiring? = FormatGetReportWarrantyExpiring.Json, withinDays: kotlin.Int? = 30, xHHOClientVersion: kotlin.String? = null) : ReportWarrantyExpiringResponse {
        val localVarResponse = getReportWarrantyExpiringWithHttpInfo(format = format, withinDays = withinDays, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as ReportWarrantyExpiringResponse
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
    fun getReportWarrantyExpiringWithHttpInfo(format: FormatGetReportWarrantyExpiring?, withinDays: kotlin.Int?, xHHOClientVersion: kotlin.String?) : ApiResponse<ReportWarrantyExpiringResponse?> {
        val localVariableConfig = getReportWarrantyExpiringRequestConfig(format = format, withinDays = withinDays, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, ReportWarrantyExpiringResponse>(
            localVariableConfig
        )
    }

    fun getReportWarrantyExpiringRequestConfig(format: FormatGetReportWarrantyExpiring?, withinDays: kotlin.Int?, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf<kotlin.String, kotlin.collections.List<kotlin.String>>()
            .apply {
                if (format != null) {
                    put("format", listOf(format.value))
                }
                if (withinDays != null) {
                    put("within_days", listOf(withinDays.toString()))
                }
            }
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/reports/warranty-expiring",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    private fun encodeURIComponent(uriComponent: kotlin.String): kotlin.String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
