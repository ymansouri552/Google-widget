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
import androidx.glance.layout.size
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

class WeeklyCalendarGlanceWidget : GlanceAppWidget() {

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
        val targetDate = today.plusDays(dayOffset.toLong())
        // Follow the device locale's first day of week (Sunday/Monday/etc.) instead
        // of hardcoding a Sunday-start week with S/M/T day letters.
        val locale = Locale.getDefault()
        val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
        val startOfWeek = targetDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val weekDates = (0L..6L).map { startOfWeek.plusDays(it) }
        val dayLetters = (0L..6L).map {
            startOfWeek.plusDays(it).dayOfWeek.getDisplayName(
                java.time.format.TextStyle.NARROW, locale
            )
        }

        val firstDay = weekDates.first()
        val lastDay = weekDates.last()
        val monthTitle = if (firstDay.month == lastDay.month && firstDay.year == lastDay.year) {
            firstDay.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
        } else if (firstDay.year == lastDay.year) {
            "${firstDay.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))} – ${lastDay.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))}"
        } else {
            "${firstDay.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))} – ${lastDay.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))}"
        }

        val repository = CalendarRepository(context)
        val startMillis = firstDay.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = lastDay.plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val calendarResult = withContext(Dispatchers.IO) {
            repository.getUpcomingEvents(startMillis, endMillis, selectedCalendarIds)
        }

        provideContent {
            WeeklyWidgetRoot(
                context = context,
                appWidgetId = appWidgetId,
                result = calendarResult,
                today = today,
                weekDates = weekDates,
                monthTitle = monthTitle,
                dayLetters = dayLetters,
                opacity = opacity,
                themeMode = themeMode
            )
        }
    }
}

@Composable
private fun WeeklyWidgetRoot(
    context: Context,
    appWidgetId: Int,
    result: CalendarResult<List<CalendarEvent>>,
    today: LocalDate,
    weekDates: List<LocalDate>,
    monthTitle: String,
    dayLetters: List<String>,
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
                PermissionRequiredWeeklyView(context, themeMode)
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
                val eventsByDate = result.data.groupBy { CalendarRepository.getEventLocalDate(it) }
                WeeklyContent(
                    context = context,
                    appWidgetId = appWidgetId,
                    today = today,
                    weekDates = weekDates,
                    eventsByDate = eventsByDate,
                    monthTitle = monthTitle,
                    dayLetters = dayLetters,
                    themeMode = themeMode
                )
            }
        }
    }
}

@Composable
private fun WeeklyContent(
    context: Context,
    appWidgetId: Int,
    today: LocalDate,
    weekDates: List<LocalDate>,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    monthTitle: String,
    dayLetters: List<String>,
    themeMode: String
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // 1. Top Bar
        WidgetTopBar(
            context = context,
            appWidgetId = appWidgetId,
            monthTitle = monthTitle,
            todayDayOfMonth = today.dayOfMonth,
            onPrevClick = actionRunCallback<WeeklyPrevAction>(),
            onNextClick = actionRunCallback<WeeklyNextAction>(),
            themeMode = themeMode
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        // 2. Day-of-Week Header Row (S, M, T, W, T, F, S)
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dayLetters.forEach { letter ->
                Box(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        style = TextStyle(
                            color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        // Thin horizontal divider line
        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WidgetThemeHelper.getDividerColor(themeMode))
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        // 3. 7-Day Columns Grid
        Row(modifier = GlanceModifier.fillMaxSize()) {
            weekDates.forEachIndexed { index, date ->
                val dayEvents = eventsByDate[date] ?: emptyList()
                val isToday = (date == today)
                DayColumn(
                    date = date,
                    isToday = isToday,
                    events = dayEvents,
                    themeMode = themeMode,
                    showDivider = index < 6,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    isToday: Boolean,
    events: List<CalendarEvent>,
    themeMode: String,
    showDivider: Boolean,
    modifier: GlanceModifier
) {
    val dateUri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .let { ContentUris.appendId(it, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
        .build()
    val openDateIntent = Intent(Intent.ACTION_VIEW).apply {
        data = dateUri
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Row(modifier = modifier) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(horizontal = 1.dp)
        ) {
            // Date number header
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clickable(actionStartActivity(openDateIntent)),
                contentAlignment = Alignment.Center
            ) {
                if (isToday) {
                    Box(
                        modifier = GlanceModifier
                            .size(22.dp)
                            .cornerRadius(11.dp)
                            .background(Color(0xFF1A73E8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                } else {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = TextStyle(
                            color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Event Chips Stack (up to 4 items)
            if (events.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(openDateIntent))
                ) {}
            } else {
                val maxVisible = 4
                val visibleEvents = if (events.size > maxVisible) events.take(maxVisible - 1) else events
                val overflowCount = events.size - visibleEvents.size

                visibleEvents.forEach { event ->
                    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = eventUri
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val eventTextColor = WidgetThemeHelper.getEventTextColor(Color(event.color))
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(4.dp)
                            .background(Color(event.color))
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .clickable(actionStartActivity(viewIntent))
                    ) {
                        Text(
                            text = event.title,
                            style = TextStyle(
                                color = ColorProvider(eventTextColor),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                }

                if (overflowCount > 0) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(openDateIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$overflowCount more",
                            style = TextStyle(
                                color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (showDivider) {
            Spacer(
                modifier = GlanceModifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(WidgetThemeHelper.getDividerColor(themeMode))
            )
        }
    }
}

@Composable
private fun PermissionRequiredWeeklyView(context: Context, themeMode: String) {
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
