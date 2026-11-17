package com.googlewidget.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import com.googlewidget.calendar.data.ThemePreferences
import com.googlewidget.calendar.data.ThemeSelection
import com.googlewidget.calendar.ui.MainActivity
import com.googlewidget.calendar.ui.theme.CalendarTheme
import com.googlewidget.calendar.ui.theme.CalendarThemes
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
import kotlin.math.roundToInt

/**
 * One continuous event box positioned in minutes since the grid start.
 * [clampedTop]/[clampedBottom] mark edges cut by the visible window, so the
 * caller can tell a real start/end from one that continues off-screen.
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
        const val BREAK_STRIP_HEIGHT_DP = 12
        const val MIN_EVENT_HEIGHT_MINUTES = 18

        // Boxes under 24 dp use the smaller one-line title, so layout stretches
        // events enough to keep that line readable at any scale.
        const val MIN_TEXT_HEIGHT_DP = 24

        const val MIN_SPAN_HOURS = 4
        const val RANGE_EARLIEST_HOUR = 10
        const val RANGE_LATEST_HOUR = 23

        // The full range prefers fitting at a reduced scale before falling back
        // to a shifted hour window; the window itself renders at the 0.6 floor.
        const val MIN_DP_PER_MINUTE = 0.6f
        const val FULL_FIT_MIN_DP_PER_MINUTE = 0.28f

        // Upper bound of the time scale. Without it, few/short events produce a
        // huge exact-fit scale and the day's events stretch to fill the whole
        // widget. 1 dp per minute (60dp per hour) keeps events a readable,
        // natural size; leftover height simply stays empty below the grid.
        const val MAX_DP_PER_MINUTE = 1.0f

        // Most aggressive stretch used when computing the axis, so a later
        // layout with a gentler stretch can never need a newly expanded hour.
        const val AXIS_STRETCH_MINUTES = 40

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

        /**
         * Visible hour cells for the timeline grid, as an inclusive range.
         * The window spans the earliest event start to the latest event end
         * rounded out to whole hours, at least [minSpanHours] cells long.
         * With no timed events in the shown days it falls back to four hours
         * around the current time.
         */
        fun calculateVisibleHours(
            dates: List<LocalDate>,
            timedEvents: List<CalendarEvent>,
            now: LocalDateTime = LocalDateTime.now(),
            zone: ZoneId = ZoneId.systemDefault(),
            minSpanHours: Int = MIN_SPAN_HOURS
        ): IntRange {
            var earliest = Int.MAX_VALUE
            var latestExclusive = Int.MIN_VALUE

            dates.forEach { date ->
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                timedEvents.forEach { event ->
                    if (event.allDay) return@forEach
                    val activeStart = maxOf(event.begin, dayStart)
                    val activeEnd = minOf(event.end, dayEnd)
                    if (activeStart >= activeEnd) return@forEach

                    earliest = minOf(earliest, Instant.ofEpochMilli(activeStart).atZone(zone).hour)
                    val endHour = if (activeEnd >= dayEnd) {
                        24
                    } else {
                        ceilHour(Instant.ofEpochMilli(activeEnd).atZone(zone))
                    }
                    latestExclusive = maxOf(latestExclusive, endHour)
                }
            }

            if (earliest >= latestExclusive) {
                val hour = now.atZone(zone).hour.coerceIn(0, 24 - minSpanHours)
                return hour..(hour + minSpanHours - 1)
            }

            var start = earliest.coerceIn(0, 23)
            var endExclusive = latestExclusive.coerceIn(1, 24)
            if (endExclusive - start < minSpanHours) {
                endExclusive = (start + minSpanHours).coerceAtMost(24)
                start = (endExclusive - minSpanHours).coerceAtLeast(0)
            }
            return start..(endExclusive - 1)
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

        /**
         * Scale for the axis: exact fit when possible, clamped to
         * [MIN_DP_PER_MINUTE] and [MAX_DP_PER_MINUTE] so a day with few or
         * short events never stretches them to fill the widget.
         */
        fun calculateDpPerMinute(
            availableHeightDp: Float,
            totalStripHeightDp: Float,
            expandedMinutes: Int,
            minDpPerMinute: Float = MIN_DP_PER_MINUTE
        ): Float {
            if (expandedMinutes <= 0) return minDpPerMinute
            return ((availableHeightDp - totalStripHeightDp) / expandedMinutes)
                .coerceIn(minDpPerMinute, MAX_DP_PER_MINUTE)
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
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
        val initialPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initialLive = WidgetLiveState.from(initialPrefs)
        WidgetLiveStateHub.publish(appWidgetId, initialLive)
        ThemePreferences.ensureLoaded(context)

        val appContext = context.applicationContext
        val today = LocalDate.now()
        val initialBase = today.plusDays(initialLive.dayOffset.toLong())
        val initialThreeDays = listOf(initialBase, initialBase.plusDays(1), initialBase.plusDays(2))
        val initialResult = withContext(Dispatchers.IO) {
            CalendarRepository(appContext).getUpcomingEvents(
                initialThreeDays.first().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                initialThreeDays.last().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                initialLive.selectedCalendarIds
            )
        }

        provideContent {
            // Config changes (calendar selection) publish here and recompose the
            // running session in-process.
            val live by WidgetLiveStateHub.flow(appWidgetId).collectAsState(initial = initialLive)
            // App-wide theme: observing it repaints live sessions immediately when
            // the switcher saves, without waiting for update() to restart Glance.
            val selection by ThemePreferences.selection.collectAsState(
                initial = ThemeSelection(ThemePreferences.getSelectedId(appContext))
            )
            val now = LocalDate.now()
            val baseDate = now.plusDays(live.dayOffset.toLong())
            val threeDays = listOf(baseDate, baseDate.plusDays(1), baseDate.plusDays(2))

            val calendarResult by produceState(
                initialValue = initialResult,
                key1 = threeDays,
                key2 = live.selectedCalendarIds
            ) {
                value = withContext(Dispatchers.IO) {
                    CalendarRepository(appContext).getUpcomingEvents(
                        threeDays.first().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        threeDays.last().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        live.selectedCalendarIds
                    )
                }
            }

            val systemDark =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val theme = CalendarThemes.resolve(selection.themeId, systemDark)

            ThreeDayTimelineRoot(
                context = context,
                result = calendarResult,
                today = now,
                threeDays = threeDays,
                theme = theme
            )
        }
    }
}

private const val SURFACE_CORNER_DP = 24
private const val SURFACE_PADDING_DP = 12
private const val HOUR_GUTTER_DP = 26
private const val HEADER_RESERVED_DP = 104f
private const val FALLBACK_WIDGET_HEIGHT_DP = 320f
private const val FALLBACK_WIDGET_WIDTH_DP = 320f
private const val EVENT_CORNER_DP = 10
private const val ACCENT_BAR_DP = 4
private const val TWO_LINE_MIN_HEIGHT_DP = 36
private const val MIN_GRID_HEIGHT_DP = 24f

/** Glance silently truncates any Row/Column with more children than this. */
private const val MAX_CONTAINER_CHILDREN = 10

@Composable
private fun ThreeDayTimelineRoot(
    context: Context,
    result: CalendarResult<List<CalendarEvent>>,
    today: LocalDate,
    threeDays: List<LocalDate>,
    theme: CalendarTheme
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(theme.bg)
            .cornerRadius(SURFACE_CORNER_DP.dp)
            .padding(SURFACE_PADDING_DP.dp)
    ) {
        when (result) {
            is CalendarResult.PermissionRequired -> PermissionRequiredThreeDayView(context, theme)
            is CalendarResult.Error -> Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = result.throwable.localizedMessage ?: "Failed to load events",
                    style = TextStyle(
                        color = ColorProvider(theme.sub),
                        fontSize = 11.sp
                    )
                )
            }
            is CalendarResult.Success -> ThreeDayContent(
                context = context,
                today = today,
                threeDays = threeDays,
                events = result.data,
                theme = theme
            )
        }
    }
}

@Composable
private fun ThreeDayContent(
    context: Context,
    today: LocalDate,
    threeDays: List<LocalDate>,
    events: List<CalendarEvent>,
    theme: CalendarTheme
) {
    val zone = ZoneId.systemDefault()
    val timedEvents = events.filter { !it.allDay }
    val nowMillis = System.currentTimeMillis()
    val hours = ThreeDayTimelineGlanceWidget.calculateVisibleHours(threeDays, timedEvents, zone = zone)
    val nextEvent = timedEvents
        .filter { it.begin > nowMillis && CalendarRepository.getEventLocalDate(it) in threeDays }
        .minByOrNull { it.begin }

    Column(modifier = GlanceModifier.fillMaxSize()) {
        TimelineHeader(
            context = context,
            today = today,
            nextEvent = nextEvent,
            nowMillis = nowMillis,
            theme = theme
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        DayHeaderRow(
            threeDays = threeDays,
            today = today,
            theme = theme
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        TimelineGrid(
            threeDays = threeDays,
            today = today,
            timedEvents = timedEvents,
            hours = hours,
            nowMillis = nowMillis,
            theme = theme
        )
    }
}

@Composable
private fun TimelineHeader(
    context: Context,
    today: LocalDate,
    nextEvent: CalendarEvent?,
    nowMillis: Long,
    theme: CalendarTheme
) {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable(actionStartActivity(openAppIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = today.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
            style = TextStyle(
                color = ColorProvider(theme.text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        if (nextEvent != null) {
            Box(
                modifier = GlanceModifier
                    .cornerRadius(12.dp)
                    .background(theme.chipBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Next: ${nextEvent.title} ${countdownText(nextEvent.begin, nowMillis)}",
                    style = TextStyle(
                        color = ColorProvider(theme.chipText),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DayHeaderRow(
    threeDays: List<LocalDate>,
    today: LocalDate,
    theme: CalendarTheme
) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Spacer(modifier = GlanceModifier.width(HOUR_GUTTER_DP.dp))

        threeDays.forEach { date ->
            val isToday = date == today
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                    style = TextStyle(
                        color = ColorProvider(theme.sub),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Box(
                    modifier = GlanceModifier.size(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isToday) {
                        Box(
                            modifier = GlanceModifier
                                .size(22.dp)
                                .cornerRadius(11.dp)
                                .background(theme.todayCircleBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = TextStyle(
                                    color = ColorProvider(theme.todayCircleText),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    } else {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = TextStyle(
                                color = ColorProvider(theme.text),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineGrid(
    threeDays: List<LocalDate>,
    today: LocalDate,
    timedEvents: List<CalendarEvent>,
    hours: IntRange,
    nowMillis: Long,
    theme: CalendarTheme
) {
    val zone = ZoneId.systemDefault()
    val reportedHeightDp = LocalSize.current.height.value
    val gridHeightDp = (
        (if (reportedHeightDp > 0f) reportedHeightDp else FALLBACK_WIDGET_HEIGHT_DP) -
            HEADER_RESERVED_DP
        ).coerceAtLeast(MIN_GRID_HEIGHT_DP)
    val hourCount = hours.count()
    val dpPerMinute = gridHeightDp / (hourCount * 60f)
    val hourHeightDp = 60f * dpPerMinute
    // Keep labels from colliding when the auto-fitted window is very long.
    val labelStep = when {
        hourHeightDp >= 18f -> 1
        hourHeightDp >= 11f -> 2
        else -> 3
    }
    // ColorFilter.tint is not honoured for widget ImageViews on every launcher
    // (the lines came out pure black), so the theme colour is baked into a
    // one-pixel-tall dotted bitmap and stretched instead.
    val density = LocalContext.current.resources.displayMetrics.density
    val reportedWidthDp = LocalSize.current.width.value
    val gridWidthDp =
        (if (reportedWidthDp > 0f) reportedWidthDp else FALLBACK_WIDGET_WIDTH_DP) - HOUR_GUTTER_DP
    val lineProvider = remember(theme.grid, gridWidthDp, density) {
        ImageProvider(
            dottedHourLine(
                color = theme.grid,
                widthPx = (gridWidthDp * density).roundToInt().coerceAtLeast(1),
                density = density
            )
        )
    }

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(gridHeightDp.dp)
    ) {
        // Layer 1: today's column wash.
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Spacer(modifier = GlanceModifier.width(HOUR_GUTTER_DP.dp))
            threeDays.forEach { date ->
                val cellModifier = if (date == today) {
                    GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(10.dp)
                        .background(theme.todayCol)
                } else {
                    GlanceModifier.defaultWeight().fillMaxHeight()
                }
                Box(modifier = cellModifier) {}
            }
        }

        // Layer 2: hour labels and faint dotted hour lines. Glance containers
        // accept at most 10 children, so a long window is split across several
        // columns that keep the same row heights.
        ChunkedColumn(
            items = hours.toList(),
            modifier = GlanceModifier.fillMaxSize()
        ) { hour ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(hourHeightDp.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(HOUR_GUTTER_DP.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    if ((hour - hours.first) % labelStep == 0) {
                        Text(
                            text = hour.toString(),
                            modifier = GlanceModifier.padding(end = 4.dp, top = 1.dp),
                            style = TextStyle(
                                color = ColorProvider(theme.sub),
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Image(
                        provider = lineProvider,
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxWidth().height(1.dp),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }

        // Layer 3: one continuous minute-positioned box per event.
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Spacer(modifier = GlanceModifier.width(HOUR_GUTTER_DP.dp))
            threeDays.forEach { date ->
                val (gridStart, gridEnd) =
                    ThreeDayTimelineGlanceWidget.gridBoundsForDay(date, hours.first, hours.last, zone)
                val clusters = ThreeDayTimelineGlanceWidget.buildDayLayout(
                    events = timedEvents,
                    gridStartMillis = gridStart,
                    gridEndMillis = gridEnd,
                    minDurationMinutes = 0
                )
                DayEventsColumn(
                    clusters = clusters,
                    gridMinutes = hourCount * 60,
                    dpPerMinute = dpPerMinute,
                    isToday = date == today,
                    nowMillis = nowMillis,
                    theme = theme,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
            }
        }
    }
}

/**
 * A 1dp-tall dotted line in [color], used for the hour grid. The theme colour is
 * painted into the pixels because widget ImageViews ignore ColorFilter.tint on
 * some launchers.
 */
private fun dottedHourLine(color: Color, widthPx: Int, density: Float): Bitmap {
    val heightPx = density.roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color.toArgb()
        strokeWidth = heightPx.toFloat()
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = false
    }
    val dash = 1.5f * density
    val gap = 3f * density
    var x = 0f
    while (x < widthPx) {
        canvas.drawLine(x, heightPx / 2f, (x + dash).coerceAtMost(widthPx.toFloat()), heightPx / 2f, paint)
        x += dash + gap
    }
    return bitmap
}

/** A vertical slot inside a day column or event lane. */
private sealed interface ColumnSlot {
    data class Gap(val minutes: Int) : ColumnSlot
    data class Cluster(val cluster: EventCluster) : ColumnSlot
    data class Box(val box: TimedEventBox) : ColumnSlot
}

/**
 * Emits [items] in nested columns of at most [MAX_CONTAINER_CHILDREN] each.
 * Glance truncates any Row/Column with more children (it logs
 * "container cannot have more than 10 elements"), which silently dropped hour
 * rows and could drop events on a busy day. Splitting keeps every child while
 * the fixed heights still stack to the same total.
 */
@Composable
private fun <T> ChunkedColumn(
    items: List<T>,
    modifier: GlanceModifier,
    content: @Composable (T) -> Unit
) {
    Column(modifier = modifier) {
        items.chunked(MAX_CONTAINER_CHILDREN).forEach { chunk ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                chunk.forEach { item -> content(item) }
            }
        }
    }
}

@Composable
private fun DayEventsColumn(
    clusters: List<EventCluster>,
    gridMinutes: Int,
    dpPerMinute: Float,
    isToday: Boolean,
    nowMillis: Long,
    theme: CalendarTheme,
    modifier: GlanceModifier
) {
    Box(modifier = modifier) {
        if (clusters.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Free",
                    style = TextStyle(
                        color = ColorProvider(theme.sub),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
        } else {
            val slots = mutableListOf<ColumnSlot>()
            var cursor = 0
            clusters.forEach { cluster ->
                val gapMinutes = cluster.startMin - cursor
                if (gapMinutes > 0) slots.add(ColumnSlot.Gap(gapMinutes))
                slots.add(ColumnSlot.Cluster(cluster))
                cursor = cluster.endMin
            }
            val tail = gridMinutes - cursor
            if (tail > 0) slots.add(ColumnSlot.Gap(tail))

            ChunkedColumn(items = slots, modifier = GlanceModifier.fillMaxSize()) { slot ->
                when (slot) {
                    is ColumnSlot.Gap -> Spacer(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height((slot.minutes * dpPerMinute).dp)
                    )

                    is ColumnSlot.Cluster -> ClusterLanes(
                        cluster = slot.cluster,
                        dpPerMinute = dpPerMinute,
                        isToday = isToday,
                        nowMillis = nowMillis,
                        theme = theme
                    )

                    is ColumnSlot.Box -> Unit
                }
            }
        }
    }
}

@Composable
private fun ClusterLanes(
    cluster: EventCluster,
    dpPerMinute: Float,
    isToday: Boolean,
    nowMillis: Long,
    theme: CalendarTheme
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(((cluster.endMin - cluster.startMin) * dpPerMinute).dp)
    ) {
        cluster.lanes.forEach { lane ->
            ChunkedColumn(
                items = laneSlots(lane, cluster),
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            ) { slot ->
                when (slot) {
                    is ColumnSlot.Gap -> Spacer(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height((slot.minutes * dpPerMinute).dp)
                    )

                    is ColumnSlot.Box -> TimelineEventCard(
                        box = slot.box,
                        dpPerMinute = dpPerMinute,
                        dimmed = isToday && slot.box.event.end < nowMillis,
                        theme = theme,
                        modifier = GlanceModifier.fillMaxWidth()
                    )

                    is ColumnSlot.Cluster -> Unit
                }
            }
        }
    }
}

/** Lane events padded with the gaps before/between/after them. */
private fun laneSlots(lane: List<TimedEventBox>, cluster: EventCluster): List<ColumnSlot> {
    val slots = mutableListOf<ColumnSlot>()
    var laneCursor = cluster.startMin
    lane.forEach { box ->
        val laneGap = box.startMin - laneCursor
        if (laneGap > 0) slots.add(ColumnSlot.Gap(laneGap))
        slots.add(ColumnSlot.Box(box))
        laneCursor = box.endMin
    }
    val laneTail = cluster.endMin - laneCursor
    if (laneTail > 0) slots.add(ColumnSlot.Gap(laneTail))
    return slots
}


/**
 * One event = one box. The outer slot keeps the exact minute height; a 1dp
 * inset and 10dp corners make the fill, then the 4dp accent bar and text sit
 * inside. Nothing here ever splits an event at an hour boundary.
 */
@Composable
private fun TimelineEventCard(
    box: TimedEventBox,
    dpPerMinute: Float,
    dimmed: Boolean,
    theme: CalendarTheme,
    modifier: GlanceModifier
) {
    val family = theme.eventColors(box.event.title)
    val alpha = if (dimmed) 0.5f else 1f
    val fill = family.fill.copy(alpha = family.fill.alpha * alpha)
    val bar = family.bar.copy(alpha = family.bar.alpha * alpha)
    val titleColor = family.title.copy(alpha = family.title.alpha * alpha)
    val timeColor = family.time.copy(alpha = family.time.alpha * alpha)

    val heightDp = ((box.endMin - box.startMin) * dpPerMinute).dp
    val showTime = heightDp >= TWO_LINE_MIN_HEIGHT_DP.dp

    val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, box.event.eventId)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        data = eventUri
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Box(
        modifier = modifier
            .height(heightDp)
            .padding(horizontal = 1.dp, vertical = 1.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(EVENT_CORNER_DP.dp)
                .background(fill)
                .clickable(actionStartActivity(viewIntent))
        ) {
            Box(
                // Inset so the square bar never pokes past the card's rounded
                // left corners; Glance only rounds backgrounds, it does not
                // clip children.
                modifier = GlanceModifier
                    .width(ACCENT_BAR_DP.dp)
                    .fillMaxHeight()
                    .padding(vertical = 5.dp)
                    .cornerRadius(2.dp)
                    .background(bar)
            ) {}

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(start = 4.dp, end = 2.dp, top = 1.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = box.event.title,
                    style = TextStyle(
                        color = ColorProvider(titleColor),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                if (showTime) {
                    Text(
                        text = ThreeDayTimelineGlanceWidget.formatTimeRange(box.event.begin, box.event.end),
                        style = TextStyle(
                            color = ColorProvider(timeColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun countdownText(startMillis: Long, nowMillis: Long): String {
    val minutes = ((startMillis - nowMillis) / 60000L).coerceAtLeast(1)
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "in ${hours}h ${mins}m"
        hours > 0 -> "in ${hours}h"
        else -> "in ${mins}m"
    }
}

@Composable
private fun PermissionRequiredThreeDayView(context: Context, theme: CalendarTheme) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Calendar access required",
            style = TextStyle(
                color = ColorProvider(theme.text),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier
                .cornerRadius(12.dp)
                .background(theme.todayCircleBg)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Grant permission",
                style = TextStyle(
                    color = ColorProvider(theme.todayCircleText),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            )
        }
    }
}
