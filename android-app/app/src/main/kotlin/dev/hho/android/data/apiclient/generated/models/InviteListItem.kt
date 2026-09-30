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

data class InviteListItem (

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "created_by_user_id")
    val createdByUserId: kotlin.String,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long,

    @SerialName(value = "expires_at")
    val expiresAt: kotlin.Long,

    @SerialName(value = "redeemed")
    val redeemed: kotlin.Boolean,

    @SerialName(value = "redeemed_at")
    val redeemedAt: kotlin.Long? = null,

    @SerialName(value = "redeemed_by_user_id")
    val redeemedByUserId: kotlin.String? = null

) {

}

