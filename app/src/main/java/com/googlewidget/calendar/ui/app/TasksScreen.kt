package com.googlewidget.calendar.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.googlewidget.calendar.data.TaskFilter
import com.googlewidget.calendar.data.TaskItem
import com.googlewidget.calendar.data.TaskLogic
import com.googlewidget.calendar.ui.theme.LocalCalendarTheme
import java.time.LocalDate

@Composable
fun TasksScreen(
    modifier: Modifier,
    tasks: List<TaskItem>,
    today: LocalDate,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (TaskItem) -> Unit
) {
    val theme = LocalCalendarTheme.current
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    val visible = TaskLogic.filter(tasks, filter, today)
    val doneCount = tasks.count { it.done }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("$doneCount of ${tasks.size} tasks done",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = theme.todayCircleBg,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = {
                        Text(
                            when (option) {
                                TaskFilter.ALL -> "All"
                                TaskFilter.TODAY -> "Today"
                                TaskFilter.UPCOMING -> "Upcoming"
                                TaskFilter.NO_DATE -> "No date"
                                TaskFilter.COMPLETED -> "Completed"
                            }
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visible, key = { it.id }) { task ->
                val bucket = TaskLogic.bucket(task, today)
                Column {
                    Text(bucket, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                    TaskRow(
                        task = task,
                        onToggle = { onToggle(task.id) },
                        onDelete = { onDelete(task.id) },
                        onEdit = { onEdit(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(22.dp)
                    .background(
                        if (task.done) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape
                    )
                    .then(
                        if (task.done) Modifier
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (task.done) Text("✓", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.note.isNotBlank() || task.dueDate != null) {
                Text(listOfNotNull(task.note.ifBlank { null }, task.dueDate).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(44.dp).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Text("Delete", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun TaskEditorDialog(
    task: TaskItem,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var note by remember { mutableStateOf(task.note) }
    var due by remember { mutableStateOf(task.dueDate.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task.title.isEmpty()) "New task" else "Edit task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = due, onValueChange = { due = it },
                    label = { Text("Due date (YYYY-MM-DD)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { due = LocalDate.now().toString() }) {
                    Text("Today")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = due.ifBlank { null }?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()?.toString()
                }
                onSave(title, note, parsed)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
