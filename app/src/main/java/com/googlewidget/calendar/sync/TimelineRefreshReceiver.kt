package com.googlewidget.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineWidgetReceiver
import com.googlewidget.calendar.widget.WidgetClock
import com.googlewidget.calendar.widget.WidgetPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires at a TimelineRefreshScheduler boundary. Bumps the live-session clock so
 * a still-alive Glance composition recomputes the countdown, then re-renders
 * the 3-Day Timeline and arms the next boundary.
 */
class TimelineRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        CalendarRepository.clearCache()
        WidgetClock.tick()

        if (!WidgetPlacement.isPlaced(context, ThreeDayTimelineWidgetReceiver::class.java)) {
            TimelineRefreshScheduler.cancel(context)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ThreeDayTimelineGlanceWidget().updateAll(context)
                TimelineRefreshScheduler.schedule(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
