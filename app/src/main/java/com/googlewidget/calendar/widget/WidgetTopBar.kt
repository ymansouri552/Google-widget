package com.googlewidget.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.googlewidget.calendar.R
import com.googlewidget.calendar.data.WidgetConfigKeys
import com.googlewidget.calendar.ui.WidgetConfigActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "March" / "Mar – Apr" / "Mar 2025 – Apr 2026" header for a day range. */
internal fun monthTitleFor(first: LocalDate, last: LocalDate): String {
    return if (first.month == last.month && first.year == last.year) {
        first.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
    } else if (first.year == last.year) {
        "${first.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))} – ${last.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))}"
    } else {
        "${first.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))} – ${last.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))}"
    }
}

@Composable
fun WidgetTopBar(
    context: Context,
    appWidgetId: Int,
    monthTitle: String,
    todayDayOfMonth: Int,
    onPrevClick: Action,
    onNextClick: Action,
    themeMode: String,
    modifier: GlanceModifier = GlanceModifier
) {
    // Concrete icon tint adapted to the active theme mode and system night state.
    val isDark = WidgetThemeHelper.isDarkTheme(context, themeMode)
    val iconColor = if (isDark) Color(0xFFE8EAED) else Color(0xFF202124)
    val barBackgroundColor = WidgetThemeHelper.getCardBackgroundColor(context, themeMode, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Calendar/Today icon button displaying current day number
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .cornerRadius(6.dp)
                .background(barBackgroundColor)
                .clickable(actionRunCallback<ResetDateOffsetAction>()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Red calendar roof strip
                Spacer(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(0xFFEA4335))
                )
                // Day number inside calendar box
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = todayDayOfMonth.toString(),
                        style = TextStyle(
                            color = ColorProvider(iconColor),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // Center: < Month >
        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // < button (enlarged touch target with visible card background for reliable thumb taps)
            Box(
                modifier = GlanceModifier
                    .size(width = 42.dp, height = 36.dp)
                    .cornerRadius(8.dp)
                    .background(barBackgroundColor)
                    .clickable(onPrevClick),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_chevron_left),
                    contentDescription = "Previous",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(iconColor))
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Month title
            Text(
                text = monthTitle,
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(themeMode),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            // > button (enlarged touch target with visible card background for reliable thumb taps)
            Box(
                modifier = GlanceModifier
                    .size(width = 42.dp, height = 36.dp)
                    .cornerRadius(8.dp)
                    .background(barBackgroundColor)
                    .clickable(onNextClick),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_chevron_right),
                    contentDescription = "Next",
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(iconColor))
                )
            }
        }

        // Right: + and ⋮ (settings)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // + button
            val addEventIntent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .cornerRadius(14.dp)
                    .background(barBackgroundColor)
                    .clickable(actionStartActivity(addEventIntent)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_add),
                    contentDescription = "Add Event",
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(iconColor))
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // ⋮ button
            val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .cornerRadius(14.dp)
                    .background(barBackgroundColor)
                    .clickable(actionStartActivity(configIntent)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_more_vert),
                    contentDescription = "Settings",
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(iconColor))
                )
            }
        }
    }
}
