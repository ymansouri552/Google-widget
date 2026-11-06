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

    const val THEME_SYSTEM = "SYSTEM"
    const val THEME_DARK = "DARK"
    const val THEME_LIGHT = "LIGHT"

    const val FONT_SCALE_NORMAL = "NORMAL"
    const val FONT_SCALE_COMPACT = "COMPACT"

    const val DEFAULT_OPACITY = 0.90f
    const val DEFAULT_LOOKAHEAD_DAYS = 14
}
