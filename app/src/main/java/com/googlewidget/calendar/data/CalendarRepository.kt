package com.googlewidget.calendar.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class CalendarRepository(private val context: Context) {

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCalendars(): List<CalendarAccount> {
        if (!hasCalendarPermission()) {
            return emptyList()
        }

        val calendars = mutableListOf<CalendarAccount>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )

        val sortOrder = "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val colorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                val visibleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val displayName = cursor.getString(nameIdx) ?: "Calendar $id"
                    val accountName = cursor.getString(accountIdx) ?: ""
                    val color = cursor.getInt(colorIdx)
                    val visible = cursor.getInt(visibleIdx) == 1

                    calendars.add(
                        CalendarAccount(
                            id = id,
                            displayName = displayName,
                            accountName = accountName,
                            color = color,
                            visible = visible
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return calendars
    }

    fun getUpcomingEvents(
        startMillis: Long,
        endMillis: Long,
        selectedCalendarIds: Set<Long> = emptySet()
    ): CalendarResult<List<CalendarEvent>> {
        if (!hasCalendarPermission()) {
            return CalendarResult.PermissionRequired
        }

        val events = mutableListOf<CalendarEvent>()
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, startMillis)
        ContentUris.appendId(uriBuilder, endMillis)
        val instancesUri = uriBuilder.build()

        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID
        )

        val selectionClauses = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        selectionClauses.add("${CalendarContract.Instances.VISIBLE} = 1")
        selectionClauses.add("${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}")

        if (selectedCalendarIds.isNotEmpty()) {
            val placeholders = selectedCalendarIds.joinToString(",") { "?" }
            selectionClauses.add("${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)")
            selectedCalendarIds.forEach { selectionArgs.add(it.toString()) }
        }

        val selection = selectionClauses.joinToString(" AND ")
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC, ${CalendarContract.Instances.ALL_DAY} DESC"

        try {
            context.contentResolver.query(
                instancesUri,
                projection,
                selection,
                selectionArgs.toTypedArray(),
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID)
                val eventIdIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val displayColorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)
                val calColorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_COLOR)
                val locationIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val eventId = cursor.getLong(eventIdIdx)
                    val rawTitle = cursor.getString(titleIdx)
                    val title = if (rawTitle.isNullOrBlank()) "(No title)" else rawTitle
                    val begin = cursor.getLong(beginIdx)
                    val end = cursor.getLong(endIdx)
                    val allDay = cursor.getInt(allDayIdx) == 1

                    val displayColor = cursor.getInt(displayColorIdx)
                    val calColor = cursor.getInt(calColorIdx)
                    val resolvedColor = when {
                        displayColor != 0 -> displayColor
                        calColor != 0 -> calColor
                        else -> 0xFF1A73E8.toInt()
                    }

                    val location = cursor.getString(locationIdx)?.takeIf { it.isNotBlank() }

                    events.add(
                        CalendarEvent(
                            id = id,
                            eventId = eventId,
                            title = title,
                            begin = begin,
                            end = end,
                            allDay = allDay,
                            color = resolvedColor,
                            location = location
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return CalendarResult.Error(e)
        }

        // Maintain sort: BEGIN ASC, ALL_DAY DESC
        val sortedEvents = events.sortedWith(
            compareBy<CalendarEvent> { it.begin }
                .thenByDescending { it.allDay }
        )

        return CalendarResult.Success(sortedEvents)
    }

    companion object {
        /**
         * Converts event timestamp to LocalDate.
         * Critical Guardrail:
         * All-Day events (ALL_DAY == 1) in CalendarContract are stored in UTC midnight.
         * Must convert ALL_DAY == 1 using ZoneOffset.UTC and timed events using ZoneId.systemDefault().
         */
        fun getEventLocalDate(event: CalendarEvent): LocalDate {
            return if (event.allDay) {
                Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate()
            } else {
                Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }

        fun formatDayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String {
            return when (date) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
            }
        }

        fun formatEventTime(event: CalendarEvent): String {
            if (event.allDay) return "All Day"
            val startTime = Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).toLocalTime()
            val endTime = Instant.ofEpochMilli(event.end).atZone(ZoneId.systemDefault()).toLocalTime()
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            return "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)}"
        }

        fun formatNextUpCountdown(event: CalendarEvent, now: Long = System.currentTimeMillis()): String {
            if (event.allDay) {
                val eventDate = getEventLocalDate(event)
                val today = LocalDate.now()
                return when {
                    eventDate == today -> if (event.end > now) "All Day Today" else "Ended"
                    eventDate == today.plusDays(1) -> "All Day Tomorrow"
                    eventDate > today -> {
                        val days = ChronoUnit.DAYS.between(today, eventDate)
                        "In $days day${if (days > 1) "s" else ""}"
                    }
                    else -> "Ended"
                }
            }
            if (event.begin <= now && event.end > now) {
                val remainingMins = ((event.end - now) / 60000L).coerceAtLeast(1)
                return "Happening now (ends in ${remainingMins}m)"
            }
            if (event.begin > now) {
                val diffMinutes = (event.begin - now) / 60000L
                return when {
                    diffMinutes < 60 -> "Starts in ${diffMinutes.coerceAtLeast(1)} min"
                    diffMinutes < 24 * 60 -> {
                        val hours = diffMinutes / 60
                        val mins = diffMinutes % 60
                        if (mins == 0L) "Starts in ${hours}h" else "Starts in ${hours}h ${mins}m"
                    }
                    else -> {
                        val days = diffMinutes / (24 * 60)
                        val timeStr = Instant.ofEpochMilli(event.begin)
                            .atZone(ZoneId.systemDefault())
                            .toLocalTime()
                            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                        "In $days day${if (days > 1) "s" else ""} at $timeStr"
                    }
                }
            }
            return "Ended"
        }
    }
}
