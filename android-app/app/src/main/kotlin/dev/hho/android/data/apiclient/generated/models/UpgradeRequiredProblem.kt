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

data class UpgradeRequiredProblem (
    @Contextual @SerialName(value = "type")
    val type: java.net.URI,

    @SerialName(value = "title")
    val title: kotlin.String,

    @SerialName(value = "status")
    val status: kotlin.Int,

    @SerialName(value = "minimum_version")
    val minimumVersion: kotlin.String,

    @SerialName(value = "detail")
    val detail: kotlin.String? = null,

    @SerialName(value = "request_id")
    val requestId: kotlin.String? = null

) {

}

