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

import dev.hho.android.data.apiclient.generated.models.SaleBlock

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class SyncChangeEntrySoldToBlock (

    @SerialName(value = "entity_type")
    val entityType: SyncChangeEntrySoldToBlock.EntityType,

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "group_change_seq")
    val groupChangeSeq: kotlin.Long,

    @SerialName(value = "data")
    val `data`: SaleBlock

) {
    @Serializable
    enum class EntityType(val value: kotlin.String) {
        @SerialName(value = "sold_to_block") SoldToBlock("sold_to_block");
    }

}

