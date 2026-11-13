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
import com.googlewidget.calendar.data.CalendarRepository
import com.googlewidget.calendar.widget.AgendaGlanceWidget
import com.googlewidget.calendar.widget.NextUpGlanceWidget
import com.googlewidget.calendar.widget.WeeklyCalendarGlanceWidget
import com.googlewidget.calendar.widget.ThreeDayTimelineGlanceWidget
import com.googlewidget.calendar.widget.WidgetPlacement
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            CalendarRepository.clearCache()
            // Re-render every placed widget instance; skipped entirely when no
            // widget is on screen so calendar churn is cheap for everyone else.
            if (WidgetPlacement.hasAnyWidget(context)) {
                AgendaGlanceWidget().updateAll(context)
                NextUpGlanceWidget().updateAll(context)
                WeeklyCalendarGlanceWidget().updateAll(context)
                ThreeDayTimelineGlanceWidget().updateAll(context)
            }

            // Re-arm the observation chain for the next content change.
            reEnqueueObservation(context)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            reEnqueueObservation(context)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "calendar_content_observer_work"

        private fun buildConstraints() = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(2, TimeUnit.SECONDS)
            .build()

        private fun buildRequest() =
            OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(buildConstraints())
                .build()

        /**
         * Entry point for external triggers (app start, boot, manual refresh).
         * REPLACE is safe here: replaces any pending chain with a fresh one.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                buildRequest()
            )
        }

        /**
         * Re-enqueue from inside [doWork]. Must use APPEND instead of REPLACE:
         * REPLACE cancels the still-running instance it replaces, which can
         * abort the observation chain and leave the widgets updating only on
         * the 30-minute system tick.
         */
        private fun reEnqueueObservation(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND,
                buildRequest()
            )
        }
    }
}
