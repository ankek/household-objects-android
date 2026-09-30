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

import dev.hho.android.data.apiclient.generated.models.Location

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class SyncChangeEntryLocation (

    @SerialName(value = "entity_type")
    val entityType: SyncChangeEntryLocation.EntityType,

    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "group_change_seq")
    val groupChangeSeq: kotlin.Long,

    @SerialName(value = "data")
    val `data`: Location

) {
    @Serializable
    enum class EntityType(val value: kotlin.String) {
        @SerialName(value = "location") Location("location");
    }

}

