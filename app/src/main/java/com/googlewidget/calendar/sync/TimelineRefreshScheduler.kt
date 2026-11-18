package com.googlewidget.calendar.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.CalendarResult
import com.googlewidget.calendar.widget.ThreeDayTimelineWidgetReceiver
import com.googlewidget.calendar.widget.WidgetPlacement
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps the 3-Day Timeline's time-dependent text accurate between launcher
 * ticks. Schedules one inexact alarm for the earliest of:
 *
 *  - the next timed event's start,
 *  - the next timed event's end (covers an event already in progress),
 *  - local midnight,
 *  - 15 minutes from now while any timed event exists today.
 *
 * The receiver re-renders the widget and then re-arms this schedule. Only
 * [AlarmManager.setAndAllowWhileIdle] is used so no exact-alarm permission is
 * required.
 */
object TimelineRefreshScheduler {

    private const val REQUEST_CODE = 4712
    private const val REFRESH_INTERVAL_MS = 15L * 60L * 1000L
    private const val GRACE_MS = 1_000L

    fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
        val appContext = context.applicationContext
        if (!WidgetPlacement.isPlaced(appContext, ThreeDayTimelineWidgetReceiver::class.java)) {
            cancel(appContext)
            return
        }

        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val queryEnd = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

        val events = when (
            val result = CalendarRepository(appContext)
                .getEventsInWindow(todayStart, queryEnd, emptySet())
        ) {
            is CalendarResult.Success -> result.data
            else -> emptyList()
        }
        val timed = events.filter { !it.allDay }

        val nextStart = timed.filter { it.begin > now }.minOfOrNull { it.begin }
        val nextEnd = timed.filter { it.end > now }.minOfOrNull { it.end }
        val hasEventToday = timed.any { it.begin < midnight && it.end > todayStart }

        val candidates = buildList {
            add(midnight)
            nextStart?.let { add(it) }
            nextEnd?.let { add(it) }
            if (hasEventToday) add(now + REFRESH_INTERVAL_MS)
        }
        val refreshAt = candidates
            .filter { it > now + GRACE_MS }
            .minOrNull()
            ?: (now + REFRESH_INTERVAL_MS)

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                refreshAt + GRACE_MS,
                pendingIntent(appContext)
            )
        } catch (e: Exception) {
            // Some OEMs restrict alarms even when no special permission is
            // needed; the launcher's periodic update remains a fallback.
            e.printStackTrace()
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        alarmManager.cancel(pendingIntent(appContext))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TimelineRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
