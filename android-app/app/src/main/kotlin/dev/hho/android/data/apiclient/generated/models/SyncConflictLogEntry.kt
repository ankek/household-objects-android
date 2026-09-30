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

data class SyncConflictLogEntry (
    @SerialName(value = "id")
    val id: kotlin.String,

    @SerialName(value = "mutation_id")
    val mutationId: kotlin.String?,

    @SerialName(value = "entity_type")
    val entityType: kotlin.String,

    @SerialName(value = "entity_id")
    val entityId: kotlin.String,

    @SerialName(value = "field_name")
    val fieldName: kotlin.String,

    @SerialName(value = "server_value")
    val serverValue: kotlin.String?,

    @SerialName(value = "losing_client_value")
    val losingClientValue: kotlin.String?,

    @SerialName(value = "detected_at")
    val detectedAt: kotlin.Long

) {

}

