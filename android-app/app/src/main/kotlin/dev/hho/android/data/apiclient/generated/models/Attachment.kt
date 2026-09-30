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

data class Attachment (

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "item_id")
    val itemId: kotlin.String,

    @SerialName(value = "category")
    val category: Attachment.Category,

    @SerialName(value = "original_filename")
    val originalFilename: kotlin.String,

    @SerialName(value = "content_type")
    val contentType: kotlin.String,

    @SerialName(value = "size_bytes")
    val sizeBytes: kotlin.Long,

    @SerialName(value = "sha256")
    val sha256: kotlin.String,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long,

    @SerialName(value = "updated_at")
    val updatedAt: kotlin.Long,

    @SerialName(value = "version")
    val version: kotlin.Long,

    @SerialName(value = "has_thumbnail")
    val hasThumbnail: kotlin.Boolean

) {
    @Serializable
    enum class Category(val value: kotlin.String) {
        @SerialName(value = "image") Image("image"),
        @SerialName(value = "manual") Manual("manual"),
        @SerialName(value = "warranty") Warranty("warranty"),
        @SerialName(value = "receipt") Receipt("receipt"),
        @SerialName(value = "general") General("general");
    }

}

