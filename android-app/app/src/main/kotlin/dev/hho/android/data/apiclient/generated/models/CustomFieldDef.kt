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

data class CustomFieldDef (

    @SerialName(value = "name")
    val name: kotlin.String,

    @SerialName(value = "field_type")
    val fieldType: CustomFieldDef.FieldType,

    @SerialName(value = "display_order")
    val displayOrder: kotlin.Long,

    @SerialName(value = "id")
    val id: kotlin.String? = null,

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

