package com.eazytagg.register.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Result of one call to the EazyTagg PHP API. */
sealed class ApiResult {
    data class Reply(val status: String, val message: String, val json: JSONObject) : ApiResult()
    data class NoConnection(val reason: String) : ApiResult()
}

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun resetConnections() { client.connectionPool.evictAll() }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** "192.168.1.5/eazytagg_test" -> "http://192.168.1.5/eazytagg_test/" */
    fun normalizeBase(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
            val host = s.substringBefore('/').substringBefore(':')
            val local = host == "localhost" || host == "10.0.2.2" ||
                Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)
            s = (if (local) "http://" else "https://") + s
        }
        s = s.replace(Regex("(?i)(/api)?(/[a-z.]+\\.php)?/*$"), "")
        return "$s/"
    }

    private fun parse(body: String): ApiResult {
        val start = body.indexOf('{')
        if (start < 0) return ApiResult.NoConnection("The server did not answer correctly. Check the server address.")
        return try {
            val o = JSONObject(body.substring(start))
            ApiResult.Reply(o.optString("status"), o.optString("message"), o)
        } catch (e: Exception) {
            ApiResult.NoConnection("The server reply could not be read.")
        }
    }

    suspend fun call(base: String, endpoint: String, payload: JSONObject? = null): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(base + "api/" + endpoint).apply {
                    if (payload == null) get() else post(payload.toString().toRequestBody(JSON))
                }.build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.code >= 500 && !text.contains("{")) ApiResult.NoConnection("Server error (${resp.code})")
                    else parse(text)
                }
            } catch (e: Exception) {
                ApiResult.NoConnection(e.message ?: "No connection")
            }
        }

    suspend fun ping(base: String) = call(base, "ping.php")

    suspend fun getEvent(base: String, code: String) =
        call(base, "get.event.php", JSONObject().put("event_code", code))

    /** Registers a new attendee. The server also checks them in and prints their tag. */
    suspend fun submit(
        base: String, code: String, entryData: String, consent: String,
        testMode: Boolean, at: Long? = null
    ): ApiResult {
        val p = JSONObject().put("event_code", code).put("entry_data", entryData).put("consent", consent)
        p.put("test_mode", if (testMode) "yes" else "no")
        if (at != null && at > 0) p.put("scanned_at", at)
        return call(base, "submit.entry.php", p)
    }

    /** Someone who registered earlier enters their 4-digit code. */
    suspend fun pin(base: String, code: String, pin: String) =
        call(base, "pin.entry.php", JSONObject().put("event_code", code).put("pin_code", pin))

    /** Someone who registered earlier shows their QR code (attendee id). */
    suspend fun checkin(base: String, code: String, uuid: String) =
        call(base, "log.checkin.php", JSONObject().put("event_code", code).put("attendee_uuid", uuid))
}
