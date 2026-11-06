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

class NextUpGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val selectedCalendarIds = prefs[WidgetConfigKeys.SELECTED_CALENDARS]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
        val opacity = prefs[WidgetConfigKeys.BG_OPACITY] ?: WidgetConfigKeys.DEFAULT_OPACITY
        val themeMode = prefs[WidgetConfigKeys.THEME_MODE] ?: WidgetConfigKeys.THEME_SYSTEM
        val fontScale = prefs[WidgetConfigKeys.FONT_SCALE] ?: WidgetConfigKeys.FONT_SCALE_NORMAL

        // Fast provideGlance execution: load calendar data on IO before provideContent
        val repository = CalendarRepository(context)
        val now = System.currentTimeMillis()
        val endMillis = now + (3 * 24 * 60 * 60 * 1000L) // 3-day window for immediate upcoming

        val calendarResult = withContext(Dispatchers.IO) {
            repository.getUpcomingEvents(now - 60_000L, endMillis, selectedCalendarIds)
        }

        provideContent {
            NextUpWidgetRoot(
                context = context,
                result = calendarResult,
                opacity = opacity,
                themeMode = themeMode,
                fontScale = fontScale,
                now = now
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun NextUpWidgetRoot(
    context: Context,
    result: CalendarResult<List<CalendarEvent>>,
    opacity: Float,
    themeMode: String,
    fontScale: String,
    now: Long
) {
    val typography = WidgetThemeHelper.getTypography(fontScale)
    val backgroundColor = WidgetThemeHelper.getSurfaceColor(themeMode, opacity)

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
                PermissionStrip(context, themeMode, typography)
            }
            is CalendarResult.Error -> {
                ErrorStrip(result.throwable.localizedMessage ?: "Error", themeMode, typography)
            }
            is CalendarResult.Success -> {
                NextUpContent(
                    events = result.data,
                    themeMode = themeMode,
                    opacity = opacity,
                    typography = typography,
                    now = now
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NextUpContent(
    events: List<CalendarEvent>,
    themeMode: String,
    opacity: Float,
    typography: WidgetTypography,
    now: Long
) {
    // Only consider events that have not ended yet
    val activeEvents = events.filter { it.end > now }
    val nextEvent = activeEvents.firstOrNull()

    val today = LocalDate.now()
    val otherEventsToday = if (nextEvent != null) {
        activeEvents.filter { event ->
            event.id != nextEvent.id && CalendarRepository.getEventLocalDate(event) == today
        }
    } else {
        emptyList()
    }

    if (nextEvent == null) {
        EmptyNextUpStrip(themeMode, opacity, typography)
    } else {
        ActiveNextUpStrip(
            nextEvent = nextEvent,
            otherCountToday = otherEventsToday.size,
            themeMode = themeMode,
            opacity = opacity,
            typography = typography,
            now = now
        )
    }
}

@androidx.compose.runtime.Composable
private fun ActiveNextUpStrip(
    nextEvent: CalendarEvent,
    otherCountToday: Int,
    themeMode: String,
    opacity: Float,
    typography: WidgetTypography,
    now: Long
) {
    val clickIntent = Intent(Intent.ACTION_VIEW).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, nextEvent.eventId)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, nextEvent.begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, nextEvent.end)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    val countdownText = CalendarRepository.formatNextUpCountdown(nextEvent, now)
    val secondaryText = when {
        otherCountToday == 1 -> "1 other event today"
        otherCountToday > 1 -> "$otherCountToday other events today"
        else -> "No more events today"
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(clickIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical accent bar
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(44.dp)
                .background(Color(nextEvent.color))
                .cornerRadius(2.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        // Center: Event title + countdown + secondary count
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Text(
                text = nextEvent.title,
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.titleSize
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Timing indicator line
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = countdownText,
                    style = TextStyle(
                        color = WidgetThemeHelper.getAccentColor(themeMode),
                        fontWeight = FontWeight.Medium,
                        fontSize = typography.subtitleSize
                    ),
                    maxLines = 1
                )

                if (!nextEvent.allDay) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "• ${CalendarRepository.formatEventTime(nextEvent)}",
                        style = TextStyle(
                            color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                            fontSize = typography.subtitleSize
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Secondary line: other events remaining today
            Text(
                text = secondaryText,
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                    fontSize = typography.badgeSize
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Right Action: Refresh
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
}

@androidx.compose.runtime.Composable
private fun EmptyNextUpStrip(
    themeMode: String,
    opacity: Float,
    typography: WidgetTypography
) {
    val addEventIntent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(addEventIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_calendar_event),
            contentDescription = "No Events",
            modifier = GlanceModifier.size(24.dp)
        )

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "No upcoming events",
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.titleSize
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "Tap to schedule an event",
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                    fontSize = typography.subtitleSize
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

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
}

@androidx.compose.runtime.Composable
private fun PermissionStrip(
    context: Context,
    themeMode: String,
    typography: WidgetTypography
) {
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(launchIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_calendar_event),
            contentDescription = "Permission Required",
            modifier = GlanceModifier.size(24.dp)
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "Permission Required",
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.titleSize
                )
            )
            Text(
                text = "Tap to grant calendar access",
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                    fontSize = typography.subtitleSize
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun ErrorStrip(
    message: String,
    themeMode: String,
    typography: WidgetTypography
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Error: $message",
            style = TextStyle(
                color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                fontSize = typography.subtitleSize
            ),
            maxLines = 1
        )
    }
}
