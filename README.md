# DailyFocus

DailyFocus is an Android application designed to help users track their daily tasks, manage subtasks, and maintain streaks. It includes a home screen widget for quick task management.

## Features

- **Task Management**: Create, edit, and delete tasks. Swipe right to complete, swipe left to delete.
- **Subtasks**: Break down tasks into smaller, manageable steps.
- **Daily, Weekday & Cooldown Tasks**: Recurring tasks every N days, on specific weekdays (e.g. Mon/Wed/Fri), or with a configurable cooldown.
- **Streaks**: Track how many days in a row you've completed your tasks — current streak plus all-time record per task. A "Restore Streak" button repairs a missed day and recomputes the full streak from history.
- **Freeze a Streak**: Going on holiday? Freeze a daily task and missed days stop breaking the chain (reminders pause too, check-offs still count). Frozen days are stored separately from completions, so they bridge the streak without polluting history or the heatmap.
- **Reset a Streak**: Start a counter over from the current period, optionally clearing the all-time record too. History and the heatmap stay intact — only the streak's starting point moves.
- **History & Heatmap**: Completion history per task and a GitHub-style activity heatmap in Stats.
- **Reminders**: Exact-alarm notifications with a "Complete" action button; alarms are restored after reboot.
- **Home Screen Widget**: View and toggle tasks directly from your home screen; follows the system light/dark theme.
- **Backup**: Manual JSON export/import plus automatic daily backups (last 7 kept) restorable in-app. Streaks are recomputed from history on import.
- **Confirmation Popups**: Safety checks when unchecking completed tasks from the widget.

## Technical Stack

- **Language**: Java
- **Database**: Room Persistence Library (SQLite, schema v8) — all access is async via a single-threaded repository
- **UI**: XML Layouts with Material Design components, day/night theme-aware
- **Architecture**: AppWidgetProvider for home screen widget integration
- **Serialization**: Gson (for subtask storage in Room and JSON backups)

## Project Structure

- `app/src/main/java/com/example/dailyfocus/data/`: Database entities (`Task`, `Subtask`, `TaskHistory`, `StreakFreeze`), DAO, Database configuration, and `TaskRepository` (async DB access + auto-backup).
- `app/src/main/java/com/example/dailyfocus/utils/`: `TaskHelper` (single source of truth for resets, completions, streaks, reminders) and `Periods` (period math for reset schedules).
- `app/src/main/java/com/example/dailyfocus/widget/`: Logic for the `TaskWidgetProvider` and its remote views.
- `app/src/main/java/com/example/dailyfocus/views/`: `HeatmapView` (custom activity heatmap).
- `app/src/main/res/layout/`: UI layouts for activities and widget items.

## Getting Started

### Prerequisites

- Android Studio (Latest stable version recommended)
- JDK 17 or higher
- Android SDK (API level 34 support)

### Setup

1.  Clone the repository or copy the project folder to your local machine.
2.  Open the project in Android Studio.
3.  Let Gradle sync finish.
4.  Run the application on an emulator or a physical device.
