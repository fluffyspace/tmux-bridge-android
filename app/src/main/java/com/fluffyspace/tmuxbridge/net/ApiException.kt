package com.fluffyspace.tmuxbridge.net

/**
 * Thrown when the daemon returns a non-success status. [status] is the HTTP
 * code (0 for transport/connection failures); [message] is the daemon's
 * `error` field when present, otherwise a human-readable fallback.
 */
class ApiException(val status: Int, message: String) : Exception(message)
