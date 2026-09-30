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

data class SessionListItem (

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "user_agent")
    val userAgent: kotlin.String,

    @SerialName(value = "created_from_ip")
    val createdFromIp: kotlin.String,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long,

    @SerialName(value = "expires_at")
    val expiresAt: kotlin.Long,

    @SerialName(value = "revoked")
    val revoked: kotlin.Boolean,

    @SerialName(value = "revoked_at")
    val revokedAt: kotlin.Long? = null

) {

}

