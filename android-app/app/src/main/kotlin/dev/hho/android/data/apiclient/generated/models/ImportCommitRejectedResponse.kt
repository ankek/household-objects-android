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

import dev.hho.android.data.apiclient.generated.models.ImportPreviewRow
import dev.hho.android.data.apiclient.generated.models.ImportPreviewSummary

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Contextual

@Serializable

data class ImportCommitRejectedResponse (

    @SerialName(value = "import_id")
    val importId: kotlin.String,

    @SerialName(value = "rows")
    val rows: kotlin.collections.List<ImportPreviewRow>,

    @SerialName(value = "summary")
    val summary: ImportPreviewSummary

) {

}

