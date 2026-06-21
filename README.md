# DailyFocus

DailyFocus is an Android application designed to help users track their daily tasks, manage subtasks, and maintain streaks. It includes a home screen widget for quick task management.

## Features

- **Task Management**: Create, edit, and delete tasks.
- **Subtasks**: Break down tasks into smaller, manageable steps.
- **Daily & Cooldown Tasks**: Support for recurring tasks and tasks with a 24-hour cooldown.
- **Streaks**: Track how many days in a row you've completed your tasks.
- **History**: View completion history for each task.
- **Home Screen Widget**: View and toggle tasks directly from your home screen.
- **Confirmation Popups**: Safety checks when unchecking completed tasks from the widget.

## Technical Stack

- **Language**: Java
- **Database**: Room Persistence Library (SQLite)
- **UI**: XML Layouts with Material Design components
- **Architecture**: AppWidgetProvider for home screen widget integration
- **Serialization**: Gson (for subtask storage in Room)

## Project Structure

- `app/src/main/java/com/example/dailyfocus/data/`: Database entities (`Task`, `Subtask`, `TaskHistory`), DAO, and Database configuration.
- `app/src/main/java/com/example/dailyfocus/widget/`: Logic for the `TaskWidgetProvider` and its remote views.
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
