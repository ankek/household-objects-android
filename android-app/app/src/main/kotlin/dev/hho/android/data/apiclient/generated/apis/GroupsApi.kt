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

import dev.hho.android.data.apiclient.generated.models.GroupMembersResponse
import dev.hho.android.data.apiclient.generated.models.GroupVisibility
import dev.hho.android.data.apiclient.generated.models.GroupVisibilityUpdateRequest
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

open class GroupsApi(basePath: kotlin.String = defaultBasePath, client: Call.Factory = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.BASE_URL_KEY, "/api/v1")
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getGroupDetailVisibility(xHHOClientVersion: kotlin.String? = null) : GroupVisibility {
        val localVarResponse = getGroupDetailVisibilityWithHttpInfo(xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as GroupVisibility
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
    fun getGroupDetailVisibilityWithHttpInfo(xHHOClientVersion: kotlin.String?) : ApiResponse<GroupVisibility?> {
        val localVariableConfig = getGroupDetailVisibilityRequestConfig(xHHOClientVersion = xHHOClientVersion)

        return request<Unit, GroupVisibility>(
            localVariableConfig
        )
    }

    fun getGroupDetailVisibilityRequestConfig(xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/groups/detail-visibility",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun listGroupMembers(xHHOClientVersion: kotlin.String? = null) : GroupMembersResponse {
        val localVarResponse = listGroupMembersWithHttpInfo(xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as GroupMembersResponse
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
    fun listGroupMembersWithHttpInfo(xHHOClientVersion: kotlin.String?) : ApiResponse<GroupMembersResponse?> {
        val localVariableConfig = listGroupMembersRequestConfig(xHHOClientVersion = xHHOClientVersion)

        return request<Unit, GroupMembersResponse>(
            localVariableConfig
        )
    }

    fun listGroupMembersRequestConfig(xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/groups/members",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateGroupDetailVisibility(groupVisibilityUpdateRequest: GroupVisibilityUpdateRequest, xHHOClientVersion: kotlin.String? = null) : GroupVisibility {
        val localVarResponse = updateGroupDetailVisibilityWithHttpInfo(groupVisibilityUpdateRequest = groupVisibilityUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as GroupVisibility
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
    fun updateGroupDetailVisibilityWithHttpInfo(groupVisibilityUpdateRequest: GroupVisibilityUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<GroupVisibility?> {
        val localVariableConfig = updateGroupDetailVisibilityRequestConfig(groupVisibilityUpdateRequest = groupVisibilityUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<GroupVisibilityUpdateRequest, GroupVisibility>(
            localVariableConfig
        )
    }

    fun updateGroupDetailVisibilityRequestConfig(groupVisibilityUpdateRequest: GroupVisibilityUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<GroupVisibilityUpdateRequest> {
        val localVariableBody = groupVisibilityUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/groups/detail-visibility",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    private fun encodeURIComponent(uriComponent: kotlin.String): kotlin.String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
