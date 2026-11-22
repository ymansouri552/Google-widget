package com.googlewidget.calendar.widget

import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.WidgetConfigKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class WeeklyCalendarLogicTest {

    private val locale = Locale.US
    // Wednesday.
    private val today = LocalDate.of(2026, 11, 18)

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

    private fun millis(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // Week geometry

    @Test
    fun rollingToday_startsOnToday_notOnTheLocaleWeekday() {
        assertEquals(
            (0..6).map { today.plusDays(it.toLong()) },
            WeeklyCalendarLogic.weekDates(today, 0, WidgetConfigKeys.WEEK_START_TODAY, locale)
        )
    }

    @Test
    fun fixedWeekStarts_anchorOnTheirWeekday_andChevronsMoveSevenDays() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 17),
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 20)
            ),
            WeeklyCalendarLogic.weekDates(today, 0, WidgetConfigKeys.WEEK_START_SATURDAY, locale)
        )
        assertEquals(
            LocalDate.of(2026, 11, 15),
            WeeklyCalendarLogic.weekDates(today, 0, WidgetConfigKeys.WEEK_START_SUNDAY, locale).first()
        )
        assertEquals(
            LocalDate.of(2026, 11, 16),
            WeeklyCalendarLogic.weekDates(today, 0, WidgetConfigKeys.WEEK_START_MONDAY, locale).first()
        )

        val nextWeek = WeeklyCalendarLogic.weekDates(today, 7, WidgetConfigKeys.WEEK_START_SUNDAY, locale)
        assertEquals(LocalDate.of(2026, 11, 22), nextWeek.first())
        assertEquals(LocalDate.of(2026, 11, 28), nextWeek.last())

        val prevWeek = WeeklyCalendarLogic.weekDates(today, -7, WidgetConfigKeys.WEEK_START_SUNDAY, locale)
        assertEquals(LocalDate.of(2026, 11, 8), prevWeek.first())
    }

    @Test
    fun weekDates_useTheSameClampAsTheThreeDayWidget() {
        val far = WeeklyCalendarLogic.weekDates(today, 500, WidgetConfigKeys.WEEK_START_SUNDAY, locale)
        assertEquals(
            WeeklyCalendarLogic.weekDates(today, 90, WidgetConfigKeys.WEEK_START_SUNDAY, locale),
            far
        )
    }

    // Range label

    @Test
    fun rangeLabel_formatsWithinMonthAcrossMonthAndAcrossYear() {
        assertEquals(
            "14-20 Nov",
            WeeklyCalendarLogic.rangeLabel(
                LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 20), locale
            )
        )
        assertEquals(
            "28 Nov - 4 Dec",
            WeeklyCalendarLogic.rangeLabel(
                LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 4), locale
            )
        )
        assertEquals(
            "30 Dec 2026 - 5 Jan 2027",
            WeeklyCalendarLogic.rangeLabel(
                LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 5), locale
            )
        )
    }

    // Day selection

    @Test
    fun selectedDate_storedDayWinsOnlyWhileVisible_thenToday_thenFirstDay() {
        val week = WeeklyCalendarLogic.weekDates(
            today, 0, WidgetConfigKeys.WEEK_START_SUNDAY, locale
        ) // 15..21 Nov

        assertEquals(
            LocalDate.of(2026, 11, 16),
            WeeklyCalendarLogic.selectedDateFor(week, "2026-11-16", today)
        )
        assertEquals(
            today,
            WeeklyCalendarLogic.selectedDateFor(week, null, today)
        )
        assertEquals(
            "a stale or invalid selection is ignored",
            today,
            WeeklyCalendarLogic.selectedDateFor(week, "not-a-date", today)
        )
        assertEquals(
            "a selection outside the new week resets to today",
            today,
            WeeklyCalendarLogic.selectedDateFor(week, "2026-11-25", today)
        )
        assertEquals(
            "when today is not visible, the first day is used",
            LocalDate.of(2026, 11, 22),
            WeeklyCalendarLogic.selectedDateFor(week.map { it.plusDays(7) }, null, today)
        )
    }

    // Offset and reset

    @Test
    fun effectiveOffset_expiresAfterTenMinutes_andNeverInteractedMeansToday() {
        val now = 1_000_000_000L
        assertEquals(7, WeeklyCalendarLogic.effectiveOffset(7, now - 9 * 60_000L, now))
        assertEquals(0, WeeklyCalendarLogic.effectiveOffset(7, now - 11 * 60_000L, now))
        assertEquals(0, WeeklyCalendarLogic.effectiveOffset(7, 0L, now))
        assertEquals(0, WeeklyCalendarLogic.effectiveOffset(0, now, now))
    }

    // Height-driven row counts

    @Test
    fun rowCounts_scaleWithHeight_andStayWithinGlanceChildLimit() {
        assertEquals(WeeklyCalendarLogic.MAX_AGENDA_ROWS, WeeklyCalendarLogic.agendaRowCount(320f))
        assertEquals(6, WeeklyCalendarLogic.agendaRowCount(200f))
        assertEquals(3, WeeklyCalendarLogic.agendaRowCount(120f))
        assertEquals(1, WeeklyCalendarLogic.agendaRowCount(8f))
        assertEquals(WeeklyCalendarLogic.MAX_AGENDA_ROWS, WeeklyCalendarLogic.agendaRowCount(1000f))

        assertEquals(WeeklyCalendarLogic.MAX_CHIP_ROWS, WeeklyCalendarLogic.chipRowCount(300f))
        assertEquals(3, WeeklyCalendarLogic.chipRowCount(100f))
        assertEquals(1, WeeklyCalendarLogic.chipRowCount(8f))
    }

    // Agenda ordering and time label

    @Test
    fun agendaOrdering_allDayThenOngoingThenUpcomingThenEnded() {
        val now = millis(today, 12)
        val ended = event(1, "Ended", millis(today, 9), millis(today, 10))
        val upcoming = event(2, "Upcoming", millis(today, 14), millis(today, 15))
        val ongoing = event(3, "Ongoing", millis(today, 11), millis(today, 13))
        val allDay = event(4, "Holiday", millis(today, 0), millis(today, 23), allDay = true)

        assertEquals(
            listOf(allDay, ongoing, upcoming, ended),
            WeeklyCalendarLogic.sortAgendaEvents(listOf(ended, upcoming, allDay, ongoing), now)
        )
    }

    @Test
    fun upcomingOnly_dropsFinishedEvents_andKeepsOngoingAndLaterOnes() {
        val now = millis(today, 12)
        val ended = event(1, "Ended", millis(today, 9), millis(today, 10))
        val ongoing = event(2, "Ongoing", millis(today, 11), millis(today, 13))
        val later = event(3, "Later", millis(today, 14), millis(today, 15))
        val allDay = event(4, "Holiday", millis(today, 0), millis(today, 23), allDay = true)

        assertEquals(
            listOf(ongoing, later, allDay),
            WeeklyCalendarLogic.upcomingOnly(listOf(ended, ongoing, later, allDay), now)
        )
        assertEquals(
            emptyList<CalendarEvent>(),
            WeeklyCalendarLogic.upcomingOnly(listOf(ended), millis(today, 20))
        )
    }

    @Test
    fun timeLabel_uses24HourRangeAndAllDay() {
        val timed = event(1, "Class", millis(today, 9), millis(today, 10))
        assertEquals("09:00–10:00", WeeklyCalendarLogic.timeLabel(timed))
        assertEquals("All day", WeeklyCalendarLogic.timeLabel(event(2, "Holiday", 0, 1, allDay = true)))
    }

    // Overflow paging

    @Test
    fun paginate_showsASingleLeftoverEvent_butAggregatesTwoOrMore() {
        assertEquals(AgendaPaging(3, 0), WeeklyCalendarLogic.paginate(3, 3))
        assertEquals(AgendaPaging(4, 0), WeeklyCalendarLogic.paginate(4, 3))
        assertEquals(AgendaPaging(2, 3), WeeklyCalendarLogic.paginate(5, 3))
        assertEquals(AgendaPaging(1, 9), WeeklyCalendarLogic.paginate(10, 2))
        assertEquals(AgendaPaging(0, 0), WeeklyCalendarLogic.paginate(0, 3))
    }

    // NOW, NEXT and ended events

    @Test
    fun markers_tagTheOngoingAndNextEvents_andIgnoreEndedOnes() {
        val now = millis(today, 12)
        val ended = event(1, "Ended", millis(today, 9), millis(today, 10))
        val ongoing = event(2, "Ongoing", millis(today, 11), millis(today, 13))
        val next = event(3, "Next", millis(today, 14), millis(today, 15))
        val later = event(4, "Later", millis(today, 16), millis(today, 17))

        assertEquals(
            AgendaMarkers(ongoing.eventId, next.eventId),
            WeeklyCalendarLogic.agendaMarkers(listOf(ended, later, next, ongoing), now)
        )
        assertTrue(WeeklyCalendarLogic.isEnded(ended, now))
        assertTrue(WeeklyCalendarLogic.isOngoing(ongoing, now))
    }

    @Test
    fun nextPill_showsCountdownAndHidesWhenNothingIsUpcoming() {
        val now = millis(today, 12)
        val upcoming = event(1, "Teen 5", millis(today, 12, 25), millis(today, 13, 30))
        assertEquals(
            "Next: Teen 5 in 25m",
            WeeklyCalendarLogic.nextPill(listOf(upcoming), now)
        )
        assertEquals(
            "Next: Teen 5 in 1h 30m",
            WeeklyCalendarLogic.nextPill(listOf(event(2, "Teen 5", millis(today, 13, 30), millis(today, 14))), now)
        )
        assertEquals(
            null,
            WeeklyCalendarLogic.nextPill(listOf(event(3, "Ended", millis(today, 9), millis(today, 10))), now)
        )
        assertEquals("45m", WeeklyCalendarLogic.countdownText(now + 45 * 60_000L, now))
    }

    @Test
    fun agendaHeaderLabel_isLocalizedAndPluralized() {
        assertEquals(
            "Wed 18 Nov • 5 events",
            WeeklyCalendarLogic.agendaHeaderLabel(today, 5, locale)
        )
        assertEquals(
            "Wed 18 Nov • 1 event",
            WeeklyCalendarLogic.agendaHeaderLabel(today, 1, locale)
        )
    }

    // Week start default

    @Test
    fun weekStart_defaultsToSaturday_andEveryModeIsRespected() {
        assertEquals(
            WidgetConfigKeys.WEEK_START_SATURDAY,
            WidgetConfigKeys.normalizedWeekStart(null)
        )
        assertEquals(
            LocalDate.of(2026, 11, 14),
            WeeklyCalendarLogic.weekDates(
                today, 0, WidgetConfigKeys.normalizedWeekStart(null), locale
            ).first()
        )
        assertEquals(
            LocalDate.of(2026, 11, 15),
            WeeklyCalendarLogic.weekDates(today, 0, WidgetConfigKeys.WEEK_START_SUNDAY, locale).first()
        )
        assertEquals(
            LocalDate.of(2026, 11, 16),
            WeeklyCalendarLogic.weekDates(today, 0, WidgetConfigKeys.WEEK_START_MONDAY, locale).first()
        )
        assertEquals(
            today,
            WeeklyCalendarLogic.weekDates(today, 0, WidgetConfigKeys.WEEK_START_TODAY, locale).first()
        )
    }

    // Daily stats

    @Test
    fun dailyStats_countsTimedEvents_sumsDurations_andFindsLatestEnd() {
        val first = event(1, "Teen 5", millis(today, 15, 0), millis(today, 16, 30))
        val second = event(2, "Teen 4", millis(today, 16, 45), millis(today, 18, 15))
        val third = event(3, "Teen 4", millis(today, 20, 15), millis(today, 21, 45))
        val allDay = event(4, "Holiday", millis(today, 0), millis(today, 23), allDay = true)

        assertEquals(
            DailyStats(3, 4L * 60 + 30, millis(today, 21, 45)),
            WeeklyCalendarLogic.dailyStats(listOf(allDay, third, first, second))
        )
    }

    @Test
    fun dailyStats_emptyDay_hasNoClassesAndNoFinishTime() {
        assertEquals(
            DailyStats(0, 0L, null),
            WeeklyCalendarLogic.dailyStats(emptyList())
        )
    }

    @Test
    fun formatTeachingTime_rendersHoursMinutesAndZero() {
        assertEquals("4h 30m", WeeklyCalendarLogic.formatTeachingTime(270))
        assertEquals("4h", WeeklyCalendarLogic.formatTeachingTime(240))
        assertEquals("45m", WeeklyCalendarLogic.formatTeachingTime(45))
        assertEquals("0m", WeeklyCalendarLogic.formatTeachingTime(0))
    }
}
