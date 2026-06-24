package com.fluffyspace.tmuxbridge.model

/**
 * A tmux session as reported by `GET /sessions`. [name] is already prefixed
 * with the server's short hostname by the daemon. [created] is a Unix epoch.
 */
data class Session(
    val name: String,
    val attached: Boolean,
    val created: Long,
    val windows: Int,
)
