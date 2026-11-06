package com.googlewidget.calendar.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.unit.ColorProvider
import com.googlewidget.calendar.R
import com.googlewidget.calendar.data.WidgetConfigKeys

data class WidgetTypography(
    val titleSize: TextUnit,
    val subtitleSize: TextUnit,
    val headerSize: TextUnit,
    val widgetHeaderSize: TextUnit,
    val badgeSize: TextUnit
)

object WidgetThemeHelper {

    fun getTypography(fontScale: String): WidgetTypography {
        return if (fontScale == WidgetConfigKeys.FONT_SCALE_COMPACT) {
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
    }

    fun getSurfaceColor(themeMode: String, opacity: Float): Color {
        val alpha = opacity.coerceIn(0.0f, 1.0f)
        return when (themeMode) {
            WidgetConfigKeys.THEME_LIGHT -> Color(0.98f, 0.98f, 0.99f, alpha)
            WidgetConfigKeys.THEME_DARK -> Color(0.12f, 0.13f, 0.14f, alpha)
            else -> Color(0.13f, 0.14f, 0.15f, alpha)
        }
    }

    fun getCardBackgroundColor(themeMode: String, opacity: Float): Color {
        val alpha = (opacity * 0.85f).coerceIn(0.0f, 1.0f)
        return when (themeMode) {
            WidgetConfigKeys.THEME_LIGHT -> Color(1.0f, 1.0f, 1.0f, alpha)
            WidgetConfigKeys.THEME_DARK -> Color(0.18f, 0.19f, 0.20f, alpha)
            else -> Color(0.18f, 0.19f, 0.20f, alpha)
        }
    }

    fun getPrimaryTextColor(themeMode: String): ColorProvider {
        return when (themeMode) {
            WidgetConfigKeys.THEME_LIGHT -> ColorProvider(Color(0xFF202124))
            WidgetConfigKeys.THEME_DARK -> ColorProvider(Color(0xFFE8EAED))
            else -> ColorProvider(R.color.text_primary)
        }
    }

    fun getSecondaryTextColor(themeMode: String): ColorProvider {
        return when (themeMode) {
            WidgetConfigKeys.THEME_LIGHT -> ColorProvider(Color(0xFF5F6368))
            WidgetConfigKeys.THEME_DARK -> ColorProvider(Color(0xFF9AA0A6))
            else -> ColorProvider(R.color.text_secondary)
        }
    }

    fun getAccentColor(themeMode: String): ColorProvider {
        return when (themeMode) {
            WidgetConfigKeys.THEME_LIGHT -> ColorProvider(Color(0xFF1A73E8))
            WidgetConfigKeys.THEME_DARK -> ColorProvider(Color(0xFF8AB4F8))
            else -> ColorProvider(R.color.accent_color)
        }
    }

    fun getDividerColor(themeMode: String): ColorProvider {
        return when (themeMode) {
            WidgetConfigKeys.THEME_LIGHT -> ColorProvider(Color(0xFFE0E0E0))
            WidgetConfigKeys.THEME_DARK -> ColorProvider(Color(0xFF3C4043))
            else -> ColorProvider(R.color.divider_color)
        }
    }
}
