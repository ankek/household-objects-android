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

data class ItemCustomField (

    @SerialName(value = "name")
    val name: kotlin.String,

    @SerialName(value = "field_type")
    val fieldType: ItemCustomField.FieldType,

    @SerialName(value = "id")
    val id: kotlin.String? = null,

    @SerialName(value = "item_id")
    val itemId: kotlin.String? = null,

    @SerialName(value = "field_def_id")
    val fieldDefId: kotlin.String? = null,

    @SerialName(value = "text_value")
    val textValue: kotlin.String? = null,

    @Contextual @SerialName(value = "number_value")
    val numberValue: java.math.BigDecimal? = null,

    @SerialName(value = "bool_value")
    val boolValue: kotlin.Boolean? = null,

    @Contextual @SerialName(value = "date_value")
    val dateValue: java.time.LocalDate? = null,

    @SerialName(value = "created_at")
    val createdAt: kotlin.Long? = null,

    @SerialName(value = "updated_at")
    val updatedAt: kotlin.Long? = null,

    @SerialName(value = "version")
    val version: kotlin.Long? = null

) {
    @Serializable
    enum class FieldType(val value: kotlin.String) {
        @SerialName(value = "text") Text("text"),
        @SerialName(value = "number") Number("number"),
        @SerialName(value = "boolean") Boolean("boolean"),
        @SerialName(value = "date") Date("date");
    }

}

