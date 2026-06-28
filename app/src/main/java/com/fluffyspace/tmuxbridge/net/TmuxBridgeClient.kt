package com.fluffyspace.tmuxbridge.net

import android.net.Uri
import com.fluffyspace.tmuxbridge.model.Computer
import com.fluffyspace.tmuxbridge.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Result of `POST /pair`: the request id to poll and the code to show the user. */
data class PairRequest(val requestId: String, val pairingCode: String)

/** Result of polling `GET /pair/<id>`. */
sealed interface PairResult {
    object Pending : PairResult
    object Denied : PairResult
    data class Approved(val token: String) : PairResult
}

/**
 * Thin HTTP client for the tmux-bridge daemon. Pure stdlib networking
 * ([HttpURLConnection]) so the app pulls in no HTTP third-party dependency.
 * All calls are suspend functions that run on [Dispatchers.IO].
 */
object TmuxBridgeClient {

    private const val TIMEOUT_MS = 8_000

    // ---- pairing (no auth) ----

    suspend fun pair(baseUrl: String, deviceName: String): PairRequest =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("device_name", deviceName)
            val (code, json) = request("$baseUrl/pair", "POST", body = body)
            if (code != 202 || json == null) throw errorFrom(code, json, "Pairing failed")
            PairRequest(
                requestId = json.getString("request_id"),
                pairingCode = json.getString("pairing_code"),
            )
        }

    suspend fun pollPair(baseUrl: String, requestId: String): PairResult =
        withContext(Dispatchers.IO) {
            val (code, json) =
                request("$baseUrl/pair/${Uri.encode(requestId)}", "GET")
            if (code != 200 || json == null) throw errorFrom(code, json, "Pairing failed")
            when (json.optString("status")) {
                "approved" -> PairResult.Approved(json.getString("token"))
                "denied" -> PairResult.Denied
                else -> PairResult.Pending
            }
        }

    // ---- sessions (Bearer auth) ----

    suspend fun listSessions(computer: Computer): List<Session> =
        withContext(Dispatchers.IO) {
            val (code, json) =
                request("${computer.baseUrl}/sessions", "GET", token = computer.token)
            if (code != 200 || json == null) throw errorFrom(code, json, "Could not list sessions")
            val arr = json.getJSONArray("sessions")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Session(
                    name = o.getString("name"),
                    attached = o.optBoolean("attached", false),
                    created = o.optLong("created", 0L),
                    windows = o.optInt("windows", 0),
                )
            }
        }

    /**
     * Creates a session; returns the full server-side name (host-prefixed).
     *
     * [name], when null/blank, is omitted and the daemon auto-generates one.
     * [path], when non-blank, sets the session's starting directory.
     */
    suspend fun createSession(computer: Computer, name: String? = null, path: String? = null): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
            if (!name.isNullOrBlank()) body.put("name", name)
            if (!path.isNullOrBlank()) body.put("path", path)
            val (code, json) = request(
                "${computer.baseUrl}/sessions", "POST",
                token = computer.token, body = body,
            )
            if (code != 201 || json == null) throw errorFrom(code, json, "Could not create session")
            json.getString("name")
        }

    /** Returns the server's recently-used path list, newest first. Empty on any error. */
    suspend fun fetchPathHistory(computer: Computer): List<String> =
        withContext(Dispatchers.IO) {
            val (code, json) =
                request("${computer.baseUrl}/session-paths", "GET", token = computer.token)
            if (code != 200 || json == null) return@withContext emptyList()
            val arr = json.getJSONArray("paths")
            (0 until arr.length()).map { arr.getString(it) }
        }

    /** Returns true if [path] exists and is a directory on the server. */
    suspend fun checkPath(computer: Computer, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val encoded = Uri.encode(path)
            val (code, json) =
                request("${computer.baseUrl}/check-path?path=$encoded", "GET", token = computer.token)
            if (code != 200 || json == null) return@withContext false
            json.optBoolean("exists", false) && json.optBoolean("is_dir", false)
        }

    suspend fun killSession(computer: Computer, name: String) =
        withContext(Dispatchers.IO) {
            val (code, json) = request(
                "${computer.baseUrl}/sessions/${Uri.encode(name)}", "DELETE",
                token = computer.token,
            )
            if (code != 200) throw errorFrom(code, json, "Could not kill session")
        }

    /** Asks the daemon to forget this device's token. Best-effort. */
    suspend fun unpairSelf(computer: Computer) =
        withContext(Dispatchers.IO) {
            request("${computer.baseUrl}/devices/self", "DELETE", token = computer.token)
            Unit
        }

    // ---- low-level helpers ----

    private fun request(
        url: String,
        method: String,
        token: String? = null,
        body: JSONObject? = null,
    ): Pair<Int, JSONObject?> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = text.takeIf { it.isNotBlank() }?.let {
                runCatching { JSONObject(it) }.getOrNull()
            }
            return status to json
        } catch (e: IOException) {
            throw ApiException(0, "Network error: ${e.message ?: "could not reach server"}")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorFrom(status: Int, json: JSONObject?, fallback: String): ApiException {
        val msg = json?.optString("error")?.takeIf { it.isNotBlank() }
            ?: when (status) {
                401 -> "Unauthorized — the saved token was rejected"
                404 -> "Not found"
                else -> "$fallback (HTTP $status)"
            }
        return ApiException(status, msg)
    }
}
