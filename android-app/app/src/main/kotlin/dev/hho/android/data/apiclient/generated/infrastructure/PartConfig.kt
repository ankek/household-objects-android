package dev.hho.android.data.apiclient.generated.infrastructure

data class PartConfig<T>(
    val headers: MutableMap<String, String> = mutableMapOf(),
    val body: T? = null,
    val serializer: ((Any?) -> String)? = null
)
