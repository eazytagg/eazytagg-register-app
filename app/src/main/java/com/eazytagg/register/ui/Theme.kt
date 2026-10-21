package com.eazytagg.register.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF16181D)
val Muted = Color(0xFF5B6070)
val Soft = Color(0xFFF2F3F5)
val Line = Color(0xFFE3E5EA)
val Ok = Color(0xFF1E8E5A)
val Warn = Color(0xFFFFF1D6)
val WarnInk = Color(0xFF7A4A00)
val Bad = Color(0xFFB3261E)

val DefaultAccent = Color(0xFF009FE3)

/** Accent colour comes from the event's colour in the system. */
val LocalAccent = staticCompositionLocalOf { DefaultAccent }
val LocalDark = staticCompositionLocalOf { false }

fun parseAccent(s: String): Color = try {
    val t = s.trim().let { if (it.startsWith("#")) it else "#$it" }
    Color(android.graphics.Color.parseColor(t))
} catch (e: Exception) { DefaultAccent }

fun Color.isLight(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) > 0.6

@Composable
fun PDynamicsTheme(accent: Color, dark: Boolean, content: @Composable () -> Unit) {
    val onAccent = if (accent.isLight()) Ink else Color.White
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, background = Color(0xFF121315), onBackground = Color.White,
        surface = Color(0xFF1B1D21), onSurface = Color.White, surfaceVariant = Color(0xFF2B2E34),
        onSurfaceVariant = Color(0xFFA3A7B0), error = Color(0xFFFF6B7D), outline = Color(0xFF4A4E57)
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, background = Color.White, onBackground = Ink,
        surface = Color.White, onSurface = Ink, surfaceVariant = Soft,
        onSurfaceVariant = Muted, error = Bad, outline = Line
    )
    CompositionLocalProvider(LocalAccent provides accent, LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
