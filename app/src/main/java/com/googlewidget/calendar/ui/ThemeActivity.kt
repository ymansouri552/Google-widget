package com.googlewidget.calendar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.googlewidget.calendar.data.ThemePreferences
import com.googlewidget.calendar.ui.theme.CalendarTheme
import com.googlewidget.calendar.ui.theme.CalendarThemes
import com.googlewidget.calendar.ui.theme.GoogleWidgetTheme
import com.googlewidget.calendar.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Teal wallpaper behind previews so Glass transparency is visible. */
private val PreviewWallpaper = Color(0xFF4F8F94)

private data class ThemeOption(val id: String, val label: String, val blurb: String)

private val themeOptions = listOf(
    ThemeOption(CalendarThemes.SOFT_PASTEL, "Soft pastel", "Warm and light"),
    ThemeOption(CalendarThemes.MIDNIGHT, "Midnight", "Dark surface for low light"),
    ThemeOption(CalendarThemes.NEON_PURPLE, "Neon Purple", "Violet accent on near-black"),
    ThemeOption(CalendarThemes.GLASS, "Glass", "Translucent, wallpaper-aware"),
    ThemeOption(CalendarThemes.AUTO, "Auto", "Glass by day, Midnight at night")
)

private data class PreviewEvent(
    val title: String,
    val time: String,
    val startMin: Int,
    val endMin: Int
)

private data class PreviewDay(
    val name: String,
    val date: String,
    val events: List<PreviewEvent>
)

/** The preview grid spans 15:00 to 20:00, matching the sample event times. */
private const val PREVIEW_START_HOUR = 15
private const val PREVIEW_HOURS = 5

private val previewDays = listOf(
    PreviewDay(
        name = "Thu",
        date = "12",
        events = listOf(
            PreviewEvent("Teen 4", "15:00 - 16:30", 0, 90),
            PreviewEvent("Teen 5", "16:45 - 18:15", 105, 195)
        )
    ),
    PreviewDay("Fri", "13", listOf(PreviewEvent("301", "16:45 - 18:15", 105, 195))),
    PreviewDay("Sat", "14", listOf(PreviewEvent("103", "18:30 - 20:00", 210, 300)))
)

class ThemeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoogleWidgetTheme {
                ThemeScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selection by ThemePreferences.selection.collectAsState()
    val systemDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) { ThemePreferences.ensureLoaded(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget theme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Choose how your calendar widgets look. One choice is saved for the whole app, and every placed widget (Timeline, Weekly, Agenda and Next Up) updates immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(themeOptions, key = { it.id }) { option ->
                val selected = selection.themeId == option.id
                ThemeOptionCard(
                    option = option,
                    theme = CalendarThemes.resolve(option.id, systemDark),
                    selected = selected,
                    onSelect = {
                        ThemePreferences.setSelectedId(context, option.id)
                        scope.launch(Dispatchers.IO) { WidgetRefresher.suspendRefreshAll(context) }
                    }
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    option: ThemeOption,
    theme: CalendarTheme,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    Modifier
                }
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = option.blurb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            WidgetThemePreview(theme)
        }
    }
}

/** Mini version of the widget, drawn entirely with the theme's own tokens. */
@Composable
private fun WidgetThemePreview(theme: CalendarTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PreviewWallpaper)
            .padding(7.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.bg)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thu 12 Nov",
                    color = theme.text,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.chipBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Next: Teen 5 in 4h 34m",
                        color = theme.chipText,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                previewDays.forEachIndexed { index, day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.name,
                            color = theme.sub,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (index == 0) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(theme.todayCircleBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.date,
                                    color = theme.todayCircleText,
                                    fontSize = 11.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.size(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.date,
                                    color = theme.text,
                                    fontSize = 11.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            PreviewGrid(
                theme = theme,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/** Hour grid with sample events positioned by their real start and duration. */
@Composable
private fun PreviewGrid(theme: CalendarTheme, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val dpPerMinute = maxHeight / (PREVIEW_HOURS * 60)
        val hourHeight = dpPerMinute * 60
        val gutterWidth = 22.dp
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(gutterWidth).fillMaxHeight()) {
                repeat(PREVIEW_HOURS) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hourHeight),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = (PREVIEW_START_HOUR + index).toString(),
                            modifier = Modifier.padding(end = 4.dp),
                            color = theme.sub,
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            previewDays.forEachIndexed { index, day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .then(
                            if (index == 0) {
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.todayCol)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dash = 3.dp.toPx()
                        val gap = 4.dp.toPx()
                        for (hour in 1 until PREVIEW_HOURS) {
                            val y = hourHeight.toPx() * hour
                            drawLine(
                                color = theme.grid,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))
                            )
                        }
                    }
                    day.events.forEach { event ->
                        PreviewEventCard(
                            theme = theme,
                            event = event,
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = dpPerMinute * event.startMin)
                                .height(dpPerMinute * (event.endMin - event.startMin))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewEventCard(
    theme: CalendarTheme,
    event: PreviewEvent,
    modifier: Modifier = Modifier
) {
    val family = theme.eventColors(event.title)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(family.fill)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 5.dp)
                .background(family.bar, RoundedCornerShape(2.dp))
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 4.dp, top = 4.dp, end = 2.dp)
        ) {
            Text(
                text = event.title,
                color = family.title,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = event.time,
                color = family.time,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
