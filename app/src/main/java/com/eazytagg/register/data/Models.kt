package com.eazytagg.register.data

import org.json.JSONArray
import org.json.JSONObject

data class Field(
    val id: String,
    val name: String,
    val type: String,
    val required: Boolean,
    val options: List<String>,
    val placeholder: String
) {
    val editable: Boolean get() = type in EDITABLE
    val isChoice: Boolean get() = type == "dropdown" || type == "selection"
    val isMulti: Boolean get() = type == "multi_selection"

    companion object {
        val EDITABLE = setOf("text", "email", "phone", "number", "date", "textarea", "dropdown", "selection", "multi_selection")
    }
}

data class EventInfo(
    val code: String,
    val fields: List<Field>,
    val successField: String,
    val endDate: String,
    val title: String,
    val consentPopup: Boolean = false,
    val consentText: String = ""
) {
    fun toJson(): String = JSONObject().apply {
        put("code", code)
        put("successField", successField)
        put("endDate", endDate)
        put("title", title)
        put("consentPopup", consentPopup)
        put("consentText", consentText)
        put("fields", JSONArray().also { arr ->
            fields.forEach { f ->
                arr.put(JSONObject().apply {
                    put("id", f.id); put("name", f.name); put("type", f.type)
                    put("required", f.required); put("placeholder", f.placeholder)
                    put("options", JSONArray(f.options))
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(s: String): EventInfo? = try {
            val o = JSONObject(s)
            val arr = o.optJSONArray("fields") ?: JSONArray()
            val fs = (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                val opts = f.optJSONArray("options")
                Field(
                    f.optString("id"), f.optString("name"), f.optString("type"),
                    f.optBoolean("required"),
                    if (opts == null) emptyList() else (0 until opts.length()).map { opts.getString(it) },
                    f.optString("placeholder")
                )
            }
            EventInfo(o.optString("code"), fs, o.optString("successField"), o.optString("endDate"), o.optString("title"), o.optBoolean("consentPopup"), o.optString("consentText"))
        } catch (e: Exception) { null }

        fun fromServer(code: String, o: JSONObject): EventInfo {
            val arr = o.optJSONArray("data") ?: JSONArray()
            val fs = (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                val raw = f.optString("field_options", "")
                val opts = if (raw.isBlank() || raw == "null") emptyList()
                else raw.replace("\r\n", "\n").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                Field(
                    id = f.optString("field_id"),
                    name = f.optString("field_name"),
                    type = f.optString("field_type"),
                    required = f.optString("required").let { it == "yes" || it == "1" || it == "true" },
                    options = opts,
                    placeholder = f.optString("field_placeholder")
                )
            }
            return EventInfo(
                code, fs, o.optString("success_field"), o.optString("end_date"), "",
                o.optString("consent_popup").let { it == "yes" || it == "1" || it == "true" },
                o.optString("consent_text").let { if (it == "null") "" else it }
            )
        }
    }
}

/** One action waiting to reach the server (made while offline). */
data class QueuedOp(
    val id: Long,
    val kind: String, // checkin | checkout | session | pin | update
    val eventCode: String,
    val attendeeUuid: String = "",
    val sessionUuid: String = "",
    val pin: String = "",
    val entryData: String = "",
    val agent: String,
    val at: Long,
    val label: String,
    val attempts: Int = 0,
    val failed: Boolean = false,
    val error: String = "",
    val consent: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("kind", kind); put("eventCode", eventCode)
        put("attendeeUuid", attendeeUuid); put("sessionUuid", sessionUuid); put("pin", pin)
        put("entryData", entryData); put("agent", agent); put("at", at); put("label", label)
        put("attempts", attempts); put("failed", failed); put("error", error); put("consent", consent)
    }

    companion object {
        fun fromJson(o: JSONObject) = QueuedOp(
            o.optLong("id"), o.optString("kind"), o.optString("eventCode"),
            o.optString("attendeeUuid"), o.optString("sessionUuid"), o.optString("pin"),
            o.optString("entryData"), o.optString("agent"), o.optLong("at"), o.optString("label"),
            o.optInt("attempts"), o.optBoolean("failed"), o.optString("error"), o.optString("consent")
        )
    }
}

/** Local audit trail: who did what, when. */
data class ActivityItem(
    val at: Long,
    val agent: String,
    val action: String,
    val who: String,
    val result: String, // ok | already | error | queued | synced
    val detail: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("at", at); put("agent", agent); put("action", action)
        put("who", who); put("result", result); put("detail", detail)
    }

    companion object {
        fun fromJson(o: JSONObject) = ActivityItem(
            o.optLong("at"), o.optString("agent"), o.optString("action"),
            o.optString("who"), o.optString("result"), o.optString("detail")
        )
    }
}

