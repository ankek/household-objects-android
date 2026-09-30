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

data class GroupMember (

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "username")
    val username: kotlin.String,

    @SerialName(value = "role")
    val role: kotlin.String,

    @SerialName(value = "joined_at")
    val joinedAt: kotlin.Long

) {

}

