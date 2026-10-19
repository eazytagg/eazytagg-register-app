package com.eazytagg.register

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eazytagg.register.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class Screen { SignIn, EventCode, Dashboard, Register, Sync, Activity, Settings }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    // ---- navigation ----
    val stack = mutableStateListOf<Screen>()
    val screen: Screen get() = stack.lastOrNull() ?: Screen.SignIn

    fun go(s: Screen) { stack.add(s) }
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        return true
    }
    private fun reset(s: Screen) { stack.clear(); stack.add(s) }

    // ---- settings / session ----
    var theme by mutableStateOf(store.theme)
    var serverUrl by mutableStateOf(store.baseUrl)
    var email by mutableStateOf(store.agentEmail)
    var password by mutableStateOf("")
    var agentName by mutableStateOf(store.agentName)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("")
    var messageIsError by mutableStateOf(true)
    var online by mutableStateOf(true)

    // ---- event ----
    var eventCodeInput by mutableStateOf("")
    var event by mutableStateOf<EventInfo?>(null)

    // ---- registration form ----
    val editValues = mutableStateMapOf<String, String>()
    val editMulti = mutableStateMapOf<String, List<String>>()
    var regConsent by mutableStateOf(false)
    var regDone by mutableStateOf<String?>(null)

    // ---- queue / activity ----
    val queue = mutableStateListOf<QueuedOp>()
    val activity = mutableStateListOf<ActivityItem>()
    var syncing by mutableStateOf(false)
    var syncNote by mutableStateOf("")

    /** Registrations made on this device today (done live or waiting in the queue). */
    val registeredToday: Int get() {
        val start = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return activity.count { it.action == "Register" && it.at >= start && (it.result == "ok" || it.result == "queued") }
    }

    init {
        queue.addAll(store.loadQueue())
        activity.addAll(store.loadActivity())
        event = EventInfo.fromJson(store.eventJson)
        if (store.signedIn && agentName.isNotBlank()) {
            reset(if (event != null) Screen.Dashboard else Screen.EventCode)
        } else reset(Screen.SignIn)

        viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (queue.any { !it.failed }) flushQueue()
            }
        }
    }

    // ================= sign in =================
    fun testConnection() {
        val base = Api.normalizeBase(serverUrl)
        if (base.isEmpty()) { setMsg("Enter the server address first.", true); return }
        serverUrl = base
        viewModelScope.launch {
            busy = true
            when (val r = Api.ping(base)) {
                is ApiResult.Reply -> { online = true; setMsg(if (r.status == "success") "Connected to the server." else "Server replied: ${r.message}", r.status != "success") }
                is ApiResult.NoConnection -> setMsg("Cannot reach the server: ${r.reason}", true)
            }
            busy = false
        }
    }

    fun signIn() {
        val base = Api.normalizeBase(serverUrl)
        if (base.isEmpty()) { setMsg("Enter the server address.", true); return }
        if (email.isBlank() || password.isBlank()) { setMsg("Enter your email and password.", true); return }
        viewModelScope.launch {
            busy = true; message = ""
            when (val r = Api.login(base, email.trim(), password)) {
                is ApiResult.Reply -> {
                    online = true
                    if (r.status == "success") {
                        serverUrl = base
                        store.baseUrl = base
                        agentName = r.json.optString("name").ifBlank { email.trim() }
                        store.agentName = agentName
                        store.agentEmail = email.trim()
                        store.signedIn = true
                        password = ""
                        log("Signed in", "", "ok")
                        reset(if (event != null) Screen.Dashboard else Screen.EventCode)
                    } else setMsg(r.message.ifBlank { "Sign in failed." }, true)
                }
                is ApiResult.NoConnection -> setMsg("Cannot reach the server: ${r.reason}", true)
            }
            busy = false
        }
    }

    fun signOut() {
        store.signedIn = false
        password = ""
        reset(Screen.SignIn)
    }

    // ================= event =================
    fun openEvent() {
        val code = eventCodeInput.trim()
        if (code.isEmpty()) { setMsg("Enter the event code.", true); return }
        viewModelScope.launch {
            busy = true; message = ""
            when (val r = Api.getEvent(store.baseUrl, code)) {
                is ApiResult.Reply -> {
                    online = true
                    if (r.status == "success") {
                        val ev = EventInfo.fromServer(code, r.json)
                        event = ev
                        store.eventJson = ev.toJson()
                        eventCodeInput = ""
                        log("Opened event", code, "ok")
                        reset(Screen.Dashboard)
                    } else setMsg(r.message.ifBlank { "Invalid event code." }, true)
                }
                is ApiResult.NoConnection -> setMsg("Cannot reach the server: ${r.reason}", true)
            }
            busy = false
        }
    }

    fun changeEvent() { event = null; store.eventJson = ""; reset(Screen.EventCode) }

    // ================= registration =================
    fun startRegister() {
        nextRegister()
        go(Screen.Register)
    }

    fun nextRegister() {
        editValues.clear(); editMulti.clear()
        regConsent = false; regDone = null; message = ""
    }

    fun saveRegister() {
        val ev = event ?: return
        val missing = ev.fields.filter { it.editable && it.required && !it.isMulti && editValues[it.id].orEmpty().isBlank() }
        if (missing.isNotEmpty()) { setMsg("Fill in: ${missing.joinToString { it.name }}", true); return }
        if (ev.consentPopup && !regConsent) { setMsg("The attendee must agree to the consent notice.", true); return }
        val data = JSONObject()
        ev.fields.filter { it.editable }.forEach { f ->
            if (f.isMulti) data.put(f.id, JSONArray(editMulti[f.id].orEmpty()))
            else data.put(f.id, editValues[f.id].orEmpty().trim())
        }
        val consent = if (ev.consentPopup && regConsent) "yes" else ""
        val who = ev.successField.split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { editValues[it].orEmpty().trim() }.trim().ifBlank { "New attendee" }
        viewModelScope.launch {
            busy = true; message = ""
            when (val r = Api.submit(store.baseUrl, ev.code, data.toString(), consent)) {
                is ApiResult.NoConnection -> {
                    online = false
                    enqueue(QueuedOp(System.nanoTime(), "register", ev.code, entryData = data.toString(), agent = agentName, at = System.currentTimeMillis(), label = "Register · $who", consent = consent))
                    log("Register", who, "queued", "No connection")
                    regDone = "Saved offline: $who\nThey will be registered, checked in and their tag printed when the connection is back."
                }
                is ApiResult.Reply -> when (r.status) {
                    "success" -> {
                        online = true
                        log("Register", who, "ok")
                        val pin = r.json.optString("pin_code").let { if (it.isBlank()) "" else "\nPIN: $it" }
                        val printed = if (r.json.optBoolean("printed", false)) "\nTag sent to the printer." else ""
                        regDone = "Registered and checked in: $who$pin$printed"
                    }
                    "exists" -> { log("Register", who, "already", r.message); setMsg(r.message, true) }
                    else -> { log("Register", who, "error", r.message); setMsg(r.message.ifBlank { "Could not register." }, true) }
                }
            }
            busy = false
        }
    }

    // ================= queue =================
    private fun enqueue(op: QueuedOp) { queue.add(op); store.saveQueue(queue.toList()) }

    private fun clock(): String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

    /** Called when the phone gets a network again or the app comes back to the front. */
    fun networkBack() {
        Api.resetConnections()
        flushQueue()
    }

    fun flushQueue() {
        if (syncing) return
        if (queue.none { !it.failed }) return
        viewModelScope.launch {
            syncing = true
            var sent = 0
            try {
                val base = store.baseUrl
                for (op in queue.toList()) {
                    if (op.failed) continue
                    val r: ApiResult = when (op.kind) {
                        else -> Api.submit(base, op.eventCode, op.entryData, op.consent, op.at)
                    }
                    if (r is ApiResult.NoConnection) {
                        online = false
                        syncNote = "${clock()}  Could not reach the server (${r.reason}). Will try again."
                        break
                    }
                    online = true
                    val rep = r as ApiResult.Reply
                    val idx = queue.indexOfFirst { it.id == op.id }
                    if (idx < 0) continue
                    if (rep.status == "success" || rep.status == "exists" || rep.message.contains("already", true)) {
                        queue.removeAt(idx)
                        sent++
                        log(op.label, "", "synced", "Sent by ${op.agent}")
                    } else {
                        queue[idx] = op.copy(attempts = op.attempts + 1, failed = true, error = rep.message)
                        syncNote = "${clock()}  The server refused one item: ${rep.message}"
                    }
                    store.saveQueue(queue.toList())
                }
                if (sent > 0) syncNote = "${clock()}  Sent $sent item${if (sent == 1) "" else "s"}."
            } catch (e: Exception) {
                syncNote = "${clock()}  Sync problem: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                syncing = false
            }
        }
    }

    fun retryFailed(op: QueuedOp) {
        val i = queue.indexOfFirst { it.id == op.id }
        if (i >= 0) { queue[i] = op.copy(failed = false, error = ""); store.saveQueue(queue.toList()); flushQueue() }
    }

    fun discard(op: QueuedOp) {
        queue.removeAll { it.id == op.id }
        store.saveQueue(queue.toList())
    }

    // ================= misc =================
    fun chooseTheme(t: String) { theme = t; store.theme = t }

    fun clearActivity() { activity.clear(); store.saveActivity(emptyList()) }

    private fun log(action: String, who: String, result: String, detail: String = "") {
        activity.add(ActivityItem(System.currentTimeMillis(), agentName, action, who, result, detail))
        store.saveActivity(activity.toList())
    }

    fun setMsg(m: String, error: Boolean) { message = m; messageIsError = error }
    fun clearMsg() { message = "" }
}
