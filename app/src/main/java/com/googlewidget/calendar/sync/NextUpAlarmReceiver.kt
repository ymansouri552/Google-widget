package com.googlewidget.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import com.googlewidget.calendar.widget.WeeklyCalendarGlanceWidget
import com.googlewidget.calendar.widget.WidgetPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fired by [NextUpRefreshScheduler] at an event boundary to re-render the Next Up widget. */
class NextUpAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        CalendarRepository.clearCache()
        if (!WidgetPlacement.hasAnyWidget(context)) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Re-rendering also reschedules the next event / midnight boundary alarm.
                if (WidgetPlacement.isPlaced(context, NextUpGlanceWidget::class.java)) {
                    NextUpGlanceWidget().updateAll(context)
                }
                AgendaGlanceWidget().updateAll(context)
                WeeklyCalendarGlanceWidget().updateAll(context)
                ThreeDayTimelineGlanceWidget().updateAll(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
