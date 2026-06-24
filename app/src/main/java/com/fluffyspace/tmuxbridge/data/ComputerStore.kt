package com.fluffyspace.tmuxbridge.data

import android.content.Context
import androidx.core.content.edit
import com.fluffyspace.tmuxbridge.model.Computer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists paired [Computer]s as a JSON array inside app-private
 * SharedPreferences. App-private storage is sandboxed per-app; the tokens are
 * the same secrets the daemon itself stores in cleartext on a trusted network.
 */
class ComputerStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<Computer> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getJSONObject(it).toComputer() }
    }

    fun get(id: String): Computer? = all().firstOrNull { it.id == id }

    /** Adds a new computer or replaces an existing one with the same id. */
    fun upsert(computer: Computer) {
        val updated = all().filterNot { it.id == computer.id } + computer
        save(updated)
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    private fun save(list: List<Computer>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit { putString(KEY, arr.toString()) }
    }

    private fun Computer.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ip", ip)
        put("port", port)
        put("token", token)
    }

    private fun JSONObject.toComputer() = Computer(
        id = getString("id"),
        name = getString("name"),
        ip = getString("ip"),
        port = getInt("port"),
        token = getString("token"),
    )

    private companion object {
        const val PREFS = "tmux_bridge_computers"
        const val KEY = "computers"
    }
}
