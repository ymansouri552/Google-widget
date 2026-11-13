package com.googlewidget.calendar.widget

import androidx.compose.ui.unit.dp
import com.googlewidget.calendar.data.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ThreeDayTimelineGlanceWidgetTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 10)
    private val tomorrow = today.plusDays(1)
    private val dayAfter = today.plusDays(2)
    private val threeDays = listOf(today, tomorrow, dayAfter)

    private fun event(
        id: Long,
        title: String,
        begin: Long,
        end: Long,
        allDay: Boolean = false
    ) = CalendarEvent(
        id = id,
        eventId = id,
        title = title,
        begin = begin,
        end = end,
        allDay = allDay,
        color = 0xFF1A73E8.toInt(),
        location = null
    )

    private fun timeMillis(date: LocalDate, hour: Int, minute: Int): Long {
        return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    private fun layout(
        vararg events: CalendarEvent,
        date: LocalDate = today,
        minHour: Int = ThreeDayTimelineGlanceWidget.RANGE_EARLIEST_HOUR,
        maxHour: Int = ThreeDayTimelineGlanceWidget.RANGE_LATEST_HOUR
    ): List<EventCluster> {
        val (gridStart, gridEnd) = ThreeDayTimelineGlanceWidget.gridBoundsForDay(date, minHour, maxHour, zone)
        return ThreeDayTimelineGlanceWidget.buildDayLayout(
            events = events.toList(),
            gridStartMillis = gridStart,
            gridEndMillis = gridEnd,
            minDurationMinutes = ThreeDayTimelineGlanceWidget.MIN_EVENT_HEIGHT_MINUTES
        )
    }

    private fun visibleRange(vararg events: CalendarEvent): IntRange =
        ThreeDayTimelineGlanceWidget.calculateVisibleRange(threeDays, events.toList(), zone)

    @Test
    fun testEvent16_45To18_15_isOneBoxWithHeight90TimesDpPerMinute() {
        val event1 = event(
            id = 1L,
            title = "Teen 5",
            begin = timeMillis(today, 16, 45),
            end = timeMillis(today, 18, 15)
        )

        val clusters = layout(event1)

        assertEquals("one cluster, one lane: exactly one box", 1, clusters.size)
        val box = clusters[0].lanes[0].single()
        assertEquals((16 - 10) * 60 + 45, box.startMin)
        assertEquals((18 - 10) * 60 + 15, box.endMin)
        assertEquals(90, box.endMin - box.startMin)

        // Box height is exactly the duration scaled, never a per-hour slice.
        assertEquals(90.dp, ThreeDayTimelineGlanceWidget.eventHeightDp(box.endMin - box.startMin, 1f))
        assertEquals(54f, ThreeDayTimelineGlanceWidget.eventHeightDp(box.endMin - box.startMin, 0.6f).value, 0.01f)
    }

    @Test
    fun testVisibleRange_roundsOutToWholeHours_andEnforcesMinSpan() {
        // 16:45 rounds down, 18:15 rounds up, span is exactly 4 hours.
        assertEquals(16..19, visibleRange(event(1, "A", timeMillis(today, 16, 45), timeMillis(today, 18, 15))))

        // 14:00-15:00 spans 2 hours, expanded to the 4-hour minimum.
        assertEquals(14..17, visibleRange(event(1, "B", timeMillis(today, 14, 0), timeMillis(today, 15, 0))))
    }

    @Test
    fun testVisibleRange_neverOutside10To23() {
        // Morning event: range pushed down to the 10:00 bound, expanded to 4 hours.
        assertEquals(10..13, visibleRange(event(1, "Early", timeMillis(today, 8, 0), timeMillis(today, 9, 0))))

        // Late event: 22:00-23:00 is 2 hours, span pulled back to 20..23.
        assertEquals(20..23, visibleRange(event(1, "Late", timeMillis(today, 22, 0), timeMillis(today, 22, 30))))

        // Full-day spread uses the entire allowed range.
        assertEquals(10..23, visibleRange(event(1, "All", timeMillis(today, 10, 0), timeMillis(today, 23, 0))))

        // No events at all: default 4-hour window at the range start.
        assertEquals(10..13, visibleRange())
    }

    @Test
    fun testDpPerMinute_exactFit_thenClampedToMinimum() {
        // (600 - 24 strips) / 540 expanded minutes = 1.0667 dp per minute,
        // but the scale never exceeds MAX_DP_PER_MINUTE so few/short events
        // cannot stretch to fill the widget.
        val scale = ThreeDayTimelineGlanceWidget.calculateDpPerMinute(600f, 24f, 540)
        assertEquals(ThreeDayTimelineGlanceWidget.MAX_DP_PER_MINUTE, scale, 1e-4f)

        // A moderate day still gets its exact-fit scale (between floor and cap).
        val exactFit = ThreeDayTimelineGlanceWidget.calculateDpPerMinute(600f, 24f, 800)
        assertEquals((600f - 24f) / 800f, exactFit, 1e-4f)

        // Not enough height: the scale bottoms out at 0.6 dp per minute.
        assertEquals(ThreeDayTimelineGlanceWidget.MIN_DP_PER_MINUTE,
            ThreeDayTimelineGlanceWidget.calculateDpPerMinute(30f, 12f, 60), 1e-6f)

        // Empty axis (everything collapsed) falls back to the minimum.
        assertEquals(ThreeDayTimelineGlanceWidget.MIN_DP_PER_MINUTE,
            ThreeDayTimelineGlanceWidget.calculateDpPerMinute(0f, 0f, 0), 1e-6f)
    }

    @Test
    fun testFitHierarchy_fullRangePreferredAtReducedScale_beforeWindowFallback() {
        // A 4x3 widget leaves ~154dp after the header reserve. Six busy hours
        // need 216dp at the 0.6 floor — which used to trigger the 2-hour
        // window fallback and blank the widget — but fit at the reduced floor,
        // keeping the entire range visible.
        val available = 154f
        val strips = 24f
        val expanded = 360

        assertFalse(
            ThreeDayTimelineGlanceWidget.fitsAtScale(available, strips, expanded, ThreeDayTimelineGlanceWidget.MIN_DP_PER_MINUTE)
        )
        assertTrue(
            ThreeDayTimelineGlanceWidget.fitsAtScale(available, strips, expanded, ThreeDayTimelineGlanceWidget.FULL_FIT_MIN_DP_PER_MINUTE)
        )
        assertEquals(
            (available - strips) / expanded,
            ThreeDayTimelineGlanceWidget.calculateDpPerMinute(available, strips, expanded, ThreeDayTimelineGlanceWidget.FULL_FIT_MIN_DP_PER_MINUTE),
            1e-6f
        )
    }

    @Test
    fun testMinDurationMinutesForScale_cappedByAxisStretch() {
        assertEquals(40, ThreeDayTimelineGlanceWidget.minDurationMinutesForScale(0.6f))
        assertEquals(40, ThreeDayTimelineGlanceWidget.minDurationMinutesForScale(0.35f))
        assertEquals(24, ThreeDayTimelineGlanceWidget.minDurationMinutesForScale(1f))
        assertEquals(18, ThreeDayTimelineGlanceWidget.minDurationMinutesForScale(2f))
    }

    @Test
    fun testTimeAxis_collapsesEmptyHoursIntoSharedStrips() {
        val axis = ThreeDayTimelineGlanceWidget.buildTimeAxis(10, 18, setOf(12, 13, 17))

        assertEquals(
            listOf(
                BreakRun(10..11) as AxisSegment,
                ExpandedRun(12..13),
                BreakRun(14..16),
                ExpandedRun(17..17),
                BreakRun(18..18)
            ),
            axis.segments
        )
        assertEquals("one strip per collapsed run, not per empty hour", 3, axis.stripCount)
        assertEquals(180, axis.expandedMinutes)

        // Consecutive empty hours merge into a single strip.
        val allEmpty = ThreeDayTimelineGlanceWidget.buildTimeAxis(10, 13, emptySet())
        assertEquals(listOf<AxisSegment>(BreakRun(10..13)), allEmpty.segments)
        assertEquals(0, allEmpty.expandedMinutes)
    }

    @Test
    fun testWindowSpan_andChevronShifting() {
        // (230 - 12) / (0.6 * 60) = 6.05 -> 6 hour window fits at minimum scale.
        assertEquals(6, ThreeDayTimelineGlanceWidget.windowSpanHours(230f))
        assertEquals(2, ThreeDayTimelineGlanceWidget.windowSpanHours(100f))

        // Chevrons shift by 2 hours, clamped to the absolute 10..23 bounds.
        assertEquals(12, ThreeDayTimelineGlanceWidget.shiftedWindowStartHour(10, +2))
        assertEquals(23, ThreeDayTimelineGlanceWidget.shiftedWindowStartHour(22, +2))
        assertEquals(10, ThreeDayTimelineGlanceWidget.shiftedWindowStartHour(9, -2))

        // Renderer re-clamps a saved start to the actual range and span.
        assertEquals(18, ThreeDayTimelineGlanceWidget.clampWindowStart(22, 10..23, 6))
        assertEquals(10, ThreeDayTimelineGlanceWidget.clampWindowStart(5, 10..23, 6))
    }

    @Test
    fun testDefaultWindowStart_currentHour_orNextUpcomingEvent() {
        // Next class at 16:00 lies beyond the 10:00-16:00 window: jump to it.
        assertEquals(16, ThreeDayTimelineGlanceWidget.defaultWindowStartHour(10..23, 6, 10, 16))

        // Next class within the current-hour window: stay on the current hour.
        assertEquals(10, ThreeDayTimelineGlanceWidget.defaultWindowStartHour(10..23, 6, 10, 14))

        // No upcoming events: current hour.
        assertEquals(10, ThreeDayTimelineGlanceWidget.defaultWindowStartHour(10..23, 6, 10, null))

        // Current hour too late for a 6-hour window: pulled back to fit.
        assertEquals(18, ThreeDayTimelineGlanceWidget.defaultWindowStartHour(10..23, 6, 21, null))
    }

    @Test
    fun testDayColumnSegments_emptyRunsStillEmitted_soColumnsStayAligned() {
        val axis = ThreeDayTimelineGlanceWidget.buildTimeAxis(10, 18, setOf(12, 13, 17))

        // A day whose only event is 17:00-17:30.
        val clusters = layout(event(1, "Solo", timeMillis(today, 17, 0), timeMillis(today, 17, 30)),
            minHour = 10, maxHour = 18)
        val segments = ThreeDayTimelineGlanceWidget.buildDayColumnSegments(clusters, axis)

        assertEquals(
            listOf(
                DayColumnSegment.BreakStrip(10..11),
                DayColumnSegment.EventRun(12..13, emptyList()),
                DayColumnSegment.BreakStrip(14..16),
                DayColumnSegment.EventRun(17..17, clusters),
                DayColumnSegment.BreakStrip(18..18)
            ),
            segments
        )

        // A completely empty day allocates the same runs, so heights match.
        val emptySegments = ThreeDayTimelineGlanceWidget.buildDayColumnSegments(emptyList(), axis)
        assertEquals(5, emptySegments.size)
        assertTrue(emptySegments.filterIsInstance<DayColumnSegment.EventRun>().all { it.clusters.isEmpty() })
    }

    @Test
    fun testEventClampedToWindow_marksOnlyCutEdges() {
        // Starts before the 10:00 window opens.
        val early = event(1, "Early", timeMillis(today, 8, 0), timeMillis(today, 11, 30))
        val earlyBox = layout(early).single().lanes[0].single()
        assertEquals(0, earlyBox.startMin)
        assertTrue(earlyBox.clampedTop)
        assertFalse(earlyBox.clampedBottom)

        // Ends after the window closes at midnight (hour 23 is the last shown).
        val late = event(2, "Late", timeMillis(today, 22, 30), timeMillis(tomorrow, 0, 30))
        val lateBox = layout(late).single().lanes[0].single()
        assertEquals((22 - 10) * 60 + 30, lateBox.startMin)
        assertTrue(lateBox.clampedBottom)
        assertFalse(lateBox.clampedTop)
    }

    @Test
    fun testShortEvent_stretchedToMinimumHeight() {
        val short = event(1, "Quick", timeMillis(today, 15, 0), timeMillis(today, 15, 10))

        val box = layout(short).single().lanes[0].single()
        assertEquals((15 - 10) * 60, box.startMin)
        assertEquals(box.startMin + ThreeDayTimelineGlanceWidget.MIN_EVENT_HEIGHT_MINUTES, box.endMin)
    }

    @Test
    fun testNonConcurrentEvents_stackedSequentiallyFullWidth() {
        val event1 = event(1, "Event 1", timeMillis(today, 16, 45), timeMillis(today, 18, 15))
        val event2 = event(2, "Event 2", timeMillis(today, 18, 30), timeMillis(today, 20, 0))

        val clusters = layout(event1, event2)

        assertEquals(2, clusters.size)
        clusters.forEach { cluster ->
            assertEquals("no overlap means a single full-width lane", 1, cluster.lanes.size)
        }
        assertEquals((18 - 10) * 60 + 30, clusters[1].startMin)
    }

    @Test
    fun testTrulyConcurrentEvents_splitIntoSideBySideLanes() {
        val eventA = event(10, "Meeting A", timeMillis(today, 14, 0), timeMillis(today, 15, 0))
        val eventB = event(11, "Meeting B", timeMillis(today, 14, 15), timeMillis(today, 14, 45))

        val clusters = layout(eventA, eventB)

        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].lanes.size)
        assertEquals(0, clusters[0].lanes[0].single().lane)
        assertEquals(1, clusters[0].lanes[1].single().lane)
    }

    @Test
    fun testOvernightEvent_clampedInEachDayColumn() {
        // 18:30 on Day 0 until 12:00 on Day 1.
        val overnightEvent = event(2, "Overnight Shift", timeMillis(today, 18, 30), timeMillis(tomorrow, 12, 0))

        val day0Box = layout(overnightEvent).single().lanes[0].single()
        assertEquals(18 * 60 + 30 - 10 * 60, day0Box.startMin)
        assertEquals((24 - 10) * 60, day0Box.endMin)
        assertTrue("day 0 continuation must be cut at the window bottom", day0Box.clampedBottom)

        val day1Box = layout(overnightEvent, date = tomorrow).single().lanes[0].single()
        assertEquals(0, day1Box.startMin)
        assertEquals(2 * 60, day1Box.endMin)
        assertTrue("day 1 continuation must be cut at the window top", day1Box.clampedTop)
    }

    @Test
    fun testEventOutsideWindow_ignored() {
        val outside = event(
            id = 1L,
            title = "Before window",
            begin = timeMillis(today.minusDays(1), 10, 0),
            end = timeMillis(today.minusDays(1), 11, 0)
        )

        assertTrue(layout(outside).isEmpty())
    }

    @Test
    fun testFullDaySchedule_fitsAtReducedScale_withoutWindowFallback() {
        // Schedule matching user's real class schedule from 11:00 AM to 21:30 PM:
        // 11:00-12:00: Class Tasnim
        // 12:00-14:00: Lunchtime
        // 15:00-16:30: Teen 4
        // 16:45-18:15: 301
        // 18:30-20:00: 103
        // 20:15-21:30: 301
        val events = listOf(
            event(1, "Tasnim", timeMillis(today, 11, 0), timeMillis(today, 12, 0)),
            event(2, "Lunchtime", timeMillis(today, 12, 0), timeMillis(today, 14, 0)),
            event(3, "Teen 4", timeMillis(today, 15, 0), timeMillis(today, 16, 30)),
            event(4, "301", timeMillis(today, 16, 45), timeMillis(today, 18, 15)),
            event(5, "103", timeMillis(today, 18, 30), timeMillis(today, 20, 0)),
            event(6, "301", timeMillis(today, 20, 15), timeMillis(today, 21, 30))
        )

        val hoursRange = ThreeDayTimelineGlanceWidget.calculateVisibleRange(threeDays, events, zone)
        assertEquals(11..22, hoursRange)

        val dayLayout = layout(*events.toTypedArray(), minHour = hoursRange.first, maxHour = hoursRange.last)
        val hoursWithEvents = ThreeDayTimelineGlanceWidget.collectHoursWithEvents(hoursRange.first, listOf(dayLayout))
        val axis = ThreeDayTimelineGlanceWidget.buildTimeAxis(hoursRange.first, hoursRange.last, hoursWithEvents)

        // On a medium-sized resized widget (e.g. 4x4 or 4x5, ~250dp available),
        // the entire day fits cleanly at the reduced scale floor.
        val availableHeightDp = 250f
        val fits = ThreeDayTimelineGlanceWidget.fitsAtScale(
            availableHeightDp = availableHeightDp,
            totalStripHeightDp = axis.totalStripHeightDp,
            expandedMinutes = axis.expandedMinutes,
            dpPerMinute = ThreeDayTimelineGlanceWidget.FULL_FIT_MIN_DP_PER_MINUTE
        )
        assertTrue("Full day schedule must fit on a resized widget without windowing", fits)

        val scale = ThreeDayTimelineGlanceWidget.calculateDpPerMinute(
            availableHeightDp = availableHeightDp,
            totalStripHeightDp = axis.totalStripHeightDp,
            expandedMinutes = axis.expandedMinutes,
            minDpPerMinute = ThreeDayTimelineGlanceWidget.FULL_FIT_MIN_DP_PER_MINUTE
        )
        assertTrue("Scale must be at or above FULL_FIT_MIN_DP_PER_MINUTE", scale >= ThreeDayTimelineGlanceWidget.FULL_FIT_MIN_DP_PER_MINUTE)

        // All 6 events must be laid out in the day column
        val totalBoxes = dayLayout.flatMap { it.lanes }.flatten().size
        assertEquals(6, totalBoxes)
    }
}
