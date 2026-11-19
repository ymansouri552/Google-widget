package com.googlewidget.calendar.ui.app

import android.content.ContentUris
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.TaskItem
import com.googlewidget.calendar.widget.WeeklyCalendarLogic
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    modifier: Modifier,
    events: List<CalendarEvent>,
    tasks: List<TaskItem>,
    today: LocalDate,
    isLoading: Boolean,
    errorMessage: String?,
    hasPermission: Boolean,
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onEditTask: (TaskItem) -> Unit
) {
    val context = LocalContext.current
    val dayEvents = events.filter { CalendarRepository.getEventLocalDate(it) == today }
    val todayTasks = tasks.filter { it.dueDate == today.toString() || it.dueDate == null }
    val stats = WeeklyCalendarLogic.dailyStats(dayEvents)
    val next = dayEvents
        .filter { it.end > System.currentTimeMillis() }
        .minByOrNull { it.begin }
    val header = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(header, style = MaterialTheme.typography.headlineMedium)
                val countdown = next?.let { CalendarRepository.formatNextUpCountdown(it) }
                if (countdown != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(countdown, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (!hasPermission) {
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Calendar access needed", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Grant read access to show your Google Calendar events here.",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = if (permanentlyDenied) onSettings else onGrant) {
                            Text(if (permanentlyDenied) "Open app settings" else "Grant permission")
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )) {
                    Text(errorMessage, modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatBlock(stats.classCount.toString(), "Classes", Modifier.weight(1f))
                    StatBlock(WeeklyCalendarLogic.formatTeachingTime(stats.teachingMinutes), "Teaching", Modifier.weight(1f))
                    StatBlock(
                        stats.lastEndMillis?.let {
                            java.time.Instant.ofEpochMilli(it)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                        } ?: "-",
                        "Last ends", Modifier.weight(1f)
                    )
                }
            }
        }

        item { SectionTitle("To do today") }
        if (todayTasks.isEmpty()) {
            item { EmptyHint("No tasks for today. Add one with the + button.") }
        } else {
            items(todayTasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { onToggleTask(task.id) },
                    onDelete = { onDeleteTask(task.id) },
                    onEdit = { onEditTask(task) }
                )
            }
        }

        item { SectionTitle("Schedule") }
        if (isLoading && dayEvents.isEmpty()) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            } }
        } else if (dayEvents.isEmpty()) {
            item { EmptyHint("No events on this day.") }
        } else {
            items(dayEvents, key = { CalendarRepository.eventInstanceKey(it) }) { event ->
                EventRow(event = event, onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = ContentUris.withAppendedId(
                            android.provider.CalendarContract.Events.CONTENT_URI, event.eventId
                        )
                    }
                    context.startActivity(intent)
                })
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (task.done) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (task.done) Text("✓", color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.note.isNotBlank()) {
                Text(task.note, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("Delete", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable(onClick = onDelete))
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(4.dp, 32.dp).background(Color(event.color), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(CalendarRepository.formatEventTime(event), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
