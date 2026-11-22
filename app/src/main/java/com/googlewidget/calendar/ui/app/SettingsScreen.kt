package com.googlewidget.calendar.ui.app

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.googlewidget.calendar.data.CalendarAccount
import com.googlewidget.calendar.ui.theme.LocalCalendarTheme

@Composable
fun SettingsScreen(
    modifier: Modifier,
    calendars: List<CalendarAccount>,
    disabledCalendarIds: Set<Long>,
    hasPermission: Boolean,
    onTheme: () -> Unit,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    onToggleCalendar: (Long) -> Unit,
    onShowAllCalendars: () -> Unit
) {
    val theme = LocalCalendarTheme.current
    val shownCount = calendars.count { it.id !in disabledCalendarIds }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("One palette for the app and all four widgets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("Current: ${theme.label}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onTheme) { Text("Choose theme") }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = if (hasPermission) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.errorContainer
            )) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Calendar source", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (hasPermission) "Google Calendar is connected. Choose below which calendars feed Today and Calendar."
                        else "Calendar access is off. Grant read access to see your events.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!hasPermission) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onGrant) { Text("Grant permission") }
                        TextButton(onClick = onSettings) { Text("Open app settings") }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Calendars", style = MaterialTheme.typography.titleMedium)
                    Text("$shownCount of ${calendars.size} shown in the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (disabledCalendarIds.isNotEmpty()) {
                    TextButton(onClick = onShowAllCalendars) { Text("Show all") }
                }
            }
        }
        items(calendars, key = { "${it.id}-${it.accountName}" }) { calendar ->
            val enabled = calendar.id !in disabledCalendarIds
            Row(
                Modifier.fillMaxWidth().background(
                    MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)
                ).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(12.dp).background(Color(calendar.color), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(calendar.displayName, style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (calendar.accountName.isNotBlank()) {
                        Text(calendar.accountName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggleCalendar(calendar.id) }
                )
            }
        }
        item {
            Text("Widgets keep their own calendar selection in widget settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Add the widgets", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Long press an empty spot on the home screen.",
                        "Choose Widgets, then pick Calendar Widget.",
                        "Select the 3-Day Timeline, 7-Day, Agenda or Next Up widget.",
                        "Place it and adjust the size by dragging its edge."
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
