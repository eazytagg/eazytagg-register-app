package com.eazytagg.register.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazytagg.register.AppViewModel
import com.eazytagg.register.Screen
import com.eazytagg.register.data.Field

@Composable
fun Root(vm: AppViewModel) {
    when (vm.screen) {
        Screen.SignIn -> SignInScreen(vm)
        Screen.EventCode -> EventCodeScreen(vm)
        Screen.Dashboard -> DashboardScreen(vm)
        Screen.Register -> RegisterScreen(vm)
        Screen.Sync -> SyncScreen(vm)
        Screen.Activity -> ActivityScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
    }
}

private fun pendingCount(vm: AppViewModel) = vm.queue.count { !it.failed }
private fun offlineBanner(vm: AppViewModel): String? {
    val n = pendingCount(vm)
    return when {
        !vm.online && n > 0 -> "Offline - $n waiting to sync"
        !vm.online -> "Offline - actions are saved and sent later"
        else -> null
    }
}

// ---------------------------------------------------------------- sign in
@Composable
fun SignInScreen(vm: AppViewModel) {
    Page(title = "EazyTagg Register", subtitle = "Registration desk sign in") {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.serverUrl, onValueChange = { vm.serverUrl = it; vm.clearMsg() },
            label = { Text("Server address") },
            placeholder = { Text("e.g. 192.168.1.20/eazytagg") },
            supportingText = { Text("The address of your EazyTagg server, from the admin.") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        OutlinedTextField(
            value = vm.email, onValueChange = { vm.email = it; vm.clearMsg() },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = vm.password, onValueChange = { vm.password = it; vm.clearMsg() },
            label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        MessageText(vm.message, vm.messageIsError)
        Button(onClick = { vm.signIn() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Sign in", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { vm.testConnection() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Test connection")
        }
    }
}

// ---------------------------------------------------------------- event code
@Composable
fun EventCodeScreen(vm: AppViewModel) {
    Page(
        title = "Event", subtitle = "Signed in as ${vm.agentName}",
        onBack = if (vm.stack.size > 1) ({ vm.back() }) else null,
        banner = offlineBanner(vm),
        actions = { TextButton(onClick = { vm.signOut() }) { Text("Sign out") } }
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Enter the event code", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("You get the code from the event organizer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = vm.eventCodeInput, onValueChange = { vm.eventCodeInput = it; vm.clearMsg() },
            label = { Text("Event code") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        MessageText(vm.message, vm.messageIsError)
        Button(onClick = { vm.openEvent() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Open event", fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- dashboard
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val ev = vm.event
    val pending = pendingCount(vm)
    Page(
        title = if (ev == null) "Event" else ev.title.ifBlank { "Event ${ev.code}" },
        subtitle = if (ev == null) null else "Code ${ev.code} · ${vm.agentName}",
        banner = offlineBanner(vm),
        actions = { TextButton(onClick = { vm.go(Screen.Settings) }) { Text("Settings") } }
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Registered on this device today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("${vm.registeredToday}", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Button(onClick = { vm.startRegister() }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("Register new attendee", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { vm.go(Screen.Sync) }, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(if (pending > 0) "Sync ($pending)" else "Sync")
            }
            OutlinedButton(onClick = { vm.go(Screen.Activity) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Activity") }
            OutlinedButton(onClick = { vm.changeEvent() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Event") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- register
@Composable
fun RegisterScreen(vm: AppViewModel) {
    val ev = vm.event
    Page(title = "Register new attendee", subtitle = "Walk-in", onBack = { vm.back() }, banner = offlineBanner(vm)) {
        if (ev == null) { Text("No event open."); return@Page }
        val done = vm.regDone
        if (done != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Text(done, modifier = Modifier.padding(20.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(onClick = { vm.nextRegister() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Register another", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done") }
            return@Page
        }
        ev.fields.filter { it.editable }.forEach { f -> FieldEditor(vm, f) }
        if (ev.consentPopup) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.regConsent = !vm.regConsent }) {
                Checkbox(checked = vm.regConsent, onCheckedChange = { vm.regConsent = it })
                Text(ev.consentText.ifBlank { "The attendee agrees to the consent notice." }, fontSize = 13.sp)
            }
        }
        MessageText(vm.message, vm.messageIsError)
        Button(onClick = { vm.saveRegister() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Register & check in", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FieldEditor(vm: AppViewModel, f: Field) {
    val label = f.name + if (f.required) " *" else ""
    when {
        f.isMulti -> {
            Text(label, fontWeight = FontWeight.SemiBold)
            val chosen = vm.editMulti[f.id].orEmpty()
            f.options.forEach { opt ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    vm.editMulti[f.id] = if (opt in chosen) chosen - opt else chosen + opt
                }) {
                    Checkbox(checked = opt in chosen, onCheckedChange = { vm.editMulti[f.id] = if (it) chosen + opt else chosen - opt })
                    Text(opt)
                }
            }
        }
        f.isChoice -> {
            var open by remember { mutableStateOf(false) }
            Text(label, fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(vm.editValues[f.id].orEmpty().ifBlank { "Choose..." }, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    f.options.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt) }, onClick = { vm.editValues[f.id] = opt; open = false })
                    }
                }
            }
        }
        else -> OutlinedTextField(
            value = vm.editValues[f.id].orEmpty(), onValueChange = { vm.editValues[f.id] = it; vm.clearMsg() },
            label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
            singleLine = f.type != "textarea",
            keyboardOptions = KeyboardOptions(
                keyboardType = when (f.type) {
                    "email" -> KeyboardType.Email
                    "phone" -> KeyboardType.Phone
                    "number" -> KeyboardType.Number
                    else -> KeyboardType.Text
                }
            )
        )
    }
}

// ---------------------------------------------------------------- sync
@Composable
fun SyncScreen(vm: AppViewModel) {
    Page(title = "Sync", subtitle = if (vm.online) "Online" else "Offline", onBack = { vm.back() }) {
        val pending = vm.queue.count { !it.failed }
        val failed = vm.queue.count { it.failed }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Waiting to send", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("$pending", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                if (failed > 0) Text("$failed need attention", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
        if (vm.syncNote.isNotBlank()) Text(vm.syncNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { vm.networkBack() }, enabled = !vm.syncing && pending > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (vm.syncing) "Sending..." else "Send now", fontWeight = FontWeight.Bold)
        }
        if (vm.queue.isEmpty()) Text("Everything has been sent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.queue.forEach { op ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, if (op.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(op.label, fontWeight = FontWeight.SemiBold)
                    Text("${timeText(op.at)} · ${op.agent}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (op.failed) {
                        Text(op.error.ifBlank { "The server refused this." }, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.retryFailed(op) }) { Text("Try again") }
                            TextButton(onClick = { vm.discard(op) }) { Text("Discard") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- activity
@Composable
fun ActivityScreen(vm: AppViewModel) {
    Page(
        title = "Activity", subtitle = "Everything done on this device", onBack = { vm.back() },
        actions = { TextButton(onClick = { vm.clearActivity() }) { Text("Clear") } }
    ) {
        val st = LocalStatus.current
        if (vm.activity.isEmpty()) Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.activity.asReversed().take(200).forEach { a ->
            val (bg, fg) = when (a.result) {
                "ok", "synced" -> st.okBg to st.okFg
                "error" -> st.errBg to st.errFg
                else -> st.warnBg to st.warnFg
            }
            Column(Modifier.fillMaxWidth()) {
                SpaceBetween {
                    Text(a.action, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Pill(a.result, bg, fg)
                }
                if (a.who.isNotBlank()) Text(a.who)
                Text("${timeText(a.at)} · ${a.agent}" + if (a.detail.isNotBlank()) " · ${a.detail}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- settings
@Composable
fun SettingsScreen(vm: AppViewModel) {
    Page(title = "Settings", onBack = { vm.back() }) {
        Text("Appearance", fontWeight = FontWeight.Bold)
        listOf("dark" to "Dark", "light" to "Light", "system" to "Follow the phone").forEach { (k, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.chooseTheme(k) }) {
                RadioButton(selected = vm.theme == k, onClick = { vm.chooseTheme(k) })
                Text(label)
            }
        }
        HorizontalDivider()
        Text("Server", fontWeight = FontWeight.Bold)
        Text(vm.serverUrl.ifBlank { "Not set" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Signed in as", fontWeight = FontWeight.Bold)
        Text("${vm.agentName} (${vm.email})", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        OutlinedButton(onClick = { vm.changeEvent() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Change event") }
        Button(
            onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text("Sign out / change server") }
        Text("EazyTagg Register 0.1.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
