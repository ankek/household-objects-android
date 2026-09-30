package dev.hho.android.data.apiclient

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoInstanceConfigured(cause: Throwable) :
        ApiError("No self-hosted instance is configured yet", cause)

    class Unauthorized(cause: Throwable? = null) : ApiError("Not authenticated", cause)

    class NotFound(cause: Throwable? = null) : ApiError("Not found", cause)

    class Conflict(val detail: String?, cause: Throwable? = null) :
        ApiError("Conflict" + (detail?.let { ": $it" } ?: ""), cause)

    open class Validation(val status: Int, val detail: String?, cause: Throwable? = null) :
        ApiError("Client error $status" + (detail?.let { ": $it" } ?: ""), cause)

    class PayloadTooLarge(detail: String?, cause: Throwable? = null) : Validation(413, detail, cause)

    class UpgradeRequired(val minimumVersion: String, val detail: String?, cause: Throwable? = null) :
        ApiError(
            "Client too old — server requires at least $minimumVersion" + (detail?.let { ": $it" } ?: ""),
            cause,
        )

    class Server(val status: Int, val detail: String?, cause: Throwable? = null) :
        ApiError("Server error $status" + (detail?.let { ": $it" } ?: ""), cause)

    class Network(cause: Throwable) : ApiError("Network failure", cause)

    class UnexpectedPayload(val detail: String, cause: Throwable? = null) :
        ApiError("Unexpected response payload: $detail", cause)

    class Unknown(cause: Throwable) : ApiError(cause.message ?: "Unexpected error", cause)
}
