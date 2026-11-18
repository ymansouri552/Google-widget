package com.googlewidget.calendar.widget

import androidx.compose.ui.graphics.Color
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.ui.theme.CalendarThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetEventColorsTest {

    private fun event(color: Int) = CalendarEvent(
        id = 1L,
        eventId = 1L,
        title = "Lunchtime",
        begin = 0L,
        end = 1L,
        allDay = false,
        color = color,
        location = null
    )

    @Test
    fun paletteUsesTheResolvedEventColorForBothWidgets() {
        // CalendarRepository already resolves DISPLAY_COLOR -> CALENDAR_COLOR,
        // so the shared resolver must use that exact value as its full color.
        val color = 0xFFD85A30.toInt()
        val soft = WidgetEventColors.palette(event(color), CalendarThemes.softPastel)
        val midnight = WidgetEventColors.palette(event(color), CalendarThemes.midnight)

        assertEquals(Color(color), soft.full)
        assertEquals(soft.full, midnight.full)
    }

    @Test
    fun tintIsALighterBlendTowardTheWidgetSurface() {
        val full = Color(0xFF1A73E8)
        val theme = CalendarThemes.softPastel
        val palette = WidgetEventColors.palette(event(0xFF1A73E8.toInt()), theme)

        assertTrue("tint red must be lighter than the full color", palette.tint.red > full.red)
        assertTrue("tint green must be lighter than the full color", palette.tint.green > full.green)
        assertEquals(theme.bg.alpha, palette.tint.alpha, 0.001f)
    }

    @Test
    fun tintStrengthZeroGivesTheSurfaceAndOneGivesTheEventColor() {
        val full = Color(0xFF112233)
        val surface = CalendarThemes.midnight.bg

        assertEquals(surface, WidgetEventColors.blend(full, surface, 0f))
        assertEquals(full, WidgetEventColors.blend(full, surface, 1f))
    }

    @Test
    fun inkFlipsForBrightAndDarkBackgrounds() {
        assertEquals(Color(0xFF202124), WidgetEventColors.readableOn(Color.White))
        assertEquals(Color.White, WidgetEventColors.readableOn(Color.Black))
    }
}
