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

data class StatusResponse (

    @SerialName(value = "status")
    val status: StatusResponse.Status,

    @SerialName(value = "version")
    val version: kotlin.String,

    @SerialName(value = "schema_version")
    val schemaVersion: kotlin.Long

) {
    @Serializable
    enum class Status(val value: kotlin.String) {
        @SerialName(value = "ok") Ok("ok");
    }

}

