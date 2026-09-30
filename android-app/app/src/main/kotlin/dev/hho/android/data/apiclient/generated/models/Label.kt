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

data class Label (

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "name")
    val name: kotlin.String,

    @SerialName(value = "color")
    val color: kotlin.String,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long,

    @SerialName(value = "updated_at")
    val updatedAt: kotlin.Long,

    @SerialName(value = "version")
    val version: kotlin.Long

) {

}

