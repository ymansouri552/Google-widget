package com.googlewidget.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayNavigation
import com.googlewidget.calendar.widget.WeeklyCalendarGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import com.googlewidget.calendar.widget.WeeklyNavigation
import com.googlewidget.calendar.widget.WidgetClock
import com.googlewidget.calendar.widget.WidgetPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalendarBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        CalendarRepository.clearCache()
        val dateChanged = intent?.action == Intent.ACTION_DATE_CHANGED
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A new calendar day always brings the 3-Day Timeline back to
                // today, even if the last chevron tap was only minutes ago.
                if (dateChanged) {
                    ThreeDayNavigation.resetAllOffsets(context)
                    WeeklyNavigation.resetAllOffsets(context)
                }
                WidgetClock.tick()

                // Only spend the ( comparatively expensive ) re-render pass when a
                // widget is actually placed on the home screen.
                if (WidgetPlacement.hasAnyWidget(context)) {
                    AgendaGlanceWidget().updateAll(context)
                    NextUpGlanceWidget().updateAll(context)
                    WeeklyCalendarGlanceWidget().updateAll(context)
                    ThreeDayTimelineGlanceWidget().updateAll(context)
                }

                // Keep the content-change observation chain armed.
                CalendarSyncWorker.enqueue(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
