package com.googlewidget.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
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
import com.googlewidget.calendar.ui.theme.CalendarTheme

@Composable
fun WidgetTopBar(
    context: Context,
    appWidgetId: Int,
    monthTitle: String,
    todayDayOfMonth: Int,
    onPrevClick: Action,
    onNextClick: Action,
    theme: CalendarTheme,
    modifier: GlanceModifier = GlanceModifier
) {
    val iconColor = theme.text
    val barBackgroundColor = WidgetThemeHelper.getCardBackgroundColor(theme, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Every visible button sits inside a 48dp touch target.
        Box(
            modifier = GlanceModifier.size(48.dp).clickable(actionRunCallback<ResetDateOffsetAction>()),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier.size(36.dp).cornerRadius(6.dp).background(barBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(WidgetThemeHelper.getAccentColor(theme))
                    )
                    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        }

        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier.size(48.dp).clickable(onPrevClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier.size(32.dp).cornerRadius(8.dp).background(barBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_chevron_left),
                        contentDescription = "Previous",
                        modifier = GlanceModifier.size(18.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(iconColor))
                    )
                }
            }

            Text(
                text = monthTitle,
                style = TextStyle(
                    color = WidgetThemeHelper.getPrimaryTextColor(theme),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                maxLines = 1
            )

            Box(
                modifier = GlanceModifier.size(48.dp).clickable(onNextClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier.size(32.dp).cornerRadius(8.dp).background(barBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_chevron_right),
                        contentDescription = "Next",
                        modifier = GlanceModifier.size(18.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(iconColor))
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val addEventIntent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            Box(
                modifier = GlanceModifier.size(48.dp).clickable(actionStartActivity(addEventIntent)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp).background(barBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_add),
                        contentDescription = "Add Event",
                        modifier = GlanceModifier.size(16.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(iconColor))
                    )
                }
            }

            val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            Box(
                modifier = GlanceModifier.size(48.dp).clickable(actionStartActivity(configIntent)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier.size(36.dp).cornerRadius(18.dp).background(barBackgroundColor),
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
}
