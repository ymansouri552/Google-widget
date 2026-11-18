package com.googlewidget.calendar.widget

import androidx.datastore.preferences.core.Preferences
import com.googlewidget.calendar.data.WidgetConfigKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The settings + navigation state the Weekly and 3-Day compositions render from.
 *
 * This mirrors what the widget's glance state holds, but kept in an observable
 * [MutableStateFlow] so a live session composition can recompose the moment an
 * action writes new state.
 */
data class WidgetLiveState(
    val dayOffset: Int,
    val viewOffsetDays: Int,
    val lastInteractionMillis: Long,
    val chevronStepDays: Int,
    val timelineMinHour: Int?,
    val timelineMaxHour: Int?,
    val windowStartHour: Int?,
    val selectedCalendarIds: Set<Long>,
    val bgOpacity: Float,
    val weekStart: String,
    val selectedDate: String?
) {
    companion object {
        fun from(prefs: Preferences): WidgetLiveState = WidgetLiveState(
            dayOffset = (prefs[WidgetConfigKeys.KEY_DAY_OFFSET] ?: 0)
                .coerceIn(WidgetConfigKeys.MIN_DAY_OFFSET, WidgetConfigKeys.MAX_DAY_OFFSET),
            viewOffsetDays = (prefs[WidgetConfigKeys.KEY_VIEW_OFFSET_DAYS] ?: 0)
                .coerceIn(WidgetConfigKeys.MIN_VIEW_OFFSET_DAYS, WidgetConfigKeys.MAX_VIEW_OFFSET_DAYS),
            lastInteractionMillis = prefs[WidgetConfigKeys.KEY_LAST_INTERACTION_MILLIS] ?: 0L,
            chevronStepDays = WidgetConfigKeys.normalizedChevronStep(
                prefs[WidgetConfigKeys.KEY_CHEVRON_STEP_DAYS]
            ),
            timelineMinHour = prefs[WidgetConfigKeys.KEY_TIMELINE_MIN_HOUR],
            timelineMaxHour = prefs[WidgetConfigKeys.KEY_TIMELINE_MAX_HOUR],
            windowStartHour = prefs[WidgetConfigKeys.KEY_WINDOW_START_HOUR],
            selectedCalendarIds = prefs[WidgetConfigKeys.SELECTED_CALENDARS]
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet(),
            bgOpacity = prefs[WidgetConfigKeys.BG_OPACITY] ?: WidgetConfigKeys.DEFAULT_OPACITY,
            weekStart = WidgetConfigKeys.normalizedWeekStart(prefs[WidgetConfigKeys.KEY_WEEK_START]),
            selectedDate = prefs[WidgetConfigKeys.KEY_SELECTED_DATE]
        )
    }
}

/**
 * Global refresh revision observed by live widget compositions. A scheduled
 * refresh calls [tick] before re-rendering, so a session that Glance keeps
 * alive still recomposes and recomputes time-dependent text such as the
 * "Next … in 19m" pill.
 */
object WidgetClock {
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun tick() = synchronized(this) {
        _revision.value = _revision.value + 1L
    }
}

/**
 * Per-widget live state published by every writer of glance state (navigation
 * actions, today reset, hour-window shift, config activity).
 *
 * Why this exists: Glance 1.2 keeps each widget's composition alive as a session
 * for ~45 seconds after the last interaction. While a session is running,
 * [androidx.glance.appwidget.GlanceAppWidget.update] does NOT re-run
 * `provideGlance`, it only re-pushes the previously rendered content. A widget
 * that hands static content to `provideContent` therefore ignores navigation
 * taps until the session dies, and then jumps to the accumulated state at once.
 *
 * The widget compositions collect [flow] inside `provideContent`; publishing
 * here recomposes the running composition in-process, which is what makes the
 * chevrons respond instantly.
 */
object WidgetLiveStateHub {
    private val flows = HashMap<Int, MutableStateFlow<WidgetLiveState>>()

    private fun mutableFlow(appWidgetId: Int): MutableStateFlow<WidgetLiveState> = synchronized(flows) {
        flows.getOrPut(appWidgetId) {
            MutableStateFlow(
                WidgetLiveState(
                    dayOffset = 0,
                    viewOffsetDays = 0,
                    lastInteractionMillis = 0L,
                    chevronStepDays = WidgetConfigKeys.DEFAULT_CHEVRON_STEP_DAYS,
                    timelineMinHour = null,
                    timelineMaxHour = null,
                    windowStartHour = null,
                    selectedCalendarIds = emptySet(),
                    bgOpacity = WidgetConfigKeys.DEFAULT_OPACITY,
                    weekStart = WidgetConfigKeys.DEFAULT_WEEK_START,
                    selectedDate = null
                )
            )
        }
    }

    /** The flow a widget composition observes for its own instance. */
    fun flow(appWidgetId: Int): StateFlow<WidgetLiveState> = mutableFlow(appWidgetId)

    /** Publishes [state] as the current live state for [appWidgetId]. */
    fun publish(appWidgetId: Int, state: WidgetLiveState) {
        mutableFlow(appWidgetId).value = state
    }
}
