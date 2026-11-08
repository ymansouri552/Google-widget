package com.googlewidget.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.WeeklyCalendarGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalendarBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update all widgets when time, timezone, date, or calendar provider changes
                AgendaGlanceWidget().updateAll(context)
                NextUpGlanceWidget().updateAll(context)
                WeeklyCalendarGlanceWidget().updateAll(context)
                ThreeDayTimelineGlanceWidget().updateAll(context)

                // Ensure content observer worker is active
                CalendarSyncWorker.enqueue(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
