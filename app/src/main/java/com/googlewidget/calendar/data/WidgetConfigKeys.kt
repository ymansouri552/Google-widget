package com.googlewidget.calendar.data

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object WidgetConfigKeys {
    val SELECTED_CALENDARS = stringSetPreferencesKey("selected_calendars")
    val BG_OPACITY = floatPreferencesKey("bg_opacity")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LOOKAHEAD_DAYS = intPreferencesKey("lookahead_days")
    val FONT_SCALE = stringPreferencesKey("font_scale")
    val KEY_DAY_OFFSET = intPreferencesKey("day_offset")

    const val THEME_SYSTEM = "SYSTEM"
    const val THEME_DARK = "DARK"
    const val THEME_LIGHT = "LIGHT"

    const val FONT_SCALE_NORMAL = "NORMAL"
    const val FONT_SCALE_COMPACT = "COMPACT"

    const val DEFAULT_OPACITY = 0.90f
    const val DEFAULT_LOOKAHEAD_DAYS = 14

    /** Placeholder calendar id stored when the user deselects every calendar.
     *  Querying with this id matches nothing, preserving "empty selection shows empty widget"
     *  while an unconfigured widget (empty state) still defaults to all calendars. */
    const val NO_CALENDAR_PLACEHOLDER = "-1"

    /** Bounds for the day-offset navigation state (weekly / 3-day widgets). */
    const val MIN_DAY_OFFSET = -366
    const val MAX_DAY_OFFSET = 366
}
