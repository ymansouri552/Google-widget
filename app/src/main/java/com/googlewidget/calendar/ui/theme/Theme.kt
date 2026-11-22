package com.googlewidget.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.googlewidget.calendar.data.ThemePreferences

/** The theme driving both widgets and companion app, available to every screen. */
val LocalCalendarTheme = compositionLocalOf<CalendarTheme> {
    CalendarThemes.resolve(null, false)
}

private fun mix(from: Color, toward: Color, fraction: Float): Color = Color(
    red = from.red + (toward.red - from.red) * fraction,
    green = from.green + (toward.green - from.green) * fraction,
    blue = from.blue + (toward.blue - from.blue) * fraction,
    alpha = 1f
)

/**
 * Maps a widget theme onto Material 3 roles. The chip and date-circle pairs are
 * already contrast-checked inside the widget, so they become the app's accent
 * and tonal container rather than inventing a second palette.
 */
internal fun colorSchemeFrom(theme: CalendarTheme, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val background = theme.bg.copy(alpha = 1f)
    val accent = theme.todayCircleBg
    val onAccent = theme.todayCircleText
    val container = theme.chipBg.copy(alpha = 1f)
    val onContainer = theme.chipText
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
        surface = background,
        onSurface = theme.text,
        surfaceVariant = mix(background, theme.sub, if (dark) 0.14f else 0.07f),
        onSurfaceVariant = theme.sub,
        background = background,
        onBackground = theme.text,
        surfaceTint = accent,
        outline = theme.sub,
        outlineVariant = mix(background, theme.sub, 0.28f)
    )
}

@Composable
fun GoogleWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val selection by ThemePreferences.selection.collectAsState()
    LaunchedEffect(Unit) { ThemePreferences.ensureLoaded(context) }

    val theme = CalendarThemes.resolve(selection.themeId, darkTheme)
    val dark = when {
        CalendarThemes.isAuto(selection.themeId) -> darkTheme
        theme.id == CalendarThemes.MIDNIGHT -> true
        theme.id == CalendarThemes.NEON_PURPLE -> true
        else -> false
    }

    CompositionLocalProvider(LocalCalendarTheme provides theme) {
        MaterialTheme(
            colorScheme = colorSchemeFrom(theme, dark),
            typography = Typography,
            content = content
        )
    }
}
