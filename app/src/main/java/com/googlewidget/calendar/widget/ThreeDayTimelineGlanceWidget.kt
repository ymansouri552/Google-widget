package com.googlewidget.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.CalendarResult
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ThreeDayTimelineGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val selectedCalendarIds = prefs[WidgetConfigKeys.SELECTED_CALENDARS]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
        val opacity = prefs[WidgetConfigKeys.BG_OPACITY] ?: WidgetConfigKeys.DEFAULT_OPACITY
        val themeMode = prefs[WidgetConfigKeys.THEME_MODE] ?: WidgetConfigKeys.THEME_SYSTEM
        val dayOffset = (prefs[WidgetConfigKeys.KEY_DAY_OFFSET] ?: 0)
            .coerceIn(WidgetConfigKeys.MIN_DAY_OFFSET, WidgetConfigKeys.MAX_DAY_OFFSET)

        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }

        val today = LocalDate.now()
        val baseDate = today.plusDays(dayOffset.toLong())
        val threeDays = listOf(baseDate, baseDate.plusDays(1), baseDate.plusDays(2))

        val day0 = threeDays.first()
        val day2 = threeDays.last()
        val monthTitle = if (day0.month == day2.month && day0.year == day2.year) {
            day0.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
        } else if (day0.year == day2.year) {
            "${day0.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))} – ${day2.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))}"
        } else {
            "${day0.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))} – ${day2.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))}"
        }

        val repository = CalendarRepository(context)
        val startMillis = day0.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = day2.plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val calendarResult = withContext(Dispatchers.IO) {
            repository.getUpcomingEvents(startMillis, endMillis, selectedCalendarIds)
        }

        provideContent {
            ThreeDayTimelineRoot(
                context = context,
                appWidgetId = appWidgetId,
                result = calendarResult,
                today = today,
                threeDays = threeDays,
                monthTitle = monthTitle,
                opacity = opacity,
                themeMode = themeMode
            )
        }
    }
}

@Composable
private fun ThreeDayTimelineRoot(
    context: Context,
    appWidgetId: Int,
    result: CalendarResult<List<CalendarEvent>>,
    today: LocalDate,
    threeDays: List<LocalDate>,
    monthTitle: String,
    opacity: Float,
    themeMode: String
) {
    val backgroundColor = WidgetThemeHelper.getSurfaceColor(themeMode, opacity)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(backgroundColor)
            .cornerRadius(16.dp)
            .padding(8.dp)
    ) {
        when (result) {
            is CalendarResult.PermissionRequired -> {
                PermissionRequiredThreeDayView(context, themeMode)
            }
            is CalendarResult.Error -> {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = result.throwable.localizedMessage ?: "Failed to load events",
                        style = TextStyle(color = WidgetThemeHelper.getSecondaryTextColor(themeMode))
                    )
                }
            }
            is CalendarResult.Success -> {
                ThreeDayContent(
                    context = context,
                    appWidgetId = appWidgetId,
                    today = today,
                    threeDays = threeDays,
                    events = result.data,
                    monthTitle = monthTitle,
                    themeMode = themeMode
                )
            }
        }
    }
}

@Composable
private fun ThreeDayContent(
    context: Context,
    appWidgetId: Int,
    today: LocalDate,
    threeDays: List<LocalDate>,
    events: List<CalendarEvent>,
    monthTitle: String,
    themeMode: String
) {
    val timedEvents = events.filter { !it.allDay }

    // Auto-calculate hour span (default 8 to 21, auto-expanding if earlier/later events exist)
    var minHour = 8
    var maxHour = 21

    timedEvents.forEach { event ->
        val startZdt = Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault())
        val endZdt = Instant.ofEpochMilli(event.end).atZone(ZoneId.systemDefault())
        if (startZdt.toLocalDate() in threeDays || endZdt.toLocalDate() in threeDays) {
            val startH = startZdt.hour
            val endH = if (endZdt.minute > 0) endZdt.hour + 1 else endZdt.hour
            minHour = minOf(minHour, startH)
            maxHour = maxOf(maxHour, endH)
        }
    }
    minHour = minHour.coerceIn(0, 23)
    maxHour = maxHour.coerceIn(minHour + 1, 23)
    val hoursRange = (minHour..maxHour).toList()

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // 1. Top Bar
        WidgetTopBar(
            context = context,
            appWidgetId = appWidgetId,
            monthTitle = monthTitle,
            todayDayOfMonth = today.dayOfMonth,
            onPrevClick = actionRunCallback<ThreeDayPrevAction>(),
            onNextClick = actionRunCallback<ThreeDayNextAction>(),
            themeMode = themeMode
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        // 2. Fixed 3-Day Header & All-Day Section
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left gutter placeholder aligning with 32dp hourly gutter
            Spacer(modifier = GlanceModifier.width(32.dp))

            // 3 Day Columns
            threeDays.forEach { date ->
                val isToday = (date == today)
                val dayAllDayEvents = events.filter { it.allDay && CalendarRepository.getEventLocalDate(it) == date }
                val dateUri = CalendarContract.CONTENT_URI.buildUpon()
                    .appendPath("time")
                    .let { ContentUris.appendId(it, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                    .build()
                val openDateIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = dateUri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: Date Number + 3-letter Day Name
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(openDateIntent)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = TextStyle(
                                color = if (isToday) ColorProvider(Color(0xFF1A73E8)) else WidgetThemeHelper.getPrimaryTextColor(themeMode),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
                            style = TextStyle(
                                color = if (isToday) ColorProvider(Color(0xFF1A73E8)) else WidgetThemeHelper.getSecondaryTextColor(themeMode),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // All-day event pills
                    if (dayAllDayEvents.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        dayAllDayEvents.take(2).forEach { ev ->
                            val evUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.eventId)
                            val evIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = evUri
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .cornerRadius(4.dp)
                                    .background(Color(ev.color))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                    .clickable(actionStartActivity(evIntent)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = ev.title,
                                    style = TextStyle(
                                        color = ColorProvider(WidgetThemeHelper.getEventTextColor(Color(ev.color))),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = GlanceModifier.height(2.dp))
                        }
                    }
                }
            }
        }

        // Horizontal divider between fixed header and scrollable timeline
        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WidgetThemeHelper.getDividerColor(themeMode))
        )

        // 3. Scrollable Hourly Timeline Grid (LazyColumn)
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(hoursRange) { hour ->
                HourlyTimelineRow(
                    hour = hour,
                    threeDays = threeDays,
                    timedEvents = timedEvents,
                    themeMode = themeMode,
                    minHour = minHour,
                    maxHour = maxHour
                )
            }
        }
    }
}

@Composable
private fun HourlyTimelineRow(
    hour: Int,
    threeDays: List<LocalDate>,
    timedEvents: List<CalendarEvent>,
    themeMode: String,
    minHour: Int,
    maxHour: Int
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left gutter: 32dp width hour label
        Box(
            modifier = GlanceModifier
                .width(32.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = hour.toString(),
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        // 3 Day Cells across
        threeDays.forEach { date ->
            val slotStart = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val slotEnd = if (hour == 23) {
                date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                date.atTime(hour + 1, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            // Render every event overlapping this slot. Concurrent events share the
            // cell width equally instead of only the first one being visible.
            val overlappingEvents = timedEvents
                .filter { it.begin < slotEnd && it.end > slotStart }
                .sortedBy { it.begin }

            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
            ) {
                if (overlappingEvents.isEmpty()) {
                    // Empty hour cell with subtle bottom divider line
                    val openSlotUri = CalendarContract.CONTENT_URI.buildUpon()
                        .appendPath("time")
                        .let { ContentUris.appendId(it, slotStart) }
                        .build()
                    val openSlotIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = openSlotUri
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clickable(actionStartActivity(openSlotIntent))
                    ) {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Spacer(modifier = GlanceModifier.defaultWeight())
                            Spacer(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(WidgetThemeHelper.getDividerColor(themeMode))
                            )
                        }
                    }
                } else {
                    Row(modifier = GlanceModifier.fillMaxHeight()) {
                        overlappingEvents.forEach { activeEvent ->
                            EventChunk(
                                activeEvent = activeEvent,
                                slotStart = slotStart,
                                slotEnd = slotEnd,
                                hour = hour,
                                minHour = minHour,
                                maxHour = maxHour,
                                themeMode = themeMode,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventChunk(
    activeEvent: CalendarEvent,
    slotStart: Long,
    slotEnd: Long,
    hour: Int,
    minHour: Int,
    maxHour: Int,
    themeMode: String,
    modifier: GlanceModifier
) {
    val isStart = activeEvent.begin >= slotStart || hour == minHour
    val isEnd = activeEvent.end <= slotEnd || hour == maxHour
    val eventColor = Color(activeEvent.color)

    val evUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, activeEvent.eventId)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        data = evUri
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    val startZdt = Instant.ofEpochMilli(activeEvent.begin).atZone(ZoneId.systemDefault())
    val endZdt = Instant.ofEpochMilli(activeEvent.end).atZone(ZoneId.systemDefault())
    val timeRangeStr = "${startZdt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))}-${endZdt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))}"

    val onEventColor = WidgetThemeHelper.getEventTextColor(eventColor)
    val onEventTextColor = ColorProvider(onEventColor)
    val onEventTimeColor = ColorProvider(onEventColor.copy(alpha = 0.75f))
    val eventColumnModifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)

    when {
        isStart && isEnd -> {
            // Single-hour event block
            Box(
                modifier = modifier
                    .fillMaxHeight()
                    .padding(start = 1.dp, end = 1.dp, top = 2.dp, bottom = 2.dp)
                    .cornerRadius(4.dp)
                    .background(eventColor)
                    .clickable(actionStartActivity(viewIntent))
            ) {
                Column(modifier = eventColumnModifier) {
                    Text(
                        text = activeEvent.title,
                        style = TextStyle(color = onEventTextColor, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        maxLines = 1
                    )
                    Text(
                        text = timeRangeStr,
                        style = TextStyle(color = onEventTimeColor, fontSize = 9.sp),
                        maxLines = 1
                    )
                }
            }
        }
        isStart && !isEnd -> {
            // Top hour cell of multi-hour block
            Box(
                modifier = modifier
                    .fillMaxHeight()
                    .padding(start = 1.dp, end = 1.dp, top = 2.dp, bottom = 0.dp)
                    .cornerRadius(4.dp)
                    .background(eventColor)
                    .clickable(actionStartActivity(viewIntent))
            ) {
                Column(modifier = eventColumnModifier) {
                    Text(
                        text = activeEvent.title,
                        style = TextStyle(color = onEventTextColor, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        maxLines = 1
                    )
                    Text(
                        text = timeRangeStr,
                        style = TextStyle(color = onEventTimeColor, fontSize = 9.sp),
                        maxLines = 1
                    )
                }
            }
        }
        !isStart && !isEnd -> {
            // Continuing middle hour cell of multi-hour block (seamless merge)
            Box(
                modifier = modifier
                    .fillMaxHeight()
                    .padding(start = 1.dp, end = 1.dp)
                    .background(eventColor)
                    .clickable(actionStartActivity(viewIntent))
            ) {}
        }
        else -> {
            // Ending hour cell of multi-hour block with 1dp boundary divider
            Column(
                modifier = modifier
                    .fillMaxHeight()
                    .padding(start = 1.dp, end = 1.dp)
                    .clickable(actionStartActivity(viewIntent))
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .cornerRadius(4.dp)
                        .background(eventColor)
                ) {}
                Spacer(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WidgetThemeHelper.getDividerColor(themeMode))
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredThreeDayView(context: Context, themeMode: String) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Calendar Access Required",
            style = TextStyle(
                color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier
                .cornerRadius(8.dp)
                .background(WidgetThemeHelper.getAccentColor(themeMode))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Grant Permission",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}
