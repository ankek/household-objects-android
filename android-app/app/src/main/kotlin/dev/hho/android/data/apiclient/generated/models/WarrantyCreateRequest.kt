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

data class WarrantyCreateRequest (

    @SerialName(value = "holder")
    val holder: kotlin.String? = null,

    @SerialName(value = "provider")
    val provider: kotlin.String? = null,

    @Contextual @SerialName(value = "starts_on")
    val startsOn: java.time.LocalDate? = null,

    @Contextual @SerialName(value = "expires_on")
    val expiresOn: java.time.LocalDate? = null,

    @SerialName(value = "is_lifetime")
    val isLifetime: kotlin.Boolean? = null,

    @SerialName(value = "notes")
    val notes: kotlin.String? = null

) {

}

