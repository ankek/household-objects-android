package dev.hho.android.data.apiclient.generated.infrastructure

import okhttp3.Response

val Response.isInformational : Boolean get() = this.code in 100..199

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
val Response.isRedirect : Boolean get() = this.code in 300..399

val Response.isClientError : Boolean get() = this.code in 400..499

val Response.isServerError : Boolean get() = this.code in 500..999
