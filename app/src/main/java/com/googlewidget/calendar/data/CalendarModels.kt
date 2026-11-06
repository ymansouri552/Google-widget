package com.googlewidget.calendar.data

data class CalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val visible: Boolean
)

data class CalendarEvent(
    val id: Long,
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String?
)

sealed interface CalendarResult<out T> {
    data class Success<T>(val data: T) : CalendarResult<T>
    data object PermissionRequired : CalendarResult<Nothing>
    data class Error(val throwable: Throwable) : CalendarResult<Nothing>
}
