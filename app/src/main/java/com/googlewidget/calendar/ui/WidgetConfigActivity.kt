package com.googlewidget.calendar.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.googlewidget.calendar.data.CalendarAccount
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.ui.theme.GoogleWidgetTheme
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set default result to CANCELED per Android AppWidget specification
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            GoogleWidgetTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Configure Widget") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { innerPadding ->
                    WidgetConfigContent(
                        modifier = Modifier.padding(innerPadding),
                        onSave = { selectedIds, opacity, themeMode, lookaheadDays, fontScale ->
                            saveConfigurationAndFinish(
                                selectedIds = selectedIds,
                                opacity = opacity,
                                themeMode = themeMode,
                                lookaheadDays = lookaheadDays,
                                fontScale = fontScale
                            )
                        }
                    )
                }
            }
        }
    }

    private fun saveConfigurationAndFinish(
        selectedIds: Set<Long>,
        opacity: Float,
        themeMode: String,
        lookaheadDays: Int,
        fontScale: String
    ) {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                    val glanceId = manager.getGlanceIdBy(appWidgetId)

                    updateAppWidgetState(
                        this@WidgetConfigActivity,
                        PreferencesGlanceStateDefinition,
                        glanceId
                    ) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[WidgetConfigKeys.SELECTED_CALENDARS] =
                                selectedIds.map { it.toString() }.toSet()
                            this[WidgetConfigKeys.BG_OPACITY] = opacity
                            this[WidgetConfigKeys.THEME_MODE] = themeMode
                            this[WidgetConfigKeys.LOOKAHEAD_DAYS] = lookaheadDays
                            this[WidgetConfigKeys.FONT_SCALE] = fontScale
                        }
                    }

                    // Trigger widget update
                    AgendaGlanceWidget().update(this@WidgetConfigActivity, glanceId)
                    NextUpGlanceWidget().update(this@WidgetConfigActivity, glanceId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    val resultValue = Intent().apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    setResult(RESULT_OK, resultValue)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigContent(
    modifier: Modifier = Modifier,
    onSave: (Set<Long>, Float, String, Int, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { CalendarRepository(context) }
    var calendars by remember { mutableStateOf<List<CalendarAccount>>(emptyList()) }
    var selectedCalendarIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var opacity by remember { mutableFloatStateOf(WidgetConfigKeys.DEFAULT_OPACITY) }
    var themeMode by remember { mutableStateOf(WidgetConfigKeys.THEME_SYSTEM) }
    var lookaheadDays by remember { mutableIntStateOf(WidgetConfigKeys.DEFAULT_LOOKAHEAD_DAYS) }
    var fontScale by remember { mutableStateOf(WidgetConfigKeys.FONT_SCALE_NORMAL) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            val list = repository.getCalendars()
            calendars = list
            selectedCalendarIds = list.map { it.id }.toSet()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            withContext(Dispatchers.IO) {
                val list = repository.getCalendars()
                calendars = list
                selectedCalendarIds = list.map { it.id }.toSet()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calendar Selection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Calendars to Include",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select which calendars should appear in the widget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        if (!hasPermission) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Calendar Permission")
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = if (selectedCalendarIds.size == calendars.size) "Deselect All" else "Select All",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        selectedCalendarIds = if (selectedCalendarIds.size == calendars.size) {
                                            emptySet()
                                        } else {
                                            calendars.map { it.id }.toSet()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (hasPermission && calendars.isNotEmpty()) {
                items(calendars, key = { it.id }) { calendar ->
                    val isChecked = selectedCalendarIds.contains(calendar.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedCalendarIds = if (isChecked) {
                                    selectedCalendarIds - calendar.id
                                } else {
                                    selectedCalendarIds + calendar.id
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(calendar.color))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = calendar.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (calendar.accountName.isNotBlank()) {
                                Text(
                                    text = calendar.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedCalendarIds = if (checked) {
                                    selectedCalendarIds + calendar.id
                                } else {
                                    selectedCalendarIds - calendar.id
                                }
                            }
                        )
                    }
                }
            }

            // Theme Options Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme & Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Theme Mode Chips
                        Text(
                            text = "Widget Theme",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                WidgetConfigKeys.THEME_SYSTEM to "Dynamic",
                                WidgetConfigKeys.THEME_DARK to "Dark",
                                WidgetConfigKeys.THEME_LIGHT to "Light"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { themeMode = mode },
                                    label = { Text(label) },
                                    leadingIcon = if (themeMode == mode) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Background Opacity
                        val percentage = (opacity * 100).roundToInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Background Opacity",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.0f..1.0f,
                            steps = 19
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Font Scale
                        Text(
                            text = "Font Scale",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                WidgetConfigKeys.FONT_SCALE_NORMAL to "Normal",
                                WidgetConfigKeys.FONT_SCALE_COMPACT to "Compact"
                            ).forEach { (scale, label) ->
                                FilterChip(
                                    selected = fontScale == scale,
                                    onClick = { fontScale = scale },
                                    label = { Text(label) },
                                    leadingIcon = if (fontScale == scale) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // Lookahead Days Card (For Agenda)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Agenda Lookahead Range",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Number of days in advance to display schedule items.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(7, 14, 30).forEach { days ->
                                FilterChip(
                                    selected = lookaheadDays == days,
                                    onClick = { lookaheadDays = days },
                                    label = { Text("$days Days") },
                                    leadingIcon = if (lookaheadDays == days) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save Button
        Button(
            onClick = {
                onSave(selectedCalendarIds, opacity, themeMode, lookaheadDays, fontScale)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Save Widget Configuration",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
