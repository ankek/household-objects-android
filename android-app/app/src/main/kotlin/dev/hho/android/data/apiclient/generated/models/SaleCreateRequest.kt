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

data class SaleCreateRequest (

    @SerialName(value = "buyer_name")
    val buyerName: kotlin.String? = null,

    @Contextual @SerialName(value = "sold_on")
    val soldOn: java.time.LocalDate? = null,

    @SerialName(value = "sale_price_minor")
    val salePriceMinor: kotlin.Long? = null,

    @SerialName(value = "notes")
    val notes: kotlin.String? = null

) {

}

