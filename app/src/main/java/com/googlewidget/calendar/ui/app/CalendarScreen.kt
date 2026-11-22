package com.googlewidget.calendar.ui.app

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.ui.theme.CalendarTheme
import com.googlewidget.calendar.ui.theme.LocalCalendarTheme
import com.googlewidget.calendar.widget.WeeklyCalendarLogic
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MODES = listOf("Day", "Week", "Month")

@Composable
fun CalendarScreen(
    modifier: Modifier,
    events: List<CalendarEvent>,
    today: LocalDate,
    isLoading: Boolean
) {
    val theme = LocalCalendarTheme.current
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(1) }
    var selected by remember { mutableStateOf(today) }
    var weekOffset by remember { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()

    // The concept's week strip is Monday-first (Mon ... Sun).
    val week = WeeklyCalendarLogic.weekDates(today, weekOffset * 7, WidgetConfigKeys.WEEK_START_MONDAY)
    val byDate = events.groupBy { CalendarRepository.getEventLocalDate(it) }
    val dayEvents = byDate[selected].orEmpty()
    val timeline = remember(dayEvents, now, selected) {
        TimelineLayout.build(dayEvents, now)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        ModeSwitcher(theme, mode) { mode = it }
        Spacer(Modifier.height(18.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (mode == 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        weekOffset -= 1
                        selected = selected.minusWeeks(1)
                    }) { Icon(Icons.Default.ChevronLeft, "Previous week", tint = theme.sub) }
                    Text(
                        WeeklyCalendarLogic.rangeLabel(week.first(), week.last()),
                        color = theme.sub, fontSize = 12.sp,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                    IconButton(onClick = {
                        weekOffset += 1
                        selected = selected.plusWeeks(1)
                    }) { Icon(Icons.Default.ChevronRight, "Next week", tint = theme.sub) }
                }
                Spacer(Modifier.height(6.dp))
            }

            when (mode) {
                0 -> DayHeader(theme, selected) { delta ->
                    selected = selected.plusDays(delta.toLong())
                }
                1 -> WeekStrip(theme, week, selected, today, byDate) { selected = it }
                else -> MonthGrid(theme, selected, today, byDate) { date ->
                    selected = date
                    mode = 0
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        CalendarRepository.formatDayHeader(selected, today),
                        color = theme.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${dayEvents.size} events", color = theme.sub, fontSize = 12.sp)
                }
                IconButton(onClick = { insertEvent(context, now, now + 30 * 60_000L) }) {
                    Icon(Icons.Default.Add, "Add event", tint = theme.todayCircleBg)
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                isLoading && dayEvents.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = theme.todayCircleBg) }

                dayEvents.isEmpty() -> Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.todayCol),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Nothing planned", color = theme.text, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("This day is open. Add an event to fill the gap.",
                            color = theme.sub, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        NeonPillButton("Schedule an event", theme) {
                            insertEvent(context, now, now + 30 * 60_000L)
                        }
                    }
                }

                else -> DayTimeline(theme, timeline, selected, now, context)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModeSwitcher(theme: CalendarTheme, mode: Int, onMode: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(theme.chipBg.copy(alpha = 0.85f))
            .padding(4.dp)
    ) {
        MODES.forEachIndexed { index, label ->
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (mode == index) Modifier.background(neonAccent(theme)) else Modifier)
                    .clickable { onMode(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (mode == index) Color.White else theme.sub,
                    fontSize = 14.sp,
                    fontWeight = if (mode == index) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DayHeader(theme: CalendarTheme, date: LocalDate, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onStep(-1) }) {
            Icon(Icons.Default.ChevronLeft, "Previous day", tint = theme.text)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())),
                color = theme.sub, fontSize = 12.sp)
            Text(date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                color = theme.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = { onStep(1) }) {
            Icon(Icons.Default.ChevronRight, "Next day", tint = theme.text)
        }
    }
}

@Composable
private fun WeekStrip(
    theme: CalendarTheme,
    week: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    byDate: Map<LocalDate, List<CalendarEvent>>,
    onSelect: (LocalDate) -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        week.forEach { date ->
            val isSelected = date == selected
            val isToday = date == today
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (isSelected) Modifier.background(neonAccent(theme)) else Modifier)
                    .clickable { onSelect(date) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    WeeklyCalendarLogic.shortWeekday(date),
                    color = if (isSelected) Color.White else theme.sub,
                    fontSize = 11.sp, maxLines = 1
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(if (isToday) CircleShape else RoundedCornerShape(10.dp))
                        .background(
                            if (isToday && !isSelected) theme.todayCircleBg else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        color = if (isSelected || isToday) Color.White else theme.text,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.height(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val eventColors = byDate[date].orEmpty().take(3)
                        .map { theme.eventColors(it.title).bar }
                    eventColors.forEach { color ->
                        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(2.dp))
                    }
                    val extra = byDate[date].orEmpty().size - eventColors.size
                    if (extra > 0) {
                        Text("+$extra", color = if (isSelected) Color.White else theme.sub,
                            fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    theme: CalendarTheme,
    selected: LocalDate,
    today: LocalDate,
    byDate: Map<LocalDate, List<CalendarEvent>>,
    onSelect: (LocalDate) -> Unit
) {
    val month = YearMonth.from(selected)
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = buildList {
        repeat(leading) { add(null) }
        (1..month.lengthOfMonth()).forEach { add(month.atDay(it)) }
        while (size % 7 != 0) add(null)
    }
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(day, color = theme.sub, fontSize = 11.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { weekRow ->
            Row(Modifier.fillMaxWidth()) {
                weekRow.forEach { date ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                when {
                                    date == selected -> Modifier.background(neonAccent(theme))
                                    date == today -> Modifier.background(theme.todayCol)
                                    else -> Modifier
                                }
                            )
                            .then(if (date != null) Modifier.clickable { onSelect(date) } else Modifier)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    color = if (date == selected) Color.White else theme.text,
                                    fontSize = 13.sp
                                )
                                val dots = byDate[date].orEmpty().take(3)
                                if (dots.isNotEmpty()) {
                                    Spacer(Modifier.height(3.dp))
                                    Row {
                                        dots.forEach {
                                            Box(
                                                Modifier.size(3.dp).clip(CircleShape)
                                                    .background(theme.eventColors(it.title).bar)
                                            )
                                            Spacer(Modifier.width(2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(
    theme: CalendarTheme,
    model: TimelineModel,
    date: LocalDate,
    now: Long,
    context: Context
) {
    val hourHeight = 72.dp
    val laneCount = model.blocks.maxOfOrNull { it.laneCount } ?: 1
    BoxWithConstraints(Modifier.fillMaxWidth().height(hourHeight * model.hourCount)) {
        val laneWidth = maxWidth / laneCount
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(46.dp).fillMaxHeight()) {
                repeat(model.hourCount) { index ->
                    Box(Modifier.fillMaxWidth().height(hourHeight), contentAlignment = Alignment.TopEnd) {
                        Text(
                            "%02d:00".format(model.startMinute / 60 + index),
                            color = theme.sub, fontSize = 11.sp,
                            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(Modifier.fillMaxSize()) {
                    val dash = 3.dp.toPx()
                    val gap = 5.dp.toPx()
                    for (hour in 1 until model.hourCount) {
                        val y = hourHeight.toPx() * hour
                        drawLine(
                            color = theme.grid,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))
                        )
                    }
                }

                val nowMinute = TimelineLayout.minuteOfDay(now)
                if (nowMinute in model.startMinute until model.endMinute) {
                    Box(
                        Modifier
                            .offset(y = hourHeight * ((nowMinute - model.startMinute) / 60f))
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(theme.a.bar.copy(alpha = 0.8f))
                    )
                }

                model.freeSlot?.let { slot ->
                    val begin = minuteMillis(date, model.startMinute + slot.startMinute)
                    val finish = minuteMillis(date, model.startMinute + slot.endMinute)
                    Box(
                        Modifier
                            .offset(y = hourHeight * (slot.startMinute / 60f))
                            .fillMaxWidth()
                            .height(hourHeight * ((slot.endMinute - slot.startMinute) / 60f))
                            .padding(end = 8.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.todayCol.copy(alpha = 0.92f))
                            .border(1.dp, theme.grid, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Large free slot", color = theme.text, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text(minuteLabel(model.startMinute + slot.startMinute) +
                                " - " + minuteLabel(model.startMinute + slot.endMinute),
                                color = theme.sub, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            NeonPillButton("Schedule an event", theme) {
                                insertEvent(context, begin, finish)
                            }
                        }
                    }
                }

                model.blocks.forEach { block ->
                    EventTimelineCard(
                        theme = theme,
                        block = block,
                        context = context,
                        modifier = Modifier
                            .offset(
                                x = laneWidth * block.lane,
                                y = hourHeight * (block.startMinute / 60f)
                            )
                            .width(laneWidth)
                            .height(hourHeight * ((block.endMinute - block.startMinute) / 60f))
                            .padding(end = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EventTimelineCard(
    theme: CalendarTheme,
    block: TimelineBlock,
    context: Context,
    modifier: Modifier = Modifier
) {
    val family = theme.eventColors(block.event.title)
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(family.fill, RoundedCornerShape(12.dp))
            .border(1.dp, family.bar.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { openCalendarEvent(context, block.event) }
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.width(4.dp).fillMaxHeight().padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(2.dp)).background(family.bar)
            )
            Column(
                Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, end = 2.dp)
            ) {
                Text(block.event.title, color = family.title, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(CalendarRepository.formatEventTime(block.event),
                    color = family.time, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, "Event options",
                        tint = family.time, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("View in Calendar") },
                        onClick = {
                            menuExpanded = false
                            openCalendarEvent(context, block.event)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to calendar") },
                        onClick = {
                            menuExpanded = false
                            insertEvent(context, block.event.begin, block.event.end)
                        }
                    )
                }
            }
        }
    }
}

internal fun minuteLabel(minute: Int): String =
    "%02d:%02d".format(minute / 60, minute % 60)

private fun minuteMillis(date: LocalDate, minute: Int): Long =
    date.atTime(LocalTime.of(minute / 60, minute % 60))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun openCalendarEvent(context: Context, event: CalendarEvent) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
    }
    runCatching { context.startActivity(intent) }
}

private fun insertEvent(context: Context, begin: Long, end: Long) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    runCatching { context.startActivity(intent) }
}
