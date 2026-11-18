package com.googlewidget.calendar.data

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object WidgetConfigKeys {
    val SELECTED_CALENDARS = stringSetPreferencesKey("selected_calendars")
    val BG_OPACITY = floatPreferencesKey("bg_opacity")
    val LOOKAHEAD_DAYS = intPreferencesKey("lookahead_days")
    val FONT_SCALE = stringPreferencesKey("font_scale")
    val KEY_DAY_OFFSET = intPreferencesKey("day_offset")
    val KEY_WINDOW_START_HOUR = intPreferencesKey("window_start_hour")

    /** Weekly widget: which day the seven-day strip starts on. */
    val KEY_WEEK_START = stringPreferencesKey("week_start")
    /** Weekly widget: ISO date of the day whose agenda is shown. */
    val KEY_SELECTED_DATE = stringPreferencesKey("selected_date")

    /** 3-Day Timeline navigation state. Kept separate from [KEY_DAY_OFFSET] so
     *  the weekly widget's persisted offset is untouched. */
    val KEY_VIEW_OFFSET_DAYS = intPreferencesKey("view_offset_days")
    val KEY_LAST_INTERACTION_MILLIS = longPreferencesKey("last_interaction_millis")
    val KEY_CHEVRON_STEP_DAYS = intPreferencesKey("chevron_step_days")

    /** Optional hour bounds for the 3-Day Timeline grid; null means no bound. */
    val KEY_TIMELINE_MIN_HOUR = intPreferencesKey("timeline_min_hour")
    val KEY_TIMELINE_MAX_HOUR = intPreferencesKey("timeline_max_hour")

    const val WEEK_START_SATURDAY = "SATURDAY"
    const val WEEK_START_SUNDAY = "SUNDAY"
    const val WEEK_START_MONDAY = "MONDAY"
    const val WEEK_START_TODAY = "TODAY"

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

    /** 3-Day Timeline offset bounds, measured in days from today. */
    const val MIN_VIEW_OFFSET_DAYS = -30
    const val MAX_VIEW_OFFSET_DAYS = 90

    const val CHEVRON_STEP_ONE_DAY = 1
    const val DEFAULT_CHEVRON_STEP_DAYS = 3

    /** A chevron tap keeps the offset for ten minutes, then the widget snaps
     *  back to today unless the user interacted again. */
    const val VIEW_OFFSET_RESET_MILLIS = 10L * 60L * 1000L
    const val VIEW_OFFSET_AUTO_RESET_DELAY_MINUTES = 10L

    fun normalizedChevronStep(value: Int?): Int =
        if (value == CHEVRON_STEP_ONE_DAY) CHEVRON_STEP_ONE_DAY else DEFAULT_CHEVRON_STEP_DAYS

    const val DEFAULT_WEEK_START = WEEK_START_SATURDAY

    fun normalizedWeekStart(value: String?): String = when (value) {
        WEEK_START_SATURDAY, WEEK_START_SUNDAY, WEEK_START_MONDAY, WEEK_START_TODAY -> value!!
        else -> DEFAULT_WEEK_START
    }

    /** User-picked bounds must leave at least two visible hours. */
    fun normalizeHourBounds(minHour: Int?, maxHour: Int?): Pair<Int?, Int?> {
        val min = minHour?.coerceIn(0, 22)
        val max = maxHour?.coerceIn(1, 23)
        return when {
            min == null || max == null -> min to max
            min < max -> min to max
            min == 22 -> 21 to 23
            else -> min to (min + 2)
        }
    }
}
