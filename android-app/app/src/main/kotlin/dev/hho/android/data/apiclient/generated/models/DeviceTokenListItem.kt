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

data class DeviceTokenListItem (

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "device_label")
    val deviceLabel: kotlin.String,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long,

    @SerialName(value = "revoked")
    val revoked: kotlin.Boolean,

    @SerialName(value = "revoked_at")
    val revokedAt: kotlin.Long? = null

) {

}

