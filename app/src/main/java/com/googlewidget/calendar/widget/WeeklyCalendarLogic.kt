package com.googlewidget.calendar.widget

import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.WidgetConfigKeys
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure, unit-testable decisions behind the 7-day widget. The composable only
 * draws what these helpers return, so week geometry, selection fallback and
 * height-driven row counts can be verified without a device.
 */
object WeeklyCalendarLogic {

    const val WEEK_DAYS = 7
    const val CHEVRON_DAYS = 7
    const val MAX_DOTS = 3

    /** Chips need roughly 64dp each before titles stop being useful. */
    const val WIDE_LAYOUT_MIN_WIDTH_DP = 448f

    /** Below this width the stats panel becomes a single compact line. */
    const val TWO_COLUMN_MIN_WIDTH_DP = 300f
    const val STATS_MIN_WIDTH_DP = 108f
    const val STATS_MAX_WIDTH_DP = 164f

    private const val AGENDA_ROW_HEIGHT_DP = 32f
    private const val AGENDA_TOP_RESERVE_DP = 6f
    private const val CHIP_ROW_HEIGHT_DP = 22f
    private const val CHIP_TOP_RESERVE_DP = 24f
    // Header row + 8 events + "+N more" stays within Glance's 10-child limit.
    const val MAX_AGENDA_ROWS = 8
    // Under Glance's 10-child limit, even with a day header, spacer and "+N".
    const val MAX_CHIP_ROWS = 8

    /** The day a fixed seven-day strip starts on. TODAY has no fixed anchor. */
    fun weekStartDay(weekStart: String, locale: Locale = Locale.getDefault()): DayOfWeek {
        val fromLocale = WeekDayCompat.firstDayOfWeek(locale)
        return when (WidgetConfigKeys.normalizedWeekStart(weekStart)) {
            WidgetConfigKeys.WEEK_START_SATURDAY -> DayOfWeek.SATURDAY
            WidgetConfigKeys.WEEK_START_MONDAY -> DayOfWeek.MONDAY
            WidgetConfigKeys.WEEK_START_TODAY -> fromLocale
            else -> DayOfWeek.SUNDAY
        }
    }

    /**
     * The seven visible dates. Fixed modes anchor the week that contains
     * [today] + [offsetDays]; TODAY is a rolling seven-day window from that day.
     */
    fun weekDates(
        today: LocalDate,
        offsetDays: Int,
        weekStart: String,
        locale: Locale = Locale.getDefault()
    ): List<LocalDate> {
        val base = today.plusDays(
            ThreeDayTimelineGlanceWidget.clampViewOffsetDays(offsetDays).toLong()
        )
        if (WidgetConfigKeys.normalizedWeekStart(weekStart) == WidgetConfigKeys.WEEK_START_TODAY) {
            return (0 until WEEK_DAYS).map { base.plusDays(it.toLong()) }
        }
        val first = base.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(weekStartDay(weekStart, locale))
        )
        return (0 until WEEK_DAYS).map { first.plusDays(it.toLong()) }
    }

    /**
     * Offset to actually render: clamped and expired after the same ten-minute
     * window the 3-Day Timeline uses.
     */
    fun effectiveOffset(
        offsetDays: Int,
        lastInteractionMillis: Long,
        nowMillis: Long
    ): Int = ThreeDayTimelineGlanceWidget.effectiveViewOffsetDays(
        offsetDays, lastInteractionMillis, nowMillis
    )

    /** "14-20 Nov" / "28 Nov - 4 Dec" / cross-year ranges. */
    fun rangeLabel(
        first: LocalDate,
        last: LocalDate,
        locale: Locale = Locale.getDefault()
    ): String = ThreeDayTimelineGlanceWidget.formatRangeLabel(first, last, locale)

    /** Localized short weekday ("Sat"), never the single-letter narrow form. */
    fun shortWeekday(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    /**
     * The stored day wins only while it is still on screen; otherwise today if
     * visible, else the first day. This also handles an invalid stored value
     * and a selection that became stale after a chevron tap or auto-reset.
     */
    fun selectedDateFor(
        weekDates: List<LocalDate>,
        storedDate: String?,
        today: LocalDate
    ): LocalDate {
        if (weekDates.isEmpty()) return today
        val parsed = storedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return when {
            parsed != null && parsed in weekDates -> parsed
            today in weekDates -> today
            else -> weekDates.first()
        }
    }

    /** How many full agenda rows fit under the strip in the available height. */
    fun agendaRowCount(availableHeightDp: Float): Int =
        (((availableHeightDp - AGENDA_TOP_RESERVE_DP) / AGENDA_ROW_HEIGHT_DP)
            .toInt()
            .coerceIn(1, MAX_AGENDA_ROWS))

    /**
     * A single leftover event is shown rather than replaced by a "+1 more"
     * row; only two or more hidden events earn an overflow row.
     */
    fun paginate(total: Int, rowCount: Int): AgendaPaging {
        if (total <= rowCount) return AgendaPaging(total, 0)
        if (total - rowCount == 1) return AgendaPaging(total, 0)
        val visible = (rowCount - 1).coerceAtLeast(0)
        return AgendaPaging(visible, total - visible)
    }

    /** How many full event chips fit in the wide grid's day columns. */
    fun chipRowCount(availableHeightDp: Float): Int =
        (((availableHeightDp - CHIP_TOP_RESERVE_DP) / CHIP_ROW_HEIGHT_DP)
            .toInt()
            .coerceIn(1, MAX_CHIP_ROWS))

    /** "HH:mm-HH:mm" for a timed event, "All day" otherwise. */
    fun timeLabel(event: CalendarEvent, zone: ZoneId = ZoneId.systemDefault()): String =
        if (event.allDay) {
            "All day"
        } else {
            "${ThreeDayTimelineGlanceWidget.formatTime(event.begin, zone)}-" +
                ThreeDayTimelineGlanceWidget.formatTime(event.end, zone)
        }

    /**
     * All-day events first, then the ongoing event, then upcoming ones, then
     * events that already ended. Within a bucket, earlier starts come first.
     */
    fun sortAgendaEvents(
        events: List<CalendarEvent>,
        nowMillis: Long
    ): List<CalendarEvent> {
        fun rank(event: CalendarEvent) = when {
            event.allDay -> 0
            event.begin <= nowMillis && nowMillis < event.end -> 1
            event.begin > nowMillis -> 2
            else -> 3
        }
        return events.sortedWith(compareBy<CalendarEvent> { rank(it) }.thenBy { it.begin })
    }

    fun isOngoing(event: CalendarEvent, nowMillis: Long): Boolean =
        event.begin <= nowMillis && nowMillis < event.end

    fun isEnded(event: CalendarEvent, nowMillis: Long): Boolean = event.end <= nowMillis

    /** The one ongoing event and the first upcoming one get the NOW/NEXT tags. */
    fun agendaMarkers(events: List<CalendarEvent>, nowMillis: Long): AgendaMarkers {
        val ongoing = events.firstOrNull { isOngoing(it, nowMillis) }
        val next = events
            .filter { it.begin > nowMillis && it.end > nowMillis }
            .minByOrNull { it.begin }
        return AgendaMarkers(ongoing?.eventId, next?.eventId)
    }

    fun countdownText(startMillis: Long, nowMillis: Long): String {
        val minutes = ((startMillis - nowMillis) / 60_000L).coerceAtLeast(1)
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    /** "Wed 18 Nov · 5 events", localized. */
    fun agendaHeaderLabel(
        date: LocalDate,
        eventCount: Int,
        locale: Locale = Locale.getDefault()
    ): String {
        val day = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        return "$day · $eventCount event${if (eventCount == 1) "" else "s"}"
    }

    /** Hidden when the selected day has no upcoming event. */
    fun nextPill(events: List<CalendarEvent>, nowMillis: Long): String? {
        val next = events
            .filter { it.begin > nowMillis && it.end > nowMillis }
            .minByOrNull { it.begin }
            ?: return null
        return "Next: ${next.title} in ${countdownText(next.begin, nowMillis)}"
    }

    /**
     * All-day entries are not classes, so only timed events feed the stats. An
     * event that crosses midnight contributes its full duration on its start day.
     */
    fun dailyStats(events: List<CalendarEvent>): DailyStats {
        val timed = events.filter { !it.allDay }
        return DailyStats(
            classCount = timed.size,
            teachingMinutes = timed.sumOf { (it.end - it.begin).coerceAtLeast(0L) / 60_000L },
            lastEndMillis = timed.maxByOrNull { it.end }?.end
        )
    }

    fun formatTeachingTime(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }
}

/** Counts and durations for the selected day, derived from its timed events. */
data class DailyStats(
    val classCount: Int,
    val teachingMinutes: Long,
    val lastEndMillis: Long?
)

/** How many events fit, and how many remain behind an overflow row. */
data class AgendaPaging(val visible: Int, val overflow: Int)

data class AgendaMarkers(val nowEventId: Long?, val nextEventId: Long?)

/** Wraps WeekFields so the pure logic stays independent of API-level quirks. */
private object WeekDayCompat {
    fun firstDayOfWeek(locale: Locale): DayOfWeek =
        java.time.temporal.WeekFields.of(locale).firstDayOfWeek
}
