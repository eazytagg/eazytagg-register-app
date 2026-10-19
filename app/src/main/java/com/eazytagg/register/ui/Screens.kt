package com.eazytagg.register.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazytagg.register.AppViewModel
import com.eazytagg.register.R
import com.eazytagg.register.Screen
import com.eazytagg.register.data.EventInfo
import com.eazytagg.register.data.Field
import com.eazytagg.register.data.Outcome
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Button wording. These are the defaults; the system's App section will override them.
private const val L_START = "Register"
private const val L_SCAN = "Scan my QR code"
private const val L_CODE = "Enter my PIN"
private const val L_SUBMIT = "Register"
private const val L_BACK = "Back"
private const val L_DONE = "Done"

private fun prettyDate(d: String): String = try {
    val t = d.trim().take(10)
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(t)!!)
} catch (e: Exception) { d }

private fun String.url(base: String) = if (isBlank()) "" else if (startsWith("http")) this else base + "uploads/" + this

@Composable
fun Root(vm: AppViewModel) {
    val ev = vm.event
    val accent = if (ev == null) DefaultAccent else parseAccent(ev.accent)
    EazyTheme(accent, vm.darkTheme) {
        Surface(Modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.background) {
            when (vm.screen) {
                Screen.Setup -> SetupScreen(vm)
                Screen.StaffGate -> GateScreen(vm)
                Screen.Staff -> StaffScreen(vm)
                else -> Kiosk(vm)
            }
        }
    }
}

/** Fixed EazyTagg mark. Three quick taps open the staff settings. */
@Composable
private fun PoweredBy(vm: AppViewModel, onDark: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = if (onDark) 0.95f else 0f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.logoTap() }
            .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("Powered by", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Image(painterResource(R.drawable.eazytagg_logo), "EazyTagg", Modifier.height(36.dp), contentScale = ContentScale.Fit)
    }
}

// ============ layout shell: banner on the left (tablet) / on top (phone) ============

@Composable
private fun Kiosk(vm: AppViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val pad = if (wide) 64.dp else 22.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.42f).fillMaxHeight()) { BrandPanel(vm, compact = false) }
                Box(Modifier.weight(0.58f).fillMaxHeight().padding(horizontal = pad, vertical = 40.dp)) { KioskBody(vm, true) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth()) { BrandPanel(vm, compact = true) }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = pad, vertical = 18.dp)) { KioskBody(vm, false) }
                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) { PoweredBy(vm) }
            }
        }
    }
}

@Composable
private fun KioskBody(vm: AppViewModel, wide: Boolean) {
    when (vm.screen) {
        Screen.Welcome -> { WelcomeBody(vm); if (vm.askDialog) AskDialog(vm) }
        Screen.Choose -> ChooseBody(vm)
        Screen.Scan -> ScanBody(vm)
        Screen.Code -> CodeBody(vm, wide)
        Screen.Form -> FormBody(vm)
        Screen.Outcome -> OutcomeBody(vm)
        Screen.Closed -> ClosedBody(vm)
        else -> {}
    }
}

@Composable
private fun BrandPanel(vm: AppViewModel, compact: Boolean) {
    val ev = vm.event ?: return
    val accent = LocalAccent.current
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val bg = ev.bgPath.url(vm.serverBase)
    val logo = ev.logoPath.url(vm.serverBase)
    Box(Modifier.fillMaxSize().background(accent)) {
        if (bg.isNotEmpty()) AsyncImage(bg, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (bg.isNotEmpty()) Box(Modifier.matchParentSize().background(Color(0x66000000)))
        val textColor = if (bg.isNotEmpty()) Color.White else onAccent
        Column(
            Modifier.fillMaxSize().padding(if (compact) 18.dp else 48.dp),
            verticalArrangement = if (compact) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                if (logo.isNotEmpty()) Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    AsyncImage(logo, null, Modifier.height(if (compact) 30.dp else 56.dp).widthIn(max = 220.dp), contentScale = ContentScale.Fit)
                }
                else Text("EazyTagg", color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                if (!vm.online) Chip("Offline", Warn, WarnInk)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ev.title.ifBlank { "Welcome" }, color = textColor, fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 22.sp else 44.sp, lineHeight = if (compact) 26.sp else 48.sp)
                val sub = listOf(prettyDate(ev.dateText), ev.venue).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (sub.isNotEmpty()) Text(sub, color = textColor, fontSize = if (compact) 14.sp else 20.sp, fontWeight = FontWeight.SemiBold)
            }
            if (!compact) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (vm.screen == Screen.Form) TagPreview(vm)
                    PoweredBy(vm, onDark = true)
                }
            }
        }
    }
}

/** Live name tag that fills in while the person types. */
@Composable
private fun TagPreview(vm: AppViewModel) {
    val ev = vm.event ?: return
    val name = vm.tagName()
    val line2 = vm.tagLine2()
    val (done, total) = vm.progress()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(24.dp)) {
            Text(ev.title, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            Text(name.ifBlank { "Your name" }, color = if (name.isBlank()) Color(0xFFB0B4C0) else Ink,
                fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
            Text(line2.ifBlank { " " }, color = Muted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (total > 0) {
            val onC = MaterialTheme.colorScheme.onPrimary
            Text("$done of $total required fields done", color = onC, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(onC.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(done.toFloat() / total).fillMaxHeight().background(onC))
            }
        }
    }
}

// ============ welcome / choose ============

@Composable
private fun AskDialog(vm: AppViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dismissAsk() },
        title = { Text("Did you preregister?", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp) },
        text = { Text("If you did, you can check in with your QR code or your PIN.", fontSize = 18.sp) },
        confirmButton = { TextButton(onClick = { vm.answerYes() }) { Text("Yes", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) } },
        dismissButton = { TextButton(onClick = { vm.answerNo() }) { Text("No", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) } }
    )
}

@Composable
private fun WelcomeBody(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.height(1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Welcome.", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp)
            Text("Register here to get your name tag.", fontSize = 20.sp, color = Muted)
        }
        BigButton(L_START, { vm.start() }, Modifier.fillMaxWidth(), height = 76)
    }
}

@Composable
private fun ChooseBody(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Check in", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp)
            Text("Show your QR code to the camera, or type the 4-digit PIN from your invitation.", fontSize = 19.sp, color = Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BigButton(L_SCAN, { vm.chooseScan() }, Modifier.fillMaxWidth(), height = 76)
            BigButton(L_CODE, { vm.chooseCode() }, Modifier.fillMaxWidth(), primary = false, height = 76)
            BigButton(L_BACK, { vm.backToWelcome() }, Modifier.fillMaxWidth(), primary = false, height = 56)
        }
    }
}

@Composable
private fun ScanBody(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Show your QR code", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
            CameraScanner(Modifier.fillMaxSize()) { vm.onScanned(it) }
        }
        if (vm.codeMessage.isNotEmpty()) Text(vm.codeMessage, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        BigButton(L_BACK, { vm.backToChoose() }, Modifier.fillMaxWidth(), primary = false, height = 56)
    }
}

@Composable
private fun CodeBody(vm: AppViewModel, wide: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Enter your code", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text("The 4 digits on your invitation.", fontSize = 18.sp, color = Muted)
        PinDots(vm.codeDigits.length)
        if (vm.codeMessage.isNotEmpty()) Text(vm.codeMessage, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Keypad(vm::codeKey, vm::codeDelete, keyH = if (wide) 72 else 60)
        BigButton(L_BACK, { vm.backToChoose() }, Modifier.fillMaxWidth(), primary = false, height = 56)
    }
}

@Composable
private fun ClosedBody(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.height(1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Registration has closed.", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 46.sp)
            Text("Please ask at the desk if you need help.", fontSize = 20.sp, color = Muted)
        }
        Spacer(Modifier.height(1.dp))
    }
}

// ============ form ============

@Composable
private fun FormBody(vm: AppViewModel) {
    val ev = vm.event ?: return
    val scroll = rememberScrollState()
    val tops = remember { mutableStateMapOf<String, Int>() }
    // after a failed Register tap, jump to the first field that needs attention
    LaunchedEffect(vm.formMessage, vm.errors.size) {
        if (vm.errors.isNotEmpty()) {
            val y = vm.errors.keys.mapNotNull { tops[it] }.minOrNull()
            if (y != null) scroll.animateScrollTo((y - 24).coerceAtLeast(0))
        }
    }
    // new person: back to the top
    LaunchedEffect(vm.formVersion) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Your details", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            key(vm.formVersion) {
                ev.fields.forEach { f ->
                    if (vm.visible(f)) FieldView(vm, f, Modifier.onGloballyPositioned { tops[f.id] = it.positionInParent().y.toInt() })
                }
            }
            Spacer(Modifier.height(120.dp)) // room to scroll the last field above the keyboard
        }
        if (vm.formMessage.isNotEmpty())
            Text(vm.formMessage, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(L_BACK, { vm.backToWelcome() }, primary = false, enabled = !vm.submitting)
            BigButton(if (vm.submitting) "Please wait…" else L_SUBMIT, { vm.submit() }, Modifier.weight(1f), enabled = !vm.submitting)
        }
    }
    if (vm.showConsent) ConsentDialog(vm, ev)
}

@Composable
private fun ConsentDialog(vm: AppViewModel, ev: EventInfo) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Before you register", fontWeight = FontWeight.ExtraBold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(ev.consentText.ifBlank { "I agree that my details may be used for this event." }) } },
        confirmButton = { TextButton(onClick = { vm.agree() }) { Text("I agree", fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onClick = { vm.decline() }) { Text("No thanks") } }
    )
}

@Composable
private fun FieldLabel(f: Field) {
    Text(f.name + if (f.required) " *" else "", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldView(vm: AppViewModel, f: Field, modifier: Modifier = Modifier) {
    if (f.unsupported) return
    if (f.isStatic) {
        Text(f.name, fontSize = 16.sp, color = Muted, textAlign = if (f.type == "text_center") TextAlign.Center else if (f.type == "text_right") TextAlign.End else TextAlign.Start, modifier = modifier.fillMaxWidth())
        return
    }
    val err = vm.errors[f.id]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(f)
        when {
            f.isSignature -> SignaturePad({ vm.setSignature(f.id, it) })
            f.isMulti -> Column {
                f.options.forEach { o ->
                    val sel = o in vm.multi[f.id].orEmpty()
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val cur = vm.multi[f.id].orEmpty()
                            vm.setMulti(f.id, if (sel) cur - o else cur + o)
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(sel, null)
                        Spacer(Modifier.width(10.dp))
                        Text(o, fontSize = 18.sp)
                    }
                }
            }
            f.isRadio -> FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                f.options.forEach { o ->
                    val sel = vm.values[f.id] == o
                    Box(
                        Modifier.clip(RoundedCornerShape(26.dp))
                            .background(if (sel) LocalAccent.current else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { vm.setValue(f.id, o) }.padding(horizontal = 22.dp, vertical = 14.dp)
                    ) { Text(o, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground) }
                }
            }
            f.isChoice -> ChoiceField(vm, f)
            f.type == "date" -> DateField(vm, f)
            else -> {
                val ev = vm.event
                val isName = ev != null && f.id in ev.successField.split(' ')
                val looksPhone = f.name.contains("phone", true) || f.name.contains("mobile", true) || f.name.contains("whatsapp", true) || f.name.contains("contact no", true)
                val kb = when {
                    f.type == "numeric" -> KeyboardType.Number
                    f.type == "email" -> KeyboardType.Email
                    looksPhone -> KeyboardType.Phone
                    else -> KeyboardType.Text
                }
                val caps = when {
                    f.type == "email" || kb == KeyboardType.Number || kb == KeyboardType.Phone -> KeyboardCapitalization.None
                    f.isLong -> KeyboardCapitalization.Sentences
                    else -> KeyboardCapitalization.Words   // names, organisations, titles: every word starts with a capital
                }
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                OutlinedTextField(
                    value = vm.values[f.id].orEmpty(), onValueChange = { vm.setValue(f.id, it) },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) keyboard?.show() },
                    placeholder = { if (f.placeholder.isNotBlank()) Text(f.placeholder) },
                    singleLine = !f.isLong, minLines = if (f.isLong) 3 else 1,
                    keyboardOptions = KeyboardOptions(
                        capitalization = caps, keyboardType = kb,
                        autoCorrectEnabled = !(isName || f.type == "email" || kb != KeyboardType.Text),
                        imeAction = if (f.isLong) ImeAction.Default else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                    isError = err != null, shape = RoundedCornerShape(16.dp),
                    textStyle = LocalTextStyleLarge()
                )
            }
        }
        if (err != null) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LocalTextStyleLarge() = LocalTextStyle.current.copy(fontSize = 19.sp)

@Composable
private fun ChoiceField(vm: AppViewModel, f: Field) {
    var open by remember { mutableStateOf(false) }
    val cur = vm.values[f.id].orEmpty()
    Box {
        Row(
            Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp))
                .border(1.dp, if (vm.errors[f.id] != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { open = true }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(cur.ifBlank { f.placeholder.ifBlank { "Choose" } }, fontSize = 19.sp, color = if (cur.isBlank()) Muted else MaterialTheme.colorScheme.onBackground)
            Text("▾", fontSize = 20.sp, color = Muted)
        }
        DropdownMenu(open, { open = false }) {
            f.options.forEach { o -> DropdownMenuItem(text = { Text(o, fontSize = 18.sp) }, onClick = { vm.setValue(f.id, o); open = false }) }
        }
    }
}

@Composable
private fun DateField(vm: AppViewModel, f: Field) {
    val ctx = LocalContext.current
    val cur = vm.values[f.id].orEmpty()
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (vm.errors[f.id] != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable {
                val c = Calendar.getInstance()
                DatePickerDialog(ctx, { _, y, m, d -> vm.setValue(f.id, String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)) },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Text(cur.ifBlank { f.placeholder.ifBlank { "Pick a date" } }, fontSize = 19.sp, color = if (cur.isBlank()) Muted else MaterialTheme.colorScheme.onBackground) }
}

// ============ outcome ============

@Composable
private fun OutcomeBody(vm: AppViewModel) {
    val o = vm.outcome ?: return
    val offline = o.kind == Outcome.Kind.SAVED_OFFLINE
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (offline) Box(Modifier.size(64.dp).clip(CircleShape).background(Warn), contentAlignment = Alignment.Center) { Text("!", color = WarnInk, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold) }
            else CheckBadge(64)
            Text(o.title, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp)
            Text(o.message, fontSize = 20.sp, color = Muted)
            if (o.pin.isNotBlank()) {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text("Your code", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(o.pin, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                }
            }
            if (offline && vm.queue.isNotEmpty())
                Text("${vm.queue.size} registration${if (vm.queue.size == 1) "" else "s"} waiting to send", fontWeight = FontWeight.SemiBold, color = Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton(L_DONE, { vm.resetAll() }, Modifier.weight(1f), height = 72)
            if (offline) BigButton("Send now", { vm.flushQueue() }, primary = false, height = 72)
        }
        Text("The form clears for the next person in ${vm.countdown} seconds", color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center)
    }
}

// ============ setup / staff ============

@Composable
private fun SetupScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("EazyTagg Register", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Set this tablet up once. Staff only.", color = Muted, fontSize = 17.sp)
        OutlinedTextField(vm.serverInput, { vm.serverInput = it }, Modifier.fillMaxWidth(), label = { Text("Server address") },
            placeholder = { Text("192.168.0.120/eazytagg_test") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(vm.codeInput, { vm.codeInput = it }, Modifier.fillMaxWidth(), label = { Text("Event code") }, singleLine = true)
        if (vm.setupMessage.isNotEmpty()) Text(vm.setupMessage, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        BigButton(if (vm.setupBusy) "Connecting…" else "Save and start", { vm.saveSetup() }, Modifier.fillMaxWidth(), enabled = !vm.setupBusy)
    }
}

@Composable
private fun GateScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
        Text("Staff PIN", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        PinDots(vm.gatePin.length)
        if (vm.gateError.isNotEmpty()) Text(vm.gateError, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        Box(Modifier.widthIn(max = 360.dp)) { Keypad(vm::gateKey, vm::gateDelete, keyH = 60) }
        BigButton("Cancel", { vm.gateCancel() }, primary = false, height = 52)
    }
}

@Composable
private fun SettingRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(sub, color = Muted, fontSize = 14.sp)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun StaffScreen(vm: AppViewModel) {
    val ev = vm.event
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Staff settings", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            BigButton("Back to kiosk", { vm.leaveStaff() }, height = 52, radius = 26)
        }
        HorizontalDivider()
        Text("Form from the system", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(ev?.let { "${it.title.ifBlank { it.code }} · ${it.fields.size} fields" } ?: "Not loaded", color = Muted)
        Text(if (vm.lastRefreshed > 0) "Last updated ${fmt.format(Date(vm.lastRefreshed))}" else "Never updated", color = Muted)
        if (vm.refreshNote.isNotEmpty()) Text(vm.refreshNote, color = Muted, fontSize = 14.sp)
        BigButton(if (vm.refreshing) "Updating…" else "Refresh now", { vm.refresh() }, enabled = !vm.refreshing, height = 56, radius = 28)
        ev?.let { e ->
            Text("Fields (name · type)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            e.fields.forEach { f ->
                Text("${f.name.take(60)} · ${f.type}${if (f.required) " · required" else ""}${if (f.isStatic) " · shown as text" else ""}${if (f.unsupported) " · not supported here" else ""}",
                    color = Muted, fontSize = 14.sp)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Kiosk", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        SettingRow("Ask if they preregistered first", "Pop-up with QR code or PIN check-in", vm.askPrereg, vm::updateAskPrereg)
        SettingRow("Test mode", "Registrations are marked as tests", vm.testMode, vm::updateTestMode)
        SettingRow("Dark screen", "Dark colours instead of light", vm.darkTheme, vm::updateDark)
        Text("Clear the form after ${vm.resetSeconds} seconds", fontWeight = FontWeight.Bold)
        Slider(vm.resetSeconds.toFloat(), { vm.updateResetSeconds(it.toInt()) }, valueRange = 5f..30f)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Waiting to send (${vm.queue.size})", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        if (vm.syncNote.isNotEmpty()) Text(vm.syncNote, color = Muted, fontSize = 14.sp)
        vm.queue.forEach { op ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(op.label, fontWeight = FontWeight.SemiBold)
                    Text(if (op.failed) "Refused: ${op.error}" else "Waiting", color = if (op.failed) MaterialTheme.colorScheme.error else Muted, fontSize = 13.sp)
                }
                if (op.failed) TextButton({ vm.retryFailed(op) }) { Text("Retry") }
                TextButton({ vm.discard(op) }) { Text("Discard") }
            }
        }
        if (vm.queue.any { !it.failed }) BigButton(if (vm.syncing) "Sending…" else "Send now", { vm.flushQueue() }, enabled = !vm.syncing, height = 52, radius = 26)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Server and event", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        OutlinedTextField(vm.serverInput, { vm.serverInput = it }, Modifier.fillMaxWidth(), label = { Text("Server address") }, singleLine = true)
        OutlinedTextField(vm.codeInput, { vm.codeInput = it }, Modifier.fillMaxWidth(), label = { Text("Event code") }, singleLine = true)
        if (vm.setupMessage.isNotEmpty()) Text(vm.setupMessage, color = MaterialTheme.colorScheme.error)
        BigButton(if (vm.setupBusy) "Connecting…" else "Save server settings", { vm.saveSetup() }, enabled = !vm.setupBusy, height = 52, radius = 26)
        Spacer(Modifier.height(30.dp))
    }
}
