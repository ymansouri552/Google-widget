# Neon Purple concept: analysis and implementation

## What the reference shows

The supplied `original-f4d7c5d9c9335a2709e2d1d82913e165.webp` is a two-screen mobile calendar concept.

Screen 1, Today:
- near-black navy surface with a low-contrast violet wash in the corner,
- avatar plus two-line greeting, notification bell,
- a `Today / Now` pair with date and live clock,
- full-width search field,
- `Upcoming event` hero card with a logo tile, status pill, time range and two action chips,
- `My tasks 16` with a `See all` link and checkbox rows,
- five-slot bottom bar with a raised violet add button.

Screen 2, Calendar:
- Day / Week / Month segmented control,
- Monday-first week strip (Mon 23 to Sun 29) with the selected day in violet,
- hour gutter and timeline with rounded event cards, colored left bars and a three-dot action,
- a highlighted `Large free slot` band with a `Schedule an event` button,
- the same bottom bar with Calendar selected.

Palette read from the image: background `#0B0A12`, surfaces `#171530`, primary violet `#6A5AE0` to `#8B7CF6`, secondary pink `#EF5DA8`, tertiary coral `#F25C6C`, primary text near-white, secondary text muted lavender.

## Design Read

Reading this as a companion-app dashboard for someone checking their day at a glance, in the supplied Neon Purple visual language, dial ENERGY 2 / RHYTHM 2 / MOTION 1. The reference is the owner's explicit direction, so the violet pair and dark surfaces are identity decisions, not defaults.

## Why each technique is here

- Dark base: calendar data is read in low light and event color bars need a dark ground to stay distinguishable.
- One violet gradient: reserved for the selected day, the add button and the primary CTA, so it marks the one action that matters on each screen.
- Pink and coral: event family colors, tied to real calendar events through `CalendarThemes.familyFor(title)`, not decoration.
- Radial corner wash: separates the app from system screens without tinting every card.
- Raised center add button: puts task creation at the thumb without shrinking the four navigation targets.
- Hour grid and absolute event blocks: time is spatial on the Calendar screen, so position carries the information a list would lose.
- Free-slot CTA: points at a real gap between meetings and opens the system event editor with that time prefilled.
- Initial avatar and calendar glyph: honest placeholders. No profile photo or invented brand mark was generated.

## Where it lives

| Concept element | Implementation |
| --- | --- |
| Palette and event families | `ui/theme/CalendarThemes.kt` (`neonPurple`) |
| App color scheme bridge | `ui/theme/Theme.kt` |
| Theme picker entry | `ui/ThemeActivity.kt` |
| Greeting, search, hero card, tasks | `ui/app/TodayScreen.kt` |
| Mode switch, week strip, month grid, timeline | `ui/app/CalendarScreen.kt` |
| Timeline geometry | `ui/app/TimelineLayout.kt` |
| Bottom bar, gradient, chips, field, avatar | `ui/app/NeonDesign.kt` |
| Screen shell and background | `ui/app/AppShell.kt` |

## Real data, no placeholders

Counts, task rows, event titles, times, dots and the free slot all come from `CalendarRepository`, `TaskRepository` and `CalendarVisibilityRepository`. The greeting name is the concept's persona label, not a claimed account identity. Empty, loading, permission-denied and error states are present on Today and Calendar.

## Verification

`./gradlew assembleDebug testDebugUnitTest` compiles the app and runs the unit suite, including `TimelineLayoutTest` for range, lanes, free-slot selection and past-time exclusion. No Android device or emulator image is available in this environment, so interactive click-through was not run; behavior was verified by code inspection and by unit tests around the pure timeline logic.
