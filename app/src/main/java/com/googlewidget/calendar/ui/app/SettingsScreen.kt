package com.googlewidget.calendar.ui.app

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.googlewidget.calendar.data.CalendarAccount

@Composable
fun SettingsScreen(
    modifier: Modifier,
    calendars: List<CalendarAccount>,
    hasPermission: Boolean,
    onTheme: () -> Unit,
    onGrant: () -> Unit,
    onSettings: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Pick the palette used by widgets. One choice covers all four widget types.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        if (hasPermission) "Google Calendar is connected. Events appear in Today and Calendar."
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

        item { Text("Connected calendars (${calendars.size})", style = MaterialTheme.typography.titleMedium) }
        items(calendars, key = { "${it.id}-${it.accountName}" }) { calendar ->
            Row(
                Modifier.fillMaxWidth().background(
                    MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)
                ).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(12.dp).background(Color(calendar.color), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(calendar.displayName, style = MaterialTheme.typography.bodyMedium)
                    if (calendar.accountName.isNotBlank()) {
                        Text(calendar.accountName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
