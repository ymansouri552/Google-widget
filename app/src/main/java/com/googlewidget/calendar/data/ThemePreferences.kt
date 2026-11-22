package com.googlewidget.calendar.data

import android.content.Context
import com.googlewidget.calendar.ui.theme.CalendarThemes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [revision] bumps even when the id stays the same, so a widget composition
 * listening to this flow recomposes after a system light/dark change too.
 */
data class ThemeSelection(val themeId: String, val revision: Int = 0)

/**
 * App-wide widget theme choice. Kept in SharedPreferences because it applies
 * to every placed widget, unlike the per-widget Glance state.
 */
object ThemePreferences {

    private const val PREFS_NAME = "calendar_widget_theme"
    private const val KEY_THEME_ID = "selected_theme_id"

    // Neon Purple is the default so first launch opens on the companion app concept.
    private val _selection = MutableStateFlow(ThemeSelection(CalendarThemes.NEON_PURPLE))
    val selection: StateFlow<ThemeSelection> = _selection.asStateFlow()

    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        val saved = prefs(context).getString(KEY_THEME_ID, null)
        val id = if (CalendarThemes.byId(saved) != null) saved!! else CalendarThemes.NEON_PURPLE
        _selection.value = ThemeSelection(id, _selection.value.revision)
        loaded = true
    }

    fun getSelectedId(context: Context): String {
        ensureLoaded(context)
        return _selection.value.themeId
    }

    fun setSelectedId(context: Context, themeId: String) {
        ensureLoaded(context)
        val normalized = if (CalendarThemes.byId(themeId) != null) themeId else CalendarThemes.NEON_PURPLE
        prefs(context).edit().putString(KEY_THEME_ID, normalized).apply()
        _selection.value = ThemeSelection(normalized, _selection.value.revision + 1)
    }

    /** Forces listeners to re-resolve Auto after the system uiMode changed. */
    fun notifySystemThemeChanged(context: Context) {
        ensureLoaded(context)
        _selection.value = _selection.value.copy(revision = _selection.value.revision + 1)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
