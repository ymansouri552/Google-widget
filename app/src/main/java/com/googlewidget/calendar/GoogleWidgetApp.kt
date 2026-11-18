package com.googlewidget.calendar

import android.app.Application
import android.content.res.Configuration
import com.googlewidget.calendar.data.ThemePreferences
import com.googlewidget.calendar.sync.CalendarSyncWorker
import com.googlewidget.calendar.ui.theme.CalendarThemes
import com.googlewidget.calendar.widget.WidgetRefresher

class GoogleWidgetApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemePreferences.ensureLoaded(this)
        CalendarSyncWorker.enqueue(this)
    }

    /**
     * Auto follows the system light/dark mode. Application receives this even
     * without a visible activity, so placed widgets refresh with the new theme.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ThemePreferences.getSelectedId(this) == CalendarThemes.AUTO) {
            ThemePreferences.notifySystemThemeChanged(this)
            WidgetRefresher.refreshAll(this)
        }
    }
}
