package com.eazytagg.register.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object KioskLayoutPolicy {
    fun sideBySide(width: Float, height: Float) = width >= 840f && width > height
    fun headerHeight(height: Float, form: Boolean): Float =
        (if (height < 500f) 72f else if (form) 108f else 136f).coerceAtMost(height * 0.24f)
}

@Composable
internal fun RegistrationFormLayout(
    scroll: ScrollState,
    message: String,
    submitting: Boolean,
    submitLabel: String,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    clearLabel: String? = null,
    onClear: () -> Unit = {},
    clearColor: Color? = null,
    submitColor: Color? = null,
    cancelLabel: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(cancelLabel, onCancel, Modifier.weight(1f), primary = false, enabled = !submitting, height = 56)
            if (clearLabel != null) BigButton(clearLabel, onClear, Modifier.weight(1f), enabled = !submitting, height = 56, tint = clearColor ?: Color(0xFFD64545))
            BigButton(submitLabel, onSubmit, Modifier.weight(1f), enabled = !submitting, height = 56, tint = submitColor)
        }
    }
}

/** The portrait header is explicitly bounded so it cannot consume the form's space. */
@Composable
internal fun AdaptiveKioskLayout(
    form: Boolean,
    modifier: Modifier = Modifier,
    forceStack: Boolean = false,
    keyboardOpen: Boolean = false,
    brand: @Composable (compact: Boolean) -> Unit,
    body: @Composable (wide: Boolean) -> Unit,
    footer: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        // With the keyboard open the form needs the full width, so the side panel folds into a thin top strip.
        val keyboardUp = form && keyboardOpen
        val wide = !forceStack && !keyboardUp && KioskLayoutPolicy.sideBySide(viewportWidth.value, viewportHeight.value)
        if (wide) {
            val pad = if (maxWidth >= 1200.dp) 40.dp else 24.dp
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.36f).fillMaxHeight()) { brand(false) }
                Box(Modifier.weight(0.64f).fillMaxHeight().padding(horizontal = pad, vertical = 24.dp)) { body(true) }
            }
        } else {
            val short = maxHeight < 500.dp
            Column(Modifier.fillMaxSize()) {
                val head = if (keyboardUp) KioskLayoutPolicy.headerHeight(viewportHeight.value, form).coerceAtMost(56f) else KioskLayoutPolicy.headerHeight(viewportHeight.value, form)
                Box(Modifier.fillMaxWidth().height(head.dp)) { brand(true) }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (viewportWidth < 400.dp) 16.dp else 24.dp, vertical = if (short) 8.dp else 18.dp)) { body(false) }
                if (!keyboardUp) Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) { footer() }
            }
        }
    }
}

@Composable
internal fun CheckInActions(title: String, text: String, scan: String, pin: String, back: String, showBack: Boolean, onScan: () -> Unit, onPin: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(scan, onScan, Modifier.fillMaxWidth(), height = 60)
                BigButton(pin, onPin, Modifier.fillMaxWidth(), primary = false, height = 60)
                if (showBack) BigButton(back, onBack, Modifier.fillMaxWidth(), primary = false, height = 52)
            }
        }
    }
}
