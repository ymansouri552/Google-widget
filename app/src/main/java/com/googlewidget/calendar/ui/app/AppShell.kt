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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.googlewidget.calendar.data.CalendarEvent
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.data.CalendarResult
import com.googlewidget.calendar.data.TaskItem
import com.googlewidget.calendar.data.TaskRepository
import com.googlewidget.calendar.sync.CalendarSyncWorker
import com.googlewidget.calendar.ui.ThemeActivity
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import com.googlewidget.calendar.widget.WeeklyCalendarGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class AppTab(val label: String) { TODAY("Today"), CALENDAR("Calendar"), TASKS("Tasks"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val calendarRepo = remember { CalendarRepository(context) }
    val taskRepo = remember { TaskRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val tasks by taskRepo.tasks.collectAsState(initial = emptyList())
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var calendars by remember { mutableStateOf<List<com.googlewidget.calendar.data.CalendarAccount>>(emptyList()) }
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
                val now = System.currentTimeMillis()
                val start = now - 7L * 24 * 60 * 60 * 1000L
                val end = now + 30L * 24 * 60 * 60 * 1000L
                val result = calendarRepo.getUpcomingEvents(start, end)
                withContext(Dispatchers.Main) {
                    calendars = calList
                    when (result) {
                        is CalendarResult.Success -> events = result.data
                        is CalendarResult.Error -> errorMessage = result.throwable.localizedMessage ?: "Could not load events"
                        CalendarResult.PermissionRequired -> hasPermission = false
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Refresh failed"
                    isLoading = false
                }
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

    val currentTab = AppTab.entries[tab]
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(currentTab.label) },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, ThemeActivity::class.java))
                    }) {
                        Icon(Icons.Default.Palette, contentDescription = "App and widget theme")
                    }
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                when (item) {
                                    AppTab.TODAY -> Icons.Default.Today
                                    AppTab.CALENDAR -> Icons.Default.CalendarMonth
                                    AppTab.TASKS -> Icons.Default.Checklist
                                    AppTab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == AppTab.TODAY.ordinal || tab == AppTab.TASKS.ordinal) {
                FloatingActionButton(onClick = { showTaskEditor = TaskItem(title = "") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            AppTab.TODAY.ordinal -> TodayScreen(
                modifier = contentModifier,
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
                onEditTask = { showTaskEditor = it }
            )
            AppTab.CALENDAR.ordinal -> CalendarScreen(
                modifier = contentModifier,
                events = events,
                today = LocalDate.now(),
                isLoading = isLoading
            )
            AppTab.TASKS.ordinal -> TasksScreen(
                modifier = contentModifier,
                tasks = tasks,
                today = LocalDate.now(),
                onToggle = { scope.launch { taskRepo.toggle(it) } },
                onDelete = { scope.launch { taskRepo.delete(it) } },
                onEdit = { showTaskEditor = it }
            )
            AppTab.SETTINGS.ordinal -> SettingsScreen(
                modifier = contentModifier,
                calendars = calendars,
                hasPermission = hasPermission,
                onTheme = { context.startActivity(Intent(context, ThemeActivity::class.java)) },
                onGrant = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                onSettings = { context.startActivity(openAppSettings(context)) }
            )
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
