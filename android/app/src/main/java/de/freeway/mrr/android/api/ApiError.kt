package de.freeway.mrr.android.api

/**
 * Typed API failures mapped from MRR HTTP errors. A 401 always clears the
 * stored session token so the UI can route back to the login screen.
 */
sealed class MrrApiException(
    message: String,
    cause: Throwable? = null,
    val code: Int? = null,
) : Exception(message, cause) {

    class Unauthorized(message: String) : MrrApiException(message, null, 401)

    class NotFound(message: String) : MrrApiException(message, null, 404)

    class Conflict(message: String) : MrrApiException(message, null, 409)

    class Unprocessable(message: String) : MrrApiException(message, null, 422)

    class Server(val status: Int, message: String) : MrrApiException(message, null, status)

    class Network(message: String, cause: Throwable) : MrrApiException(message, cause, null)
}
