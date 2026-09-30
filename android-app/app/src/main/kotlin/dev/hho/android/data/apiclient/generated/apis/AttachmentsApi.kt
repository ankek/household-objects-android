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

import dev.hho.android.data.apiclient.generated.models.Attachment
import dev.hho.android.data.apiclient.generated.models.AttachmentListResponse
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

open class AttachmentsApi(basePath: kotlin.String = defaultBasePath, client: Call.Factory = ApiClient.defaultClient) : ApiClient(basePath, client) {
    companion object {
        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(ApiClient.BASE_URL_KEY, "/api/v1")
        }
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteItemAttachment(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteItemAttachmentWithHttpInfo(itemID = itemID, attachmentID = attachmentID, xHHOClientVersion = xHHOClientVersion)

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
    fun deleteItemAttachmentWithHttpInfo(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteItemAttachmentRequestConfig(itemID = itemID, attachmentID = attachmentID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteItemAttachmentRequestConfig(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/items/{itemID}/attachments/{attachmentID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"attachmentID"+"}", encodeURIComponent(attachmentID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun downloadItemAttachment(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : java.io.File {
        val localVarResponse = downloadItemAttachmentWithHttpInfo(itemID = itemID, attachmentID = attachmentID, xHHOClientVersion = xHHOClientVersion)

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
    fun downloadItemAttachmentWithHttpInfo(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<java.io.File?> {
        val localVariableConfig = downloadItemAttachmentRequestConfig(itemID = itemID, attachmentID = attachmentID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, java.io.File>(
            localVariableConfig
        )
    }

    fun downloadItemAttachmentRequestConfig(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/octet-stream, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/attachments/{attachmentID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"attachmentID"+"}", encodeURIComponent(attachmentID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun downloadItemAttachmentThumbnail(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : java.io.File {
        val localVarResponse = downloadItemAttachmentThumbnailWithHttpInfo(itemID = itemID, attachmentID = attachmentID, xHHOClientVersion = xHHOClientVersion)

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
    fun downloadItemAttachmentThumbnailWithHttpInfo(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<java.io.File?> {
        val localVariableConfig = downloadItemAttachmentThumbnailRequestConfig(itemID = itemID, attachmentID = attachmentID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, java.io.File>(
            localVariableConfig
        )
    }

    fun downloadItemAttachmentThumbnailRequestConfig(itemID: kotlin.String, attachmentID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/attachments/{attachmentID}/thumbnail".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"attachmentID"+"}", encodeURIComponent(attachmentID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun listItemAttachments(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : AttachmentListResponse {
        val localVarResponse = listItemAttachmentsWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as AttachmentListResponse
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
    fun listItemAttachmentsWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<AttachmentListResponse?> {
        val localVariableConfig = listItemAttachmentsRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, AttachmentListResponse>(
            localVariableConfig
        )
    }

    fun listItemAttachmentsRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/attachments".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

     enum class CategoryUploadItemAttachment(val value: kotlin.String) {
         @SerialName(value = "image") Image("image"),
         @SerialName(value = "manual") Manual("manual"),
         @SerialName(value = "warranty") Warranty("warranty"),
         @SerialName(value = "receipt") Receipt("receipt"),
         @SerialName(value = "general") General("general");

        override fun toString(): kotlin.String = "$value"
     }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun uploadItemAttachment(itemID: kotlin.String, category: CategoryUploadItemAttachment, file: java.io.File, xHHOClientVersion: kotlin.String? = null) : Attachment {
        val localVarResponse = uploadItemAttachmentWithHttpInfo(itemID = itemID, category = category, file = file, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as Attachment
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
    fun uploadItemAttachmentWithHttpInfo(itemID: kotlin.String, category: CategoryUploadItemAttachment, file: java.io.File, xHHOClientVersion: kotlin.String?) : ApiResponse<Attachment?> {
        val localVariableConfig = uploadItemAttachmentRequestConfig(itemID = itemID, category = category, file = file, xHHOClientVersion = xHHOClientVersion)

        return request<Map<String, PartConfig<*>>, Attachment>(
            localVariableConfig
        )
    }

    fun uploadItemAttachmentRequestConfig(itemID: kotlin.String, category: CategoryUploadItemAttachment, file: java.io.File, xHHOClientVersion: kotlin.String?) : RequestConfig<Map<String, PartConfig<*>>> {
        val localVariableBody = mapOf(
            "category" to PartConfig(body = category.value, headers = mutableMapOf()),
            "file" to PartConfig(body = file, headers = mutableMapOf("Content-Type" to "*/*")),)
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf("Content-Type" to "multipart/form-data")
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/items/{itemID}/attachments".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    private fun encodeURIComponent(uriComponent: kotlin.String): kotlin.String =
        HttpUrl.Builder().scheme("http").host("localhost").addPathSegment(uriComponent).build().encodedPathSegments[0]
}
