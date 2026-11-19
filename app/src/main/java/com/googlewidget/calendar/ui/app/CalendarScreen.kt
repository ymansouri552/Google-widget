package com.googlewidget.calendar.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.widget.WeeklyCalendarLogic
import java.time.LocalDate

@Composable
fun CalendarScreen(
    modifier: Modifier,
    events: List<CalendarEvent>,
    today: LocalDate,
    isLoading: Boolean
) {
    var selected by remember { mutableStateOf(today) }
    var weekOffset by remember { mutableIntStateOf(0) }
    val week = WeeklyCalendarLogic.weekDates(today, weekOffset, WidgetConfigKeys.DEFAULT_WEEK_START)
    val byDate = events.groupBy { CalendarRepository.getEventLocalDate(it) }
    val dayEvents = byDate[selected].orEmpty()

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                weekOffset -= 1
                selected = selected.minusWeeks(1)
            }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous week") }
            Text(WeeklyCalendarLogic.rangeLabel(week.first(), week.last()),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = {
                weekOffset += 1
                selected = selected.plusWeeks(1)
            }) { Icon(Icons.Default.ChevronRight, contentDescription = "Next week") }
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                val isSelected = date == selected
                val isToday = date == today
                Column(
                    Modifier.weight(1f).clickable { selected = date }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        ).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(WeeklyCalendarLogic.shortWeekday(date),
                        style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    if (isToday) {
                        Box(Modifier.size(26.dp).background(
                            MaterialTheme.colorScheme.primary, CircleShape
                        ), contentAlignment = Alignment.Center) {
                            Text(date.dayOfMonth.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        byDate[date].orEmpty().take(3).forEach { event ->
                            Box(Modifier.size(5.dp).background(Color(event.color), CircleShape))
                            Spacer(Modifier.size(2.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(CalendarRepository.formatDayHeader(selected, today),
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (isLoading && dayEvents.isEmpty()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (dayEvents.isEmpty()) {
            Text("No events on this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayEvents, key = { CalendarRepository.eventInstanceKey(it) }) { event ->
                    Row(
                        Modifier.fillMaxWidth().background(
                            MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)
                        ).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(4.dp, 32.dp).background(
                            Color(event.color), RoundedCornerShape(2.dp)
                        ))
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(CalendarRepository.formatEventTime(event),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            CalendarRepository.getEventLocalDate(event).dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
