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

import dev.hho.android.data.apiclient.generated.models.PurchaseBlock

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class SyncChangeEntryPurchasedFromBlock (

    @SerialName(value = "entity_type")
    val entityType: SyncChangeEntryPurchasedFromBlock.EntityType,

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "group_change_seq")
    val groupChangeSeq: kotlin.Long,

    @SerialName(value = "data")
    val `data`: PurchaseBlock

) {
    @Serializable
    enum class EntityType(val value: kotlin.String) {
        @SerialName(value = "purchased_from_block") PurchasedFromBlock("purchased_from_block");
    }

}

