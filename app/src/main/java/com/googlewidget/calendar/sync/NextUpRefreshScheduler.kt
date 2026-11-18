package com.googlewidget.calendar.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps the Next Up widget's countdown text from going stale between the
 * 30-minute system update ticks. Schedules a one-shot alarm at the next
 * transition of the currently shown event (start or end) that re-renders the
 * widget, and the new render then reschedules for its own next boundary.
 */
object NextUpRefreshScheduler {

    private const val REQUEST_CODE = 4711
    private const val BOUNDARY_GRACE_MS = 1_000L

    fun schedule(context: Context, event: CalendarEvent?, now: Long) {
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val eventBoundary = when {
            event == null -> Long.MAX_VALUE
            event.begin > now -> event.begin             // upcoming: refresh when it starts
            event.end > now -> event.end                 // ongoing: refresh when it ends
            else -> Long.MAX_VALUE
        }

        val boundaryAt = minOf(eventBoundary, nextMidnight)
        if (boundaryAt == Long.MAX_VALUE) {
            cancel(context)
            return
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
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    boundaryAt + BOUNDARY_GRACE_MS,
                    15_000L,
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
            // Exact alarms unavailable; fall back to windowed or inexact variant.
            try {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    boundaryAt + BOUNDARY_GRACE_MS,
                    15_000L,
                    pendingIntent
                )
            } catch (_: Exception) {
                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        boundaryAt + BOUNDARY_GRACE_MS,
                        pendingIntent
                    )
                } catch (_: Exception) {
                }
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
