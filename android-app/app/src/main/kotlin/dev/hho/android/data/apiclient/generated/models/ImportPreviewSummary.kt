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

data class ImportPreviewSummary (

    @SerialName(value = "create")
    val create: kotlin.Int,

    @SerialName(value = "update")
    val update: kotlin.Int,

    @SerialName(value = "unchanged")
    val unchanged: kotlin.Int,

    @SerialName(value = "error")
    val error: kotlin.Int

) {

}

