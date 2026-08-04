<div align="center">

# DailyFocus

**A no-nonsense Android habit and task tracker built around streaks that behave the way you'd expect.**

[![Latest release](https://img.shields.io/github/v/release/yuuge7/dailytasks?label=release&color=4CAF50)](https://github.com/yuuge7/dailytasks/releases/latest)
[![Release workflow](https://github.com/yuuge7/dailytasks/actions/workflows/release.yml/badge.svg)](https://github.com/yuuge7/dailytasks/actions/workflows/release.yml)
[![License](https://img.shields.io/github/license/yuuge7/dailytasks?color=blue)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-24-informational)](app/build.gradle.kts)

</div>

---

## Overview

DailyFocus tracks recurring tasks and the streaks you build by finishing them. It runs
entirely on-device — no account, no network, no analytics — and keeps a persistent
completion history so your numbers survive reinstalls, restores and schedule changes.

The app is written in plain Java against Room and the Android view system, so the codebase
stays approachable: no dependency-injection framework, no reactive stack, no build plugins
beyond the Android Gradle Plugin.

## Features

| | |
|---|---|
| **Task management** | Create, edit, reorder and delete tasks. Swipe right to complete, swipe left to delete. |
| **Subtasks** | Break a task into steps; ticking the last one completes the parent. |
| **Flexible schedules** | Daily at a fixed reset hour, every *N* days, on selected weekdays (e.g. Mon/Wed/Fri), or on a configurable cooldown timer. |
| **Streaks** | Current streak plus an all-time record per task, computed from real history rather than a running counter. |
| **Freeze a streak** | Going away? Freeze a task and missed days stop breaking the chain — reminders pause, check-offs still count. |
| **Reset a streak** | Start a counter over from the current period, optionally clearing the all-time record too. |
| **Restore a streak** | Repair a single missed day and recompute the whole chain from history. |
| **History & heatmap** | Per-task completion log and a GitHub-style activity heatmap in Stats. |
| **Reminders** | Exact-alarm notifications with an inline *Complete* action; alarms are restored after reboot. |
| **Home screen widget** | View and toggle tasks without opening the app; follows the system light/dark theme. |
| **Backup** | Manual JSON export/import plus automatic daily backups (last 7 kept), restorable in-app. |

## Download

Grab the latest signed APK from the [Releases page](https://github.com/yuuge7/dailytasks/releases/latest).
Android will ask you to allow installs from your browser or file manager the first time.

## How streaks work

Streaks are **derived from history**, not from a counter that drifts. Every completion writes
one row keyed to the day the current period started, under a unique `(taskId, day)` index — so
ticking and un-ticking a task repeatedly can never inflate a streak.

Three controls exist for when life interferes:

- **Freeze** — while a daily task is frozen, a period that closes unfinished is recorded as a
  *freeze day* instead of resetting the counter. Freeze days bridge the chain without counting
  as completions, so they never pollute your history or heatmap, and they can't be applied
  retroactively to days you already lost. Reminders are suppressed until you unfreeze.
- **Reset** — moves the streak's starting point to the current period. History and the heatmap
  are left untouched; only the chain restarts.
- **Restore** — fills in the previous scheduled day and recomputes the full streak, for the
  times you genuinely did the thing but forgot to tick it.

All of this lives in [`TaskHelper`](app/src/main/java/com/example/dailyfocus/utils/TaskHelper.java),
the single source of truth for resets, completions and streaks, with period arithmetic isolated
in [`Periods`](app/src/main/java/com/example/dailyfocus/utils/Periods.java).

## Tech stack

- **Language** — Java 11 source level
- **Database** — Room (SQLite, schema v8) with hand-written migrations; all access is async
  through a single-threaded repository
- **UI** — XML layouts with Material Components, day/night theme-aware
- **Widget** — `AppWidgetProvider` + `RemoteViewsService`
- **Serialization** — Gson, for subtask storage and JSON backups
- **Build** — Gradle 8.13, Android Gradle Plugin 8.13.2, version catalog in `gradle/libs.versions.toml`

## Project structure

```
app/src/main/java/com/example/dailyfocus/
├── data/        Room entities (Task, Subtask, TaskHistory, StreakFreeze), DAO,
│                database + migrations, TaskRepository (async access, auto-backup)
├── utils/       TaskHelper (resets, completions, streaks, reminders), Periods (period math)
├── receiver/    Boot, notification and widget-update broadcast receivers
├── widget/      Home screen widget provider and remote views
├── views/       HeatmapView (custom activity heatmap)
└── *.java       Activities: Main, Stats, Reorder, TaskDetails, widget dialogs
```

## Getting started

### Prerequisites

| Requirement | Notes |
|---|---|
| **Android Studio** | Latest stable release |
| **JDK 17** | Gradle 8.13 does not run on JDK 24+. Android Studio's bundled JDK works only if it is 17–23; otherwise install Temurin 17 and point Gradle at it. |
| **Android SDK** | Platform 36 (`compileSdk`/`targetSdk`), `minSdk` 24 |

### Run it

```bash
git clone https://github.com/yuuge7/dailytasks.git
cd dailytasks
./gradlew :app:assembleDebug        # or open the folder in Android Studio and hit Run
```

`local.properties` (your SDK path) is generated by Android Studio on first sync and is
git-ignored. If you build from the command line without Studio, create it yourself:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

To select a specific JDK for a command-line build:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew :app:assembleDebug
```

### Release builds

`assembleRelease` produces an **unsigned** APK unless signing material is supplied — so
contributors can build every variant without owning the release key. To sign locally, copy
[`keystore.properties.example`](keystore.properties.example) to `keystore.properties`, fill in
your own values, and place the keystore at the repository root:

```properties
storeFile=dailyfocus-release.jks
storePassword=…
keyAlias=dailyfocus
keyPassword=…
```

Both the keystore and `keystore.properties` are git-ignored. The build also accepts the
equivalent environment variables — `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD` — which is how CI supplies them.

## Release automation

[`.github/workflows/release.yml`](.github/workflows/release.yml) runs on every push to `main`:

1. Reads `versionName` from [`app/build.gradle.kts`](app/build.gradle.kts).
2. Skips everything if a release for `v<versionName>` already exists — ordinary commits are a
   no-op, so the Releases page never fills with duplicates.
3. Otherwise decodes the keystore, builds a signed release APK, and publishes a GitHub release
   tagged `v1.2` and titled **DailyFocus v1.2**, with auto-generated notes and the APK attached
   as `DailyFocus-v1.2.apk`.

**Cutting a release is therefore one edit:** bump `versionName` (and `versionCode`) in
`app/build.gradle.kts`, push to `main`, and the workflow does the rest.

### Required repository secrets

Set these under **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The keystore file, base64-encoded (see below) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias — `dailyfocus` |
| `KEY_PASSWORD` | Key password |

Produce the base64 blob with:

```bash
base64 -w0 dailyfocus-release.jks > keystore.base64.txt   # Linux / Git Bash
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("dailyfocus-release.jks")) | Set-Content keystore.base64.txt
```

Paste the file's contents as the `KEYSTORE_BASE64` secret, then delete the file.

## Keeping the signing key

Android identifies an app by its signing certificate. **If the key is lost, no future build can
update an existing installation** — users would have to uninstall and reinstall, losing their
data. Treat it accordingly:

- The keystore lives at `dailyfocus-release.jks` in the repository root and is git-ignored.
  Cloning the repo on another machine does **not** bring it along — that is deliberate.
- Store a copy of the `.jks` file **and** its password somewhere durable and private: a password
  manager, an encrypted archive, or a private (non-public) backup location. Never commit either.
- Record the certificate fingerprint so you can confirm a restored key is the right one:

  ```bash
  keytool -list -v -keystore dailyfocus-release.jks -alias dailyfocus
  ```

### Setting the key up on another machine

1. Copy `dailyfocus-release.jks` into the repository root of the fresh clone.
   If your only copy is the GitHub secret, decode it back:

   ```bash
   base64 -d keystore.base64.txt > dailyfocus-release.jks
   ```

2. Recreate `keystore.properties` next to it, from `keystore.properties.example`.
3. Verify the key matches the published releases:

   ```bash
   ./gradlew :app:assembleRelease
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```

   The SHA-256 digest must match the one from the machine you generated the key on.

CI needs nothing beyond the four repository secrets — they are configured once per repository,
not per machine.

## Contributing

Issues and pull requests are welcome.

1. Fork the repository and create a branch off `main`.
2. Build and run the app before opening a PR: `./gradlew :app:assembleDebug`.
3. Match the surrounding code style — plain Java, no new frameworks, no formatting-only churn.
4. Keep database changes migration-safe: bump the version in
   [`AppDatabase`](app/src/main/java/com/example/dailyfocus/data/AppDatabase.java) and add a
   `Migration` alongside the existing ones. Never rely on destructive migration.
5. Leave `versionName`/`versionCode` alone in feature PRs — version bumps trigger a release and
   are handled by the maintainer.

## License

Released under the [MIT License](LICENSE).
