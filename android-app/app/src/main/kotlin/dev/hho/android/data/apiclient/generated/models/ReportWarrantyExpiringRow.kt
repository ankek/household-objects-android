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

data class ReportWarrantyExpiringRow (

    @SerialName(value = "item_id")
    val itemId: kotlin.String,

    @SerialName(value = "item_name")
    val itemName: kotlin.String,

    @SerialName(value = "expires_on")
    val expiresOn: kotlin.String,

    @SerialName(value = "days_remaining")
    val daysRemaining: kotlin.Int

) {

}

