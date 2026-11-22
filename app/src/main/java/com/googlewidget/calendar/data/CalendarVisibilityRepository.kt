package com.googlewidget.calendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calendarVisibilityDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "app_calendar_visibility")

/**
 * App-wide calendar visibility. Storing disabled ids keeps the default at
 * "show everything", which matches a fresh install and the widget defaults.
 * This is separate from each widget's own selected-calendar setting.
 */
class CalendarVisibilityRepository(private val context: Context) {

    private val key = stringSetPreferencesKey("disabled_calendar_ids_v1")

    val disabledIds: Flow<Set<Long>> = context.calendarVisibilityDataStore.data.map { prefs ->
        (prefs[key] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun toggle(id: Long) {
        context.calendarVisibilityDataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toMutableSet()
            if (!current.add(id)) current.remove(id)
            prefs[key] = current.map { it.toString() }.toSet()
        }
    }

    suspend fun showAll() {
        context.calendarVisibilityDataStore.edit { prefs -> prefs.remove(key) }
    }
}
