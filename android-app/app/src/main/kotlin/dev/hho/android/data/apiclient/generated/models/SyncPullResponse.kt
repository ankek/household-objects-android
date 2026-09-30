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

import dev.hho.android.data.apiclient.generated.models.SyncChangeEntry
import dev.hho.android.data.apiclient.generated.models.SyncCursorTooOld
import dev.hho.android.data.apiclient.generated.models.SyncPullResult
import dev.hho.android.data.apiclient.generated.models.SyncTombstoneEntry

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class SyncPullResponse (
    @SerialName(value = "changes")
    val changes: kotlin.collections.List<SyncChangeEntry>,

    @SerialName(value = "tombstones")
    val tombstones: kotlin.collections.List<SyncTombstoneEntry>,

    @SerialName(value = "next_watermark")
    val nextWatermark: kotlin.Long,

    @SerialName(value = "has_more")
    val hasMore: kotlin.Boolean,

    @SerialName(value = "cursor_too_old")
    val cursorTooOld: SyncPullResponse.CursorTooOld

) {
    @Serializable
    enum class CursorTooOld(val value: kotlin.Boolean) {
        @SerialName(value = "true") True(true);
    }

}

