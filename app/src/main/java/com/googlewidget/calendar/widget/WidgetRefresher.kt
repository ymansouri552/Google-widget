package com.googlewidget.calendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** One place that re-renders every placed widget (theme changes, uiMode changes). */
object WidgetRefresher {

    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { suspendRefreshAll(appContext) }
    }

    suspend fun suspendRefreshAll(context: Context) {
        if (!WidgetPlacement.hasAnyWidget(context)) return
        AgendaGlanceWidget().updateAll(context)
        NextUpGlanceWidget().updateAll(context)
        WeeklyCalendarGlanceWidget().updateAll(context)
        ThreeDayTimelineGlanceWidget().updateAll(context)
    }
}
