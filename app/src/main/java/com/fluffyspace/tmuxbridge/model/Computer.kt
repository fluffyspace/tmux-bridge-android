package com.fluffyspace.tmuxbridge.model

/**
 * A paired tmux-bridge server. Persisted locally (including its bearer [token],
 * which authenticates every session call). [id] is a stable local identifier.
 */
data class Computer(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int,
    val token: String,
) {
    /** Base URL for the daemon, e.g. "http://10.0.0.2:7842". */
    val baseUrl: String get() = "http://$ip:$port"
}
