package com.eazytagg.register.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.eazytagg.register.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Button look set from the system's App section. */
data class ButtonStyle(val color: Color? = null, val text: Color? = null, val radius: Int? = null, val height: Int? = null)
val LocalButtonStyle = compositionLocalOf { ButtonStyle() }

/** Big pill button. Primary uses the event accent. */
@Composable
fun BigButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = true, enabled: Boolean = true, height: Int = 68, radius: Int = -1, tint: Color? = null
) {
    val st = LocalButtonStyle.current
    val shape = RoundedCornerShape((if (radius >= 0) radius else (st.radius ?: 34)).dp)
    val accent = tint ?: st.color ?: LocalAccent.current
    val h = if (height >= 68 && st.height != null) st.height else height
    val fg = if (primary) (if (tint != null) Color.White else st.text ?: MaterialTheme.colorScheme.onPrimary) else MaterialTheme.colorScheme.onBackground
    BoxWithConstraints(
        modifier
            .heightIn(min = h.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.background(if (enabled) accent else accent.copy(alpha = 0.4f))
                else Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.4f)), shape)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxWidth < 200.dp
        Text(text, modifier = Modifier.padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 12.dp),
            color = fg, fontSize = if (compact) 18.sp else 20.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CheckBadge(size: Int = 56, color: Color = Ok) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((size * 0.5f).dp)) {
            val p = Path().apply {
                moveTo(size * 0.04f * density, size * 0.26f * density)
                lineTo(size * 0.17f * density, size * 0.39f * density)
                lineTo(size * 0.44f * density, size * 0.11f * density)
            }
            drawPath(p, Color.White, style = Stroke(width = 3.2f * density, cap = StrokeCap.Round))
        }
    }
}

/** 0-9 keypad for the 4-digit code and staff PIN. */
@Composable
fun Keypad(onKey: (String) -> Unit, onDelete: () -> Unit, keyH: Int = 72) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                r.forEach { k ->
                    if (k.isEmpty()) Spacer(Modifier.weight(1f).height(keyH.dp))
                    else Box(
                        Modifier.weight(1f).height(keyH.dp).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { if (k == "<") onDelete() else onKey(k) },
                        contentAlignment = Alignment.Center
                    ) { Text(if (k == "<") "Delete" else k, fontSize = if (k == "<") 16.sp else 28.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun PinDots(count: Int, total: Int = 4) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(total) { i ->
            val filled = i < count
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                    .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
            )
        }
    }
}

/** Finger-drawn signature. Reports a base64 JPEG when a stroke ends, or "" when cleared. */
@Composable
fun SignaturePad(onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }

    fun export(): String {
        if (strokes.isEmpty()) return ""
        val bmp = Bitmap.createBitmap(padSize.width.coerceAtLeast(1), padSize.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f; isAntiAlias = true; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        strokes.forEach { s ->
            val p = android.graphics.Path()
            s.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
            if (s.size == 1) c.drawPoint(s[0].x, s[0].y, paint) else c.drawPath(p, paint)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))
                .background(Color.White).border(2.dp, Line, RoundedCornerShape(16.dp))
        ) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> change.consume(); current = current + change.position },
                            onDragEnd = { if (current.isNotEmpty()) { strokes.add(current); current = emptyList(); onChange(export()) } },
                            onDragCancel = { current = emptyList() }
                        )
                    }
            ) {
                padSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt())
                (strokes.toList() + listOf(current)).forEach { s ->
                    if (s.size > 1) {
                        val p = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(p, Color(0xFF16181D), style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }
                }
            }
            if (strokes.isEmpty() && current.isEmpty())
                Text("Sign here with your finger", Modifier.align(Alignment.Center), color = Color(0xFF9AA0AE), fontSize = 16.sp)
        }
        TextButton(onClick = { strokes.clear(); current = emptyList(); onChange("") }) { Text("Clear signature") }
    }
}

/** Company wordmark is separate from the launcher icon and event artwork. */
@Composable
internal fun PoweredByLogo(onDark: Boolean = false, onClick: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (onDark || LocalDark.current) 0.95f else 0f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("Powered by", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Image(painterResource(R.drawable.pdynamics_logo), "Popular Dynamics",
            Modifier.width(132.dp).height(42.dp), contentScale = ContentScale.Fit)
    }
}
