package com.googlewidget.calendar.ui.app

import com.googlewidget.calendar.data.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val ZONE = ZoneOffset.UTC
private val DAY = LocalDate.of(2026, 1, 23)

private fun at(hour: Int, minute: Int = 0): Long =
    DAY.atTime(hour, minute).toInstant(ZONE).toEpochMilli()

private fun event(title: String, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) =
    CalendarEvent(
        id = title.hashCode().toLong(),
        eventId = title.hashCode().toLong(),
        title = title,
        begin = at(startHour, startMinute),
        end = at(endHour, endMinute),
        allDay = false,
        color = 0,
        location = null
    )

class TimelineLayoutTest {

    @Test
    fun emptyDayShowsAtLeastEightHours() {
        val model = TimelineLayout.build(emptyList(), at(11, 30), ZONE)
        assertTrue(model.endMinute - model.startMinute >= 8 * 60)
        assertTrue(model.hourCount >= 8)
        assertEquals(emptyList<TimelineBlock>(), model.blocks)
    }

    @Test
    fun blocksUseMinutesFromRangeStartAndSplitOverlapsIntoLanes() {
        val events = listOf(
            event("Standup", 11, 0, 11, 30),
            event("Review", 11, 15, 12, 0)
        )
        val model = TimelineLayout.build(events, at(10), ZONE)

        assertEquals(2, model.blocks.size)
        assertEquals(0, model.blocks[0].lane)
        assertEquals(1, model.blocks[1].lane)
        assertEquals(2, model.blocks[0].laneCount)
        assertEquals("11:00", minuteLabel(model.startMinute + model.blocks[0].startMinute))
        assertEquals("11:30", minuteLabel(model.startMinute + model.blocks[0].endMinute))
    }

    @Test
    fun largestGapBecomesTheFreeSlot() {
        val events = listOf(
            event("Early", 11, 0, 11, 30),
            event("Late", 14, 0, 14, 30)
        )
        val model = TimelineLayout.build(events, at(10), ZONE)

        val slot = checkNotNull(model.freeSlot)
        assertEquals("11:30", minuteLabel(model.startMinute + slot.startMinute))
        assertEquals("14:00", minuteLabel(model.startMinute + slot.endMinute))
    }

    @Test
    fun shortGapsAreNotOfferedAsSchedulingActions() {
        val events = listOf(
            event("First", 11, 0, 11, 30),
            event("Second", 11, 45, 12, 0)
        )
        assertNull(TimelineLayout.build(events, at(10), ZONE).freeSlot)
    }

    @Test
    fun freeSlotNeverStartsBeforeNow() {
        val events = listOf(
            event("Morning", 9, 0, 9, 30),
            event("Afternoon", 14, 0, 14, 30)
        )
        val model = TimelineLayout.build(events, at(12), ZONE)
        val slot = checkNotNull(model.freeSlot)
        assert(slot.startMinute + model.startMinute >= 12 * 60)
    }
}
