package com.googlewidget.calendar

import android.app.Application
import com.googlewidget.calendar.sync.CalendarSyncWorker

class GoogleWidgetApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Register ContentUri trigger observer via WorkManager
        CalendarSyncWorker.enqueue(this)
    }
}
