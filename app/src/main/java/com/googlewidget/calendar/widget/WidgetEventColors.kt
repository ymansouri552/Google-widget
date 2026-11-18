package com.googlewidget.calendar.widget

import androidx.compose.ui.graphics.Color
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.ui.theme.CalendarTheme

/** The colors both calendar widgets use for one event. */
data class WidgetEventPalette(
    /** Event display color, falling back to the calendar color in the repository. */
    val full: Color,
    /** A light tint of [full] over the widget surface, used as a row fill. */
    val tint: Color,
    /** Ink that stays readable on [tint]. */
    val onTint: Color,
    /** Ink that stays readable on [full]. */
    val onFull: Color
)

/**
 * Single source of event color for the Timeline and the 7-day widget.
 * [CalendarEvent.color] is already resolved by the repository as
 * DISPLAY_COLOR -> CALENDAR_COLOR -> default, so neither widget re-decides it.
 */
object WidgetEventColors {

    const val DEFAULT_TINT_STRENGTH = 0.16f

    private val DARK_INK = Color(0xFF202124)
    private val LIGHT_INK = Color(0xFFFFFFFF)

    fun palette(
        event: CalendarEvent,
        theme: CalendarTheme,
        tintStrength: Float = DEFAULT_TINT_STRENGTH
    ): WidgetEventPalette {
        val full = Color(event.color)
        val tint = blend(full, theme.bg, tintStrength)
        return WidgetEventPalette(
            full = full,
            tint = tint,
            onTint = readableOn(tint),
            onFull = readableOn(full)
        )
    }

    /** Mixes [foreground] into [background], keeping the background alpha. */
    fun blend(foreground: Color, background: Color, strength: Float): Color {
        val amount = strength.coerceIn(0f, 1f)
        return Color(
            red = background.red + (foreground.red - background.red) * amount,
            green = background.green + (foreground.green - background.green) * amount,
            blue = background.blue + (foreground.blue - background.blue) * amount,
            alpha = background.alpha
        )
    }

    /** Coarse bright/dark split, enough for a coarse ink choice. */
    fun readableOn(background: Color): Color {
        val brightness = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
        return if (brightness > 0.6f) DARK_INK else LIGHT_INK
    }
}
