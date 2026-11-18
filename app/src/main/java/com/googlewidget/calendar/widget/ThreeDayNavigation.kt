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

/**
 * Per-widget re-entrancy guard. While a chevron update is in flight, further
 * taps are ignored instead of queueing several offset jumps that the widget
 * would apply after the first render finishes.
 */
internal object ThreeDayNavigationGate {
    private val inFlight = mutableSetOf<String>()

    @Synchronized
    fun tryBegin(key: String): Boolean = inFlight.add(key)

    @Synchronized
    fun end(key: String) {
        inFlight.remove(key)
    }
}

/**
 * State transitions for the 3-Day Timeline. Every write publishes through
 * [WidgetLiveStateHub] so a still-running Glance session recomposes, then
 * re-renders only the widget that owns [glanceId].
 */
object ThreeDayNavigation {

    suspend fun shift(context: Context, glanceId: GlanceId, direction: Int) {
        val key = glanceId.toString()
        if (!ThreeDayNavigationGate.tryBegin(key)) return
        try {
            val appWidgetId = appWidgetId(context, glanceId)
            val updated = updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    val step = WidgetConfigKeys.normalizedChevronStep(
                        prefs[WidgetConfigKeys.KEY_CHEVRON_STEP_DAYS]
                    )
                    val current = prefs[WidgetConfigKeys.KEY_VIEW_OFFSET_DAYS] ?: 0
                    this[WidgetConfigKeys.KEY_VIEW_OFFSET_DAYS] =
                        ThreeDayTimelineGlanceWidget.chevronOffsetAfter(current, direction, step)
                    this[WidgetConfigKeys.KEY_LAST_INTERACTION_MILLIS] =
                        System.currentTimeMillis()
                }
            }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
                ThreeDayAutoResetWorker.enqueue(context, appWidgetId)
            }
            ThreeDayTimelineGlanceWidget().update(context, glanceId)
        } finally {
            ThreeDayNavigationGate.end(key)
        }
    }

    suspend fun resetToToday(context: Context, glanceId: GlanceId) {
        val key = glanceId.toString()
        if (!ThreeDayNavigationGate.tryBegin(key)) return
        try {
            val appWidgetId = appWidgetId(context, glanceId)
            val updated = updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetConfigKeys.KEY_VIEW_OFFSET_DAYS] = 0
                    this[WidgetConfigKeys.KEY_LAST_INTERACTION_MILLIS] = 0L
                }
            }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
                ThreeDayAutoResetWorker.cancel(context, appWidgetId)
            }
            ThreeDayTimelineGlanceWidget().update(context, glanceId)
        } finally {
            ThreeDayNavigationGate.end(key)
        }
    }

    /** Called on DATE_CHANGED: a new day always starts the widget at today. */
    suspend fun resetAllOffsets(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(ThreeDayTimelineGlanceWidget::class.java).forEach { glanceId ->
            val appWidgetId = appWidgetId(context, glanceId)
            val updated = updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetConfigKeys.KEY_VIEW_OFFSET_DAYS] = 0
                    this[WidgetConfigKeys.KEY_LAST_INTERACTION_MILLIS] = 0L
                }
            }
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
                ThreeDayAutoResetWorker.cancel(context, appWidgetId)
            }
        }
    }

    private fun appWidgetId(context: Context, glanceId: GlanceId): Int = try {
        GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    } catch (_: Exception) {
        AppWidgetManager.INVALID_APPWIDGET_ID
    }
}

/**
 * One-shot ten-minute reset queued by every chevron tap. Enqueue uses REPLACE
 * with a per-widget unique name, so repeated taps keep pushing the deadline
 * out; once it runs, the widget returns to today even if the user left it on a
 * future or past window.
 */
class ThreeDayAutoResetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.success()

        return try {
            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            val updated = updateAppWidgetState(
                applicationContext,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetConfigKeys.KEY_VIEW_OFFSET_DAYS] = 0
                    this[WidgetConfigKeys.KEY_LAST_INTERACTION_MILLIS] = 0L
                }
            }
            WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
            ThreeDayTimelineGlanceWidget().update(applicationContext, glanceId)
            Result.success()
        } catch (e: Exception) {
            // The widget was removed or its state is gone; there is nothing to
            // retry. A normal render also self-resets a stale offset.
            e.printStackTrace()
            Result.success()
        }
    }

    companion object {
        private const val KEY_APP_WIDGET_ID = "app_widget_id"
        private const val UNIQUE_PREFIX = "three_day_auto_reset_"

        fun enqueue(context: Context, appWidgetId: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val request = OneTimeWorkRequestBuilder<ThreeDayAutoResetWorker>()
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
