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

import dev.hho.android.data.apiclient.generated.models.SyncAppliedEntry
import dev.hho.android.data.apiclient.generated.models.SyncConflictEntry
import dev.hho.android.data.apiclient.generated.models.SyncSkippedEntry

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class SyncPushResponse (

    @SerialName(value = "applied")
    val applied: kotlin.collections.List<SyncAppliedEntry>,

    @SerialName(value = "skipped")
    val skipped: kotlin.collections.List<SyncSkippedEntry>,

    @SerialName(value = "conflicts")
    val conflicts: kotlin.collections.List<SyncConflictEntry>,

    @SerialName(value = "new_watermark")
    val newWatermark: kotlin.Long

) {

}

