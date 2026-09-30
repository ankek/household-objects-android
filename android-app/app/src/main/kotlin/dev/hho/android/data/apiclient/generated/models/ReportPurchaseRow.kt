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

data class ReportPurchaseRow (

    @SerialName(value = "item_id")
    val itemId: kotlin.String,

    @SerialName(value = "item_name")
    val itemName: kotlin.String,

    @SerialName(value = "purchased_on")
    val purchasedOn: kotlin.String,

    @SerialName(value = "vendor")
    val vendor: kotlin.String,

    @SerialName(value = "purchase_price_minor")
    val purchasePriceMinor: kotlin.Long

) {

}

