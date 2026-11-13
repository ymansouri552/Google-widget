package com.googlewidget.calendar.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import com.googlewidget.calendar.R
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.CalendarResult
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AgendaGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val selectedCalendarIds = prefs[WidgetConfigKeys.SELECTED_CALENDARS]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
        val opacity = prefs[WidgetConfigKeys.BG_OPACITY] ?: WidgetConfigKeys.DEFAULT_OPACITY
        val themeMode = prefs[WidgetConfigKeys.THEME_MODE] ?: WidgetConfigKeys.THEME_SYSTEM
        val lookaheadDays = prefs[WidgetConfigKeys.LOOKAHEAD_DAYS] ?: WidgetConfigKeys.DEFAULT_LOOKAHEAD_DAYS
        val fontScale = prefs[WidgetConfigKeys.FONT_SCALE] ?: WidgetConfigKeys.FONT_SCALE_NORMAL

        // Fast provideGlance execution: load calendar data on IO before provideContent
        val repository = CalendarRepository(context)
        val now = System.currentTimeMillis()
        val endMillis = now + (lookaheadDays.toLong() * 24 * 60 * 60 * 1000L)

        val calendarResult = withContext(Dispatchers.IO) {
            repository.getUpcomingEvents(now, endMillis, selectedCalendarIds)
        }

        provideContent {
            AgendaWidgetRoot(
                context = context,
                result = calendarResult,
                opacity = opacity,
                themeMode = themeMode,
                fontScale = fontScale,
                lookaheadDays = lookaheadDays,
                noCalendarsSelected = selectedCalendarIds == setOf(WidgetConfigKeys.NO_CALENDAR_PLACEHOLDER.toLong())
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun AgendaWidgetRoot(
    context: Context,
    result: CalendarResult<List<CalendarEvent>>,
    opacity: Float,
    themeMode: String,
    fontScale: String,
    lookaheadDays: Int,
    noCalendarsSelected: Boolean
) {
    val typography = WidgetThemeHelper.getTypography(fontScale)
    val backgroundColor = WidgetThemeHelper.getSurfaceColor(context, themeMode, opacity)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(backgroundColor)
            .cornerRadius(16.dp)
            .padding(10.dp)
    ) {
        when (result) {
            is CalendarResult.PermissionRequired -> {
                PermissionRequiredView(context, themeMode, typography)
            }
            is CalendarResult.Error -> {
                ErrorView(result.throwable.localizedMessage ?: "Failed to load events", themeMode, typography)
            }
            is CalendarResult.Success -> {
                AgendaContent(
                    context = context,
                    events = result.data,
                    themeMode = themeMode,
                    opacity = opacity,
                    typography = typography,
                    lookaheadDays = lookaheadDays,
                    noCalendarsSelected = noCalendarsSelected
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AgendaContent(
    context: Context,
    events: List<CalendarEvent>,
    themeMode: String,
    opacity: Float,
    typography: WidgetTypography,
    lookaheadDays: Int,
    noCalendarsSelected: Boolean
) {
    val today = LocalDate.now()
    val headerDateStr = today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Widget Header
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calendar icon + Date header
            Image(
                provider = ImageProvider(R.drawable.ic_calendar_event),
                contentDescription = "Calendar",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = headerDateStr,
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.widgetHeaderSize
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )

            // Action: Add new event
            val addEventIntent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            Box(
                modifier = GlanceModifier
                    .size(30.dp)
                    .cornerRadius(15.dp)
                    .background(WidgetThemeHelper.getCardBackgroundColor(themeMode, opacity))
                    .clickable(actionStartActivity(addEventIntent)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_calendar_add),
                    contentDescription = "New Event",
                    modifier = GlanceModifier.size(16.dp)
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Action: Refresh
            Box(
                modifier = GlanceModifier
                    .size(30.dp)
                    .cornerRadius(15.dp)
                    .background(WidgetThemeHelper.getCardBackgroundColor(themeMode, opacity))
                    .clickable(actionRunCallback<RefreshActionCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier.size(16.dp)
                )
            }
        }

        // Event List
        if (events.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (noCalendarsSelected) "No calendars selected" else "No events in next $lookaheadDays days",
                        style = TextStyle(
                            color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                            fontSize = typography.subtitleSize
                        )
                    )
                }
            }
        } else {
            // Group events by day using the correct UTC/System timezone per Guardrail 3
            val groupedEvents = events.groupBy { CalendarRepository.getEventLocalDate(it) }

            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                groupedEvents.forEach { (date, dayEvents) ->
                    item {
                        DayHeaderItem(date = date, today = today, themeMode = themeMode, typography = typography)
                    }
                    items(dayEvents) { event ->
                        AgendaEventRow(
                            event = event,
                            themeMode = themeMode,
                            opacity = opacity,
                            typography = typography
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DayHeaderItem(
    date: LocalDate,
    today: LocalDate,
    themeMode: String,
    typography: WidgetTypography
) {
    val dayLabel = CalendarRepository.formatDayHeader(date, today)

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Text(
            text = dayLabel.uppercase(Locale.getDefault()),
            style = TextStyle(
                color = WidgetThemeHelper.getAccentColor(themeMode),
                fontWeight = FontWeight.Bold,
                fontSize = typography.headerSize
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun AgendaEventRow(
    event: CalendarEvent,
    themeMode: String,
    opacity: Float,
    typography: WidgetTypography
) {
    val clickIntent = Intent(Intent.ACTION_VIEW).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetThemeHelper.getCardBackgroundColor(themeMode, opacity))
            .cornerRadius(8.dp)
            .clickable(actionStartActivity(clickIntent))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Event color bar
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(34.dp)
                    .background(Color(event.color))
                    .cornerRadius(2.dp)
            ) {}

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Event Details
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = event.title,
                    style = TextStyle(
                        color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                        fontWeight = FontWeight.Bold,
                        fontSize = typography.titleSize
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.allDay) {
                        Box(
                            modifier = GlanceModifier
                                .background(WidgetThemeHelper.getAccentColor(themeMode))
                                .cornerRadius(4.dp)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "All Day",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = typography.badgeSize
                                )
                            )
                        }
                    } else {
                        Text(
                            text = CalendarRepository.formatEventTime(event),
                            style = TextStyle(
                                color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                                fontSize = typography.subtitleSize
                            )
                        )
                    }

                    if (!event.location.isNullOrBlank()) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = "• ${event.location}",
                            style = TextStyle(
                                color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                                fontSize = typography.subtitleSize
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PermissionRequiredView(
    context: Context,
    themeMode: String,
    typography: WidgetTypography
) {
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_calendar_event),
            contentDescription = "Permission Required",
            modifier = GlanceModifier.size(32.dp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Calendar Permission Needed",
            style = TextStyle(
                color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                fontWeight = FontWeight.Bold,
                fontSize = typography.widgetHeaderSize
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to grant permission in app",
            style = TextStyle(
                color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                fontSize = typography.subtitleSize
            )
        )
        Spacer(modifier = GlanceModifier.height(12.dp))
        Box(
            modifier = GlanceModifier
                .background(WidgetThemeHelper.getAccentColor(themeMode))
                .cornerRadius(8.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .clickable(actionStartActivity(launchIntent))
        ) {
            Text(
                text = "Grant Permission",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.titleSize
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun ErrorView(
    errorMessage: String,
    themeMode: String,
    typography: WidgetTypography
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Unable to load calendar",
            style = TextStyle(
                color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                fontWeight = FontWeight.Bold,
                fontSize = typography.titleSize
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = errorMessage,
            style = TextStyle(
                color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                fontSize = typography.subtitleSize
            ),
            maxLines = 2
        )
    }
}
