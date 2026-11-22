package com.googlewidget.calendar.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeBridgeTest {

    @Test
    fun appSchemeUsesTheSelectedWidgetThemeTokens() {
        listOf(
            CalendarThemes.softPastel,
            CalendarThemes.midnight,
            CalendarThemes.glass,
            CalendarThemes.neonPurple
        ).forEach { theme ->
            val dark = theme.id == CalendarThemes.MIDNIGHT ||
                theme.id == CalendarThemes.NEON_PURPLE
            val scheme = colorSchemeFrom(theme, dark = dark)
                assertEquals(theme.todayCircleBg, scheme.primary)
                assertEquals(theme.todayCircleText, scheme.onPrimary)
                assertEquals(theme.chipBg.copy(alpha = 1f), scheme.primaryContainer)
                assertEquals(theme.chipText, scheme.onPrimaryContainer)
                assertEquals(theme.bg.copy(alpha = 1f), scheme.surface)
                assertEquals(theme.text, scheme.onSurface)
                assertEquals(theme.sub, scheme.onSurfaceVariant)
            }
    }

    @Test
    fun autoResolvesToTheSystemAppropriateTheme() {
        assertEquals(CalendarThemes.glass, CalendarThemes.resolve(CalendarThemes.AUTO, false))
        assertEquals(CalendarThemes.midnight, CalendarThemes.resolve(CalendarThemes.AUTO, true))
    }
}
