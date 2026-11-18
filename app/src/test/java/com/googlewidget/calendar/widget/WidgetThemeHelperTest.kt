package com.googlewidget.calendar.widget

import com.googlewidget.calendar.ui.theme.CalendarThemes
import com.googlewidget.calendar.ui.theme.EventFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetThemeHelperTest {

    @Test
    fun appWideTheme_autoFollowsSystemNightMode() {
        assertEquals(CalendarThemes.glass, CalendarThemes.resolve(CalendarThemes.AUTO, false))
        assertEquals(CalendarThemes.midnight, CalendarThemes.resolve(CalendarThemes.AUTO, true))
        assertEquals(
            CalendarThemes.softPastel,
            CalendarThemes.resolve(CalendarThemes.SOFT_PASTEL, true)
        )
    }

    @Test
    fun surfaceColor_appliesPerWidgetOpacityOnTopOfThemeAlpha() {
        // Compose stores alpha as 8-bit, so allow one step of quantization.
        assertEquals(
            0.5f,
            WidgetThemeHelper.getSurfaceColor(CalendarThemes.softPastel, 0.5f).alpha,
            1f / 255f
        )
        assertEquals(
            CalendarThemes.glass.bg.alpha * 0.5f,
            WidgetThemeHelper.getSurfaceColor(CalendarThemes.glass, 0.5f).alpha,
            1f / 255f
        )
    }

    @Test
    fun cardColor_usesChipTokenSoftenedByOpacity() {
        assertEquals(
            CalendarThemes.midnight.chipBg.copy(alpha = 0.85f),
            WidgetThemeHelper.getCardBackgroundColor(CalendarThemes.midnight, 1f)
        )
    }

    @Test
    fun eventColors_shareTheSameFamilyTokensAcrossAllViews() {
        assertEquals(CalendarThemes.softPastel.teen, CalendarThemes.softPastel.eventColors("Teen 5"))
        assertEquals(EventFamily.A, CalendarThemes.familyFor("301"))
    }
}
