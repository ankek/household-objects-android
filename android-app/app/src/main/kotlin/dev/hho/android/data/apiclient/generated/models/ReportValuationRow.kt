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

data class ReportValuationRow (
    @SerialName(value = "group_key")
    val groupKey: kotlin.String,

    @SerialName(value = "group_label")
    val groupLabel: kotlin.String,

    @SerialName(value = "item_count")
    val itemCount: kotlin.Long,

    @SerialName(value = "total_value_minor")
    val totalValueMinor: kotlin.Long

) {

}

