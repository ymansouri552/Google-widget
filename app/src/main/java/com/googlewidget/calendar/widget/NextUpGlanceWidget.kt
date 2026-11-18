package com.googlewidget.calendar.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.ColorFilter
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
import com.googlewidget.calendar.ui.theme.CalendarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.googlewidget.calendar.sync.NextUpRefreshScheduler
import java.time.LocalDate

class NextUpGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val selectedCalendarIds = prefs[WidgetConfigKeys.SELECTED_CALENDARS]
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
        val opacity = prefs[WidgetConfigKeys.BG_OPACITY] ?: WidgetConfigKeys.DEFAULT_OPACITY
        val fontScale = prefs[WidgetConfigKeys.FONT_SCALE] ?: WidgetConfigKeys.FONT_SCALE_NORMAL

        // Calendar data loads on IO before provideContent.
        val repository = CalendarRepository(context)
        val now = System.currentTimeMillis()
        val endMillis = now + (3 * 24 * 60 * 60 * 1000L) // 3-day window for immediate upcoming

        val calendarResult = withContext(Dispatchers.IO) {
            repository.getUpcomingEvents(now - 60_000L, endMillis, selectedCalendarIds)
        }

        // Schedule a refresh exactly at the shown event's next boundary so the
        // countdown text ("Starts in…" ↔ "Happening now" → next event) stays
        // accurate between the 30-minute system update ticks.
        (calendarResult as? CalendarResult.Success)?.data
            ?.firstOrNull { it.end > now }
            ?.let { NextUpRefreshScheduler.schedule(context, it, now) }

        provideContent {
            NextUpWidgetRoot(
                context = context,
                result = calendarResult,
                opacity = opacity,
                theme = WidgetThemeHelper.currentCalendarTheme(context),
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
    theme: CalendarTheme,
    fontScale: String,
    now: Long
) {
    val typography = WidgetThemeHelper.getTypography(fontScale)
    val backgroundColor = WidgetThemeHelper.getSurfaceColor(theme, opacity)

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
                PermissionStrip(context, theme, typography)
            }
            is CalendarResult.Error -> {
                ErrorStrip(result.throwable.localizedMessage ?: "Error", theme, typography)
            }
            is CalendarResult.Success -> {
                NextUpContent(
                    events = result.data,
                    theme = theme,
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
    theme: CalendarTheme,
    opacity: Float,
    typography: WidgetTypography,
    now: Long
) {
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
        EmptyNextUpStrip(theme, opacity, typography)
    } else {
        ActiveNextUpStrip(
            nextEvent = nextEvent,
            otherCountToday = otherEventsToday.size,
            theme = theme,
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
    theme: CalendarTheme,
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

    val family = theme.eventColors(nextEvent.title)
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
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(44.dp)
                .background(family.bar)
                .cornerRadius(2.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = nextEvent.title,
                style = TextStyle(
                    color = ColorProvider(family.title),
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.titleSize
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = countdownText,
                    style = TextStyle(
                        color = WidgetThemeHelper.getAccentColor(theme),
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
                            color = WidgetThemeHelper.getSecondaryTextColor(theme),
                            fontSize = typography.subtitleSize
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = secondaryText,
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(theme),
                    fontSize = typography.badgeSize
                ),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Box(
            modifier = GlanceModifier
                .size(30.dp)
                .cornerRadius(15.dp)
                .background(WidgetThemeHelper.getCardBackgroundColor(theme, opacity))
                .clickable(actionRunCallback<RefreshActionCallback>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(WidgetThemeHelper.getPrimaryTextColor(theme))
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmptyNextUpStrip(
    theme: CalendarTheme,
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
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(WidgetThemeHelper.getPrimaryTextColor(theme))
        )

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "No upcoming events",
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(theme),
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.titleSize
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "Tap to schedule an event",
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(theme),
                    fontSize = typography.subtitleSize
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Box(
            modifier = GlanceModifier
                .size(30.dp)
                .cornerRadius(15.dp)
                .background(WidgetThemeHelper.getCardBackgroundColor(theme, opacity))
                .clickable(actionRunCallback<RefreshActionCallback>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(WidgetThemeHelper.getPrimaryTextColor(theme))
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun PermissionStrip(
    context: Context,
    theme: CalendarTheme,
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
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(WidgetThemeHelper.getPrimaryTextColor(theme))
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "Permission Required",
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(theme),
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.titleSize
                )
            )
            Text(
                text = "Tap to grant calendar access",
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(theme),
                    fontSize = typography.subtitleSize
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun ErrorStrip(
    message: String,
    theme: CalendarTheme,
    typography: WidgetTypography
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Error: $message",
            style = TextStyle(
                color = WidgetThemeHelper.getPrimaryTextColor(theme),
                fontSize = typography.subtitleSize
            ),
            maxLines = 1
        )
    }
}
