package com.googlewidget.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.googlewidget.calendar.data.WidgetConfigKeys
import java.util.concurrent.TimeUnit

/** Same re-entrancy rule as the 3-Day Timeline: ignore taps while updating. */
private object WeeklyNavigationGate {
    private val inFlight = mutableSetOf<String>()

    @Synchronized
    fun tryBegin(key: String): Boolean = inFlight.add(key)

    @Synchronized
    fun end(key: String) {
        inFlight.remove(key)
    }
}

/**
 * State transitions for the 7-day widget. Chevrons move by seven days, expire
 * after ten minutes, and always clear the day-agnostic agenda selection so a
 * new week opens on today (or its first day).
 */
object WeeklyNavigation {

    suspend fun shift(context: Context, glanceId: GlanceId, direction: Int) {
        val key = glanceId.toString()
        if (!WeeklyNavigationGate.tryBegin(key)) return
        try {
            val appWidgetId = appWidgetId(context, glanceId)
            val updated = updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    val current = prefs[WidgetConfigKeys.KEY_DAY_OFFSET] ?: 0
                    this[WidgetConfigKeys.KEY_DAY_OFFSET] = ThreeDayTimelineGlanceWidget
                        .clampViewOffsetDays(current + direction * WeeklyCalendarLogic.CHEVRON_DAYS)
                    this[WidgetConfigKeys.KEY_LAST_INTERACTION_MILLIS] = System.currentTimeMillis()
                    this.remove(WidgetConfigKeys.KEY_SELECTED_DATE)
                }
            }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
                WeeklyAutoResetWorker.enqueue(context, appWidgetId)
            }
            WeeklyCalendarGlanceWidget().update(context, glanceId)
        } finally {
            WeeklyNavigationGate.end(key)
        }
    }

    suspend fun selectDay(context: Context, glanceId: GlanceId, date: String) {
        val key = glanceId.toString()
        if (!WeeklyNavigationGate.tryBegin(key)) return
        try {
            val appWidgetId = appWidgetId(context, glanceId)
            val updated = updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetConfigKeys.KEY_SELECTED_DATE] = date
                }
            }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
            }
            WeeklyCalendarGlanceWidget().update(context, glanceId)
        } finally {
            WeeklyNavigationGate.end(key)
        }
    }

    suspend fun resetToToday(context: Context, glanceId: GlanceId) {
        val key = glanceId.toString()
        if (!WeeklyNavigationGate.tryBegin(key)) return
        try {
            val appWidgetId = appWidgetId(context, glanceId)
            val updated = updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs -> resetState(prefs) }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
                WeeklyAutoResetWorker.cancel(context, appWidgetId)
            }
            WeeklyCalendarGlanceWidget().update(context, glanceId)
        } finally {
            WeeklyNavigationGate.end(key)
        }
    }

    /** Called on DATE_CHANGED: a new day opens on today, never a stale selection. */
    suspend fun resetAllOffsets(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(WeeklyCalendarGlanceWidget::class.java).forEach { glanceId ->
            val appWidgetId = appWidgetId(context, glanceId)
            val updated = updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs -> resetState(prefs) }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
                WeeklyAutoResetWorker.cancel(context, appWidgetId)
            }
        }
    }

    private fun resetState(prefs: androidx.datastore.preferences.core.Preferences) =
        prefs.toMutablePreferences().apply {
            this[WidgetConfigKeys.KEY_DAY_OFFSET] = 0
            this[WidgetConfigKeys.KEY_LAST_INTERACTION_MILLIS] = 0L
            this.remove(WidgetConfigKeys.KEY_SELECTED_DATE)
        }

    private fun appWidgetId(context: Context, glanceId: GlanceId): Int = try {
        GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    } catch (_: Exception) {
        AppWidgetManager.INVALID_APPWIDGET_ID
    }
}

/** One-shot reset queued by every chevron tap, replaced on each new tap. */
class WeeklyAutoResetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.success()

        return try {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            val updated = updateAppWidgetState(
                applicationContext,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetConfigKeys.KEY_DAY_OFFSET] = 0
                    this[WidgetConfigKeys.KEY_LAST_INTERACTION_MILLIS] = 0L
                    this.remove(WidgetConfigKeys.KEY_SELECTED_DATE)
                }
            }
            WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
            WeeklyCalendarGlanceWidget().update(applicationContext, glanceId)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.success()
        }
    }

    companion object {
        private const val KEY_APP_WIDGET_ID = "app_widget_id"
        private const val UNIQUE_PREFIX = "weekly_auto_reset_"

        fun enqueue(context: Context, appWidgetId: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val request = OneTimeWorkRequestBuilder<WeeklyAutoResetWorker>()
                .setInitialDelay(
                    WidgetConfigKeys.VIEW_OFFSET_AUTO_RESET_DELAY_MINUTES,
                    TimeUnit.MINUTES
                )
                .setInputData(workDataOf(KEY_APP_WIDGET_ID to appWidgetId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PREFIX + appWidgetId,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, appWidgetId: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PREFIX + appWidgetId)
        }
    }
}
