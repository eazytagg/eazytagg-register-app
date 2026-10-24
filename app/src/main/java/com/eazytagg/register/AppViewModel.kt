package com.eazytagg.register

import android.app.Application
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eazytagg.register.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { Splash, Setup, Welcome, Choose, Scan, Code, Form, Outcome, Closed, StaffGate, Staff }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    val serverBase: String get() = store.baseUrl
    val staffPinSet: Boolean get() = store.staffPin.isNotBlank()

    // ---- navigation ----
    var screen by mutableStateOf(Screen.Splash)
        private set
    private var before = Screen.Welcome

    fun go(s: Screen) { screen = s }

    // ---- device / event ----
    var event by mutableStateOf<EventInfo?>(null)
        private set
    var online by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var lastRefreshed by mutableStateOf(store.lastRefreshed)
        private set
    var refreshNote by mutableStateOf("")
        private set

    // ---- staff settings (mirrors Store) ----
    var serverInput by mutableStateOf(ServerAddress.displayIp(store.baseUrl))
    var codeInput by mutableStateOf(store.eventCode)
    var staffPinInput by mutableStateOf("")
    var setupMessage by mutableStateOf("")
    var setupBusy by mutableStateOf(false)
    var askPrereg by mutableStateOf(store.askPrereg)
        private set
    var testMode by mutableStateOf(store.testMode)
        private set
    var darkTheme by mutableStateOf(store.darkTheme)
        private set
    var resetSeconds by mutableStateOf(store.resetSeconds)
        private set

    // ---- staff gate ----
    var gatePin by mutableStateOf("")
        private set
    var gateError by mutableStateOf("")
        private set

    // ---- preregistered code ----
    var codeDigits by mutableStateOf("")
        private set
    var codeMessage by mutableStateOf("")
        private set
    var codeBusy by mutableStateOf(false)
        private set

    // ---- form ----
    val values = mutableStateMapOf<String, String>()
    val multi = mutableStateMapOf<String, List<String>>()
    val signatures = mutableStateMapOf<String, String>()
    val errors = mutableStateMapOf<String, String>()
    var formMessage by mutableStateOf("")
        private set
    var showConsent by mutableStateOf(false)
        private set
    var submitting by mutableStateOf(false)
        private set
    var formVersion by mutableStateOf(0)
        private set

    // ---- outcome ----
    var outcome by mutableStateOf<Outcome?>(null)
        private set
    var countdown by mutableStateOf(0)
        private set
    private var countdownJob: Job? = null

    // ---- offline queue ----
    val queue = mutableStateListOf<QueuedOp>()
    var syncing by mutableStateOf(false)
        private set
    var syncNote by mutableStateOf("")
        private set

    init {
        store.staffPin = "" // the staff PIN was removed: Staff opens the settings directly
        queue.addAll(store.loadQueue())
        event = EventInfo.fromJson(store.eventJson)
        fun configured() = !(store.baseUrl.isBlank() || store.eventCode.isBlank() || event == null)
        screen = Screen.Splash
        if (configured()) refresh()
        // logo splash first, then the login (server IP + event code) the first time, or Welcome once set up
        viewModelScope.launch {
            delay(1800)
            if (screen == Screen.Splash) screen = if (configured()) startScreen() else Screen.Setup
        }

        viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (queue.any { !it.failed }) flushQueue()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(10 * 60_000L)
                if (screen == Screen.Welcome || screen == Screen.Closed) refresh()
            }
        }
    }

    // ================= helpers =================
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ended(ev: EventInfo?): Boolean {
        val end = ev?.endDate.orEmpty().trim().take(10)
        return end.length == 10 && today() > end
    }

    /** Text set in the system's App section, falling back to the built-in wording. */
    fun t(key: String, default: String, vararg vars: Pair<String, String>): String {
        var s = event?.ui?.get(key)?.takeIf { it.isNotBlank() } ?: default
        vars.forEach { (k, x) -> s = s.replace("{$k}", x) }
        return s.replace("{event}", event?.title ?: "")
    }
    fun flag(key: String, default: Boolean = true): Boolean = when (event?.ui?.get(key)) { "no" -> false; "yes" -> true; else -> default }
    fun uiInt(key: String, default: Int): Int = event?.ui?.get(key)?.toIntOrNull() ?: default

    private fun startScreen(): Screen = if (ended(event)) Screen.Closed else Screen.Welcome

    private fun clock(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    // ================= setup =================
    var testBusy by mutableStateOf(false)
        private set
    var setupGood by mutableStateOf(false)
        private set

    /** Checks the server address only. Nothing is saved and nobody is registered. */
    fun testConnection() {
        if (testBusy || setupBusy) return
        val ip = ServerAddress.ip(serverInput)
        if (ip == null) { setupGood = false; setupMessage = "Enter only the server IP address, for example 192.168.0.39."; return }
        viewModelScope.launch {
            testBusy = true; setupMessage = ""; setupGood = false
            val failures = mutableListOf<String>()
            var found = ""
            for (b in ServerAddress.candidates(ip, store.baseUrl)) {
                when (val r = Api.ping(b)) {
                    is ApiResult.Reply -> { found = b; break }
                    is ApiResult.NoConnection -> failures += "$b  ->  ${r.reason}"
                }
            }
            setupGood = found.isNotEmpty()
            setupMessage = if (found.isNotEmpty()) "Connected. The server answered at $found"
                else "Cannot reach the server. Tried:\n" + failures.joinToString("\n")
            testBusy = false
        }
    }

    fun saveSetup() {
        if (setupBusy) return
        val ip = ServerAddress.ip(serverInput)
        val code = codeInput.trim()
        if (ip == null) { setupMessage = "Enter only the server IP address, for example 192.168.0.39."; return }
        if (code.isEmpty()) { setupMessage = "Enter the event code."; return }
        viewModelScope.launch {
            setupBusy = true; setupMessage = ""
            val (base, result) = Api.connectToEvent(ip, code, store.baseUrl)
            when (val r = result) {
                is ApiResult.NoConnection -> setupMessage = "Cannot reach the server: ${r.reason}"
                is ApiResult.Reply -> {
                    if (base.isEmpty()) setupMessage = "The registration server was not found. Check the IP address and connect this tablet to the same network as the server."
                    else if (r.status != "success") setupMessage = r.message.ifBlank { "That event code was not accepted." }
                    else if (queue.isNotEmpty() && store.baseUrl.isNotBlank() && base != store.baseUrl) setupMessage = "Sync the registrations waiting on this tablet before changing the server."
                    else {
                        val ev = EventInfo.fromServer(code, r.json)
                        store.baseUrl = base; store.eventCode = code
                        serverInput = ip
                        event = ev; store.eventJson = ev.toJson()
                        store.lastRefreshed = System.currentTimeMillis(); lastRefreshed = store.lastRefreshed
                        online = true
                        resetAll()
                    }
                }
            }
            setupBusy = false
        }
    }

    // ================= refresh from the system =================
    /** Pulls the form, logo, colours and rules again from the PDynamics App section. */
    fun refresh() {
        if (refreshing || store.baseUrl.isBlank() || store.eventCode.isBlank()) return
        viewModelScope.launch {
            refreshing = true
            when (val r = Api.getEvent(store.baseUrl, store.eventCode)) {
                is ApiResult.NoConnection -> { online = false; refreshNote = "Could not reach the system. Using the last saved form." }
                is ApiResult.Reply -> {
                    online = true
                    if (r.status == "success") {
                        val ev = EventInfo.fromServer(store.eventCode, r.json)
                        event = ev; store.eventJson = ev.toJson()
                        store.lastRefreshed = System.currentTimeMillis(); lastRefreshed = store.lastRefreshed
                        refreshNote = "Updated."
                        if (screen == Screen.Welcome && ended(ev)) screen = Screen.Closed
                        if (screen == Screen.Closed && !ended(ev)) screen = Screen.Welcome
                    } else refreshNote = r.message.ifBlank { "The system did not accept the event code." }
                }
            }
            refreshing = false
        }
    }

    fun onResume() {
        networkBack()
        if (System.currentTimeMillis() - lastRefreshed > 2 * 60_000L && (screen == Screen.Welcome || screen == Screen.Closed)) refresh()
    }

    // ================= staff =================
    private var tapCount = 0
    private var lastTapAt = 0L

    /** Tapping the logo three times in a row opens the staff settings. */
    fun logoTap() {
        val now = System.currentTimeMillis()
        tapCount = if (now - lastTapAt <= 800) tapCount + 1 else 1
        lastTapAt = now
        if (tapCount >= 3) { tapCount = 0; openStaff() }
    }

    fun openStaff() {
        before = screen
        gatePin = ""; gateError = ""
        screen = if (store.staffPin.isBlank()) Screen.Staff else Screen.StaffGate
    }

    fun gateKey(d: String) {
        if (gatePin.length >= 4) return
        gatePin += d; gateError = ""
        if (gatePin.length == 4) {
            if (gatePin == store.staffPin) { gatePin = ""; screen = Screen.Staff }
            else { gatePin = ""; gateError = "That PIN is not right." }
        }
    }
    fun gateDelete() { gatePin = gatePin.dropLast(1) }
    fun gateCancel() { gatePin = ""; gateError = ""; screen = before }

    fun leaveStaff() { resetAll() }

    fun updateAskPrereg(v: Boolean) { askPrereg = v; store.askPrereg = v }
    fun updateTestMode(v: Boolean) { testMode = v; store.testMode = v }
    fun updateDark(v: Boolean) { darkTheme = v; store.darkTheme = v }
    fun updateResetSeconds(v: Int) { resetSeconds = v; store.resetSeconds = v }
    fun changeStaffPin(p: String) { if (p.length == 4) store.staffPin = p }

    // ================= welcome / choose =================
    var askDialog by mutableStateOf(false)
        private set

    /** First screen: "Did you preregister?" pops up. */
    fun start() {
        clearForm()
        val ask = when (event?.ui?.get("ask_prereg")) { "yes" -> true; "no" -> false; else -> askPrereg }
        if (ask) askDialog = true else screen = Screen.Form
    }

    fun answerNo() { askDialog = false; clearForm(); screen = Screen.Form }
    fun answerYes() { askDialog = false; codeMessage = ""; screen = Screen.Choose }
    fun dismissAsk() { askDialog = false }
    fun chooseCode() { codeDigits = ""; codeMessage = ""; screen = Screen.Code }
    fun chooseScan() { codeMessage = ""; lastCode = ""; screen = Screen.Scan }
    fun backToChoose() { screen = Screen.Choose }
    fun backToWelcome() { screen = Screen.Welcome }
    fun cancelRegistration() { if (!submitting) { clearForm(); screen = Screen.Welcome } }

    private var lastCode = ""
    private var lastCodeAt = 0L
    private val uuidRe = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** Camera found a QR code. */
    fun onScanned(text: String) {
        val now = System.currentTimeMillis()
        if (codeBusy || screen != Screen.Scan) return
        if (text == lastCode && now - lastCodeAt < 4000) return
        lastCode = text; lastCodeAt = now
        val uuid = uuidRe.find(text)?.value
        if (uuid == null) { codeMessage = "That is not a PDynamics code."; return }
        val ev = event ?: return
        viewModelScope.launch {
            codeBusy = true
            when (val r = Api.checkin(store.baseUrl, ev.code, uuid)) {
                is ApiResult.NoConnection -> { online = false; codeMessage = "No connection right now. Please ask at the desk." }
                is ApiResult.Reply -> {
                    online = true
                    val name = r.json.optString("name").let { if (it == "null") "" else it }
                    when {
                        r.status == "success" -> showOutcome(Outcome(
                            Outcome.Kind.CHECKED_IN,
                            if (name.isBlank()) t("msg_plain", "You're checked in.") else t("checkedin_title", "Welcome, {first_name}.", "first_name" to name.substringBefore(' '), "name" to name),
                            if (r.json.optBoolean("printed", false)) t("msg_printed", "You're checked in. Your tag is printing now. Collect it from the print desk.")
                            else t("msg_plain", "You're checked in. Enjoy the event."), name))
                        r.status == "exists" -> showOutcome(Outcome(Outcome.Kind.ALREADY, t("already_title", "You're already checked in."), t("already_message", "You checked in earlier today. Enjoy the event."), name))
                        else -> codeMessage = "We could not find that code. Try again or enter your PIN."
                    }
                }
            }
            codeBusy = false
        }
    }

    // ================= preregistered code =================
    fun codeKey(d: String) {
        if (codeBusy || codeDigits.length >= 4) return
        codeDigits += d; codeMessage = ""
        if (codeDigits.length == 4) submitCode()
    }
    fun codeDelete() { if (!codeBusy) { codeDigits = codeDigits.dropLast(1); codeMessage = "" } }

    private fun submitCode() {
        val ev = event ?: return
        val pin = codeDigits
        viewModelScope.launch {
            codeBusy = true
            when (val r = Api.pin(store.baseUrl, ev.code, pin)) {
                is ApiResult.NoConnection -> { online = false; codeMessage = "No connection right now. Please ask at the desk."; codeDigits = "" }
                is ApiResult.Reply -> {
                    online = true
                    val name = r.json.optString("name").let { if (it == "null") "" else it }
                    when {
                        r.status == "success" -> showOutcome(Outcome(
                            Outcome.Kind.CHECKED_IN,
                            if (name.isBlank()) t("msg_plain", "You're checked in.") else t("checkedin_title", "Welcome, {first_name}.", "first_name" to name.substringBefore(' '), "name" to name),
                            if (r.json.optBoolean("printed", false)) t("msg_printed", "You're checked in. Your tag is printing now. Collect it from the print desk.")
                            else t("msg_plain", "You're checked in. Enjoy the event."),
                            name
                        ))
                        r.status == "exists" -> showOutcome(Outcome(Outcome.Kind.ALREADY, t("already_title", "You're already checked in."), t("already_message", "You checked in earlier today. Enjoy the event."), name))
                        else -> { codeMessage = "We could not find that code. Check it and try again."; codeDigits = "" }
                    }
                }
            }
            codeBusy = false
        }
    }

    // ================= form =================
    private fun matches(fieldId: String, expected: String): Boolean {
        val list = multi[fieldId]
        return if (list != null) expected in list else values[fieldId].orEmpty() == expected
    }

    /** Applies the system's show/hide rule for this field. */
    fun visible(f: Field): Boolean {
        if (f.condField.isBlank() || f.condType.isBlank()) return true
        val m = matches(f.condField, f.condValue)
        return if (f.condType == "hide") !m else m
    }

    fun setValue(id: String, v: String) { values[id] = v; errors.remove(id); formMessage = "" }
    fun setMulti(id: String, v: List<String>) { multi[id] = v; errors.remove(id); formMessage = "" }
    fun setSignature(id: String, b64: String) { signatures[id] = b64; errors.remove(id) }

    /** Clear button: empties every field but stays on the form. */
    fun clearFormFields() { if (!submitting) clearForm() }

    private fun clearForm() {
        values.clear(); multi.clear(); signatures.clear(); errors.clear()
        formMessage = ""; showConsent = false; submitting = false
        formVersion++
    }

    /** Required fields done / total, among the fields currently shown. */
    fun progress(): Pair<Int, Int> {
        val ev = event ?: return 0 to 0
        val req = ev.fields.filter { it.isInput && it.required && visible(it) }
        val done = req.count { filled(it) }
        return done to req.size
    }

    private fun filled(f: Field): Boolean = when {
        f.isMulti -> multi[f.id].orEmpty().isNotEmpty()
        f.isSignature -> signatures[f.id].orEmpty().isNotBlank()
        else -> values[f.id].orEmpty().isNotBlank()
    }

    /** The name shown on the tag, from the event's "success field" setting. */
    fun tagName(): String {
        val ev = event ?: return ""
        return ev.successField.split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { values[it].orEmpty().trim() }.trim()
    }

    fun tagLine2(): String {
        val ev = event ?: return ""
        val used = ev.successField.split(' ').toSet()
        // first filled single-line field that is not part of the name (organisation, company...)
        val f = ev.fields.firstOrNull { it.isInput && it.type == "single" && it.id !in used && values[it.id].orEmpty().isNotBlank() }
        return f?.let { values[it.id].orEmpty().trim() }.orEmpty()
    }

    fun submit() {
        val ev = event ?: return
        errors.clear(); formMessage = ""
        var first: String? = null
        ev.fields.filter { it.isInput && visible(it) }.forEach { f ->
            when {
                f.required && !filled(f) -> { errors[f.id] = if (f.isSignature) "Please sign here." else "${f.name} is needed."; if (first == null) first = f.id }
                f.type == "email" && values[f.id].orEmpty().isNotBlank() &&
                    !Patterns.EMAIL_ADDRESS.matcher(values[f.id].orEmpty().trim()).matches() -> { errors[f.id] = "Enter an email like name@example.com."; if (first == null) first = f.id }
            }
        }
        if (errors.isNotEmpty()) { formMessage = "Please check the fields marked below."; return }
        if (ev.consentPopup) showConsent = true else doSubmit("")
    }

    fun agree() { showConsent = false; doSubmit("yes") }
    fun decline() { showConsent = false; doSubmit("no") }

    private fun entryJson(ev: EventInfo): JSONObject {
        val data = JSONObject()
        ev.fields.filter { it.isInput }.forEach { f ->
            val shown = visible(f)
            when {
                f.isMulti -> data.put(f.id, JSONArray(if (shown) multi[f.id].orEmpty() else emptyList<String>()))
                f.isSignature -> data.put(f.id, if (shown) signatures[f.id].orEmpty() else "")
                else -> data.put(f.id, if (shown) values[f.id].orEmpty().trim() else "")
            }
        }
        return data
    }

    private fun doSubmit(consent: String) {
        val ev = event ?: return
        val data = entryJson(ev).toString()
        val who = tagName().ifBlank { "New attendee" }
        viewModelScope.launch {
            submitting = true; formMessage = ""
            when (val r = Api.submit(store.baseUrl, ev.code, data, consent, testMode)) {
                is ApiResult.NoConnection -> {
                    online = false
                    enqueue(QueuedOp(System.nanoTime(), ev.code, data, consent, testMode, System.currentTimeMillis(), "Register · $who"))
                    showOutcome(Outcome(
                        Outcome.Kind.SAVED_OFFLINE, t("offline_title", "Saved on this device."),
                        t("offline_message", "There is no connection right now. Your registration will be sent automatically when it is back, and your tag prints then."),
                        who
                    ))
                }
                is ApiResult.Reply -> {
                    online = true
                    when (r.status) {
                        "success" -> {
                            val name = r.json.optString("display_name").ifBlank { who }
                            showOutcome(Outcome(
                                Outcome.Kind.REGISTERED,
                                t("registered_title", "You're registered, {first_name}.", "first_name" to name.substringBefore(' '), "name" to name),
                                if (r.json.optBoolean("printed", false)) t("msg_printed", "You're checked in. Your tag is printing now. Collect it from the print desk.")
                                else t("msg_plain", "You're checked in. Enjoy the event."),
                                name, r.json.optString("pin_code")
                            ))
                        }
                        "exists" -> formMessage = r.message.ifBlank { "This person is already registered." }
                        else -> formMessage = r.message.ifBlank { "Could not register. Please try again." }
                    }
                }
            }
            submitting = false
        }
    }

    // ================= outcome + auto reset =================
    private fun showOutcome(o: Outcome) {
        outcome = o
        screen = Screen.Outcome
        countdown = uiInt("reset_seconds", resetSeconds)
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (countdown > 0) { delay(1_000); countdown-- }
            resetAll()
        }
    }

    /** Clears everything and goes back to the start, ready for the next person. */
    fun resetAll() {
        countdownJob?.cancel()
        outcome = null; countdown = 0; askDialog = false
        clearForm()
        codeDigits = ""; codeMessage = ""
        screen = startScreen()
    }

    // ================= offline queue =================
    private fun enqueue(op: QueuedOp) { queue.add(op); store.saveQueue(queue.toList()) }

    fun networkBack() { Api.resetConnections(); flushQueue() }

    fun flushQueue() {
        if (syncing || queue.none { !it.failed }) return
        syncing = true
        viewModelScope.launch {
            var sent = 0
            try {
                val base = store.baseUrl
                for (op in queue.toList()) {
                    if (op.failed) continue
                    val r = Api.submit(base, op.eventCode, op.entryData, op.consent, op.testMode, op.at)
                    if (r is ApiResult.NoConnection) {
                        online = false
                        syncNote = "${clock()}  Could not reach the server (${r.reason}). Will try again."
                        break
                    }
                    online = true
                    val rep = r as ApiResult.Reply
                    val idx = queue.indexOfFirst { it.id == op.id }
                    if (idx < 0) continue
                    if (rep.status == "success" || rep.status == "exists") { queue.removeAt(idx); sent++ }
                    else {
                        queue[idx] = op.copy(attempts = op.attempts + 1, failed = true, error = rep.message)
                        syncNote = "${clock()}  The server refused one item: ${rep.message}"
                    }
                    store.saveQueue(queue.toList())
                }
                if (sent > 0) syncNote = "${clock()}  Sent $sent registration${if (sent == 1) "" else "s"}."
            } catch (e: Exception) {
                syncNote = "${clock()}  Sync problem: ${e.message ?: e.javaClass.simpleName}"
            } finally { syncing = false }
        }
    }

    fun retryFailed(op: QueuedOp) {
        val i = queue.indexOfFirst { it.id == op.id }
        if (i >= 0) { queue[i] = op.copy(failed = false, error = ""); store.saveQueue(queue.toList()); flushQueue() }
    }

    fun discard(op: QueuedOp) { queue.removeAll { it.id == op.id }; store.saveQueue(queue.toList()) }
}
