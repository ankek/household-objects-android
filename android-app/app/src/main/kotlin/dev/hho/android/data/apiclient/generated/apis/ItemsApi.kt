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
import dev.hho.android.data.apiclient.generated.models.Identification
import dev.hho.android.data.apiclient.generated.models.IdentificationCreateRequest
import dev.hho.android.data.apiclient.generated.models.IdentificationListResponse
import dev.hho.android.data.apiclient.generated.models.IdentificationUpdateRequest
import dev.hho.android.data.apiclient.generated.models.Item
import dev.hho.android.data.apiclient.generated.models.ItemCustomField
import dev.hho.android.data.apiclient.generated.models.ItemCustomFieldCreateRequest
import dev.hho.android.data.apiclient.generated.models.ItemCustomFieldListResponse
import dev.hho.android.data.apiclient.generated.models.ItemCustomFieldUpdateRequest
import dev.hho.android.data.apiclient.generated.models.ItemListResponse
import dev.hho.android.data.apiclient.generated.models.ItemUpdateRequest
import dev.hho.android.data.apiclient.generated.models.LabelListResponse
import dev.hho.android.data.apiclient.generated.models.Problem
import dev.hho.android.data.apiclient.generated.models.PurchaseBlock
import dev.hho.android.data.apiclient.generated.models.PurchaseCreateRequest
import dev.hho.android.data.apiclient.generated.models.PurchaseUpdateRequest
import dev.hho.android.data.apiclient.generated.models.SaleBlock
import dev.hho.android.data.apiclient.generated.models.SaleCreateRequest
import dev.hho.android.data.apiclient.generated.models.SaleUpdateRequest
import dev.hho.android.data.apiclient.generated.models.StockAdjustment
import dev.hho.android.data.apiclient.generated.models.StockAdjustmentCreateRequest
import dev.hho.android.data.apiclient.generated.models.StockAdjustmentListResponse
import dev.hho.android.data.apiclient.generated.models.UpgradeRequiredProblem
import dev.hho.android.data.apiclient.generated.models.WarrantyBlock
import dev.hho.android.data.apiclient.generated.models.WarrantyCreateRequest
import dev.hho.android.data.apiclient.generated.models.WarrantyUpdateRequest

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

open class ItemsApi(basePath: kotlin.String = defaultBasePath, client: Call.Factory = ApiClient.defaultClient) : ApiClient(basePath, client) {
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
    fun createItem(item: Item, xHHOClientVersion: kotlin.String? = null) : Item {
        val localVarResponse = createItemWithHttpInfo(item = item, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as Item
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
    fun createItemWithHttpInfo(item: Item, xHHOClientVersion: kotlin.String?) : ApiResponse<Item?> {
        val localVariableConfig = createItemRequestConfig(item = item, xHHOClientVersion = xHHOClientVersion)

        return request<Item, Item>(
            localVariableConfig
        )
    }

    fun createItemRequestConfig(item: Item, xHHOClientVersion: kotlin.String?) : RequestConfig<Item> {
        val localVariableBody = item
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/items",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun createItemCustomField(itemID: kotlin.String, itemCustomFieldCreateRequest: ItemCustomFieldCreateRequest, xHHOClientVersion: kotlin.String? = null) : ItemCustomField {
        val localVarResponse = createItemCustomFieldWithHttpInfo(itemID = itemID, itemCustomFieldCreateRequest = itemCustomFieldCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as ItemCustomField
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
    fun createItemCustomFieldWithHttpInfo(itemID: kotlin.String, itemCustomFieldCreateRequest: ItemCustomFieldCreateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<ItemCustomField?> {
        val localVariableConfig = createItemCustomFieldRequestConfig(itemID = itemID, itemCustomFieldCreateRequest = itemCustomFieldCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<ItemCustomFieldCreateRequest, ItemCustomField>(
            localVariableConfig
        )
    }

    fun createItemCustomFieldRequestConfig(itemID: kotlin.String, itemCustomFieldCreateRequest: ItemCustomFieldCreateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<ItemCustomFieldCreateRequest> {
        val localVariableBody = itemCustomFieldCreateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/items/{itemID}/custom-fields".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun createItemIdentification(itemID: kotlin.String, identificationCreateRequest: IdentificationCreateRequest, xHHOClientVersion: kotlin.String? = null) : Identification {
        val localVarResponse = createItemIdentificationWithHttpInfo(itemID = itemID, identificationCreateRequest = identificationCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as Identification
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
    fun createItemIdentificationWithHttpInfo(itemID: kotlin.String, identificationCreateRequest: IdentificationCreateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<Identification?> {
        val localVariableConfig = createItemIdentificationRequestConfig(itemID = itemID, identificationCreateRequest = identificationCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<IdentificationCreateRequest, Identification>(
            localVariableConfig
        )
    }

    fun createItemIdentificationRequestConfig(itemID: kotlin.String, identificationCreateRequest: IdentificationCreateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<IdentificationCreateRequest> {
        val localVariableBody = identificationCreateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/items/{itemID}/identifications".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun createItemPurchase(itemID: kotlin.String, purchaseCreateRequest: PurchaseCreateRequest, xHHOClientVersion: kotlin.String? = null) : PurchaseBlock {
        val localVarResponse = createItemPurchaseWithHttpInfo(itemID = itemID, purchaseCreateRequest = purchaseCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as PurchaseBlock
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
    fun createItemPurchaseWithHttpInfo(itemID: kotlin.String, purchaseCreateRequest: PurchaseCreateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<PurchaseBlock?> {
        val localVariableConfig = createItemPurchaseRequestConfig(itemID = itemID, purchaseCreateRequest = purchaseCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<PurchaseCreateRequest, PurchaseBlock>(
            localVariableConfig
        )
    }

    fun createItemPurchaseRequestConfig(itemID: kotlin.String, purchaseCreateRequest: PurchaseCreateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<PurchaseCreateRequest> {
        val localVariableBody = purchaseCreateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/items/{itemID}/purchase".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun createItemSale(itemID: kotlin.String, saleCreateRequest: SaleCreateRequest, xHHOClientVersion: kotlin.String? = null) : SaleBlock {
        val localVarResponse = createItemSaleWithHttpInfo(itemID = itemID, saleCreateRequest = saleCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as SaleBlock
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
    fun createItemSaleWithHttpInfo(itemID: kotlin.String, saleCreateRequest: SaleCreateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<SaleBlock?> {
        val localVariableConfig = createItemSaleRequestConfig(itemID = itemID, saleCreateRequest = saleCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<SaleCreateRequest, SaleBlock>(
            localVariableConfig
        )
    }

    fun createItemSaleRequestConfig(itemID: kotlin.String, saleCreateRequest: SaleCreateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<SaleCreateRequest> {
        val localVariableBody = saleCreateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/items/{itemID}/sale".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun createItemStockAdjustment(itemID: kotlin.String, stockAdjustmentCreateRequest: StockAdjustmentCreateRequest, xHHOClientVersion: kotlin.String? = null) : StockAdjustment {
        val localVarResponse = createItemStockAdjustmentWithHttpInfo(itemID = itemID, stockAdjustmentCreateRequest = stockAdjustmentCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as StockAdjustment
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
    fun createItemStockAdjustmentWithHttpInfo(itemID: kotlin.String, stockAdjustmentCreateRequest: StockAdjustmentCreateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<StockAdjustment?> {
        val localVariableConfig = createItemStockAdjustmentRequestConfig(itemID = itemID, stockAdjustmentCreateRequest = stockAdjustmentCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<StockAdjustmentCreateRequest, StockAdjustment>(
            localVariableConfig
        )
    }

    fun createItemStockAdjustmentRequestConfig(itemID: kotlin.String, stockAdjustmentCreateRequest: StockAdjustmentCreateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<StockAdjustmentCreateRequest> {
        val localVariableBody = stockAdjustmentCreateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/items/{itemID}/stock-adjustments".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun createItemWarranty(itemID: kotlin.String, warrantyCreateRequest: WarrantyCreateRequest, xHHOClientVersion: kotlin.String? = null) : WarrantyBlock {
        val localVarResponse = createItemWarrantyWithHttpInfo(itemID = itemID, warrantyCreateRequest = warrantyCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as WarrantyBlock
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
    fun createItemWarrantyWithHttpInfo(itemID: kotlin.String, warrantyCreateRequest: WarrantyCreateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<WarrantyBlock?> {
        val localVariableConfig = createItemWarrantyRequestConfig(itemID = itemID, warrantyCreateRequest = warrantyCreateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<WarrantyCreateRequest, WarrantyBlock>(
            localVariableConfig
        )
    }

    fun createItemWarrantyRequestConfig(itemID: kotlin.String, warrantyCreateRequest: WarrantyCreateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<WarrantyCreateRequest> {
        val localVariableBody = warrantyCreateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.POST,
            path = "/items/{itemID}/warranty".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteItem(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteItemWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

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
    fun deleteItemWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteItemRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteItemRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/items/{itemID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
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

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteItemCustomField(itemID: kotlin.String, customFieldID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteItemCustomFieldWithHttpInfo(itemID = itemID, customFieldID = customFieldID, xHHOClientVersion = xHHOClientVersion)

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
    fun deleteItemCustomFieldWithHttpInfo(itemID: kotlin.String, customFieldID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteItemCustomFieldRequestConfig(itemID = itemID, customFieldID = customFieldID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteItemCustomFieldRequestConfig(itemID: kotlin.String, customFieldID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/items/{itemID}/custom-fields/{customFieldID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"customFieldID"+"}", encodeURIComponent(customFieldID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteItemIdentification(itemID: kotlin.String, identificationID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteItemIdentificationWithHttpInfo(itemID = itemID, identificationID = identificationID, xHHOClientVersion = xHHOClientVersion)

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
    fun deleteItemIdentificationWithHttpInfo(itemID: kotlin.String, identificationID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteItemIdentificationRequestConfig(itemID = itemID, identificationID = identificationID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteItemIdentificationRequestConfig(itemID: kotlin.String, identificationID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/items/{itemID}/identifications/{identificationID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"identificationID"+"}", encodeURIComponent(identificationID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteItemPurchase(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteItemPurchaseWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

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
    fun deleteItemPurchaseWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteItemPurchaseRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteItemPurchaseRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/items/{itemID}/purchase".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteItemSale(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteItemSaleWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

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
    fun deleteItemSaleWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteItemSaleRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteItemSaleRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/items/{itemID}/sale".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun deleteItemWarranty(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Unit {
        val localVarResponse = deleteItemWarrantyWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

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
    fun deleteItemWarrantyWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Unit?> {
        val localVariableConfig = deleteItemWarrantyRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Unit>(
            localVariableConfig
        )
    }

    fun deleteItemWarrantyRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.DELETE,
            path = "/items/{itemID}/warranty".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
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
    fun getItem(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : Item {
        val localVarResponse = getItemWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as Item
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
    fun getItemWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<Item?> {
        val localVariableConfig = getItemRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, Item>(
            localVariableConfig
        )
    }

    fun getItemRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getItemPurchase(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : PurchaseBlock {
        val localVarResponse = getItemPurchaseWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as PurchaseBlock
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
    fun getItemPurchaseWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<PurchaseBlock?> {
        val localVariableConfig = getItemPurchaseRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, PurchaseBlock>(
            localVariableConfig
        )
    }

    fun getItemPurchaseRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/purchase".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getItemSale(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : SaleBlock {
        val localVarResponse = getItemSaleWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as SaleBlock
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
    fun getItemSaleWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<SaleBlock?> {
        val localVariableConfig = getItemSaleRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, SaleBlock>(
            localVariableConfig
        )
    }

    fun getItemSaleRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/sale".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun getItemWarranty(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : WarrantyBlock {
        val localVarResponse = getItemWarrantyWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as WarrantyBlock
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
    fun getItemWarrantyWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<WarrantyBlock?> {
        val localVariableConfig = getItemWarrantyRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, WarrantyBlock>(
            localVariableConfig
        )
    }

    fun getItemWarrantyRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/warranty".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
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

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun listItemCustomFields(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : ItemCustomFieldListResponse {
        val localVarResponse = listItemCustomFieldsWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as ItemCustomFieldListResponse
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
    fun listItemCustomFieldsWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<ItemCustomFieldListResponse?> {
        val localVariableConfig = listItemCustomFieldsRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, ItemCustomFieldListResponse>(
            localVariableConfig
        )
    }

    fun listItemCustomFieldsRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/custom-fields".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun listItemIdentifications(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : IdentificationListResponse {
        val localVarResponse = listItemIdentificationsWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as IdentificationListResponse
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
    fun listItemIdentificationsWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<IdentificationListResponse?> {
        val localVariableConfig = listItemIdentificationsRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, IdentificationListResponse>(
            localVariableConfig
        )
    }

    fun listItemIdentificationsRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/identifications".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
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
    fun listItemStockAdjustments(itemID: kotlin.String, xHHOClientVersion: kotlin.String? = null) : StockAdjustmentListResponse {
        val localVarResponse = listItemStockAdjustmentsWithHttpInfo(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as StockAdjustmentListResponse
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
    fun listItemStockAdjustmentsWithHttpInfo(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : ApiResponse<StockAdjustmentListResponse?> {
        val localVariableConfig = listItemStockAdjustmentsRequestConfig(itemID = itemID, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, StockAdjustmentListResponse>(
            localVariableConfig
        )
    }

    fun listItemStockAdjustmentsRequestConfig(itemID: kotlin.String, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items/{itemID}/stock-adjustments".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

     enum class WarrantyStatusListItems(val value: kotlin.String) {
         @SerialName(value = "none") None("none"),
         @SerialName(value = "any") Any("any"),
         @SerialName(value = "active") Active("active"),
         @SerialName(value = "expired") Expired("expired"),
         @SerialName(value = "lifetime") Lifetime("lifetime");

        override fun toString(): kotlin.String = "$value"
     }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun listItems(q: kotlin.String? = null, locationId: kotlin.String? = null, descendants: kotlin.Boolean? = false, labelId: kotlin.collections.List<kotlin.String>? = null, customField: kotlin.collections.List<kotlin.String>? = null, warrantyStatus: WarrantyStatusListItems? = null, createdFrom: kotlin.Long? = null, createdTo: kotlin.Long? = null, updatedFrom: kotlin.Long? = null, updatedTo: kotlin.Long? = null, sort: kotlin.String? = null, limit: kotlin.Int? = 50, offset: kotlin.Int? = 0, xHHOClientVersion: kotlin.String? = null) : ItemListResponse {
        val localVarResponse = listItemsWithHttpInfo(q = q, locationId = locationId, descendants = descendants, labelId = labelId, customField = customField, warrantyStatus = warrantyStatus, createdFrom = createdFrom, createdTo = createdTo, updatedFrom = updatedFrom, updatedTo = updatedTo, sort = sort, limit = limit, offset = offset, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as ItemListResponse
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
    fun listItemsWithHttpInfo(q: kotlin.String?, locationId: kotlin.String?, descendants: kotlin.Boolean?, labelId: kotlin.collections.List<kotlin.String>?, customField: kotlin.collections.List<kotlin.String>?, warrantyStatus: WarrantyStatusListItems?, createdFrom: kotlin.Long?, createdTo: kotlin.Long?, updatedFrom: kotlin.Long?, updatedTo: kotlin.Long?, sort: kotlin.String?, limit: kotlin.Int?, offset: kotlin.Int?, xHHOClientVersion: kotlin.String?) : ApiResponse<ItemListResponse?> {
        val localVariableConfig = listItemsRequestConfig(q = q, locationId = locationId, descendants = descendants, labelId = labelId, customField = customField, warrantyStatus = warrantyStatus, createdFrom = createdFrom, createdTo = createdTo, updatedFrom = updatedFrom, updatedTo = updatedTo, sort = sort, limit = limit, offset = offset, xHHOClientVersion = xHHOClientVersion)

        return request<Unit, ItemListResponse>(
            localVariableConfig
        )
    }

    fun listItemsRequestConfig(q: kotlin.String?, locationId: kotlin.String?, descendants: kotlin.Boolean?, labelId: kotlin.collections.List<kotlin.String>?, customField: kotlin.collections.List<kotlin.String>?, warrantyStatus: WarrantyStatusListItems?, createdFrom: kotlin.Long?, createdTo: kotlin.Long?, updatedFrom: kotlin.Long?, updatedTo: kotlin.Long?, sort: kotlin.String?, limit: kotlin.Int?, offset: kotlin.Int?, xHHOClientVersion: kotlin.String?) : RequestConfig<Unit> {
        val localVariableBody = null
        val localVariableQuery: MultiValueMap = mutableMapOf<kotlin.String, kotlin.collections.List<kotlin.String>>()
            .apply {
                if (q != null) {
                    put("q", listOf(q.toString()))
                }
                if (locationId != null) {
                    put("location_id", listOf(locationId.toString()))
                }
                if (descendants != null) {
                    put("descendants", listOf(descendants.toString()))
                }
                if (labelId != null) {
                    put("label_id", toMultiValue(labelId.toList(), "multi"))
                }
                if (customField != null) {
                    put("custom_field", toMultiValue(customField.toList(), "multi"))
                }
                if (warrantyStatus != null) {
                    put("warranty_status", listOf(warrantyStatus.value))
                }
                if (createdFrom != null) {
                    put("created_from", listOf(createdFrom.toString()))
                }
                if (createdTo != null) {
                    put("created_to", listOf(createdTo.toString()))
                }
                if (updatedFrom != null) {
                    put("updated_from", listOf(updatedFrom.toString()))
                }
                if (updatedTo != null) {
                    put("updated_to", listOf(updatedTo.toString()))
                }
                if (sort != null) {
                    put("sort", listOf(sort.toString()))
                }
                if (limit != null) {
                    put("limit", listOf(limit.toString()))
                }
                if (offset != null) {
                    put("offset", listOf(offset.toString()))
                }
            }
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.GET,
            path = "/items",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateItem(itemID: kotlin.String, itemUpdateRequest: ItemUpdateRequest, xHHOClientVersion: kotlin.String? = null) : Item {
        val localVarResponse = updateItemWithHttpInfo(itemID = itemID, itemUpdateRequest = itemUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as Item
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
    fun updateItemWithHttpInfo(itemID: kotlin.String, itemUpdateRequest: ItemUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<Item?> {
        val localVariableConfig = updateItemRequestConfig(itemID = itemID, itemUpdateRequest = itemUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<ItemUpdateRequest, Item>(
            localVariableConfig
        )
    }

    fun updateItemRequestConfig(itemID: kotlin.String, itemUpdateRequest: ItemUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<ItemUpdateRequest> {
        val localVariableBody = itemUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/items/{itemID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateItemCustomField(itemID: kotlin.String, customFieldID: kotlin.String, itemCustomFieldUpdateRequest: ItemCustomFieldUpdateRequest, xHHOClientVersion: kotlin.String? = null) : ItemCustomField {
        val localVarResponse = updateItemCustomFieldWithHttpInfo(itemID = itemID, customFieldID = customFieldID, itemCustomFieldUpdateRequest = itemCustomFieldUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as ItemCustomField
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
    fun updateItemCustomFieldWithHttpInfo(itemID: kotlin.String, customFieldID: kotlin.String, itemCustomFieldUpdateRequest: ItemCustomFieldUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<ItemCustomField?> {
        val localVariableConfig = updateItemCustomFieldRequestConfig(itemID = itemID, customFieldID = customFieldID, itemCustomFieldUpdateRequest = itemCustomFieldUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<ItemCustomFieldUpdateRequest, ItemCustomField>(
            localVariableConfig
        )
    }

    fun updateItemCustomFieldRequestConfig(itemID: kotlin.String, customFieldID: kotlin.String, itemCustomFieldUpdateRequest: ItemCustomFieldUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<ItemCustomFieldUpdateRequest> {
        val localVariableBody = itemCustomFieldUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/items/{itemID}/custom-fields/{customFieldID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"customFieldID"+"}", encodeURIComponent(customFieldID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateItemIdentification(itemID: kotlin.String, identificationID: kotlin.String, identificationUpdateRequest: IdentificationUpdateRequest, xHHOClientVersion: kotlin.String? = null) : Identification {
        val localVarResponse = updateItemIdentificationWithHttpInfo(itemID = itemID, identificationID = identificationID, identificationUpdateRequest = identificationUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as Identification
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
    fun updateItemIdentificationWithHttpInfo(itemID: kotlin.String, identificationID: kotlin.String, identificationUpdateRequest: IdentificationUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<Identification?> {
        val localVariableConfig = updateItemIdentificationRequestConfig(itemID = itemID, identificationID = identificationID, identificationUpdateRequest = identificationUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<IdentificationUpdateRequest, Identification>(
            localVariableConfig
        )
    }

    fun updateItemIdentificationRequestConfig(itemID: kotlin.String, identificationID: kotlin.String, identificationUpdateRequest: IdentificationUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<IdentificationUpdateRequest> {
        val localVariableBody = identificationUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/items/{itemID}/identifications/{identificationID}".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())).replace("{"+"identificationID"+"}", encodeURIComponent(identificationID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateItemPurchase(itemID: kotlin.String, purchaseUpdateRequest: PurchaseUpdateRequest, xHHOClientVersion: kotlin.String? = null) : PurchaseBlock {
        val localVarResponse = updateItemPurchaseWithHttpInfo(itemID = itemID, purchaseUpdateRequest = purchaseUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as PurchaseBlock
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
    fun updateItemPurchaseWithHttpInfo(itemID: kotlin.String, purchaseUpdateRequest: PurchaseUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<PurchaseBlock?> {
        val localVariableConfig = updateItemPurchaseRequestConfig(itemID = itemID, purchaseUpdateRequest = purchaseUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<PurchaseUpdateRequest, PurchaseBlock>(
            localVariableConfig
        )
    }

    fun updateItemPurchaseRequestConfig(itemID: kotlin.String, purchaseUpdateRequest: PurchaseUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<PurchaseUpdateRequest> {
        val localVariableBody = purchaseUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/items/{itemID}/purchase".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateItemSale(itemID: kotlin.String, saleUpdateRequest: SaleUpdateRequest, xHHOClientVersion: kotlin.String? = null) : SaleBlock {
        val localVarResponse = updateItemSaleWithHttpInfo(itemID = itemID, saleUpdateRequest = saleUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as SaleBlock
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
    fun updateItemSaleWithHttpInfo(itemID: kotlin.String, saleUpdateRequest: SaleUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<SaleBlock?> {
        val localVariableConfig = updateItemSaleRequestConfig(itemID = itemID, saleUpdateRequest = saleUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<SaleUpdateRequest, SaleBlock>(
            localVariableConfig
        )
    }

    fun updateItemSaleRequestConfig(itemID: kotlin.String, saleUpdateRequest: SaleUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<SaleUpdateRequest> {
        val localVariableBody = saleUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/items/{itemID}/sale".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
            body = localVariableBody
        )
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalStateException::class, IOException::class, UnsupportedOperationException::class, ClientException::class, ServerException::class)
    fun updateItemWarranty(itemID: kotlin.String, warrantyUpdateRequest: WarrantyUpdateRequest, xHHOClientVersion: kotlin.String? = null) : WarrantyBlock {
        val localVarResponse = updateItemWarrantyWithHttpInfo(itemID = itemID, warrantyUpdateRequest = warrantyUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return when (localVarResponse.responseType) {
            ResponseType.Success -> (localVarResponse as Success<*>).data as WarrantyBlock
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
    fun updateItemWarrantyWithHttpInfo(itemID: kotlin.String, warrantyUpdateRequest: WarrantyUpdateRequest, xHHOClientVersion: kotlin.String?) : ApiResponse<WarrantyBlock?> {
        val localVariableConfig = updateItemWarrantyRequestConfig(itemID = itemID, warrantyUpdateRequest = warrantyUpdateRequest, xHHOClientVersion = xHHOClientVersion)

        return request<WarrantyUpdateRequest, WarrantyBlock>(
            localVariableConfig
        )
    }

    fun updateItemWarrantyRequestConfig(itemID: kotlin.String, warrantyUpdateRequest: WarrantyUpdateRequest, xHHOClientVersion: kotlin.String?) : RequestConfig<WarrantyUpdateRequest> {
        val localVariableBody = warrantyUpdateRequest
        val localVariableQuery: MultiValueMap = mutableMapOf()
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = "application/json"
        localVariableHeaders["Accept"] = "application/json, application/problem+json"
        xHHOClientVersion?.apply { localVariableHeaders["X-HHO-Client-Version"] = this.toString() }

        return RequestConfig(
            method = RequestMethod.PUT,
            path = "/items/{itemID}/warranty".replace("{"+"itemID"+"}", encodeURIComponent(itemID.toString())),
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
