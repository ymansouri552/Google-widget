package com.googlewidget.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure-JVM tests for the calendar formatting/date guardrails. These encode the
 * contract the widgets rely on, including the all-day UTC-midnight handling and
 * the date-aware all-day countdowns ("All Day Today" must never appear for
 * future all-day events).
 */
class CalendarRepositoryTest {

    private val today = LocalDate.now()

    private fun event(begin: Long, end: Long, allDay: Boolean = false) = CalendarEvent(
        id = 1L,
        eventId = 1L,
        title = "Test Event",
        begin = begin,
        end = end,
        allDay = allDay,
        color = 0xFF1A73E8.toInt(),
        location = null
    )

    private fun timedMinutesFromNow(startMin: Long, endMin: Long, now: Long): CalendarEvent =
        event(now + startMin * 60_000L, now + endMin * 60_000L)

    private fun allDayEventOn(date: LocalDate): CalendarEvent {
        val begin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return event(begin, end, allDay = true)
    }

    private fun timedOn(date: LocalDate, hour: Int, minute: Int): CalendarEvent {
        val begin = date.atTime(hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.atTime(hour + 1, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return event(begin, end)
    }

    // --- formatNextUpCountdown: timed events -------------------------------------------------

    @Test
    fun countdown_startsInMinutes() {
        val now = System.currentTimeMillis()
        val e = timedMinutesFromNow(25, 85, now)
        assertEquals("Starts in 25 min", CalendarRepository.formatNextUpCountdown(e, now))
    }

    @Test
    fun countdown_startsInHoursAndMinutes() {
        val now = System.currentTimeMillis()
        val e = timedMinutesFromNow(90, 200, now)
        assertEquals("Starts in 1h 30m", CalendarRepository.formatNextUpCountdown(e, now))
    }

    @Test
    fun countdown_startsInWholeHours() {
        val now = System.currentTimeMillis()
        val e = timedMinutesFromNow(120, 240, now)
        assertEquals("Starts in 2h", CalendarRepository.formatNextUpCountdown(e, now))
    }

    @Test
    fun countdown_happeningNow() {
        val now = System.currentTimeMillis()
        val e = timedMinutesFromNow(-20, 40, now)
        assertEquals("Happening now (ends in 40m)", CalendarRepository.formatNextUpCountdown(e, now))
    }

    @Test
    fun countdown_futureBeyondADay() {
        val now = System.currentTimeMillis()
        // Two days and three hours in the future.
        val e = timedMinutesFromNow(2 * 24 * 60 + 180, 2 * 24 * 60 + 240, now)
        val text = CalendarRepository.formatNextUpCountdown(e, now)
        assertTrue("Expected day-style text, was: $text", text.startsWith("In 2 days at "))
    }

    @Test
    fun countdown_ended() {
        val now = System.currentTimeMillis()
        val e = timedMinutesFromNow(-90, -30, now)
        assertEquals("Ended", CalendarRepository.formatNextUpCountdown(e, now))
    }

    // --- formatNextUpCountdown: all-day events (regression: bug #2) ---------------------------

    @Test
    fun countdown_allDayToday() {
        val now = System.currentTimeMillis()
        val e = allDayEventOn(today)
        assertEquals("All Day Today", CalendarRepository.formatNextUpCountdown(e, now))
    }

    @Test
    fun countdown_allDayTomorrow() {
        val now = System.currentTimeMillis()
        val e = allDayEventOn(today.plusDays(1))
        assertEquals("All Day Tomorrow", CalendarRepository.formatNextUpCountdown(e, now))
    }

    @Test
    fun countdown_allDayLaterReturnsDayCount() {
        val now = System.currentTimeMillis()
        val e = allDayEventOn(today.plusDays(4))
        assertEquals("In 4 days", CalendarRepository.formatNextUpCountdown(e, now))
    }

    // --- getEventLocalDate: all-day UTC guardrail ---------------------------------------------

    @Test
    fun eventDate_allDayUsesUtc() {
        val e = allDayEventOn(LocalDate.of(2026, 3, 14))
        assertEquals(LocalDate.of(2026, 3, 14), CalendarRepository.getEventLocalDate(e))
    }

    @Test
    fun eventDate_timedUsesSystemZone() {
        val e = timedOn(LocalDate.of(2026, 6, 1), 9, 30)
        assertEquals(LocalDate.of(2026, 6, 1), CalendarRepository.getEventLocalDate(e))
    }

    // --- formatDayHeader / formatEventTime ----------------------------------------------------

    @Test
    fun dayHeader_relativeLabels() {
        assertEquals("Today", CalendarRepository.formatDayHeader(today, today))
        assertEquals("Tomorrow", CalendarRepository.formatDayHeader(today.plusDays(1), today))
        val later = LocalDate.of(2026, 11, 14)
        val expected = later.format(
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
        )
        assertEquals(expected, CalendarRepository.formatDayHeader(later, LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun eventTime_allDayShowsBadge() {
        val e = allDayEventOn(today)
        assertEquals("All Day", CalendarRepository.formatEventTime(e))
    }
}
