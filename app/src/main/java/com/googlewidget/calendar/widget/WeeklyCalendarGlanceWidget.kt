package com.googlewidget.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.CalendarResult
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.R
import com.googlewidget.calendar.ui.MainActivity
import com.googlewidget.calendar.ui.theme.CalendarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Seven-day widget. Narrow widgets show a compact week strip plus the agenda
 * of the selected day; wide widgets keep the original chip grid. Both share
 * the header, navigation and the app-wide theme.
 */
class WeeklyCalendarGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 140.dp),
            DpSize(240.dp, 180.dp),
            DpSize(320.dp, 240.dp),
            DpSize(380.dp, 300.dp),
            // Common launcher cell on 1080x2400 @420dpi (~395x297dp): 300dp is
            // just too tall, so without this bucket the widget falls back to
            // 320x240 and leaves a large unused band under the agenda.
            DpSize(380.dp, 280.dp),
            DpSize(448.dp, 300.dp),
            DpSize(448.dp, 420.dp),
            DpSize(600.dp, 360.dp)
        )
    )
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
        var prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initialLive = WidgetLiveState.from(prefs)
        WidgetLiveStateHub.publish(appWidgetId, initialLive)

        // Persist an expired offset before rendering, otherwise the next
        // chevron tap would continue counting from a stale week.
        val nowMillis = System.currentTimeMillis()
        val storedOffset = initialLive.dayOffset
        val effectiveOffset = WeeklyCalendarLogic.effectiveOffset(
            storedOffset, initialLive.lastInteractionMillis, nowMillis
        )
        if (effectiveOffset != storedOffset) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
                state.toMutablePreferences().apply {
                    this[WidgetConfigKeys.KEY_DAY_OFFSET] = effectiveOffset
                }
            }
            prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        }

        val appContext = context.applicationContext
        val today = LocalDate.now()
        val locale = Locale.getDefault()
        val initialWeekDates = WeeklyCalendarLogic.weekDates(
            today, effectiveOffset, initialLive.weekStart, locale
        )
        val initialResult = withContext(Dispatchers.IO) {
            CalendarRepository(appContext).getUpcomingEvents(
                initialWeekDates.first().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                initialWeekDates.last().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                initialLive.selectedCalendarIds
            )
        }

        provideContent {
            // A tap publishes here, which recomposes this running session
            // in-process instead of re-pushing stale content on update().
            val live by WidgetLiveStateHub.flow(appWidgetId).collectAsState(initial = initialLive)
            val theme = WidgetThemeHelper.currentCalendarTheme(context)
            val now = LocalDate.now()
            val offset = WeeklyCalendarLogic.effectiveOffset(
                live.dayOffset, live.lastInteractionMillis, System.currentTimeMillis()
            )
            val weekDates = WeeklyCalendarLogic.weekDates(now, offset, live.weekStart, locale)
            val selectedDate = WeeklyCalendarLogic.selectedDateFor(weekDates, live.selectedDate, now)
            val dayLabels = weekDates.map { WeeklyCalendarLogic.shortWeekday(it, locale) }

            val calendarResult by produceState(
                initialValue = initialResult,
                key1 = weekDates,
                key2 = live.selectedCalendarIds
            ) {
                value = withContext(Dispatchers.IO) {
                    CalendarRepository(appContext).getUpcomingEvents(
                        weekDates.first().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        weekDates.last().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        live.selectedCalendarIds
                    )
                }
            }

            WeeklyWidgetRoot(
                context = context,
                appWidgetId = appWidgetId,
                result = calendarResult,
                today = now,
                weekDates = weekDates,
                selectedDate = selectedDate,
                dayLabels = dayLabels,
                opacity = live.bgOpacity,
                theme = theme
            )
        }
    }
}

private const val HEADER_RESERVE_DP = 120f

@Composable
private fun WeeklyWidgetRoot(
    context: Context,
    appWidgetId: Int,
    result: CalendarResult<List<CalendarEvent>>,
    today: LocalDate,
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    dayLabels: List<String>,
    opacity: Float,
    theme: CalendarTheme
) {
    val widthDp = LocalSize.current.width.value
    val heightDp = LocalSize.current.height.value
    val isWide = widthDp >= WeeklyCalendarLogic.WIDE_LAYOUT_MIN_WIDTH_DP

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetThemeHelper.getSurfaceColor(theme, opacity))
            .cornerRadius(16.dp)
            .padding(8.dp)
    ) {
        when (result) {
            is CalendarResult.PermissionRequired -> {
                PermissionRequiredWeeklyView(context, theme)
            }
            is CalendarResult.Error -> {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = result.throwable.localizedMessage ?: "Failed to load events",
                        style = TextStyle(color = WidgetThemeHelper.getSecondaryTextColor(theme))
                    )
                }
            }
            is CalendarResult.Success -> {
                val eventsByDate = result.data.groupBy { CalendarRepository.getEventLocalDate(it) }
                if (isWide) {
                    WideWeekContent(
                        context = context,
                        appWidgetId = appWidgetId,
                        today = today,
                        weekDates = weekDates,
                        dayLabels = dayLabels,
                        eventsByDate = eventsByDate,
                        availableHeightDp = heightDp,
                        theme = theme
                    )
                } else {
                    NarrowWeekContent(
                        context = context,
                        appWidgetId = appWidgetId,
                        today = today,
                        weekDates = weekDates,
                        dayLabels = dayLabels,
                        selectedDate = selectedDate,
                        eventsByDate = eventsByDate,
                        availableWidthDp = widthDp,
                        availableHeightDp = heightDp,
                        theme = theme
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekTopBar(
    context: Context,
    appWidgetId: Int,
    title: String,
    today: LocalDate,
    theme: CalendarTheme
) {
    WidgetTopBar(
        context = context,
        appWidgetId = appWidgetId,
        monthTitle = title,
        todayDayOfMonth = today.dayOfMonth,
        onPrevClick = actionRunCallback<WeeklyPrevAction>(),
        onNextClick = actionRunCallback<WeeklyNextAction>(),
        theme = theme
    )
    Spacer(modifier = GlanceModifier.height(4.dp))
}

@Composable
private fun NarrowWeekContent(
    context: Context,
    appWidgetId: Int,
    today: LocalDate,
    weekDates: List<LocalDate>,
    dayLabels: List<String>,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    availableWidthDp: Float,
    availableHeightDp: Float,
    theme: CalendarTheme
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        WeekTopBar(
            context = context,
            appWidgetId = appWidgetId,
            title = WeeklyCalendarLogic.rangeLabel(weekDates.first(), weekDates.last()),
            today = today,
            theme = theme
        )

        WeekStrip(
            weekDates = weekDates,
            dayLabels = dayLabels,
            selectedDate = selectedDate,
            today = today,
            eventsByDate = eventsByDate,
            theme = theme
        )

        Spacer(modifier = GlanceModifier.height(2.dp))
        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WidgetThemeHelper.getDividerColor(theme))
        )
        Spacer(modifier = GlanceModifier.height(4.dp))

        val dayEvents = eventsByDate[selectedDate] ?: emptyList()
        val nowMillis = System.currentTimeMillis()
        AgendaList(
            context = context,
            date = selectedDate,
            events = dayEvents,
            rowCount = WeeklyCalendarLogic.agendaRowCount(availableHeightDp - HEADER_RESERVE_DP),
            nowMillis = nowMillis,
            availableWidthDp = availableWidthDp,
            theme = theme
        )
    }
}

@Composable
private fun WeekStrip(
    weekDates: List<LocalDate>,
    dayLabels: List<String>,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    theme: CalendarTheme
) {
    val mutedInk = WidgetThemeHelper.getSecondaryTextColor(theme)
    val normalInk = WidgetThemeHelper.getPrimaryTextColor(theme)
    val selectedInk = ColorProvider(WidgetEventColors.readableOn(Color.White))

    Row(modifier = GlanceModifier.fillMaxWidth().height(62.dp)) {
        weekDates.forEachIndexed { index, date ->
            val isSelected = date == selectedDate
            val isToday = date == today
            val past = date.isBefore(today)
            val cellModifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(
                    actionRunCallback<WeeklySelectDayAction>(
                        actionParametersOf(WeeklySelectDayAction.KEY_DATE to date.toString())
                    )
                )
                .let {
                    if (isSelected) it.cornerRadius(8.dp).background(Color.White) else it
                }
            Box(modifier = cellModifier, contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayLabels.getOrElse(index) { WeeklyCalendarLogic.shortWeekday(date) },
                        style = TextStyle(
                            color = if (past) mutedInk else normalInk,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    if (isToday) {
                        Box(
                            modifier = GlanceModifier
                                .size(20.dp)
                                .cornerRadius(10.dp)
                                .background(WidgetThemeHelper.getAccentColor(theme)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = TextStyle(
                                    color = WidgetThemeHelper.getAccentTextColor(theme),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    } else {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = TextStyle(
                                color = when {
                                    isSelected -> selectedInk
                                    past -> mutedInk
                                    else -> normalInk
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    DotRow(eventsByDate[date] ?: emptyList(), theme)
                }
            }
        }
    }
}

@Composable
private fun DotRow(events: List<CalendarEvent>, theme: CalendarTheme) {
    if (events.isEmpty()) return
    Row(
        modifier = GlanceModifier.height(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        events.take(WeeklyCalendarLogic.MAX_DOTS).forEachIndexed { index, event ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(2.dp))
            val palette = WidgetEventColors.palette(event, theme)
            Box(
                modifier = GlanceModifier
                    .size(5.dp)
                    .cornerRadius(3.dp)
                    .background(palette.full)
            ) {}
        }
        val hidden = events.size - WeeklyCalendarLogic.MAX_DOTS
        if (hidden > 0) {
            Spacer(modifier = GlanceModifier.width(2.dp))
            Text(
                text = "+$hidden",
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(theme),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AgendaList(
    context: Context,
    date: LocalDate,
    events: List<CalendarEvent>,
    rowCount: Int,
    nowMillis: Long,
    availableWidthDp: Float,
    theme: CalendarTheme
) {
    val sorted = WeeklyCalendarLogic.sortAgendaEvents(events, nowMillis)
    val paging = WeeklyCalendarLogic.paginate(sorted.size, rowCount)
    val markers = WeeklyCalendarLogic.agendaMarkers(events, nowMillis)
    val nextPill = WeeklyCalendarLogic.nextPill(events, nowMillis)
    val stats = WeeklyCalendarLogic.dailyStats(events)
    val mutedInk = WidgetThemeHelper.getSecondaryTextColor(theme)
    val twoColumns = availableWidthDp >= WeeklyCalendarLogic.TWO_COLUMN_MIN_WIDTH_DP
    val statsWidthDp = (availableWidthDp * 0.32f)
        .coerceIn(WeeklyCalendarLogic.STATS_MIN_WIDTH_DP, WeeklyCalendarLogic.STATS_MAX_WIDTH_DP)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = WeeklyCalendarLogic.agendaHeaderLabel(date, events.size),
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(theme),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            if (nextPill != null) {
                Text(
                    text = nextPill,
                    style = TextStyle(
                        color = WidgetThemeHelper.getAccentTextColor(theme),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier
                        .background(WidgetThemeHelper.getAccentColor(theme))
                        .cornerRadius(8.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Row(modifier = GlanceModifier.fillMaxSize()) {
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                if (events.isEmpty()) {
                    Text(
                        text = "No events",
                        style = TextStyle(color = mutedInk, fontSize = 12.sp),
                        modifier = GlanceModifier.padding(top = 12.dp)
                    )
                } else {
                    val cardTint = WidgetEventColors.palette(sorted.first(), theme).tint
                    // The tinted surface and its overflow row fill the height left
                    // by the week strip, so no empty band sits under the events.
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .cornerRadius(6.dp)
                            .background(cardTint)
                    ) {
                        Column(modifier = GlanceModifier.fillMaxWidth().fillMaxHeight()) {
                            sorted.take(paging.visible).forEach { event ->
                                val tag = when (event.eventId) {
                                    markers.nowEventId -> "NOW"
                                    markers.nextEventId -> "NEXT"
                                    else -> null
                                }
                                AgendaRow(
                                    event = event,
                                    tag = tag,
                                    dimmed = WeeklyCalendarLogic.isEnded(event, nowMillis),
                                    theme = theme
                                )
                            }
                            if (paging.overflow > 0) {
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .defaultWeight()
                                        .clickable(
                                            actionStartActivity(openDayIntent(context, date))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${paging.overflow} more",
                                        style = TextStyle(
                                            color = WidgetThemeHelper.getPrimaryTextColor(theme),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (twoColumns) {
                Spacer(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(WidgetThemeHelper.getDividerColor(theme))
                )
                DailyStatsPanel(stats = stats, width = statsWidthDp.dp, theme = theme)
            }
        }

        if (!twoColumns) {
            CompactStatsRow(stats = stats, theme = theme)
        }
    }
}

@Composable
private fun DailyStatsPanel(stats: DailyStats, width: androidx.compose.ui.unit.Dp, theme: CalendarTheme) {
    val accent = WidgetThemeHelper.getAccentColor(theme)
    // Every value starts after the same icon-width slot, so the three stats
    // read as one aligned column instead of three unrelated rows.
    val valueIndent = 17.dp
    Box(
        modifier = GlanceModifier
            .width(width)
            .fillMaxHeight()
            .background(theme.chipBg)
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_school_cap),
                    contentDescription = null,
                    modifier = GlanceModifier.size(13.dp),
                    colorFilter = ColorFilter.tint(accent)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "Today",
                    style = TextStyle(
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = GlanceModifier.width(valueIndent))
                Text(
                    text = stats.classCount.toString(),
                    style = TextStyle(
                        color = WidgetThemeHelper.getPrimaryTextColor(theme),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "Classes",
                    style = TextStyle(
                        color = WidgetThemeHelper.getSecondaryTextColor(theme),
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
            StatsDivider(theme)
            StatLine(
                iconRes = R.drawable.ic_clock,
                value = WeeklyCalendarLogic.formatTeachingTime(stats.teachingMinutes),
                label = "Teaching time",
                theme = theme
            )
            StatsDivider(theme)
            StatLine(
                iconRes = R.drawable.ic_calendar_check,
                value = stats.lastEndMillis?.let { ThreeDayTimelineGlanceWidget.formatTime(it) } ?: "-",
                label = "Last class ends",
                theme = theme
            )
        }
    }
}

@Composable
private fun StatsDivider(theme: CalendarTheme) {
    Spacer(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WidgetThemeHelper.getDividerColor(theme))
    )
}

@Composable
private fun StatLine(iconRes: Int, value: String, label: String, theme: CalendarTheme) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(13.dp),
            colorFilter = ColorFilter.tint(WidgetThemeHelper.getAccentColor(theme))
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = value,
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(theme),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = label,
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(theme),
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactStatsRow(stats: DailyStats, theme: CalendarTheme) {
    val end = stats.lastEndMillis?.let { ThreeDayTimelineGlanceWidget.formatTime(it) } ?: "-"
    Text(
        text = "${stats.classCount} classes · " +
            "${WeeklyCalendarLogic.formatTeachingTime(stats.teachingMinutes)} · ends $end",
        style = TextStyle(
            color = WidgetThemeHelper.getSecondaryTextColor(theme),
            fontSize = 9.sp
        ),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)
    )
}

@Composable
private fun AgendaRow(
    event: CalendarEvent,
    tag: String?,
    dimmed: Boolean,
    theme: CalendarTheme
) {
    val palette = WidgetEventColors.palette(event, theme)
    val titleColor = if (dimmed) {
        WidgetThemeHelper.getSecondaryTextColor(theme)
    } else {
        ColorProvider(palette.onTint)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 6.dp)
            .clickable(actionStartActivity(openEventIntent(event))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(20.dp)
                .cornerRadius(2.dp)
                .background(palette.full)
        ) {}
        Spacer(modifier = GlanceModifier.width(5.dp))
        // Fixed width plus a monospace face keeps every "HH:mm-HH:mm" aligned
        // and stops the time from being clipped by a long title.
        Text(
            text = "\u200E${WeeklyCalendarLogic.timeLabel(event)}",
            style = TextStyle(
                color = ColorProvider(palette.onTint),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Left
            ),
            maxLines = 1,
            modifier = GlanceModifier.width(90.dp)
        )
        Spacer(modifier = GlanceModifier.width(5.dp))
        Text(
            text = event.title,
            style = TextStyle(
                color = titleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = if (isRightToLeftTitle(event.title)) TextAlign.Right else TextAlign.Left
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        if (tag != null) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Box(
                modifier = GlanceModifier
                    .background(WidgetThemeHelper.getAccentColor(theme))
                    .cornerRadius(4.dp)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = tag,
                    style = TextStyle(
                        color = WidgetThemeHelper.getAccentTextColor(theme),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WideWeekContent(
    context: Context,
    appWidgetId: Int,
    today: LocalDate,
    weekDates: List<LocalDate>,
    dayLabels: List<String>,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    availableHeightDp: Float,
    theme: CalendarTheme
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        WeekTopBar(
            context = context,
            appWidgetId = appWidgetId,
            title = WeeklyCalendarLogic.rangeLabel(weekDates.first(), weekDates.last()),
            today = today,
            theme = theme
        )
        Row(modifier = GlanceModifier.fillMaxSize()) {
            weekDates.forEachIndexed { index, date ->
                WideDayColumn(
                    context = context,
                    date = date,
                    weekday = dayLabels.getOrElse(index) { WeeklyCalendarLogic.shortWeekday(date) },
                    isToday = date == today,
                    events = eventsByDate[date] ?: emptyList(),
                    maxChips = WeeklyCalendarLogic.chipRowCount(availableHeightDp),
                    theme = theme,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun WideDayColumn(
    context: Context,
    date: LocalDate,
    weekday: String,
    isToday: Boolean,
    events: List<CalendarEvent>,
    maxChips: Int,
    theme: CalendarTheme,
    modifier: GlanceModifier
) {
    Row(modifier = modifier) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(horizontal = 1.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxWidth().height(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = weekday,
                    style = TextStyle(
                        color = WidgetThemeHelper.getSecondaryTextColor(theme),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                if (isToday) {
                    Box(
                        modifier = GlanceModifier
                            .size(16.dp)
                            .cornerRadius(8.dp)
                            .background(WidgetThemeHelper.getAccentColor(theme)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = TextStyle(
                                color = WidgetThemeHelper.getAccentTextColor(theme),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = TextStyle(
                            color = WidgetThemeHelper.getPrimaryTextColor(theme),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            val sorted = WeeklyCalendarLogic.sortAgendaEvents(events, System.currentTimeMillis())
            val limit = if (sorted.size > maxChips) maxChips - 1 else maxChips
            sorted.take(limit).forEach { event ->
                val palette = WidgetEventColors.palette(event, theme)
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(4.dp)
                        .background(palette.tint)
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .clickable(actionStartActivity(openEventIntent(event)))
                ) {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            text = "\u200E${WeeklyCalendarLogic.timeLabel(event)}",
                            style = TextStyle(
                                color = ColorProvider(palette.onTint),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Left
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = event.title,
                            style = TextStyle(
                                color = ColorProvider(palette.onTint),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = if (isRightToLeftTitle(event.title)) TextAlign.Right else TextAlign.Left
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
            if (sorted.size > limit) {
                Text(
                    text = "+${sorted.size - limit} more",
                    style = TextStyle(
                        color = WidgetThemeHelper.getSecondaryTextColor(theme),
                        fontSize = 8.sp
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.clickable(actionStartActivity(openDayIntent(context, date)))
                )
            }
        }
    }
}

private fun openDayIntent(context: Context, date: LocalDate): Intent {
    val dayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val uri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .let { ContentUris.appendId(it, dayMillis) }
        .build()
    return Intent(Intent.ACTION_VIEW).apply {
        data = uri
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

private fun openEventIntent(event: CalendarEvent): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

@Composable
private fun PermissionRequiredWeeklyView(context: Context, theme: CalendarTheme) {
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
                color = WidgetThemeHelper.getPrimaryTextColor(theme),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier
                .cornerRadius(8.dp)
                .background(WidgetThemeHelper.getAccentColor(theme))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Grant Permission",
                style = TextStyle(
                    color = WidgetThemeHelper.getAccentTextColor(theme),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}
