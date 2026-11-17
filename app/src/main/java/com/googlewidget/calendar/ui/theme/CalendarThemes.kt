package com.googlewidget.calendar.ui.theme

import androidx.compose.ui.graphics.Color

/** Event color families the calendar widget can draw. */
enum class EventFamily { TEEN, A, B }

/** The four colors a single event family uses inside the widget. */
data class EventFamilyColors(
    val fill: Color,
    val bar: Color,
    val title: Color,
    val time: Color
)

/**
 * Every color the calendar widget draws comes from one of these tokens.
 * Widget code must never build a [Color] of its own: add a token here instead,
 * so a new theme only needs a new [CalendarTheme] instance.
 */
data class CalendarTheme(
    val id: String,
    val label: String,
    val bg: Color,
    val text: Color,
    val sub: Color,
    val grid: Color,
    val todayCol: Color,
    val todayCircleBg: Color,
    val todayCircleText: Color,
    val chipBg: Color,
    val chipText: Color,
    val teen: EventFamilyColors,
    val a: EventFamilyColors,
    val b: EventFamilyColors
) {
    fun familyColors(family: EventFamily): EventFamilyColors = when (family) {
        EventFamily.TEEN -> teen
        EventFamily.A -> a
        EventFamily.B -> b
    }

    fun eventColors(title: String): EventFamilyColors =
        familyColors(CalendarThemes.familyFor(title))
}

/**
 * The selectable widget themes. To add a theme, append one entry to
 * [selectable] (see README) and nothing else needs to change.
 */
object CalendarThemes {

    const val AUTO = "AUTO"
    const val SOFT_PASTEL = "SOFT_PASTEL"
    const val MIDNIGHT = "MIDNIGHT"
    const val GLASS = "GLASS"

    val softPastel = CalendarTheme(
        id = SOFT_PASTEL,
        label = "Soft pastel",
        bg = Color(0xFFFBF8F3),
        text = Color(0xFF2C2C2A),
        sub = Color(0xFF6B6A64),
        grid = Color(0xFFDDD6C6),
        todayCol = Color(0xFFF3ECE0),
        todayCircleBg = Color(0xFFD85A30),
        todayCircleText = Color(0xFFFFFFFF),
        chipBg = Color(0xFFFAECE7),
        chipText = Color(0xFF712B13),
        teen = EventFamilyColors(
            fill = Color(0xFFFAECE7),
            bar = Color(0xFFD85A30),
            title = Color(0xFF712B13),
            time = Color(0xFF993C1D)
        ),
        a = EventFamilyColors(
            fill = Color(0xFFE1F5EE),
            bar = Color(0xFF1D9E75),
            title = Color(0xFF085041),
            time = Color(0xFF0F6E56)
        ),
        b = EventFamilyColors(
            fill = Color(0xFFEEEDFE),
            bar = Color(0xFF7F77DD),
            title = Color(0xFF3C3489),
            time = Color(0xFF534AB7)
        )
    )

    val midnight = CalendarTheme(
        id = MIDNIGHT,
        label = "Midnight",
        bg = Color(0xFF14161C),
        text = Color(0xFFECEBE6),
        sub = Color(0xFF9A9A94),
        grid = Color(0xFF2A2E38),
        todayCol = Color(0xFF1B1F29),
        todayCircleBg = Color(0xFFAFA9EC),
        todayCircleText = Color(0xFF26215C),
        chipBg = Color(0xFF26215C),
        chipText = Color(0xFFCECBF6),
        teen = EventFamilyColors(
            fill = Color(0xFF2B2650),
            bar = Color(0xFFAFA9EC),
            title = Color(0xFFEEEDFE),
            time = Color(0xFFAFA9EC)
        ),
        a = EventFamilyColors(
            fill = Color(0xFF0E3A32),
            bar = Color(0xFF5DCAA5),
            title = Color(0xFFE1F5EE),
            time = Color(0xFF9FE1CB)
        ),
        b = EventFamilyColors(
            fill = Color(0xFF4A2518),
            bar = Color(0xFFF0997B),
            title = Color(0xFFFAECE7),
            time = Color(0xFFF5C4B3)
        )
    )

    val glass = CalendarTheme(
        id = GLASS,
        label = "Glass",
        // RemoteViews has no backdrop blur; the alpha keeps the wallpaper visible.
        bg = Color(0xCCFFFFFF),
        text = Color(0xFF1F2A2B),
        sub = Color(0xFF4A5A5B),
        grid = Color(0x24000000),
        todayCol = Color(0x8CFFFFFF),
        todayCircleBg = Color(0xFF0F6E56),
        todayCircleText = Color(0xFFFFFFFF),
        chipBg = Color(0xFFFFFFFF),
        chipText = Color(0xFF085041),
        teen = EventFamilyColors(
            fill = Color(0xFFFDE2D8),
            bar = Color(0xFFD85A30),
            title = Color(0xFF4A1B0C),
            time = Color(0xFF712B13)
        ),
        a = EventFamilyColors(
            fill = Color(0xFFD5EEE6),
            bar = Color(0xFF0F6E56),
            title = Color(0xFF04342C),
            time = Color(0xFF085041)
        ),
        b = EventFamilyColors(
            fill = Color(0xFFE3E0FA),
            bar = Color(0xFF534AB7),
            title = Color(0xFF26215C),
            time = Color(0xFF3C3489)
        )
    )

    /** Every concrete theme. Auto resolves through [resolve]. */
    val selectable: List<CalendarTheme> = listOf(softPastel, midnight, glass)

    fun byId(id: String?): CalendarTheme? = selectable.firstOrNull { it.id == id }

    /** Auto = Glass in light mode, Midnight in dark mode. */
    fun resolve(id: String?, systemDark: Boolean): CalendarTheme =
        byId(id) ?: if (systemDark) midnight else glass

    fun isAuto(id: String?): Boolean = id == null || id == AUTO

    /**
     * The single place that maps an event title to a color family.
     * "Teen…" classes are always [EventFamily.TEEN]; everything else gets a
     * stable A/B split from the title hash, so a class keeps its color forever.
     *
     * Change this function (only this function) to switch to by-level or
     * per-class manual palettes later.
     */
    fun familyFor(title: String): EventFamily {
        if (title.startsWith("Teen")) return EventFamily.TEEN
        val hash = title.hashCode()
        val bucket = ((hash % 2) + 2) % 2
        return if (bucket == 0) EventFamily.A else EventFamily.B
    }
}
