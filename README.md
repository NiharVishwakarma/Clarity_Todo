# Dark Todo Widget

Native Kotlin Android app and home-screen widget. GitHub Actions builds a debug-signed APK named `dark_todo.apk`.

## Build with GitHub Actions

The workflow in `.github/workflows/android-apk.yml` runs on pushes and pull requests to `main`, and can also be started manually from the repository's **Actions** tab. It installs Java 17 and the Android SDK, generates the Gradle wrapper on the runner, and builds the APK.

To download it, open a successful **Android APK Build** run in GitHub Actions and download the `dark-todo-debug-apk` artifact. Extract it and install `dark_todo.apk` on Android. If Android asks, allow installation from the browser or file manager used to open it. Then long-press the home screen, open **Widgets**, find **Dark Todo**, and place it.

No Codemagic account or local Android SDK is needed for the GitHub-hosted build.

## Data behavior

Active tasks use the `active_tasks_v1` preference key. Activity records use `task_history_v1`. Deleting or clearing active tasks writes a deletion event to history; deleting history only rewrites the history key. Relative countdowns use elapsed realtime while the device is running and are rebased from their saved deadline after reboot. Local-time reminders retain their local date/time and IANA time-zone ID; blank time-zone selection follows the device zone. Storage remains local and is retained across app restarts and reboots.

## Current implementation notes

The widget lists up to five tasks at once; the app shows the full list. Widget task titles open the app, while the checkbox toggles completion. Use the up/down controls in the app to reorder tasks within the incomplete or completed group; the widget follows that order. Long-press any task in the app for Edit or Delete actions; deletion requires confirmation. Widget countdowns use the same formatter as the Tasks screen and include minutes. Android limits periodic widget refreshes to at least 30 minutes; the widget also refreshes on task changes, reminder milestones, and system updates. While the app is foregrounded, it refreshes displayed countdowns once per minute. Widget colors, completed-task fading, font weight, and italic task text can be adjusted in Settings. The app and widget use the bundled Nunito variable fonts for normal and italic styles. Font source: Google Fonts Nunito, licensed under SIL Open Font License 1.1 (see `licenses/Nunito-NOTICE.md`).
