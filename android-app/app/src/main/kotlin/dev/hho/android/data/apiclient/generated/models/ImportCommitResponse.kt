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

import dev.hho.android.data.apiclient.generated.models.ImportCommitCreatedItem
import dev.hho.android.data.apiclient.generated.models.ImportCommitSummary

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class ImportCommitResponse (

    @SerialName(value = "import_id")
    val importId: kotlin.String,

    @SerialName(value = "summary")
    val summary: ImportCommitSummary,

    @SerialName(value = "created_items")
    val createdItems: kotlin.collections.List<ImportCommitCreatedItem>

) {

}

