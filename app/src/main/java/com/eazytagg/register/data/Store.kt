package com.eazytagg.register.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File

/** Small on-device storage: staff settings, the cached event and the offline queue. */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("eazytagg_register", Context.MODE_PRIVATE)
    private val lock = Any()

    var baseUrl: String
        get() = prefs.getString("base", "") ?: ""
        set(v) = prefs.edit().putString("base", v).apply()

    var eventCode: String
        get() = prefs.getString("code", "") ?: ""
        set(v) = prefs.edit().putString("code", v).apply()

    var eventJson: String
        get() = prefs.getString("event", "") ?: ""
        set(v) = prefs.edit().putString("event", v).apply()

    var staffPin: String
        get() = prefs.getString("staffPin", "") ?: ""
        set(v) = prefs.edit().putString("staffPin", v).apply()

    var lastRefreshed: Long
        get() = prefs.getLong("refreshed", 0L)
        set(v) = prefs.edit().putLong("refreshed", v).apply()

    /** Ask "Did you preregister?" before the form. */
    var askPrereg: Boolean
        get() = prefs.getBoolean("askPrereg", true)
        set(v) = prefs.edit().putBoolean("askPrereg", v).apply()

    var testMode: Boolean
        get() = prefs.getBoolean("test", false)
        set(v) = prefs.edit().putBoolean("test", v).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark", true)
        set(v) = prefs.edit().putBoolean("dark", v).apply()

    /** Seconds the "registered" screen shows before the form clears for the next person. */
    var resetSeconds: Int
        get() = prefs.getInt("reset", 12)
        set(v) = prefs.edit().putInt("reset", v).apply()

    private fun file(name: String) = File(ctx.filesDir, name)

    private fun writeAtomic(name: String, data: String) {
        val target = AtomicFile(file(name))
        val out = target.startWrite()
        try { out.write(data.toByteArray(Charsets.UTF_8)); target.finishWrite(out) }
        catch (error: Exception) { target.failWrite(out); throw error }
    }

    private fun readArray(name: String): JSONArray = synchronized(lock) {
        try {
            val f = file(name)
            if (f.exists() || File(f.path + ".bak").exists()) JSONArray(AtomicFile(f).openRead().bufferedReader().use { it.readText() }) else JSONArray()
        } catch (e: Exception) { JSONArray() }
    }

    fun loadQueue(): List<QueuedOp> {
        val a = readArray("queue.json")
        return (0 until a.length()).map { QueuedOp.fromJson(a.getJSONObject(it)) }
    }

    fun saveQueue(items: List<QueuedOp>) = synchronized(lock) {
        val a = JSONArray(); items.forEach { a.put(it.toJson()) }
        writeAtomic("queue.json", a.toString())
    }
}
