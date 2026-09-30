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

data class ItemCustomFieldCreateRequest (
    @SerialName(value = "field_def_id")
    val fieldDefId: kotlin.String,

    @SerialName(value = "name")
    val name: kotlin.String,

    @SerialName(value = "field_type")
    val fieldType: ItemCustomFieldCreateRequest.FieldType,

    @SerialName(value = "text_value")
    val textValue: kotlin.String?,

    @Contextual @SerialName(value = "number_value")
    val numberValue: java.math.BigDecimal?,

    @SerialName(value = "bool_value")
    val boolValue: kotlin.Boolean?,

    @Contextual @SerialName(value = "date_value")
    val dateValue: java.time.LocalDate?

) {
    @Serializable
    enum class FieldType(val value: kotlin.String) {
        @SerialName(value = "text") Text("text"),
        @SerialName(value = "number") Number("number"),
        @SerialName(value = "boolean") Boolean("boolean"),
        @SerialName(value = "date") Date("date");
    }

}

