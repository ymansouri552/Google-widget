package com.googlewidget.calendar.data

import java.time.LocalDate
import java.util.UUID

/** A personal to-do. Dates are ISO yyyy-MM-dd so they stay readable in storage. */
data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val dueDate: String? = null,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TaskFilter { ALL, TODAY, UPCOMING, NO_DATE, COMPLETED }

/**
 * Tasks live in one DataStore string-set entry each. Fields are escaped so a
 * title containing a separator or newline cannot corrupt the record.
 */
object TaskCodec {

    private const val SEPARATOR = '|'

    fun encode(task: TaskItem): String = listOf(
        task.id, task.title, task.note, task.dueDate.orEmpty(),
        if (task.done) "1" else "0", task.createdAt.toString()
    ).joinToString(SEPARATOR.toString()) { escape(it) }

    fun decode(raw: String): TaskItem? {
        val parts = split(raw)
        if (parts.size != 6) return null
        val id = parts[0]
        val title = parts[1]
        if (id.isBlank() || title.isBlank()) return null
        return TaskItem(
            id = id,
            title = title,
            note = parts[2],
            dueDate = parts[3].ifBlank { null },
            done = parts[4] == "1",
            createdAt = parts[5].toLongOrNull() ?: 0L
        )
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(SEPARATOR.toString(), "\\|")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun split(raw: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        raw.forEach { char ->
            when {
                escaped -> {
                    current.append(
                        when (char) {
                            'n' -> '\n'
                            'r' -> '\r'
                            else -> char
                        }
                    )
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == SEPARATOR -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
}

/** Pure task ordering and filtering, shared by every screen. */
object TaskLogic {

    fun bucket(task: TaskItem, today: LocalDate): String {
        if (task.done) return "Completed"
        val due = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return when {
            due == null -> "No date"
            due.isBefore(today) -> "Overdue"
            due == today -> "Today"
            else -> "Upcoming"
        }
    }

    private val bucketOrder = listOf("Overdue", "Today", "Upcoming", "No date", "Completed")

    fun sort(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> =
        tasks.sortedWith(
            compareBy<TaskItem> { bucketOrder.indexOf(bucket(it, today)) }
                .thenBy { it.dueDate ?: "9999-12-31" }
                .thenByDescending { it.createdAt }
        )

    fun filter(tasks: List<TaskItem>, filter: TaskFilter, today: LocalDate): List<TaskItem> =
        sort(
            when (filter) {
                TaskFilter.ALL -> tasks
                TaskFilter.TODAY -> tasks.filter { bucket(it, today) == "Today" }
                TaskFilter.UPCOMING -> tasks.filter { bucket(it, today) == "Upcoming" }
                TaskFilter.NO_DATE -> tasks.filter { bucket(it, today) == "No date" }
                TaskFilter.COMPLETED -> tasks.filter { it.done }
            },
            today
        )

    fun forDate(tasks: List<TaskItem>, date: LocalDate): List<TaskItem> =
        sort(tasks.filter { it.dueDate == date.toString() || it.dueDate == null }, date)
}
