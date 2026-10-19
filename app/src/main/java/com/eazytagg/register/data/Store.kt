package com.eazytagg.register.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small on-device storage: settings, the offline queue and the activity log. */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("eazytagg_register", Context.MODE_PRIVATE)
    private val lock = Any()

    var baseUrl: String
        get() = prefs.getString("base", "") ?: ""
        set(v) = prefs.edit().putString("base", v).apply()

    var agentName: String
        get() = prefs.getString("agent", "") ?: ""
        set(v) = prefs.edit().putString("agent", v).apply()

    var agentEmail: String
        get() = prefs.getString("email", "") ?: ""
        set(v) = prefs.edit().putString("email", v).apply()

    var signedIn: Boolean
        get() = prefs.getBoolean("signedIn", false)
        set(v) = prefs.edit().putBoolean("signedIn", v).apply()

    var eventJson: String
        get() = prefs.getString("event", "") ?: ""
        set(v) = prefs.edit().putString("event", v).apply()

    /** dark (default) | light | system */
    var theme: String
        get() = prefs.getString("theme", "dark") ?: "dark"
        set(v) = prefs.edit().putString("theme", v).apply()

    private fun file(name: String) = File(ctx.filesDir, name)

    private fun readArray(name: String): JSONArray = synchronized(lock) {
        try {
            val f = file(name)
            if (f.exists()) JSONArray(f.readText()) else JSONArray()
        } catch (e: Exception) { JSONArray() }
    }

    private fun writeArray(name: String, arr: JSONArray) = synchronized(lock) {
        file(name).writeText(arr.toString())
    }

    fun loadQueue(): List<QueuedOp> {
        val a = readArray("queue.json")
        return (0 until a.length()).map { QueuedOp.fromJson(a.getJSONObject(it)) }
    }

    fun saveQueue(items: List<QueuedOp>) {
        val a = JSONArray(); items.forEach { a.put(it.toJson()) }
        writeArray("queue.json", a)
    }

    fun loadActivity(): List<ActivityItem> {
        val a = readArray("activity.json")
        return (0 until a.length()).map { ActivityItem.fromJson(a.getJSONObject(it)) }
    }

    fun saveActivity(items: List<ActivityItem>) {
        val a = JSONArray(); items.takeLast(500).forEach { a.put(it.toJson()) }
        writeArray("activity.json", a)
    }
}
