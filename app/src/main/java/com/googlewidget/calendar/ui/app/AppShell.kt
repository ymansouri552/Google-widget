package com.googlewidget.calendar.ui.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.googlewidget.calendar.data.CalendarAccount
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.CalendarResult
import com.googlewidget.calendar.data.CalendarVisibilityRepository
import com.googlewidget.calendar.data.TaskItem
import com.googlewidget.calendar.data.TaskRepository
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.sync.CalendarSyncWorker
import com.googlewidget.calendar.ui.ThemeActivity
import com.googlewidget.calendar.ui.theme.LocalCalendarTheme
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import com.googlewidget.calendar.widget.WeeklyCalendarGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class AppTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Today),
    CALENDAR("Calendar", Icons.Default.CalendarMonth),
    NOTES("Notes", Icons.Default.Description),
    PROFILE("Profile", Icons.Default.Person)
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val theme = LocalCalendarTheme.current
    val calendarRepo = remember { CalendarRepository(context) }
    val taskRepo = remember { TaskRepository(context) }
    val visibilityRepo = remember { CalendarVisibilityRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val tasks by taskRepo.tasks.collectAsState(initial = emptyList())
    val disabledCalendarIds by visibilityRepo.disabledIds.collectAsState(initial = emptySet())
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var calendars by remember { mutableStateOf<List<CalendarAccount>>(emptyList()) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var showTaskEditor by remember { mutableStateOf<TaskItem?>(null) }

    val enabledIds = calendars.map { it.id }.toSet() - disabledCalendarIds
    val queryCalendarIds = if (calendars.isNotEmpty() && enabledIds.isEmpty()) {
        setOf(WidgetConfigKeys.NO_CALENDAR_PLACEHOLDER.toLong())
    } else {
        enabledIds
    }

    suspend fun fetchEvents(selectedIds: Set<Long>) {
        val now = System.currentTimeMillis()
        val result = calendarRepo.getUpcomingEvents(
            now - 7L * 24 * 60 * 60 * 1000L,
            now + 30L * 24 * 60 * 60 * 1000L,
            selectedIds
        )
        withContext(Dispatchers.Main) {
            when (result) {
                is CalendarResult.Success -> events = result.data
                is CalendarResult.Error -> errorMessage =
                    result.throwable.localizedMessage ?: "Could not load events"
                CalendarResult.PermissionRequired -> hasPermission = false
            }
        }
    }

    fun reloadEvents() {
        if (calendars.isEmpty()) return
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                fetchEvents(queryCalendarIds)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Refresh failed"
                }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun refresh() {
        isLoading = true
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                AgendaGlanceWidget().updateAll(context)
                NextUpGlanceWidget().updateAll(context)
                WeeklyCalendarGlanceWidget().updateAll(context)
                ThreeDayTimelineGlanceWidget().updateAll(context)
                CalendarSyncWorker.enqueue(context)

                val calList = calendarRepo.getCalendars()
                withContext(Dispatchers.Main) { calendars = calList }
                fetchEvents(queryCalendarIds)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Refresh failed"
                }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            permanentlyDenied = false
            refresh()
        } else {
            permanentlyDenied = (context as? Activity)?.shouldShowRequestPermissionRationale(
                Manifest.permission.READ_CALENDAR
            ) != true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
                if (grantedNow != hasPermission) {
                    hasPermission = grantedNow
                    if (grantedNow) {
                        permanentlyDenied = false
                        refresh()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        else refresh()
    }

    LaunchedEffect(disabledCalendarIds) { reloadEvents() }

    Scaffold(
        containerColor = theme.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NeonBottomBar(
                theme = theme,
                tabs = AppTab.entries.map { NeonTab(it.label, it.icon) },
                selected = tab,
                onTab = { tab = it },
                onAdd = { showTaskEditor = TaskItem(title = "") }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).neonAppBackground(theme)) {
            when (tab) {
                AppTab.TODAY.ordinal -> TodayScreen(
                    modifier = Modifier,
                    events = events,
                    tasks = tasks,
                    today = LocalDate.now(),
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    hasPermission = hasPermission,
                    permanentlyDenied = permanentlyDenied,
                    onGrant = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                    onSettings = { context.startActivity(openAppSettings(context)) },
                    onToggleTask = { scope.launch { taskRepo.toggle(it) } },
                    onDeleteTask = { scope.launch { taskRepo.delete(it) } },
                    onEditTask = { showTaskEditor = it },
                    onSeeAll = { tab = AppTab.NOTES.ordinal },
                    onRefresh = { refresh() },
                    onTheme = { context.startActivity(Intent(context, ThemeActivity::class.java)) }
                )
                AppTab.CALENDAR.ordinal -> CalendarScreen(
                    modifier = Modifier,
                    events = events,
                    today = LocalDate.now(),
                    isLoading = isLoading
                )
                AppTab.NOTES.ordinal -> TasksScreen(
                    modifier = Modifier,
                    tasks = tasks,
                    today = LocalDate.now(),
                    onToggle = { scope.launch { taskRepo.toggle(it) } },
                    onDelete = { scope.launch { taskRepo.delete(it) } },
                    onEdit = { showTaskEditor = it }
                )
                AppTab.PROFILE.ordinal -> SettingsScreen(
                    modifier = Modifier,
                    calendars = calendars,
                    disabledCalendarIds = disabledCalendarIds,
                    hasPermission = hasPermission,
                    onTheme = { context.startActivity(Intent(context, ThemeActivity::class.java)) },
                    onGrant = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                    onSettings = { context.startActivity(openAppSettings(context)) },
                    onToggleCalendar = { scope.launch { visibilityRepo.toggle(it) } },
                    onShowAllCalendars = { scope.launch { visibilityRepo.showAll() } }
                )
            }
        }
    }

    showTaskEditor?.let { task ->
        TaskEditorDialog(
            task = task,
            onDismiss = { showTaskEditor = null },
            onSave = { title, note, due ->
                scope.launch {
                    if (task.title.isEmpty()) taskRepo.add(title, note, due)
                    else taskRepo.update(task.id, title, note, due)
                    showTaskEditor = null
                }
            }
        )
    }
}

internal fun openAppSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
