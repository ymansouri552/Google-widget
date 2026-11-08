package com.googlewidget.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidgetReceiver
import com.googlewidget.calendar.widget.WidgetPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fired by [NextUpRefreshScheduler] at an event boundary to re-render the Next Up widget. */
class NextUpAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // If the widget was removed since the alarm was armed, do nothing.
        if (!WidgetPlacement.isPlaced(context, NextUpGlanceWidgetReceiver::class.java)) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Re-rendering provideGlance also reschedules the next boundary alarm.
                NextUpGlanceWidget().updateAll(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
