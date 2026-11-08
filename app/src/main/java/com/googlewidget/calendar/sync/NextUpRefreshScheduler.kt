package com.googlewidget.calendar.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.widget.NextUpGlanceWidget

/**
 * Keeps the Next Up widget's countdown text from going stale between the
 * 30-minute system update ticks. Schedules a one-shot alarm at the next
 * transition of the currently shown event (start or end) that re-renders the
 * widget — the new render then reschedules for its own next boundary.
 */
object NextUpRefreshScheduler {

    private const val REQUEST_CODE = 4711
    private const val BOUNDARY_GRACE_MS = 1_000L

    fun schedule(context: Context, event: CalendarEvent?, now: Long) {
        if (event == null) {
            cancel(context)
            return
        }

        val boundaryAt = when {
            event.begin > now -> event.begin             // upcoming: refresh when it starts
            event.end > now -> event.end                 // ongoing: refresh when it ends
            else -> return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, NextUpAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    boundaryAt + BOUNDARY_GRACE_MS,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    boundaryAt + BOUNDARY_GRACE_MS,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Exact alarms unavailable; fall back to the inexact variant.
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    boundaryAt + BOUNDARY_GRACE_MS,
                    pendingIntent
                )
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    private fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, NextUpAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
