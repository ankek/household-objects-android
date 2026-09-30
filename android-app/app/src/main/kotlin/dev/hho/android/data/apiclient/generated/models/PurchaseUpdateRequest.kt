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

package dev.hho.android.data.apiclient.generated.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class PurchaseUpdateRequest (

    @SerialName(value = "version")
    val version: kotlin.Long,

    @SerialName(value = "vendor")
    val vendor: kotlin.String? = null,

    @Contextual @SerialName(value = "purchased_on")
    val purchasedOn: java.time.LocalDate? = null,

    @SerialName(value = "purchase_price_minor")
    val purchasePriceMinor: kotlin.Long? = null,

    @SerialName(value = "order_reference")
    val orderReference: kotlin.String? = null,

    @SerialName(value = "notes")
    val notes: kotlin.String? = null

) {

}

