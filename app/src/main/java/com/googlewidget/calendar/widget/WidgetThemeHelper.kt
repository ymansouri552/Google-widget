package com.googlewidget.calendar.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.unit.ColorProvider
import com.googlewidget.calendar.data.ThemePreferences
import com.googlewidget.calendar.data.ThemeSelection
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.ui.theme.CalendarTheme
import com.googlewidget.calendar.ui.theme.CalendarThemes

data class WidgetTypography(
    val titleSize: TextUnit,
    val subtitleSize: TextUnit,
    val headerSize: TextUnit,
    val widgetHeaderSize: TextUnit,
    val badgeSize: TextUnit
)

/**
 * Shared by all four widget types so the app-wide theme picker colors every
 * view, not just the 3-Day Timeline. Every color comes from a [CalendarTheme]
 * token; there are no per-widget color literals.
 */
object WidgetThemeHelper {

    fun getTypography(fontScale: String): WidgetTypography =
        if (fontScale == WidgetConfigKeys.FONT_SCALE_COMPACT) {
            WidgetTypography(
                titleSize = 13.sp,
                subtitleSize = 11.sp,
                headerSize = 12.sp,
                widgetHeaderSize = 14.sp,
                badgeSize = 10.sp
            )
        } else {
            WidgetTypography(
                titleSize = 15.sp,
                subtitleSize = 13.sp,
                headerSize = 14.sp,
                widgetHeaderSize = 16.sp,
                badgeSize = 11.sp
            )
        }

    /**
     * Resolves the app-wide theme and observes it inside the composition, so a
     * still-alive Glance session repaints the moment the user switches themes
     * or the system flips between light and dark (Auto).
     */
    @Composable
    fun currentCalendarTheme(context: Context): CalendarTheme {
        ThemePreferences.ensureLoaded(context)
        val selection by ThemePreferences.selection.collectAsState(
            initial = ThemeSelection(ThemePreferences.getSelectedId(context))
        )
        val systemDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return CalendarThemes.resolve(selection.themeId, systemDark)
    }

    /** The theme surface with the per-widget background-opacity slider applied. */
    fun getSurfaceColor(theme: CalendarTheme, opacity: Float): Color =
        theme.bg.copy(alpha = theme.bg.alpha * opacity.coerceIn(0f, 1f))

    /** A raised card on top of the surface; slightly softer than the surface. */
    fun getCardBackgroundColor(theme: CalendarTheme, opacity: Float): Color =
        theme.chipBg.copy(alpha = theme.chipBg.alpha * (opacity * 0.85f).coerceIn(0f, 1f))

    fun getPrimaryTextColor(theme: CalendarTheme): ColorProvider = ColorProvider(theme.text)
    fun getSecondaryTextColor(theme: CalendarTheme): ColorProvider = ColorProvider(theme.sub)

    /** Accent used for the today badge and emphasis; its ink is theme-aware too. */
    fun getAccentColor(theme: CalendarTheme): ColorProvider = ColorProvider(theme.todayCircleBg)
    fun getAccentTextColor(theme: CalendarTheme): ColorProvider = ColorProvider(theme.todayCircleText)

    fun getDividerColor(theme: CalendarTheme): ColorProvider = ColorProvider(theme.grid)
}
