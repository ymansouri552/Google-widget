package com.googlewidget.calendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.taskDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_tasks")

/** Local to-do storage. Calendar events stay in the system provider repository. */
class TaskRepository(private val context: Context) {

    private val key = stringSetPreferencesKey("tasks_v1")

    val tasks: Flow<List<TaskItem>> = context.taskDataStore.data.map { prefs ->
        (prefs[key] ?: emptySet()).mapNotNull(TaskCodec::decode)
    }

    suspend fun add(title: String, note: String = "", dueDate: String? = null) {
        if (title.isBlank()) return
        val task = TaskItem(title = title.trim(), note = note.trim(), dueDate = dueDate)
        update { it + task }
    }

    suspend fun toggle(id: String) {
        update { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } }
    }

    suspend fun update(id: String, title: String, note: String, dueDate: String?) {
        if (title.isBlank()) return
        update { list ->
            list.map {
                if (it.id == id) it.copy(title = title.trim(), note = note.trim(), dueDate = dueDate) else it
            }
        }
    }

    suspend fun delete(id: String) {
        update { list -> list.filterNot { it.id == id } }
    }

    private suspend fun update(transform: (List<TaskItem>) -> List<TaskItem>) {
        context.taskDataStore.edit { prefs ->
            val current = (prefs[key] ?: emptySet()).mapNotNull(TaskCodec::decode)
            prefs[key] = transform(current).map(TaskCodec::encode).toSet()
        }
    }
}
