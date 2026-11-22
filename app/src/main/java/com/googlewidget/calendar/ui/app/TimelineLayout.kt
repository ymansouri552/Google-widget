package com.googlewidget.calendar.ui.app

import com.googlewidget.calendar.data.CalendarEvent
import java.time.Instant
import java.time.ZoneId

/** One event placed on the day timeline, with its overlap lane resolved. */
data class TimelineBlock(
    val event: CalendarEvent,
    val startMinute: Int,
    val endMinute: Int,
    val lane: Int,
    val laneCount: Int
)

/** A stretch of unscheduled time inside the visible range. */
data class FreeSlot(val startMinute: Int, val endMinute: Int)

/** Everything the timeline composable needs, computed without Android UI. */
data class TimelineModel(
    val startMinute: Int,
    val endMinute: Int,
    val blocks: List<TimelineBlock>,
    val freeSlot: FreeSlot?
) {
    val hourCount: Int get() = (endMinute - startMinute) / 60
}

/**
 * Pure geometry for the Calendar screen's hour grid. Times are minutes from
 * midnight; blocks and the free slot are minutes from [TimelineModel.startMinute].
 */
object TimelineLayout {

    const val MIN_HOURS = 8
    const val MIN_FREE_SLOT_MINUTES = 45

    fun minuteOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    /** Events crossing midnight are clamped to the end of their start day. */
    fun build(
        events: List<CalendarEvent>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): TimelineModel {
        val timed = events.filter { !it.allDay }.map { event ->
            val start = minuteOfDay(event.begin, zone)
            val rawEnd = minuteOfDay(event.end, zone)
            val end = if (rawEnd > start) rawEnd else (start + 30).coerceAtMost(1439)
            Triple(event, start, end)
        }.sortedBy { it.second }

        val nowMinute = minuteOfDay(nowMillis, zone)
        val earliest = timed.minOfOrNull { it.second } ?: (nowMinute - 120)
        val latest = timed.maxOfOrNull { it.third } ?: (nowMinute + 480)

        var startHour = (earliest / 60).coerceIn(0, 23)
        var endHour = ((latest + 59) / 60).coerceIn(startHour + 1, 24)
        if (endHour - startHour < MIN_HOURS) {
            endHour = (startHour + MIN_HOURS).coerceAtMost(24)
            startHour = (endHour - MIN_HOURS).coerceAtLeast(0)
        }
        val start = startHour * 60
        val end = endHour * 60

        val laneEnds = mutableListOf<Int>()
        val blocks = mutableListOf<TimelineBlock>()
        timed.forEach { (event, eventStart, eventEnd) ->
            var lane = laneEnds.indexOfFirst { it <= eventStart }
            if (lane == -1) {
                lane = laneEnds.size
                laneEnds.add(eventEnd)
            } else {
                laneEnds[lane] = eventEnd
            }
            blocks += TimelineBlock(
                event = event,
                startMinute = (eventStart - start).coerceAtLeast(0),
                endMinute = (eventEnd - start).coerceAtMost(end - start),
                lane = lane,
                laneCount = 1
            )
        }
        val laneCount = laneEnds.size.coerceAtLeast(1)
        val placed = blocks.map { it.copy(laneCount = laneCount) }

        return TimelineModel(start, end, placed, largestFreeSlot(timed, start, nowMinute))
    }

    /**
     * The biggest gap between two meetings that is still ahead of the viewer.
     * Interior gaps are the only useful "free slot" target: the open end of a
     * day already has its own scheduling action in the empty state.
     */
    private fun largestFreeSlot(
        timed: List<Triple<CalendarEvent, Int, Int>>,
        rangeStart: Int,
        nowMinute: Int
    ): FreeSlot? {
        var cursor = rangeStart.coerceAtLeast(nowMinute)
        var best: FreeSlot? = null
        timed.forEach { (_, start, end) ->
            val clippedStart = start.coerceAtLeast(rangeStart)
            val clippedEnd = end
            if (clippedEnd <= clippedStart) return@forEach
            if (clippedStart > cursor) {
                recordGap(cursor, clippedStart, rangeStart, best)?.let { best = it }
            }
            cursor = cursor.coerceAtLeast(clippedEnd)
        }
        return best
    }

    private fun recordGap(
        from: Int,
        to: Int,
        rangeStart: Int,
        current: FreeSlot?
    ): FreeSlot? {
        if (to - from < MIN_FREE_SLOT_MINUTES) return current
        val candidate = FreeSlot(from - rangeStart, to - rangeStart)
        val currentLength = current?.let { it.endMinute - it.startMinute } ?: -1
        return if (candidate.endMinute - candidate.startMinute > currentLength) candidate else current
    }
}
