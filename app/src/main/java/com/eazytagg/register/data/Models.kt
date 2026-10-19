package com.eazytagg.register.data

import org.json.JSONArray
import org.json.JSONObject

/** One field of the event form, as defined in the EazyTagg system (App section / event fields). */
data class Field(
    val id: String,
    val name: String,
    val type: String,
    val required: Boolean,
    val options: List<String>,
    val placeholder: String,
    val condType: String = "",   // "", "show" or "hide"
    val condField: String = "",
    val condValue: String = ""
) {
    val isChoice: Boolean get() = type == "dropdown" || type == "autocomplete"
    val isRadio: Boolean get() = type == "selection"
    val isMulti: Boolean get() = type == "multi_selection"
    val isSignature: Boolean get() = type == "signature"
    val isLong: Boolean get() = type == "multi"

    /** Text blocks on the form (not inputs). */
    val isStatic: Boolean
        get() = type == "text_center" || type == "text_right" ||
            (type == "text" && !required && placeholder.isBlank() && name.length > 40)

    /** Types this app cannot fill in on a device. */
    val unsupported: Boolean get() = type == "attachment" || type == "packages"

    val isInput: Boolean get() = !isStatic && !unsupported
}

data class EventInfo(
    val code: String,
    val title: String = "",
    val dateText: String = "",
    val venue: String = "",
    val fields: List<Field> = emptyList(),
    val successField: String = "",
    val endDate: String = "",
    val accent: String = "",
    val logoPath: String = "",
    val bgPath: String = "",
    val consentPopup: Boolean = false,
    val consentText: String = "",
    val printer: Boolean = false
) {
    fun toJson(): String = JSONObject().apply {
        put("code", code); put("title", title); put("dateText", dateText); put("venue", venue)
        put("successField", successField); put("endDate", endDate); put("accent", accent)
        put("logoPath", logoPath); put("bgPath", bgPath)
        put("consentPopup", consentPopup); put("consentText", consentText); put("printer", printer)
        put("fields", JSONArray().also { arr ->
            fields.forEach { f ->
                arr.put(JSONObject().apply {
                    put("id", f.id); put("name", f.name); put("type", f.type)
                    put("required", f.required); put("placeholder", f.placeholder)
                    put("options", JSONArray(f.options))
                    put("condType", f.condType); put("condField", f.condField); put("condValue", f.condValue)
                })
            }
        })
    }.toString()

    companion object {
        private fun yes(s: String) = s == "yes" || s == "1" || s == "true"
        private fun clean(s: String) = if (s == "null") "" else s

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
                    f.optString("placeholder"),
                    f.optString("condType"), f.optString("condField"), f.optString("condValue")
                )
            }
            EventInfo(
                o.optString("code"), o.optString("title"), o.optString("dateText"), o.optString("venue"),
                fs, o.optString("successField"), o.optString("endDate"), o.optString("accent"),
                o.optString("logoPath"), o.optString("bgPath"),
                o.optBoolean("consentPopup"), o.optString("consentText"), o.optBoolean("printer")
            )
        } catch (e: Exception) { null }

        fun fromServer(code: String, o: JSONObject): EventInfo {
            val arr = o.optJSONArray("data") ?: JSONArray()
            val fs = (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                val raw = clean(f.optString("field_options", ""))
                val opts = if (raw.isBlank()) emptyList()
                else raw.replace("\r\n", "\n").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                Field(
                    id = f.optString("field_id"),
                    name = f.optString("field_name"),
                    type = f.optString("field_type"),
                    required = yes(f.optString("required")),
                    options = opts,
                    placeholder = clean(f.optString("field_placeholder")),
                    condType = clean(f.optString("condition_type")),
                    condField = clean(f.optString("condition_field")),
                    condValue = clean(f.optString("condition_value"))
                )
            }
            return EventInfo(
                code = code,
                title = clean(o.optString("title")),
                dateText = clean(o.optString("start_date")),
                venue = clean(o.optString("venue")),
                fields = fs,
                successField = clean(o.optString("success_field")),
                endDate = clean(o.optString("end_date")),
                accent = clean(o.optString("label_color")),
                logoPath = clean(o.optString("logo")),
                bgPath = clean(o.optString("bg")),
                consentPopup = yes(o.optString("consent_popup")),
                consentText = clean(o.optString("consent_text")),
                printer = o.optString("enable_printer") == "yes"
            )
        }
    }
}

/** A registration saved on the device because there was no connection. */
data class QueuedOp(
    val id: Long,
    val eventCode: String,
    val entryData: String,
    val consent: String,
    val testMode: Boolean,
    val at: Long,
    val label: String,
    val attempts: Int = 0,
    val failed: Boolean = false,
    val error: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("eventCode", eventCode); put("entryData", entryData)
        put("consent", consent); put("testMode", testMode); put("at", at); put("label", label)
        put("attempts", attempts); put("failed", failed); put("error", error)
    }

    companion object {
        fun fromJson(o: JSONObject) = QueuedOp(
            o.optLong("id"), o.optString("eventCode"), o.optString("entryData"),
            o.optString("consent"), o.optBoolean("testMode"), o.optLong("at"), o.optString("label"),
            o.optInt("attempts"), o.optBoolean("failed"), o.optString("error")
        )
    }
}

/** What the person sees after finishing. */
data class Outcome(
    val kind: Kind,
    val title: String,
    val message: String,
    val name: String = "",
    val pin: String = ""
) {
    enum class Kind { REGISTERED, CHECKED_IN, ALREADY, SAVED_OFFLINE }
}
