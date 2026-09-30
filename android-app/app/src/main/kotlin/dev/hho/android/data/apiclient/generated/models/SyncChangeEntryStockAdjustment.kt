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

import dev.hho.android.data.apiclient.generated.models.StockAdjustment

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class SyncChangeEntryStockAdjustment (

    @SerialName(value = "entity_type")
    val entityType: SyncChangeEntryStockAdjustment.EntityType,

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "group_change_seq")
    val groupChangeSeq: kotlin.Long,

    @SerialName(value = "data")
    val `data`: StockAdjustment

) {
    @Serializable
    enum class EntityType(val value: kotlin.String) {
        @SerialName(value = "stock_adjustment") StockAdjustment("stock_adjustment");
    }

}

