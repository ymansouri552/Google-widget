package com.googlewidget.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import com.googlewidget.calendar.R
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.CalendarResult
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * One continuous event box positioned in minutes since the grid start.
 * [clampedTop]/[clampedBottom] mark edges cut by the visible window; those
 * edges get square corners because they are not the event's real start/end.
 */
data class TimedEventBox(
    val event: CalendarEvent,
    val startMin: Int,
    val endMin: Int,
    val lane: Int,
    val clampedTop: Boolean,
    val clampedBottom: Boolean
)

/**
 * A vertical run of the day column whose events overlap in time; each lane
 * is a side-by-side slice of the column width, laid out sequentially.
 */
data class EventCluster(
    val startMin: Int,
    val endMin: Int,
    val lanes: List<List<TimedEventBox>>
)

/** A stretch of hours on the shared time axis. */
sealed interface AxisSegment {
    val hours: IntRange
}

/** Hours that contain at least one event in some day; rendered at full scale. */
data class ExpandedRun(override val hours: IntRange) : AxisSegment

/** Hours with no event in any shown day; collapsed into a single break strip. */
data class BreakRun(override val hours: IntRange) : AxisSegment

/**
 * The shared time axis all three day columns render against: alternating
 * expanded runs and break strips, so the columns stay aligned.
 */
data class TimeAxis(
    val minHour: Int,
    val maxHour: Int,
    val segments: List<AxisSegment>
) {
    val expandedMinutes: Int
        get() = segments.filterIsInstance<ExpandedRun>().sumOf { it.hours.count() } * 60

    val stripCount: Int
        get() = segments.count { it is BreakRun }

    val totalStripHeightDp: Float
        get() = stripCount * ThreeDayTimelineGlanceWidget.BREAK_STRIP_HEIGHT_DP.toFloat()
}

/** One column segment for a single day: a break strip or an event run. */
sealed interface DayColumnSegment {
    val hours: IntRange
    data class BreakStrip(override val hours: IntRange) : DayColumnSegment

    /**
     * An expanded run as rendered in this day's column. Always emitted, even
     * with an empty cluster list, so every column allocates identical height
     * per run and stays aligned with the others.
     */
    data class EventRun(override val hours: IntRange, val clusters: List<EventCluster>) : DayColumnSegment
}

class ThreeDayTimelineGlanceWidget : GlanceAppWidget() {

    companion object {
        // The timeline maps minutes of event duration to dp at [dpPerMinute];
        // 1 dp per minute when the widget is tall enough.
        const val EVENT_CORNER_RADIUS_DP = 10
        const val BREAK_STRIP_HEIGHT_DP = 12
        const val MIN_EVENT_HEIGHT_MINUTES = 18

        // Boxes under 24 dp use the smaller one-line title, so layout stretches
        // events enough to keep that line readable at any scale.
        const val MIN_TEXT_HEIGHT_DP = 24
        const val TWO_LINE_MIN_HEIGHT_DP = 44
        // Below this a title would be clipped rather than ellipsized, so the box
        // renders as a plain colored block instead.
        const val TEXT_MIN_HEIGHT_DP = 12

        const val MIN_SPAN_HOURS = 4
        const val RANGE_EARLIEST_HOUR = 10
        const val RANGE_LATEST_HOUR = 23

        // The full range prefers fitting at a reduced scale before falling back
        // to a shifted hour window; the window itself renders at the 0.6 floor.
        const val MIN_DP_PER_MINUTE = 0.6f
        const val FULL_FIT_MIN_DP_PER_MINUTE = 0.28f
        const val WINDOW_SHIFT_HOURS = 2

        // Most aggressive stretch used when computing the axis, so a later
        // layout with a gentler stretch can never need a newly expanded hour.
        const val AXIS_STRETCH_MINUTES = 40

        // LocalSize is the widget's size, not the grid's; the header stack
        // (top bar, day header, divider, root padding) plus any all-day pill
        // rows is subtracted to get the height available to the timeline.
        const val HEADER_BASE_RESERVED_DP = 96
        const val ALL_DAY_PILL_ROW_HEIGHT_DP = 20

        // Fallback when the launcher reports no usable widget size options.
        const val FALLBACK_WIDGET_HEIGHT_DP = 320f

        /**
         * Visible range: earliest event start to latest event end across the
         * shown days, rounded out to whole hours, at least [MIN_SPAN_HOURS]
         * long, and never outside [RANGE_EARLIEST_HOUR]..[RANGE_LATEST_HOUR].
         */
        fun calculateVisibleRange(
            threeDays: List<LocalDate>,
            timedEvents: List<CalendarEvent>,
            zone: ZoneId = ZoneId.systemDefault(),
            minSpanHours: Int = MIN_SPAN_HOURS,
            earliestHour: Int = RANGE_EARLIEST_HOUR,
            latestHour: Int = RANGE_LATEST_HOUR
        ): IntRange {
            var rawMin = Int.MAX_VALUE
            var rawMax = Int.MIN_VALUE

            threeDays.forEach { date ->
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                timedEvents.forEach { event ->
                    if (event.begin < dayEnd && event.end > dayStart) {
                        val activeStart = maxOf(event.begin, dayStart)
                        val activeEnd = minOf(event.end, dayEnd)
                        val sZdt = Instant.ofEpochMilli(activeStart).atZone(zone)
                        rawMin = minOf(rawMin, sZdt.hour)
                        rawMax = maxOf(rawMax, if (activeEnd >= dayEnd) 24 else ceilHour(Instant.ofEpochMilli(activeEnd).atZone(zone)))
                    }
                }
            }

            if (rawMin > rawMax) {
                return earliestHour until (earliestHour + minSpanHours).coerceAtMost(latestHour + 1)
            }

            var minHour = rawMin.coerceIn(earliestHour, latestHour)
            var maxHour = rawMax.coerceIn(earliestHour, latestHour)
            if (maxHour <= minHour) {
                maxHour = (minHour + 1).coerceAtMost(latestHour)
            }
            if (maxHour - minHour + 1 < minSpanHours) {
                maxHour = (minHour + minSpanHours - 1).coerceAtMost(latestHour)
                minHour = (maxHour - minSpanHours + 1).coerceAtLeast(earliestHour)
            }
            return minHour..maxHour
        }

        private fun ceilHour(zdt: java.time.ZonedDateTime): Int =
            if (zdt.minute > 0 || zdt.second > 0 || zdt.nano > 0) zdt.hour + 1 else zdt.hour

        /** Millis bounds of one day's visible grid, [minHour] inclusive, [maxHour] exclusive of its end. */
        fun gridBoundsForDay(
            date: LocalDate,
            minHour: Int,
            maxHour: Int,
            zone: ZoneId = ZoneId.systemDefault()
        ): Pair<Long, Long> {
            val start = date.atTime(minHour, 0).atZone(zone).toInstant().toEpochMilli()
            val end = if (maxHour >= 23) {
                date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                date.atTime(maxHour + 1, 0).atZone(zone).toInstant().toEpochMilli()
            }
            return start to end
        }

        /**
         * Clamps [event] to the visible grid, returning null when it does not intersect it.
         * Start rounds down and end rounds up so the box always covers the real duration;
         * very short events are stretched to [minDurationMinutes] to stay readable.
         */
        fun clampEventToGrid(
            event: CalendarEvent,
            gridStartMillis: Long,
            gridEndMillis: Long,
            minDurationMinutes: Int
        ): TimedEventBox? {
            val start = maxOf(event.begin, gridStartMillis)
            val end = minOf(event.end, gridEndMillis)
            if (start >= end) return null

            val startMin = ((start - gridStartMillis) / 60000L).toInt()
            val endMin = ((end - gridStartMillis + 59999L) / 60000L).toInt()
            val gridMinutes = ((gridEndMillis - gridStartMillis) / 60000L).toInt()
            val stretchedEnd = maxOf(endMin, startMin + minDurationMinutes).coerceAtMost(gridMinutes)

            return TimedEventBox(
                event = event,
                startMin = startMin,
                endMin = stretchedEnd,
                lane = 0,
                clampedTop = event.begin < gridStartMillis,
                clampedBottom = event.end > gridEndMillis
            )
        }

        /**
         * Lays out a day's events as sequential clusters; within a cluster, events
         * are greedily assigned to the first lane free at their start time, so
         * overlapping events sit side by side and each event renders exactly once.
         */
        fun buildDayLayout(
            events: List<CalendarEvent>,
            gridStartMillis: Long,
            gridEndMillis: Long,
            minDurationMinutes: Int
        ): List<EventCluster> {
            val boxes = events.mapNotNull { clampEventToGrid(it, gridStartMillis, gridEndMillis, minDurationMinutes) }
                .sortedWith(compareBy({ it.startMin }, { it.endMin }))
            if (boxes.isEmpty()) return emptyList()

            val clusters = mutableListOf<EventCluster>()
            var i = 0
            while (i < boxes.size) {
                var clusterEnd = boxes[i].endMin
                var j = i + 1
                while (j < boxes.size && boxes[j].startMin < clusterEnd) {
                    clusterEnd = maxOf(clusterEnd, boxes[j].endMin)
                    j++
                }

                val laneEnds = mutableListOf<Int>()
                val placed = mutableListOf<TimedEventBox>()
                for (k in i until j) {
                    val box = boxes[k]
                    val laneIdx = laneEnds.indexOfFirst { it <= box.startMin }
                    if (laneIdx >= 0) {
                        laneEnds[laneIdx] = box.endMin
                        placed.add(box.copy(lane = laneIdx))
                    } else {
                        laneEnds.add(box.endMin)
                        placed.add(box.copy(lane = laneEnds.lastIndex))
                    }
                }
                val lanes = List(laneEnds.size) { laneIdx -> placed.filter { it.lane == laneIdx } }
                clusters.add(EventCluster(startMin = boxes[i].startMin, endMin = clusterEnd, lanes = lanes))
                i = j
            }
            return clusters
        }

        /** Hours (absolute) overlapped by any laid-out box, including min-height stretch. */
        fun collectHoursWithEvents(
            minHour: Int,
            dayLayouts: Collection<List<EventCluster>>
        ): Set<Int> {
            val hours = mutableSetOf<Int>()
            dayLayouts.forEach { clusters ->
                clusters.forEach { cluster ->
                    cluster.lanes.forEach { lane ->
                        lane.forEach { box ->
                            val firstOffset = box.startMin / 60
                            val lastOffset = (box.endMin + 59) / 60 - 1
                            for (offset in firstOffset..lastOffset) {
                                hours.add(minHour + offset)
                            }
                        }
                    }
                }
            }
            return hours
        }

        /** Groups [minHour]..[maxHour] into expanded runs and collapsed break runs. */
        fun buildTimeAxis(minHour: Int, maxHour: Int, hoursWithEvents: Set<Int>): TimeAxis {
            val segments = mutableListOf<AxisSegment>()
            var hour = minHour
            while (hour <= maxHour) {
                val expanded = hour in hoursWithEvents
                var runEnd = hour
                while (runEnd + 1 <= maxHour && ((runEnd + 1) in hoursWithEvents) == expanded) {
                    runEnd++
                }
                segments.add(if (expanded) ExpandedRun(hour..runEnd) else BreakRun(hour..runEnd))
                hour = runEnd + 1
            }
            return TimeAxis(minHour, maxHour, segments)
        }

        /**
         * Maps a day's clusters onto the shared axis. Runs this day has no events
         * in are still emitted (empty) so column heights match across days.
         */
        fun buildDayColumnSegments(clusters: List<EventCluster>, axis: TimeAxis): List<DayColumnSegment> {
            val segments = mutableListOf<DayColumnSegment>()
            var idx = 0
            axis.segments.forEach { axisSegment ->
                when (axisSegment) {
                    is BreakRun -> segments.add(DayColumnSegment.BreakStrip(axisSegment.hours))
                    is ExpandedRun -> {
                        val runStartMin = (axisSegment.hours.first - axis.minHour) * 60
                        val runEndMin = (axisSegment.hours.last + 1 - axis.minHour) * 60
                        val inRun = mutableListOf<EventCluster>()
                        while (idx < clusters.size && clusters[idx].startMin < runEndMin) {
                            val cluster = clusters[idx]
                            if (cluster.endMin > runStartMin) {
                                inRun.add(cluster)
                            }
                            idx++
                        }
                        segments.add(DayColumnSegment.EventRun(axisSegment.hours, inRun))
                    }
                }
            }
            return segments
        }

        /** Whether the axis fits the available height at [dpPerMinute]. */
        fun fitsAtScale(
            availableHeightDp: Float,
            totalStripHeightDp: Float,
            expandedMinutes: Int,
            dpPerMinute: Float
        ): Boolean = expandedMinutes * dpPerMinute + totalStripHeightDp <= availableHeightDp

        /** Scale for the axis: exact fit when possible, clamped to [minDpPerMinute]. */
        fun calculateDpPerMinute(
            availableHeightDp: Float,
            totalStripHeightDp: Float,
            expandedMinutes: Int,
            minDpPerMinute: Float = MIN_DP_PER_MINUTE
        ): Float {
            if (expandedMinutes <= 0) return minDpPerMinute
            return maxOf(minDpPerMinute, (availableHeightDp - totalStripHeightDp) / expandedMinutes)
        }

        /** Events stretch toward the minimum readable text height, capped by the axis stretch. */
        fun minDurationMinutesForScale(dpPerMinute: Float): Int =
            ceil(MIN_TEXT_HEIGHT_DP / dpPerMinute).toInt().coerceIn(MIN_EVENT_HEIGHT_MINUTES, AXIS_STRETCH_MINUTES)

        fun eventHeightDp(durationMinutes: Int, dpPerMinute: Float): Dp =
            (durationMinutes * dpPerMinute).dp

        /** Hour window that fits at minimum scale, never smaller than 2 hours. */
        fun windowSpanHours(availableHeightDp: Float): Int =
            maxOf(2, ((availableHeightDp - BREAK_STRIP_HEIGHT_DP) / (MIN_DP_PER_MINUTE * 60)).toInt())

        /** Chevrons shift the window, clamped to the absolute range bounds. */
        fun shiftedWindowStartHour(current: Int, deltaHours: Int): Int =
            (current + deltaHours).coerceIn(RANGE_EARLIEST_HOUR, RANGE_LATEST_HOUR)

        fun clampWindowStart(windowStart: Int, range: IntRange, spanHours: Int): Int =
            windowStart.coerceIn(range.first, (range.last - spanHours + 1).coerceAtLeast(range.first))

        /**
         * Window default: the current hour, or the next upcoming event's hour
         * when that event lies beyond the current-hour window.
         */
        fun defaultWindowStartHour(
            range: IntRange,
            spanHours: Int,
            currentHour: Int,
            nextEventStartHour: Int?
        ): Int {
            val lastValidStart = (range.last - spanHours + 1).coerceAtLeast(range.first)
            val current = currentHour.coerceIn(range.first, lastValidStart)
            if (nextEventStartHour == null) return current
            val next = nextEventStartHour.coerceIn(range.first, lastValidStart)
            return if (next >= current + spanHours) next else current
        }

        fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
            return Instant.ofEpochMilli(millis)
                .atZone(zone)
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        }

        fun formatTimeRange(startMillis: Long, endMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
            val startStr = formatTime(startMillis, zone)
            val endStr = formatTime(endMillis, zone)
            return "$startStr - $endStr"
        }
    }

    override val sizeMode = SizeMode.Exact
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
        val windowStartHour = prefs[WidgetConfigKeys.KEY_WINDOW_START_HOUR]

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
                themeMode = themeMode,
                savedWindowStartHour = windowStartHour
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
    themeMode: String,
    savedWindowStartHour: Int?
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
                    themeMode = themeMode,
                    savedWindowStartHour = savedWindowStartHour
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
    themeMode: String,
    savedWindowStartHour: Int?
) {
    val timedEvents = events.filter { !it.allDay }
    val zone = ZoneId.systemDefault()
    val hoursRange = ThreeDayTimelineGlanceWidget.calculateVisibleRange(threeDays, timedEvents, zone)

    val allDayByDate = threeDays.associateWith { date ->
        events.filter { ev ->
            if (!ev.allDay) return@filter false
            val evStart = Instant.ofEpochMilli(ev.begin).atZone(ZoneOffset.UTC).toLocalDate()
            val evEnd = Instant.ofEpochMilli(ev.end).atZone(ZoneOffset.UTC).toLocalDate()
            date >= evStart && date < evEnd
        }
    }
    // The header's height is driven by the day with the most all-day pills.
    val allDayPillRows = allDayByDate.values.maxOf { it.size.coerceAtMost(2) }

    // Axis occupancy uses the most aggressive stretch so a gentler stretch in
    // the final layout can never push an event into a collapsed hour.
    val axisLayouts = threeDays.associateWith { date ->
        val (gridStart, gridEnd) = ThreeDayTimelineGlanceWidget.gridBoundsForDay(date, hoursRange.first, hoursRange.last, zone)
        ThreeDayTimelineGlanceWidget.buildDayLayout(timedEvents, gridStart, gridEnd, ThreeDayTimelineGlanceWidget.AXIS_STRETCH_MINUTES)
    }
    val hoursWithEvents = ThreeDayTimelineGlanceWidget.collectHoursWithEvents(hoursRange.first, axisLayouts.values)

    Column(modifier = GlanceModifier.fillMaxSize()) {
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

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            threeDays.forEach { date ->
                val isToday = (date == today)
                val dayAllDayEvents = allDayByDate[date].orEmpty()
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

        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WidgetThemeHelper.getDividerColor(themeMode))
        )

        TimelineGrid(
            threeDays = threeDays,
            timedEvents = timedEvents,
            hoursRange = hoursRange,
            hoursWithEvents = hoursWithEvents,
            allDayPillRows = allDayPillRows,
            savedWindowStartHour = savedWindowStartHour,
            themeMode = themeMode
        )
    }
}

/**
 * Fixed, non-scrolling timeline. Every event is a single Box on a shared time
 * axis. The full range is always preferred: exact scale when it fits, then a
 * reduced scale down to [FULL_FIT_MIN_DP_PER_MINUTE]. Only when even that
 * cannot fit does the view become a shifted hour window with chevron buttons.
 */
@Composable
private fun TimelineGrid(
    threeDays: List<LocalDate>,
    timedEvents: List<CalendarEvent>,
    hoursRange: IntRange,
    hoursWithEvents: Set<Int>,
    allDayPillRows: Int,
    savedWindowStartHour: Int?,
    themeMode: String
) {
    val zone = ZoneId.systemDefault()
    val widgetHeightDp = LocalSize.current.height.value
    val headerReserve = ThreeDayTimelineGlanceWidget.HEADER_BASE_RESERVED_DP +
        allDayPillRows * ThreeDayTimelineGlanceWidget.ALL_DAY_PILL_ROW_HEIGHT_DP
    val availableDp = (
        (if (widgetHeightDp > 0f) widgetHeightDp else ThreeDayTimelineGlanceWidget.FALLBACK_WIDGET_HEIGHT_DP) -
            headerReserve
        ).coerceAtLeast(0f)

    if (hoursWithEvents.isEmpty()) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No scheduled events",
                style = TextStyle(
                    color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        return
    }

    val fullAxis = ThreeDayTimelineGlanceWidget.buildTimeAxis(hoursRange.first, hoursRange.last, hoursWithEvents)
    val fullAxisFits = fullAxis.expandedMinutes == 0 ||
        ThreeDayTimelineGlanceWidget.fitsAtScale(
            availableHeightDp = availableDp,
            totalStripHeightDp = fullAxis.totalStripHeightDp,
            expandedMinutes = fullAxis.expandedMinutes,
            dpPerMinute = ThreeDayTimelineGlanceWidget.MIN_DP_PER_MINUTE
        )

    if (fullAxisFits) {
        val dpPerMinute = ThreeDayTimelineGlanceWidget.calculateDpPerMinute(availableDp, fullAxis.totalStripHeightDp, fullAxis.expandedMinutes)
        AxisTimeline(
            threeDays = threeDays,
            axis = fullAxis,
            dpPerMinute = dpPerMinute,
            timedEvents = timedEvents,
            hoursRange = hoursRange,
            themeMode = themeMode
        )
    } else if (ThreeDayTimelineGlanceWidget.fitsAtScale(
            availableHeightDp = availableDp,
            totalStripHeightDp = fullAxis.totalStripHeightDp,
            expandedMinutes = fullAxis.expandedMinutes,
            dpPerMinute = ThreeDayTimelineGlanceWidget.FULL_FIT_MIN_DP_PER_MINUTE
        )
    ) {
        // Reduced scale: the entire range stays visible instead of a narrow window.
        val dpPerMinute = ThreeDayTimelineGlanceWidget.calculateDpPerMinute(
            availableHeightDp = availableDp,
            totalStripHeightDp = fullAxis.totalStripHeightDp,
            expandedMinutes = fullAxis.expandedMinutes,
            minDpPerMinute = ThreeDayTimelineGlanceWidget.FULL_FIT_MIN_DP_PER_MINUTE
        )
        AxisTimeline(
            threeDays = threeDays,
            axis = fullAxis,
            dpPerMinute = dpPerMinute,
            timedEvents = timedEvents,
            hoursRange = hoursRange,
            themeMode = themeMode
        )
    } else {
        val shiftHeaderReserve = 28f
        val windowAvailableDp = (availableDp - shiftHeaderReserve).coerceAtLeast(0f)
        val span = ThreeDayTimelineGlanceWidget.windowSpanHours(windowAvailableDp)
            .coerceIn(2, hoursRange.count())
        val currentHour = LocalDateTime.now(zone).hour
        val nextEventStartHour = timedEvents
            .filter { it.begin >= System.currentTimeMillis() }
            .minOfOrNull { Instant.ofEpochMilli(it.begin).atZone(zone).hour }
        val defaultStart = ThreeDayTimelineGlanceWidget.defaultWindowStartHour(hoursRange, span, currentHour, nextEventStartHour)
        val windowStart = savedWindowStartHour
            ?.let { ThreeDayTimelineGlanceWidget.clampWindowStart(it, hoursRange, span) }
            ?.takeIf { savedWindowStartHour in hoursRange }
            ?: defaultStart
        val windowRange = windowStart..(windowStart + span - 1).coerceAtMost(hoursRange.last)

        val windowHoursWithEvents = hoursWithEvents.filter { it in windowRange }.toSet()
        val windowAxis = ThreeDayTimelineGlanceWidget.buildTimeAxis(windowRange.first, windowRange.last, windowHoursWithEvents)
        val dpPerMinute = ThreeDayTimelineGlanceWidget.calculateDpPerMinute(
            availableHeightDp = windowAvailableDp,
            totalStripHeightDp = windowAxis.totalStripHeightDp,
            expandedMinutes = windowAxis.expandedMinutes,
            minDpPerMinute = ThreeDayTimelineGlanceWidget.FULL_FIT_MIN_DP_PER_MINUTE
        )

        Column(modifier = GlanceModifier.fillMaxSize()) {
            WindowShiftHeader(windowRange = windowRange, themeMode = themeMode)
            AxisTimeline(
                threeDays = threeDays,
                axis = windowAxis,
                dpPerMinute = dpPerMinute,
                timedEvents = timedEvents,
                hoursRange = windowRange,
                themeMode = themeMode
            )
        }
    }
}

@Composable
private fun WindowShiftHeader(
    windowRange: IntRange,
    themeMode: String
) {
    val iconColor = when (themeMode) {
        WidgetConfigKeys.THEME_LIGHT -> Color(0xFF202124)
        else -> Color(0xFFE8EAED)
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(24.dp)
                .cornerRadius(12.dp)
                .clickable(
                    actionRunCallback<ThreeDayWindowShiftAction>(
                        actionParametersOf(ThreeDayWindowShiftAction.KEY_DELTA to -ThreeDayTimelineGlanceWidget.WINDOW_SHIFT_HOURS)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_chevron_up),
                contentDescription = "Earlier hours",
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(ColorProvider(iconColor))
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Text(
            text = "${windowRange.first}:00 – ${windowRange.last + 1}:00",
            style = TextStyle(
                color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                fontSize = 9.sp
            ),
            maxLines = 1
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        Box(
            modifier = GlanceModifier
                .size(24.dp)
                .cornerRadius(12.dp)
                .clickable(
                    actionRunCallback<ThreeDayWindowShiftAction>(
                        actionParametersOf(ThreeDayWindowShiftAction.KEY_DELTA to ThreeDayTimelineGlanceWidget.WINDOW_SHIFT_HOURS)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_chevron_down),
                contentDescription = "Later hours",
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(ColorProvider(iconColor))
            )
        }
    }
}

@Composable
private fun AxisTimeline(
    threeDays: List<LocalDate>,
    axis: TimeAxis,
    dpPerMinute: Float,
    timedEvents: List<CalendarEvent>,
    hoursRange: IntRange,
    themeMode: String
) {
    val zone = ZoneId.systemDefault()
    val minDurationMinutes = ThreeDayTimelineGlanceWidget.minDurationMinutesForScale(dpPerMinute)

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        threeDays.forEach { date ->
            val (gridStart, gridEnd) = ThreeDayTimelineGlanceWidget.gridBoundsForDay(date, hoursRange.first, hoursRange.last, zone)
            val clusters = ThreeDayTimelineGlanceWidget.buildDayLayout(timedEvents, gridStart, gridEnd, minDurationMinutes)
            DayColumn(
                date = date,
                axis = axis,
                clusters = clusters,
                dpPerMinute = dpPerMinute,
                gridStartMillis = gridStart,
                themeMode = themeMode,
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    axis: TimeAxis,
    clusters: List<EventCluster>,
    dpPerMinute: Float,
    gridStartMillis: Long,
    themeMode: String,
    modifier: GlanceModifier
) {
    val columnSegments = ThreeDayTimelineGlanceWidget.buildDayColumnSegments(clusters, axis)

    Column(modifier = modifier) {
        columnSegments.forEach { segment ->
            when (segment) {
                is DayColumnSegment.BreakStrip -> {
                    val stripStart = gridStartMillis + (segment.hours.first - axis.minHour) * 3600000L
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(ThreeDayTimelineGlanceWidget.BREAK_STRIP_HEIGHT_DP.dp)
                            .clickable(actionStartActivity(openCalendarAt(stripStart))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "···",
                            style = TextStyle(
                                color = WidgetThemeHelper.getSecondaryTextColor(themeMode),
                                fontSize = 9.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
                is DayColumnSegment.EventRun -> {
                    val runStartMin = (segment.hours.first - axis.minHour) * 60
                    val runEndMin = (segment.hours.last + 1 - axis.minHour) * 60

                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        var cursor = runStartMin
                        segment.clusters.forEach { cluster ->
                            val gapMin = cluster.startMin - cursor
                            if (gapMin > 0 || cursor > runStartMin) {
                                EmptyTimeSpacer(
                                    gridStartMillis = gridStartMillis,
                                    fromMin = cursor,
                                    minutes = gapMin,
                                    dpPerMinute = dpPerMinute,
                                    betweenEvents = true
                                )
                            }
                            ClusterRow(
                                cluster = cluster,
                                dpPerMinute = dpPerMinute,
                                gridStartMillis = gridStartMillis
                            )
                            cursor = cluster.endMin
                        }
                        val trailingMin = runEndMin - cursor
                        if (trailingMin > 0) {
                            EmptyTimeSpacer(
                                gridStartMillis = gridStartMillis,
                                fromMin = cursor,
                                minutes = trailingMin,
                                dpPerMinute = dpPerMinute,
                                betweenEvents = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClusterRow(
    cluster: EventCluster,
    dpPerMinute: Float,
    gridStartMillis: Long
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ThreeDayTimelineGlanceWidget.eventHeightDp(cluster.endMin - cluster.startMin, dpPerMinute))
    ) {
        cluster.lanes.forEach { lane ->
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
            ) {
                var laneCursor = cluster.startMin
                lane.forEach { box ->
                    val gapMin = box.startMin - laneCursor
                    if (gapMin > 0 || laneCursor > cluster.startMin) {
                        EmptyTimeSpacer(
                            gridStartMillis = gridStartMillis,
                            fromMin = laneCursor,
                            minutes = gapMin,
                            dpPerMinute = dpPerMinute,
                            betweenEvents = true
                        )
                    }
                    EventBox(
                        box = box,
                        dpPerMinute = dpPerMinute,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                    laneCursor = box.endMin
                }
                val trailingMin = cluster.endMin - laneCursor
                if (trailingMin > 0) {
                    EmptyTimeSpacer(
                        gridStartMillis = gridStartMillis,
                        fromMin = laneCursor,
                        minutes = trailingMin,
                        dpPerMinute = dpPerMinute,
                        betweenEvents = false
                    )
                }
            }
        }
    }
}

/** Empty time between events, exactly [minutes] * scale tall (min 1dp between events). */
@Composable
private fun EmptyTimeSpacer(
    gridStartMillis: Long,
    fromMin: Int,
    minutes: Int,
    dpPerMinute: Float,
    betweenEvents: Boolean
) {
    val heightDp = when {
        minutes > 0 -> ThreeDayTimelineGlanceWidget.eventHeightDp(minutes, dpPerMinute)
        betweenEvents -> 1.dp
        else -> return
    }

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(heightDp)
            .clickable(actionStartActivity(openCalendarAt(gridStartMillis + fromMin * 60000L)))
    ) {}
}

private fun openCalendarAt(timeMillis: Long): Intent {
    val uri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .let { ContentUris.appendId(it, timeMillis) }
        .build()
    return Intent(Intent.ACTION_VIEW).apply {
        data = uri
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

/**
 * Draws the event once, at its exact offset and duration. All corners are
 * rounded except edges cut by the visible window, which are squared off with
 * a patch of the same color (Glance has no per-corner radius).
 */
@Composable
private fun EventBox(
    box: TimedEventBox,
    dpPerMinute: Float,
    modifier: GlanceModifier
) {
    val heightDp = ThreeDayTimelineGlanceWidget.eventHeightDp(box.endMin - box.startMin, dpPerMinute)
    val eventColor = Color(box.event.color)
    val onEventColor = WidgetThemeHelper.getEventTextColor(eventColor)
    val onEventTextColor = ColorProvider(onEventColor)
    val onEventTimeColor = ColorProvider(onEventColor.copy(alpha = 0.8f))
    val radius = if (box.clampedTop || box.clampedBottom) {
        if (heightDp > ThreeDayTimelineGlanceWidget.EVENT_CORNER_RADIUS_DP.dp) {
            ThreeDayTimelineGlanceWidget.EVENT_CORNER_RADIUS_DP.dp
        } else {
            0.dp
        }
    } else {
        ThreeDayTimelineGlanceWidget.EVENT_CORNER_RADIUS_DP.dp
    }

    val evUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, box.event.eventId)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        data = evUri
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Box(
        modifier = modifier
            .height(heightDp)
            .padding(horizontal = 1.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(radius)
                .background(eventColor)
                .clickable(actionStartActivity(viewIntent))
        ) {
            if (heightDp >= ThreeDayTimelineGlanceWidget.TWO_LINE_MIN_HEIGHT_DP.dp) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = box.event.title,
                        style = TextStyle(
                            color = onEventTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = ThreeDayTimelineGlanceWidget.formatTimeRange(box.event.begin, box.event.end),
                        style = TextStyle(
                            color = onEventTimeColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1
                    )
                }
            } else if (heightDp >= ThreeDayTimelineGlanceWidget.MIN_TEXT_HEIGHT_DP.dp) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = box.event.title,
                        style = TextStyle(
                            color = onEventTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        maxLines = 1
                    )
                }
            } else if (heightDp >= ThreeDayTimelineGlanceWidget.TEXT_MIN_HEIGHT_DP.dp) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 3.dp)
                ) {
                    Text(
                        text = box.event.title,
                        style = TextStyle(
                            color = onEventTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        if (box.clampedTop && radius > 0.dp) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(ThreeDayTimelineGlanceWidget.EVENT_CORNER_RADIUS_DP.dp)
                    .background(eventColor)
            ) {}
        }
        if (box.clampedBottom && radius > 0.dp) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(ThreeDayTimelineGlanceWidget.EVENT_CORNER_RADIUS_DP.dp)
                        .background(eventColor)
                ) {}
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
