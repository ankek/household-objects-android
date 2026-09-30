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

data class Identification (
    @SerialName(value = "kind")
    val kind: Identification.Kind,

    @SerialName(value = "value")
    val `value`: kotlin.String,

    @SerialName(value = "id")
    val id: kotlin.String? = null,

    @SerialName(value = "item_id")
    val itemId: kotlin.String? = null,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long? = null,

    @SerialName(value = "updated_at")
    val updatedAt: kotlin.Long? = null,

    @SerialName(value = "version")
    val version: kotlin.Long? = null

) {
    @Serializable
    enum class Kind(val value: kotlin.String) {
        @SerialName(value = "serial") Serial("serial"),
        @SerialName(value = "model") Model("model"),
        @SerialName(value = "asset_tag") AssetTag("asset_tag"),
        @SerialName(value = "barcode") Barcode("barcode"),
        @SerialName(value = "other") Other("other");
    }

}

