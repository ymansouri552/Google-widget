package com.googlewidget.calendar.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AgendaGlanceWidget().updateAll(context)
        NextUpGlanceWidget().updateAll(context)
        WeeklyCalendarGlanceWidget().updateAll(context)
        ThreeDayTimelineGlanceWidget().updateAll(context)
    }
}
