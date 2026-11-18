package com.googlewidget.calendar.ui

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.googlewidget.calendar.data.CalendarAccount
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.ThemePreferences
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.ui.theme.CalendarThemes
import com.googlewidget.calendar.ui.theme.GoogleWidgetTheme
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.WeeklyCalendarGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import com.googlewidget.calendar.widget.WidgetLiveState
import com.googlewidget.calendar.widget.WidgetLiveStateHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun openAppSettings(context: android.content.Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
                        onSave = { selectedIds, opacity, lookaheadDays, fontScale, chevronStepDays, timelineMinHour, timelineMaxHour, weekStart ->
                            saveConfigurationAndFinish(
                                selectedIds = selectedIds,
                                opacity = opacity,
                                lookaheadDays = lookaheadDays,
                                fontScale = fontScale,
                                chevronStepDays = chevronStepDays,
                                timelineMinHour = timelineMinHour,
                                timelineMaxHour = timelineMaxHour,
                                weekStart = weekStart
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
        lookaheadDays: Int,
        fontScale: String,
        chevronStepDays: Int,
        timelineMinHour: Int?,
        timelineMaxHour: Int?,
        weekStart: String
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                CalendarRepository.clearCache()
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                    val glanceId = manager.getGlanceIdBy(appWidgetId)

                    updateAppWidgetState(
                        this@WidgetConfigActivity,
                        PreferencesGlanceStateDefinition,
                        glanceId
                    ) { prefs ->
                        val updated = prefs.toMutablePreferences().apply {
                            // Persist a placeholder when nothing is selected so the widget
                            // shows no events; an empty set would filter to "all calendars".
                            this[WidgetConfigKeys.SELECTED_CALENDARS] =
                                if (selectedIds.isEmpty()) {
                                    setOf(WidgetConfigKeys.NO_CALENDAR_PLACEHOLDER)
                                } else {
                                    selectedIds.map { it.toString() }.toSet()
                                }
                            this[WidgetConfigKeys.BG_OPACITY] = opacity
                            this[WidgetConfigKeys.LOOKAHEAD_DAYS] = lookaheadDays
                            this[WidgetConfigKeys.FONT_SCALE] = fontScale
                            this[WidgetConfigKeys.KEY_CHEVRON_STEP_DAYS] =
                                WidgetConfigKeys.normalizedChevronStep(chevronStepDays)
                            this[WidgetConfigKeys.KEY_WEEK_START] =
                                WidgetConfigKeys.normalizedWeekStart(weekStart)
                            val (minHour, maxHour) = WidgetConfigKeys.normalizeHourBounds(
                                timelineMinHour,
                                timelineMaxHour
                            )
                            if (minHour == null) {
                                this.remove(WidgetConfigKeys.KEY_TIMELINE_MIN_HOUR)
                            } else {
                                this[WidgetConfigKeys.KEY_TIMELINE_MIN_HOUR] = minHour
                            }
                            if (maxHour == null) {
                                this.remove(WidgetConfigKeys.KEY_TIMELINE_MAX_HOUR)
                            } else {
                                this[WidgetConfigKeys.KEY_TIMELINE_MAX_HOUR] = maxHour
                            }
                        }
                        // Keep a live session composition in sync; otherwise the
                        // widget would ignore the new settings until the session died.
                        WidgetLiveStateHub.publish(appWidgetId, WidgetLiveState.from(updated))
                        updated
                    }

                    // One update pass over every placed instance. Per-glanceId
                    // update() calls would throw for the three widget types that
                    // don't own this glanceId, and updateAll already covers them.
                    AgendaGlanceWidget().updateAll(this@WidgetConfigActivity)
                    NextUpGlanceWidget().updateAll(this@WidgetConfigActivity)
                    WeeklyCalendarGlanceWidget().updateAll(this@WidgetConfigActivity)
                    ThreeDayTimelineGlanceWidget().updateAll(this@WidgetConfigActivity)
                }

                finishWithResult(RESULT_OK)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@WidgetConfigActivity,
                        "Failed to save widget settings",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                // Widget keeps its previous state; still return OK so an initial
                // placement isn't discarded by the launcher.
                finishWithResult(RESULT_OK)
            }
        }
    }

    private suspend fun finishWithResult(resultCode: Int) {
        withContext(Dispatchers.Main) {
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(resultCode, resultValue)
            finish()
        }
    }
}

@Composable
private fun WidgetConfigContent(
    modifier: Modifier = Modifier,
    onSave: (Set<Long>, Float, Int, String, Int, Int?, Int?, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { CalendarRepository(context) }
    var calendars by remember { mutableStateOf<List<CalendarAccount>>(emptyList()) }
    var selectedCalendarIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var opacity by remember { mutableFloatStateOf(WidgetConfigKeys.DEFAULT_OPACITY) }
    var lookaheadDays by remember { mutableIntStateOf(WidgetConfigKeys.DEFAULT_LOOKAHEAD_DAYS) }
    var fontScale by remember { mutableStateOf(WidgetConfigKeys.FONT_SCALE_NORMAL) }
    var chevronStepDays by remember {
        mutableIntStateOf(WidgetConfigKeys.DEFAULT_CHEVRON_STEP_DAYS)
    }
    var timelineMinHour by remember { mutableStateOf<Int?>(null) }
    var timelineMaxHour by remember { mutableStateOf<Int?>(null) }
    var weekStart by remember {
        mutableStateOf(WidgetConfigKeys.normalizedWeekStart(null))
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            permanentlyDenied = false
            val list = repository.getCalendars()
            calendars = list
            selectedCalendarIds = list.map { it.id }.toSet()
        } else {
            // When the system no longer shows a rationale, the permission was
            // permanently denied; the only recovery path is system settings.
            permanentlyDenied = (context as? Activity)?.shouldShowRequestPermissionRationale(
                Manifest.permission.READ_CALENDAR
            ) != true
        }
    }

    // Re-check when returning from the system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
                hasPermission = grantedNow
                if (grantedNow) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                            if (permanentlyDenied) {
                                Text(
                                    text = "Calendar access is turned off for this app. Re-enable it in system settings, then return here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { context.startActivity(openAppSettings(context)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open App Settings")
                                }
                            } else {
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Calendar Permission")
                                }
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

                        // One app-wide theme drives all four widget views. The
                        // old per-widget Dynamic/Dark/Light mode is gone.
                        val selection by ThemePreferences.selection.collectAsState()
                        LaunchedEffect(Unit) { ThemePreferences.ensureLoaded(context) }
                        val themeLabel = if (CalendarThemes.isAuto(selection.themeId)) {
                            "Auto"
                        } else {
                            CalendarThemes.byId(selection.themeId)?.label ?: "Auto"
                        }
                        Text(
                            text = "Widget Theme",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Current: $themeLabel (applies to all widgets)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { context.startActivity(Intent(context, ThemeActivity::class.java)) }
                            ) {
                                Text("Choose theme")
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

            // 3-Day Timeline navigation step
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Timeline Navigation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "How far the chevron buttons move the 3-Day Timeline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chevron step",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                WidgetConfigKeys.CHEVRON_STEP_ONE_DAY to "1 day",
                                WidgetConfigKeys.DEFAULT_CHEVRON_STEP_DAYS to "3 days"
                            ).forEach { (step, label) ->
                                FilterChip(
                                    selected = chevronStepDays == step,
                                    onClick = { chevronStepDays = step },
                                    label = { Text(label) },
                                    leadingIcon = if (chevronStepDays == step) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // Optional timeline hour bounds
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Timeline Hours",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Optionally limit the hours shown on the 3-Day Timeline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Earliest hour", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf<Int?>(null, 8, 10, 12).forEach { hour ->
                                FilterChip(
                                    selected = timelineMinHour == hour,
                                    onClick = { timelineMinHour = hour },
                                    label = { Text(if (hour == null) "Any" else "$hour:00") },
                                    leadingIcon = if (timelineMinHour == hour) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Latest hour", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf<Int?>(null, 18, 20, 22).forEach { hour ->
                                FilterChip(
                                    selected = timelineMaxHour == hour,
                                    onClick = { timelineMaxHour = hour },
                                    label = { Text(if (hour == null) "Any" else "$hour:00") },
                                    leadingIcon = if (timelineMaxHour == hour) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // 7-Day widget week start
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Week Starts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "First day of the 7-day widget strip. Today rolls the window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                WidgetConfigKeys.WEEK_START_SATURDAY to "Sat",
                                WidgetConfigKeys.WEEK_START_SUNDAY to "Sun",
                                WidgetConfigKeys.WEEK_START_MONDAY to "Mon",
                                WidgetConfigKeys.WEEK_START_TODAY to "Today"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = weekStart == mode,
                                    onClick = { weekStart = mode },
                                    label = { Text(label) },
                                    leadingIcon = if (weekStart == mode) {
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
                onSave(
                    selectedCalendarIds,
                    opacity,
                    lookaheadDays,
                    fontScale,
                    chevronStepDays,
                    timelineMinHour,
                    timelineMaxHour,
                    weekStart
                )
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
