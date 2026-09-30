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

data class StockAdjustment (
    @SerialName(value = "delta")
    val delta: kotlin.Long,

    @SerialName(value = "resulting_quantity")
    val resultingQuantity: kotlin.Long,

    @SerialName(value = "id")
    val id: kotlin.String? = null,

    @SerialName(value = "item_id")
    val itemId: kotlin.String? = null,

    @SerialName(value = "reason")
    val reason: kotlin.String? = null,

    @SerialName(value = "note")
    val note: kotlin.String? = null,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long? = null,

    @SerialName(value = "updated_at")
    val updatedAt: kotlin.Long? = null,

    @SerialName(value = "version")
    val version: kotlin.Long? = null

) {

}

