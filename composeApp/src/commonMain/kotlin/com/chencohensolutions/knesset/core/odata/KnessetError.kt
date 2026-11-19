package com.chencohensolutions.knesset.core.odata

/** Everything that can go wrong when talking to the Knesset API, in the terms the UI needs. */
sealed class KnessetError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** HTTP 200 that is not JSON: the server's "not accessible from your location" page, or maintenance. */
    class BlockedOrUnavailable : KnessetError("The Knesset server returned a page instead of data")
    class RateLimited : KnessetError("Too many requests")
    class Offline(cause: Throwable? = null) : KnessetError("No connection", cause)
    class NotFound : KnessetError("Not found")
    class Server(val code: Int) : KnessetError("Server error $code")
    class Parse(cause: Throwable) : KnessetError("Unexpected data", cause)
}
