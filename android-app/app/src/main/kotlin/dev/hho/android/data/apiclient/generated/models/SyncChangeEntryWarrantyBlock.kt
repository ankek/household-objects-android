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

import dev.hho.android.data.apiclient.generated.models.WarrantyBlock

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class SyncChangeEntryWarrantyBlock (

    @SerialName(value = "entity_type")
    val entityType: SyncChangeEntryWarrantyBlock.EntityType,

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "group_change_seq")
    val groupChangeSeq: kotlin.Long,

    @SerialName(value = "data")
    val `data`: WarrantyBlock

) {
    @Serializable
    enum class EntityType(val value: kotlin.String) {
        @SerialName(value = "warranty_block") WarrantyBlock("warranty_block");
    }

}

