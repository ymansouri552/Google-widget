package com.googlewidget.calendar.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.googlewidget.calendar.data.WidgetConfigKeys

/**
 * Day-offset navigation for the Weekly and 3-Day widgets.
 *
 * Offsets are clamped to [WidgetConfigKeys.MIN_DAY_OFFSET]..[WidgetConfigKeys.MAX_DAY_OFFSET]
 * so the state can never be wound arbitrarily far in either direction, and each
 * action only updates the widget type that owns [glanceId] instead of updating
 * both widget classes (one of which always threw) and swallowing the exception.
 */
private suspend fun updateOwningWidget(context: Context, glanceId: GlanceId) {
    val manager = GlanceAppWidgetManager(context)
    if (manager.getGlanceIds(WeeklyCalendarGlanceWidget::class.java).contains(glanceId)) {
        WeeklyCalendarGlanceWidget().update(context, glanceId)
    }
    if (manager.getGlanceIds(ThreeDayTimelineGlanceWidget::class.java).contains(glanceId)) {
        ThreeDayTimelineGlanceWidget().update(context, glanceId)
    }
}

private fun updateDayOffset(
    prefs: androidx.datastore.preferences.core.Preferences,
    delta: Int
) = prefs.toMutablePreferences().apply {
    val current = prefs[WidgetConfigKeys.KEY_DAY_OFFSET] ?: 0
    this[WidgetConfigKeys.KEY_DAY_OFFSET] = (current + delta)
        .coerceIn(WidgetConfigKeys.MIN_DAY_OFFSET, WidgetConfigKeys.MAX_DAY_OFFSET)
}

class WeeklyPrevAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            updateDayOffset(prefs, -7)
        }
        WeeklyCalendarGlanceWidget().update(context, glanceId)
    }
}

class WeeklyNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            updateDayOffset(prefs, +7)
        }
        WeeklyCalendarGlanceWidget().update(context, glanceId)
    }
}

class ThreeDayPrevAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            updateDayOffset(prefs, -3)
        }
        ThreeDayTimelineGlanceWidget().update(context, glanceId)
    }
}

class ThreeDayNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            updateDayOffset(prefs, +3)
        }
        ThreeDayTimelineGlanceWidget().update(context, glanceId)
    }
}

class ResetDateOffsetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetConfigKeys.KEY_DAY_OFFSET] = 0
                this.remove(WidgetConfigKeys.KEY_WINDOW_START_HOUR)
            }
        }
        updateOwningWidget(context, glanceId)
    }
}

class NavigateDateOffsetAction : ActionCallback {
    companion object {
        val KEY_DELTA = ActionParameters.Key<Int>("delta")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[KEY_DELTA] ?: 0
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            updateDayOffset(prefs, delta)
        }
        updateOwningWidget(context, glanceId)
    }
}

/**
 * Shifts the 3-Day timeline's visible hour window by [KEY_DELTA] hours when the
 * widget is too small to fit the full range at minimum scale. The start hour is
 * clamped to the widget's absolute range bounds; the renderer re-clamps it to
 * the actual computed range when it falls outside.
 */
class ThreeDayWindowShiftAction : ActionCallback {
    companion object {
        val KEY_DELTA = ActionParameters.Key<Int>("window_delta")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[KEY_DELTA] ?: 0
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                val current = prefs[WidgetConfigKeys.KEY_WINDOW_START_HOUR]
                    ?: ThreeDayTimelineGlanceWidget.RANGE_EARLIEST_HOUR
                this[WidgetConfigKeys.KEY_WINDOW_START_HOUR] =
                    ThreeDayTimelineGlanceWidget.shiftedWindowStartHour(current, delta)
            }
        }
        ThreeDayTimelineGlanceWidget().update(context, glanceId)
    }
}
