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

data class Item (

    @SerialName(value = "name")
    val name: kotlin.String,

    @SerialName(value = "id")
    val id: kotlin.String? = null,

    @SerialName(value = "description")
    val description: kotlin.String? = null,

    @SerialName(value = "location_id")
    val locationId: kotlin.String? = null,

    @SerialName(value = "quantity")
    val quantity: kotlin.Long? = null,

    @SerialName(value = "short_code")
    val shortCode: kotlin.String? = null,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long? = null,

    @SerialName(value = "updated_at")
    val updatedAt: kotlin.Long? = null,

    @SerialName(value = "version")
    val version: kotlin.Long? = null

) {

}

