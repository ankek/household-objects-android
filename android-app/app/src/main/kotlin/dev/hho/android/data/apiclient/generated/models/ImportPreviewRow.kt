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

import dev.hho.android.data.apiclient.generated.models.ImportPreviewRowError

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class ImportPreviewRow (
    @SerialName(value = "line")
    val line: kotlin.Int,

    @SerialName(value = "action")
    val action: ImportPreviewRow.Action,

    @SerialName(value = "item_id")
    val itemId: kotlin.String? = null,

    @SerialName(value = "name")
    val name: kotlin.String? = null,

    @SerialName(value = "changes")
    val changes: kotlin.collections.List<ImportPreviewRow.Changes>? = null,

    @SerialName(value = "errors")
    val errors: kotlin.collections.List<ImportPreviewRowError>? = null

) {
    @Serializable
    enum class Action(val value: kotlin.String) {
        @SerialName(value = "create") Create("create"),
        @SerialName(value = "update") Update("update"),
        @SerialName(value = "unchanged") Unchanged("unchanged"),
        @SerialName(value = "error") Error("error");
    }
    @Serializable
    enum class Changes(val value: kotlin.String) {
        @SerialName(value = "name") Name("name"),
        @SerialName(value = "description") Description("description"),
        @SerialName(value = "quantity") Quantity("quantity"),
        @SerialName(value = "location_id") LocationId("location_id"),
        @SerialName(value = "labels") Labels("labels"),
        @SerialName(value = "identifications") Identifications("identifications"),
        @SerialName(value = "warranty") Warranty("warranty"),
        @SerialName(value = "purchase") Purchase("purchase"),
        @SerialName(value = "sale") Sale("sale"),
        @SerialName(value = "custom_fields") CustomFields("custom_fields");
    }

}

