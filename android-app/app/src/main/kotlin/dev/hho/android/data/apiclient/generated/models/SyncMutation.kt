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

data class SyncMutation (
    @SerialName(value = "mutation_id")
    val mutationId: kotlin.String,

    @SerialName(value = "entity_type")
    val entityType: kotlin.String,

    @SerialName(value = "entity_id")
    val entityId: kotlin.String,

    @SerialName(value = "base_version")
    val baseVersion: kotlin.Long,

    @Contextual @SerialName(value = "fields")
    val fields: kotlin.Any,

    @SerialName(value = "op")
    val op: SyncMutation.Op? = null

) {
    @Serializable
    enum class Op(val value: kotlin.String) {
        @SerialName(value = "upsert") Upsert("upsert"),
        @SerialName(value = "delete") Delete("delete");
    }

}

