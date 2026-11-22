package com.googlewidget.calendar.ui.app

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.TaskItem
import com.googlewidget.calendar.ui.theme.LocalCalendarTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
    onEditTask: (TaskItem) -> Unit,
    onSeeAll: () -> Unit,
    onRefresh: () -> Unit,
    onTheme: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalCalendarTheme.current
    var query by remember { mutableStateOf("") }
    var showReminders by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val dayEvents = events.filter { CalendarRepository.getEventLocalDate(it) == today }
    val futureEvents = events.filter { it.end > now }.sortedBy { it.begin }
    val upcoming = futureEvents.firstOrNull()
    val todayTasks = tasks.filter { it.dueDate == today.toString() || it.dueDate == null }
    val doneCount = todayTasks.count { it.done }
    val matchingTasks = todayTasks.filter {
        query.isBlank() || it.title.contains(query, true) || it.note.contains(query, true)
    }
    val matchingEvents = dayEvents.filter {
        query.isBlank() || it.title.contains(query, true) || it.location.orEmpty().contains(query, true)
    }

    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning,"
        in 12..17 -> "Good afternoon,"
        else -> "Good evening,"
    }
    val dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
    val clockLabel = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        .toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonAvatar(theme = theme, label = "A")
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(greeting, color = theme.sub, fontSize = 13.sp)
                    Text("Alisia", color = theme.text, fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Box {
                    IconButton(onClick = { showReminders = !showReminders }) {
                        Icon(Icons.Default.NotificationsNone, "Upcoming reminders", tint = theme.text)
                    }
                    if (futureEvents.isNotEmpty()) {
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 10.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(theme.todayCircleBg)
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More options", tint = theme.text)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = { showMenu = false; onRefresh() },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Change theme") },
                            onClick = { showMenu = false; onTheme() }
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("TODAY", color = theme.sub, fontSize = 11.sp)
                    Text(dateLabel, color = theme.text, fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("NOW", color = theme.sub, fontSize = 11.sp)
                    Text(clockLabel, color = theme.text, fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }

        item { NeonSearchField(query, { query = it }, theme) }

        if (showReminders) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.chipBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Coming up", color = theme.text, fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        val reminders = futureEvents.take(3)
                        if (reminders.isEmpty()) {
                            Text("Nothing coming up.", color = theme.sub, fontSize = 13.sp)
                        } else {
                            reminders.forEach { event ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(
                                        CalendarRepository.formatEventTime(event),
                                        color = theme.chipText, fontSize = 11.sp,
                                        maxLines = 1,
                                        modifier = Modifier.width(128.dp)
                                    )
                                    Text(event.title, color = theme.text, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!hasPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.chipBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Calendar access needed", color = theme.text, fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Grant read access to show Google Calendar events here.",
                            color = theme.sub, fontSize = 12.sp)
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.chipBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(errorMessage, modifier = Modifier.padding(16.dp),
                        color = theme.text, fontSize = 13.sp)
                }
            }
        }

        item {
            NeonSectionTitle("Upcoming event", theme) {
                IconButton(onClick = { upcoming?.let { openEvent(context, it) } }) {
                    Icon(Icons.Default.Edit, "Open event", tint = theme.todayCircleBg)
                }
            }
        }

        item {
            if (upcoming == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.todayCol),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Nothing scheduled ahead", color = theme.text, fontSize = 16.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Add an event and it will show up here.", color = theme.sub, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        NeonPillButton("Schedule an event", theme) {
                            addToCalendar(context, now, now + 30 * 60_000L, "")
                        }
                    }
                }
            } else {
                UpcomingEventCard(theme, upcoming, now, context)
            }
        }

        item {
            NeonSectionTitle("My tasks", theme) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(theme.chipBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("${todayTasks.size}", color = theme.teen.bar,
                            fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("See all", color = theme.teen.bar, fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onSeeAll)
                            .padding(start = 6.dp).padding(vertical = 12.dp))
                }
            }
        }

        if (todayTasks.isNotEmpty()) {
            item {
                Column {
                    Text("$doneCount of ${todayTasks.size} done", color = theme.sub, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { doneCount.toFloat() / todayTasks.size },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                        color = theme.todayCircleBg,
                        trackColor = theme.grid
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        if (matchingTasks.isEmpty()) {
            item {
                Text(
                    if (todayTasks.isEmpty()) "No tasks for today. Add one with the + button."
                    else "No tasks match your search.",
                    color = theme.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        } else {
            items(matchingTasks, key = { it.id }) { task ->
                TaskRow(task, theme,
                    { onToggleTask(task.id) },
                    { onDeleteTask(task.id) },
                    { onEditTask(task) })
            }
        }

        item {
            NeonSectionTitle("Schedule", theme)
        }

        if (isLoading && matchingEvents.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.todayCircleBg)
                }
            }
        } else if (matchingEvents.isEmpty()) {
            item { Text("No events on this day.", color = theme.sub, fontSize = 13.sp) }
        } else {
            items(matchingEvents, key = { CalendarRepository.eventInstanceKey(it) }) { event ->
                ScheduleRow(event, theme) { openEvent(context, event) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun UpcomingEventCard(
    theme: com.googlewidget.calendar.ui.theme.CalendarTheme,
    event: CalendarEvent,
    now: Long,
    context: Context
) {
    val isOnline = event.location?.let {
        it.contains("http", true) || it.contains("meet", true) || it.contains("zoom", true)
    } == true
    val colors = theme.eventColors(event.title)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = theme.todayCol),
        modifier = Modifier.fillMaxWidth().border(1.dp, theme.grid.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null,
                        tint = theme.todayCircleBg, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(event.title, color = colors.title, fontSize = 17.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(CalendarRepository.formatEventTime(event), color = theme.sub, fontSize = 13.sp)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(theme.chipBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(if (isOnline) "Online" else "In person", color = theme.chipText, fontSize = 11.sp)
                }
            }
            if (!event.location.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                NeonCaption(event.location, theme)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonPillButton("Open event", theme) { openEvent(context, event) }
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(theme.chipBg.copy(alpha = 0.8f))
                        .clickable {
                            addToCalendar(context, event.begin, event.end, event.title)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("Add to calendar", color = theme.chipText, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    theme: com.googlewidget.calendar.ui.theme.CalendarTheme,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.chipBg.copy(alpha = 0.55f))
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .then(if (task.done) Modifier.background(neonAccent(theme)) else Modifier)
                    .then(
                        if (task.done) Modifier
                        else Modifier.border(1.5.dp, theme.sub, RoundedCornerShape(7.dp))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (task.done) Icon(Icons.Default.Check, "Done", tint = Color.White,
                    modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                color = if (task.done) theme.sub else theme.text,
                fontSize = 14.sp,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val detail = listOfNotNull(task.note.ifBlank { null }, task.dueDate).joinToString("  ·  ")
            if (detail.isNotBlank()) NeonCaption(detail, theme)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete task", tint = theme.sub,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ScheduleRow(
    event: CalendarEvent,
    theme: com.googlewidget.calendar.ui.theme.CalendarTheme,
    onClick: () -> Unit
) {
    val colors = theme.eventColors(event.title)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(theme.todayCol).clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(4.dp, 32.dp).clip(RoundedCornerShape(2.dp)).background(colors.bar))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, color = colors.title, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(CalendarRepository.formatEventTime(event), color = colors.time, fontSize = 12.sp)
        }
    }
}

private fun openEvent(context: Context, event: CalendarEvent) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
    }
    runCatching { context.startActivity(intent) }
}

private fun addToCalendar(context: Context, begin: Long, end: Long, title: String) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    runCatching { context.startActivity(intent) }
}
