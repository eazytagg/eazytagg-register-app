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
import androidx.compose.ui.text.style.TextOverflow
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


private fun prettyDate(d: String): String = try {
    val t = d.trim().take(10)
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(t)!!)
} catch (e: Exception) { d }

private fun String.url(base: String) = if (isBlank()) "" else if (startsWith("http")) this else base + "uploads/" + this

@Composable
fun Root(vm: AppViewModel) {
    val ev = vm.event
    val accent = if (ev == null) DefaultAccent else parseAccent(ev.accent)
    val u = ev?.ui ?: emptyMap()
    val style = ButtonStyle(
        u["btn_color"]?.let { parseAccent(it) }, u["btn_text_color"]?.let { parseAccent(it) },
        u["btn_radius"]?.toIntOrNull()?.coerceIn(0, 40), u["btn_height"]?.toIntOrNull()?.coerceIn(48, 100)
    )
    PDynamicsTheme(accent, vm.darkTheme) {
      CompositionLocalProvider(LocalButtonStyle provides style) {
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
}

/** Full Popular Dynamics wordmark. Three quick taps open the staff settings. */
@Composable
private fun PoweredBy(vm: AppViewModel, onDark: Boolean = false) {
    PoweredByLogo(onDark = onDark, onClick = { vm.logoTap() })
}

// ============ layout shell: banner on the left (tablet) / on top (phone) ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kiosk(vm: AppViewModel) {
    AdaptiveKioskLayout(
        keyboardOpen = WindowInsets.isImeVisible,
        form = vm.screen == Screen.Form,
        forceStack = vm.t("layout", "split") == "stack",
        brand = { compact -> BrandPanel(vm, compact) },
        body = { wide -> KioskBody(vm, wide) },
        footer = { PoweredBy(vm) }
    )
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
    val sub = listOf(prettyDate(ev.dateText), ev.venue).filter { it.isNotBlank() }.joinToString("  ·  ")
    BoxWithConstraints(Modifier.fillMaxSize().background(accent)) {
        val short = maxHeight < if (compact) 90.dp else 600.dp
        if (bg.isNotEmpty()) AsyncImage(bg, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (bg.isNotEmpty()) Box(Modifier.matchParentSize().background(Color(0x66000000)))
        val textColor = if (bg.isNotEmpty()) Color.White else onAccent
        val logoOnly = vm.flag("show_logo", false) && logo.isNotEmpty()
        if (logoOnly) {
            // Logo-only banner. "cover" fills the whole area, "contain" shows the whole logo boxed in the middle.
            val fit = ev.ui["logo_fit"] ?: "auto"
            var ratio by remember(logo) { mutableStateOf(0f) }
            val boxRatio = maxWidth.value / maxHeight.value
            val cover = fit == "cover" || (fit == "auto" && ratio > 0f && kotlin.math.abs(ratio / boxRatio - 1f) <= 0.25f)
            Box(Modifier.matchParentSize().background(if (ev.ui["logo_tile"] == "dark") Color(0xFF111111) else Color.White), contentAlignment = Alignment.Center) {
                AsyncImage(logo, "Event logo",
                    if (cover) Modifier.matchParentSize() else Modifier.fillMaxSize().padding(if (compact) 6.dp else 28.dp),
                    contentScale = if (cover) ContentScale.Crop else ContentScale.Fit,
                    onSuccess = { st -> val d = st.result.drawable; if (d.intrinsicHeight > 0) ratio = d.intrinsicWidth.toFloat() / d.intrinsicHeight })
            }
            if (!vm.online) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { Chip("Offline", Warn, WarnInk) }
            // no visible "Powered by" so the logo fills the area; an invisible hotspot keeps the 3-tap staff entry
            Box(Modifier.align(Alignment.BottomCenter).size(72.dp, 40.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.logoTap() })
        } else if (compact) {
            val artwork = if (ev.ui["artwork_mode"] == "logo") logo.ifBlank { bg } else bg.ifBlank { logo }
            if (artwork.isNotEmpty()) {
                AsyncImage(artwork, "Event artwork", Modifier.matchParentSize(),
                    contentScale = if (ev.ui["artwork_mode"] == "logo") ContentScale.Fit else ContentScale.Crop)
            } else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                if (vm.flag("show_title")) Text(ev.title.ifBlank { "Welcome" }, color = textColor,
                    fontWeight = FontWeight.ExtraBold, fontSize = if (short) 18.sp else 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) Text(sub, color = textColor, fontSize = 12.sp, maxLines = 1)
            }
            if (!vm.online) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { Chip("Offline", Warn, WarnInk) }
        } else {
            Column(Modifier.fillMaxSize().padding(if (short) 24.dp else 36.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    if (logo.isNotEmpty()) Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                        AsyncImage(logo, "Event logo", Modifier.height(if (short) 40.dp else 56.dp).widthIn(max = 180.dp), contentScale = ContentScale.Fit)
                    } else Text("PDynamics", color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    if (!vm.online) Chip("Offline", Warn, WarnInk)
                }
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.flag("show_title")) Text(ev.title.ifBlank { "Welcome" }, color = textColor,
                        fontWeight = FontWeight.ExtraBold, fontSize = if (short) 30.sp else 40.sp,
                        lineHeight = if (short) 34.sp else 44.sp, maxLines = if (short) 3 else 5, overflow = TextOverflow.Ellipsis)
                    if (sub.isNotEmpty()) Text(sub, color = textColor, fontSize = if (short) 15.sp else 18.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (!short && vm.screen == Screen.Form && vm.flag("show_preview")) TagPreview(vm)
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
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { vm.dismissAsk() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 480.dp || maxHeight < 600.dp
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = maxHeight)
                .clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState()).padding(if (compact) 24.dp else 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(vm.t("ask_title", "Did you preregister?"), fontSize = if (compact) 28.sp else 38.sp,
                    lineHeight = if (compact) 33.sp else 42.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text(vm.t("ask_text", "If you did, you can check in with your QR code or your PIN."),
                    fontSize = if (compact) 17.sp else 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(vm.t("ask_no", "No"), { vm.answerNo() }, Modifier.weight(1f), primary = false, height = 56)
                    BigButton(vm.t("ask_yes", "Yes"), { vm.answerYes() }, Modifier.weight(1f), height = 56)
                }
            }
        }
    }
}

@Composable
private fun WelcomeBody(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.height(1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(vm.t("welcome_title", "Welcome."), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp)
            Text(vm.t("welcome_text", "Register here to get your name tag."), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BigButton(vm.t("start_label", "Register"), { vm.start() }, Modifier.fillMaxWidth(), height = 76)
    }
}

@Composable
private fun ChooseBody(vm: AppViewModel) {
    CheckInActions(
        vm.t("choose_title", "Check in"),
        vm.t("choose_text", "Show your QR code to the camera, or type the 4-digit PIN from your invitation."),
        vm.t("scan_label", "Scan my QR code"), vm.t("code_label", "Enter my PIN"), vm.t("back_label", "Back"),
        vm.flag("show_back"), { vm.chooseScan() }, { vm.chooseCode() }, { vm.backToWelcome() }
    )
}

@Composable
private fun ScanBody(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(vm.t("scan_title", "Show your QR code"), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
            CameraScanner(Modifier.fillMaxSize()) { vm.onScanned(it) }
        }
        if (vm.codeMessage.isNotEmpty()) Text(vm.codeMessage, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        if (vm.flag("show_back")) BigButton(vm.t("back_label", "Back"), { vm.backToChoose() }, Modifier.fillMaxWidth(), primary = false, height = 56)
    }
}

@Composable
private fun CodeBody(vm: AppViewModel, wide: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(vm.t("code_title", "Enter your code"), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text(vm.t("code_text", "The 4 digits on your invitation."), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PinDots(vm.codeDigits.length)
        if (vm.codeMessage.isNotEmpty()) Text(vm.codeMessage, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Keypad(vm::codeKey, vm::codeDelete, keyH = if (wide) 72 else 60)
        if (vm.flag("show_back")) BigButton(vm.t("back_label", "Back"), { vm.backToChoose() }, Modifier.fillMaxWidth(), primary = false, height = 56)
    }
}

@Composable
private fun ClosedBody(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.height(1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(vm.t("closed_title", "Registration has closed."), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 46.sp)
            Text(vm.t("closed_text", "Please ask at the desk if you need help."), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    RegistrationFormLayout(
        scroll = scroll, message = vm.formMessage, submitting = vm.submitting,
        submitLabel = if (vm.submitting) vm.t("wait_label", "Please wait…") else vm.t("submit_label", "Submit"),
        onCancel = { vm.cancelRegistration() }, onSubmit = { vm.submit() },
        clearLabel = vm.t("clear_label", "").ifBlank { null },
        onClear = { vm.clearFormFields() },
        clearColor = ev.ui["clear_color"]?.takeIf { it.isNotBlank() }?.let { parseAccent(it) },
        submitColor = ev.ui["submit_color"]?.takeIf { it.isNotBlank() }?.let { parseAccent(it) },
        cancelLabel = vm.t("cancel_label", "Cancel")
    ) {
        Text(vm.t("form_title", "Your details"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        key(vm.formVersion) {
            val fields = ev.fields.filter { vm.visible(it) }
            var i = 0
            while (i < fields.size) {
                val f = fields[i]
                val next = fields.getOrNull(i + 1)
                val firstName = f.name.replace(" ", "").equals("firstname", true)
                val lastName = next?.name?.replace(" ", "")?.let { it.equals("lastname", true) || it.equals("surname", true) } == true
                if (firstName && lastName && next != null && !f.isLong && !next.isLong) {
                    key(f.id, next.id) {
                        Row(Modifier.fillMaxWidth().onGloballyPositioned { p ->
                            tops[f.id] = p.positionInParent().y.toInt(); tops[next.id] = p.positionInParent().y.toInt()
                        }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FieldView(vm, f, Modifier.weight(1f))
                            FieldView(vm, next, Modifier.weight(1f))
                        }
                    }
                    i += 2
                } else {
                    key(f.id) { FieldView(vm, f, Modifier.onGloballyPositioned { tops[f.id] = it.positionInParent().y.toInt() }) }
                    i++
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (vm.showConsent) ConsentDialog(vm, ev)
}

@Composable
private fun ConsentDialog(vm: AppViewModel, ev: EventInfo) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(vm.t("consent_title", "Before you register"), fontWeight = FontWeight.ExtraBold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(ev.consentText.ifBlank { "May the organizer send you email campaigns?" }); Spacer(Modifier.height(12.dp)); Text("Selecting No still completes your registration. You will receive your confirmation and e-ticket, but will not be included in email campaigns.") } },
        confirmButton = { TextButton(onClick = { vm.agree() }) { Text(vm.t("agree_label", "Yes — allow campaign emails"), fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onClick = { vm.decline() }) { Text(vm.t("decline_label", "No — continue without campaign emails")) } }
    )
}

@Composable
private fun FieldLabel(f: Field) {
    Text(f.name.uppercase(Locale.getDefault()) + if (f.required) " *" else "", fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldView(vm: AppViewModel, f: Field, modifier: Modifier = Modifier) {
    if (f.unsupported) return
    if (f.isStatic) {
        Text(f.name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = if (f.type == "text_center") TextAlign.Center else if (f.type == "text_right") TextAlign.End else TextAlign.Start, modifier = modifier.fillMaxWidth())
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
                val capSetting = vm.event?.ui?.get("autocap")
                val caps = when {
                    capSetting == "none" -> KeyboardCapitalization.None
                    capSetting == "sentences" && !(f.type == "email" || kb == KeyboardType.Number || kb == KeyboardType.Phone) -> KeyboardCapitalization.Sentences
                    f.type == "email" || kb == KeyboardType.Number || kb == KeyboardType.Phone -> KeyboardCapitalization.None
                    f.isLong -> KeyboardCapitalization.Sentences
                    else -> KeyboardCapitalization.Words   // names, organisations, titles: every word starts with a capital
                }
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                TextField(
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
                    isError = err != null, shape = RoundedCornerShape(0.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent, errorContainerColor = Color.Transparent),
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
            Text("▾", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(o.message, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (o.pin.isNotBlank() && vm.flag("show_pin")) {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(vm.t("pin_label", "Your code"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(o.pin, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                }
            }
            if (offline && vm.queue.isNotEmpty())
                Text("${vm.queue.size} registration${if (vm.queue.size == 1) "" else "s"} waiting to send", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton(vm.t("done_label", "Done"), { vm.resetAll() }, Modifier.weight(1f), height = 72)
            if (offline) BigButton("Send now", { vm.flushQueue() }, primary = false, height = 72)
        }
        Text(vm.t("countdown_text", "The form clears for the next person in {seconds} seconds", "seconds" to vm.countdown.toString()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center)
    }
}

// ============ setup / staff ============

@Composable
private fun SetupScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("PDynamics Registration", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Enter the server computer’s IP address and the event code. Connect this tablet to the same network.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
        OutlinedTextField(vm.serverInput, { vm.serverInput = it }, Modifier.fillMaxWidth(), label = { Text("Server IP address") },
            placeholder = { Text("192.168.0.39") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
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
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
        Text(ev?.let { "${it.title.ifBlank { it.code }} · ${it.fields.size} fields" } ?: "Not loaded", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (vm.lastRefreshed > 0) "Last updated ${fmt.format(Date(vm.lastRefreshed))}" else "Never updated", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (vm.refreshNote.isNotEmpty()) Text(vm.refreshNote, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        BigButton(if (vm.refreshing) "Updating…" else "Refresh now", { vm.refresh() }, enabled = !vm.refreshing, height = 56, radius = 28)
        ev?.let { e ->
            Text("Fields (name · type)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            e.fields.forEach { f ->
                Text("${f.name.take(60)} · ${f.type}${if (f.required) " · required" else ""}${if (f.isStatic) " · shown as text" else ""}${if (f.unsupported) " · not supported here" else ""}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
        if (vm.syncNote.isNotEmpty()) Text(vm.syncNote, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
        OutlinedTextField(vm.serverInput, { vm.serverInput = it }, Modifier.fillMaxWidth(), label = { Text("Server IP address") }, placeholder = { Text("192.168.0.39") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(vm.codeInput, { vm.codeInput = it }, Modifier.fillMaxWidth(), label = { Text("Event code") }, singleLine = true)
        if (vm.setupMessage.isNotEmpty()) Text(vm.setupMessage, color = MaterialTheme.colorScheme.error)
        BigButton(if (vm.setupBusy) "Connecting…" else "Save server settings", { vm.saveSetup() }, enabled = !vm.setupBusy, height = 52, radius = 26)
        Spacer(Modifier.height(30.dp))
    }
}
