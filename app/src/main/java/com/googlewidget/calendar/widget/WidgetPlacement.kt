package com.googlewidget.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Cheap checks for whether widgets are actually placed on the home screen, so
 * broadcast and background-work paths can skip re-rendering when nothing is on
 * screen (and the reverse: keep the observation chain armed so the first widget
 * placed starts updating immediately).
 */
object WidgetPlacement {

    private val allWidgetReceivers = listOf(
        AgendaGlanceWidgetReceiver::class,
        NextUpGlanceWidgetReceiver::class,
        WeeklyCalendarWidgetReceiver::class,
        ThreeDayTimelineWidgetReceiver::class
    )

    fun isPlaced(context: Context, receiverClass: Class<*>): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return manager
            .getAppWidgetIds(ComponentName(context, receiverClass))
            .any { it != AppWidgetManager.INVALID_APPWIDGET_ID }
    }

    fun hasAnyWidget(context: Context): Boolean =
        allWidgetReceivers.any { isPlaced(context, it.java) }
}
