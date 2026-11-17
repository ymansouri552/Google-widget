# Google Calendar Glance Widgets for Android

A production-ready native Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Jetpack Glance 1.2.0**. It seamlessly reads the user's synced Google Calendar schedule via Android's local `CalendarContract.Instances` provider and offers two home screen widgets.

---

## Features

### 1. Agenda Widget (`AgendaGlanceWidget`) — 4x3 Scrollable Schedule
* **Date Header & Quick Actions:** Live day/date header with a **"+"** quick-add button launching Google Calendar's event creation activity and an instant refresh button.
* **14-Day Lookahead:** High-performance `LazyColumn` grouping events under **"Today"**, **"Tomorrow"**, and **"EEEE, MMM d"**.
* **Rich Event Cards:** Displays a colored vertical bar matching the calendar/display color, event title, formatted time or "All Day" badge, and location info.
* **Direct Deep Linking:** Tapping an event opens the native calendar event viewer.
* **Permission Fallback:** In-widget prompt to open the companion app when `READ_CALENDAR` permission is needed.

### 2. Next Up Widget (`NextUpGlanceWidget`) — 4x1 Compact Summary Strip
* **Glanceable Timeline:** Designed for minimalist home screens, featuring the immediate upcoming event.
* **Dynamic Countdown:** Displays real-time status such as *"Starts in 25 min"*, *"Happening now (ends in 40m)"*, or *"Tomorrow at 2:00 PM"*.
* **Day Context:** Secondary status indicator (e.g., *"3 other events today"* or *"No more events today"*).
* **Direct Interaction:** Tap to view details or quick-refresh.

### 3. Widget Customization Activity (`WidgetConfigActivity`)
* **Per-Widget Preferences:** Configured independently for each placed widget using `PreferencesGlanceStateDefinition`.
* **Multi-Select Calendar Filter:** Choose which device calendars to include or exclude.
* **Theme Modes:** System Dynamic (Day/Night Material 3), Dark, or Light mode.
* **Background Opacity:** Adjustable slider from 0% (fully transparent) to 100% (solid).
* **Lookahead Range & Typography:** Choose 7, 14, or 30 days lookahead and Normal vs. Compact font scaling.

### 3b. Widget Themes (`ThemeActivity`)
* **Four choices:** Soft pastel, Midnight, Glass, and Auto (Glass in light mode, Midnight in dark mode).
* **Live previews:** Every card renders the sample events (Teen 5, Teen 4, 301, 103) with that theme's colors, and Glass previews sit on a teal wallpaper so the translucency is visible.
* **One-tap apply:** The choice is saved app-wide (SharedPreferences) and every placed widget is refreshed immediately; Auto also refreshes on system light/dark changes.

### 3c. 3-Day Timeline Widget (`ThreeDayTimelineGlanceWidget`)
* **Minute-accurate timeline:** Every event is one continuous rounded box positioned by its start minute; an event that crosses an hour boundary is never split into per-hour rows.
* **Auto-fit hours:** The visible range spans the shown days' earliest start to latest end, rounded to whole hours with a 4-hour minimum.
* **Hour grid:** Hour labels on the left, faint dotted hour lines across the columns, and a tinted today column.
* **Overlap lanes:** Concurrent events split the day column side by side.
* **Theme tokens:** Every color comes from a `CalendarTheme` object, including event families; "Teen…" titles always use the teen family and other titles get a stable A/B color from a title hash.


### 4. Background Sync & Content Observers
* **WorkManager ContentUri Trigger (`CalendarSyncWorker`):** Observes `CalendarContract.CONTENT_URI` changes with a 2-second debounce, triggering immediate widget updates when events are added, edited, or deleted in Google Calendar.
* **System Event Receiver (`CalendarBroadcastReceiver`):** Automatically refreshes widgets upon `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`, `BOOT_COMPLETED`, and `PROVIDER_CHANGED`.

### 5. Companion App (`MainActivity`)
* Clean Material 3 interface requesting `Manifest.permission.READ_CALENDAR`.
* Instant sync trigger upon permission grant.
* Detected calendar listing with color badges and account details.
* Live 7-day upcoming event preview.
* Step-by-step home screen placement guide.

---

## Technical Architecture & Guardrails

* **Instances Provider (Not Events):** Queries `CalendarContract.Instances` using `ContentUris.appendId` with time ranges to correctly capture recurring events.
* **All-Day Event Timezone Integrity:** Correctly handles `ALL_DAY == 1` events (stored in UTC midnight) with `ZoneOffset.UTC`, while timed events use `ZoneId.systemDefault()`, preventing date shift anomalies across timezones.
* **Fast `provideGlance` IO Preloading:** Reads DataStore preferences and queries calendar data on `Dispatchers.IO` *prior* to composing UI within `provideContent`.
* **Strict Glance Scoping:** Widget UI files exclusively utilize Glance layouts, modifiers, and text components without conflicting standard Compose Foundation or Compose Material 3 elements.

---

## Adding a new theme

All widget colors live in `app/src/main/java/com/googlewidget/calendar/ui/theme/CalendarThemes.kt`:

1. Copy one of the existing `CalendarTheme(...)` blocks and give it a new `id` and `label`.
2. Fill in the tokens: `bg`, `text`, `sub`, `grid`, `todayCol`, `todayCircleBg` / `todayCircleText`, `chipBg` / `chipText`, and the `teen`, `a`, `b` event families (each with `fill`, `bar`, `title`, `time`).
3. Append it to `CalendarThemes.selectable`.

That is the whole change. The widget renderer and the Theme screen both read the token object, so the new theme appears as a preview card and applies live. Widget code must not declare `Color(...)` values of its own: add a token instead. To change how titles map to event families (for example by grade level), edit only `CalendarThemes.familyFor(title)`.

---

## Tech Stack
* **Language:** Kotlin (JVM Target 17)
* **Android SDK:** Min SDK 26 (Android 8.0) | Compile/Target SDK 35
* **Main UI:** Jetpack Compose + Material 3
* **Widget Framework:** Jetpack Glance 1.2.0 (`androidx.glance:glance-appwidget` & `glance-material3`)
* **Persistence:** Jetpack Preferences DataStore
* **Background Jobs:** AndroidX WorkManager 2.10.0
* **Build System:** Gradle 8.12 + Android Gradle Plugin 8.7.3 (Kotlin 2.0.21)

---

## Building and Running

### Prerequisites
* Java Development Kit (JDK 17)
* Android SDK Platform 35 & Build-Tools 35.0.0

### Compile Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install Directly to Device or Emulator
```bash
./gradlew installDebug
```

---

## License
MIT License.
