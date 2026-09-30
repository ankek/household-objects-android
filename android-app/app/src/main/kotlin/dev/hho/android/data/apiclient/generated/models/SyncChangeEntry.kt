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

import dev.hho.android.data.apiclient.generated.models.Attachment
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryAttachment
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryItem
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryItemCustomField
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryItemIdentification
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryItemLabel
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryLabel
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryLocation
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryPurchasedFromBlock
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntrySoldToBlock
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryStockAdjustment
import dev.hho.android.data.apiclient.generated.models.SyncChangeEntryWarrantyBlock

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator(discriminator = "entity_type")
sealed class SyncChangeEntry {

    @SerialName(value = "entity_type")
    abstract val entityType: SyncChangeEntry.EntityType
    @SerialName(value = "id")
    abstract val id: kotlin.String
    @SerialName(value = "group_change_seq")
    abstract val groupChangeSeq: kotlin.Long
    @SerialName(value = "data")
    abstract val `data`: Attachment
    @Serializable
    enum class EntityType(val value: kotlin.String) {
        @SerialName(value = "attachment") Attachment("attachment");
    }

}

