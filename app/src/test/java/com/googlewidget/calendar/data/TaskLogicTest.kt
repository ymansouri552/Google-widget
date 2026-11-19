package com.googlewidget.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskLogicTest {

    private val today = LocalDate.of(2026, 11, 20)

    private fun task(
        title: String,
        due: String? = null,
        done: Boolean = false,
        created: Long = 0L
    ) = TaskItem(
        id = title,
        title = title,
        dueDate = due,
        done = done,
        createdAt = created
    )

    @Test
    fun codec_roundTripsFieldsIncludingSeparatorsAndNewlines() {
        val original = TaskItem(
            id = "id|1",
            title = "Read\\write | draft\nnext line",
            note = "Note with | pipe",
            dueDate = "2026-11-21",
            done = true,
            createdAt = 12345L
        )
        val decoded = TaskCodec.decode(TaskCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun codec_rejectsMalformedRecords() {
        assertNull(TaskCodec.decode("only|three|fields"))
        assertNull(TaskCodec.decode("||note||0|1"))
    }

    @Test
    fun bucket_classifiesByDueDateAndCompletion() {
        assertEquals("Overdue", TaskLogic.bucket(task("a", "2026-11-19"), today))
        assertEquals("Today", TaskLogic.bucket(task("b", "2026-11-20"), today))
        assertEquals("Upcoming", TaskLogic.bucket(task("c", "2026-11-22"), today))
        assertEquals("No date", TaskLogic.bucket(task("d"), today))
        assertEquals("Completed", TaskLogic.bucket(task("e", "2026-11-19", done = true), today))
    }

    @Test
    fun sort_putsOverdueFirstAndCompletedLast() {
        val sorted = TaskLogic.sort(
            listOf(
                task("done", "2026-11-19", done = true),
                task("no date"),
                task("later", "2026-11-25"),
                task("today", "2026-11-20"),
                task("late", "2026-11-18")
            ),
            today
        )
        assertEquals(listOf("late", "today", "later", "no date", "done"), sorted.map { it.title })
    }

    @Test
    fun filter_respectsTheSelectedBucket() {
        val tasks = listOf(
            task("late", "2026-11-18"),
            task("today", "2026-11-20"),
            task("later", "2026-11-25"),
            task("no date"),
            task("done", done = true)
        )
        assertEquals(listOf("today"), TaskLogic.filter(tasks, TaskFilter.TODAY, today).map { it.title })
        assertEquals(listOf("later"), TaskLogic.filter(tasks, TaskFilter.UPCOMING, today).map { it.title })
        assertEquals(listOf("no date"), TaskLogic.filter(tasks, TaskFilter.NO_DATE, today).map { it.title })
        assertEquals(listOf("done"), TaskLogic.filter(tasks, TaskFilter.COMPLETED, today).map { it.title })
        assertTrue(TaskLogic.filter(tasks, TaskFilter.ALL, today).size == 5)
    }

    @Test
    fun forDate_includesUndatedTasks() {
        val result = TaskLogic.forDate(
            listOf(task("today", "2026-11-20"), task("other", "2026-11-21"), task("undated")),
            today
        )
        assertEquals(setOf("today", "undated"), result.map { it.title }.toSet())
    }
}
