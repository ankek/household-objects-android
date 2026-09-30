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

data class GroupVisibilityUpdateRequest (

    @SerialName(value = "warranty_visible")
    val warrantyVisible: kotlin.Boolean,

    @SerialName(value = "sale_visible")
    val saleVisible: kotlin.Boolean,

    @SerialName(value = "purchase_visible")
    val purchaseVisible: kotlin.Boolean,

    @SerialName(value = "version")
    val version: kotlin.Long

) {

}

