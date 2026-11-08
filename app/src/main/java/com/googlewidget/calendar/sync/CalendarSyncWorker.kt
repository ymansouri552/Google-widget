package com.googlewidget.calendar.sync

import android.content.Context
import android.provider.CalendarContract
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.WeeklyCalendarGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Trigger instant update on all 4 widgets
            AgendaGlanceWidget().updateAll(context)
            NextUpGlanceWidget().updateAll(context)
            WeeklyCalendarGlanceWidget().updateAll(context)
            ThreeDayTimelineGlanceWidget().updateAll(context)

            // Re-enqueue observation trigger to capture future edits
            enqueue(context)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            enqueue(context)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "calendar_content_observer_work"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                .setTriggerContentUpdateDelay(2, TimeUnit.SECONDS)
                .build()

            val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
