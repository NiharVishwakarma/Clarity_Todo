# Dark Todo Widget

Native Kotlin Android app and home-screen widget. GitHub Actions builds an APK named `dark_todo.apk`, signed with a persistent app key.

## Build with GitHub Actions

The workflow in `.github/workflows/android-apk.yml` runs on pushes and pull requests to `main`, and can also be started manually from the repository's **Actions** tab. It installs Java 17 and the Android SDK, generates the Gradle wrapper on the runner, and builds the APK.

To download it, open a successful **Android APK Build** run in GitHub Actions and download the `dark-todo-debug-apk` artifact. Extract it and install `dark_todo.apk` on Android. If Android asks, allow installation from the browser or file manager used to open it. Then long-press the home screen, open **Widgets**, find **Dark Todo**, and place it.

### Persistent APK signing

The debug APK uses `clarity-signing.jks` and `keystore.properties` when they are present. Both files are excluded from Git. Back them up securely: losing this key means Android will no longer accept future APKs as updates to installations signed with it.

The GitHub Actions workflow uses the same key from repository Actions secrets. Add these four secrets under **Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: the Base64 output of `[Convert]::ToBase64String([IO.File]::ReadAllBytes('.\\clarity-signing.jks'))` in PowerShell.
- `ANDROID_KEYSTORE_PASSWORD`: the `storePassword` value in your local `keystore.properties`.
- `ANDROID_KEY_PASSWORD`: the `keyPassword` value in `keystore.properties`.
- `ANDROID_KEY_ALIAS`: the `keyAlias` value in `keystore.properties` (`clarity`).

Push and manual GitHub Actions builds require these secrets, so they cannot silently produce APKs signed with a temporary key. Pull request builds from forks can still compile with the default debug key, but those APKs are not for installing over the persistent-key build.

Changing from a previously installed APK signed with another key requires a one-time uninstall before Android will accept this signing key. Uninstalling erases app data and removes that app's home-screen widgets, so preserve any needed task data and keep the existing installation until ready to switch. After installing the persistent-key APK, later builds signed with this key install as updates and retain app data and widget placements.

No Codemagic account or local Android SDK is needed for the GitHub-hosted build.

## Countdown and widget refresh behavior

The task screen, widget, and reminder progress notifications use the same deadline formatter. Relative countdowns use the saved elapsed-realtime deadline during the current boot, with the saved wall-clock deadline as recovery data across reboot; local-time reminders use their saved date/time and selected time zone. Positive durations round up to the next minute, while expired duration deadlines show `OVERDUE`.

Task changes persist before their alarms and widget are updated. The existing reminder alarm is also the deadline refresh trigger: it refreshes the widget when a deadline is reached, while hourly progress reminders continue to refresh it before then. Alarms are restored after reboot, app replacement, clock/date/time-zone changes, and when the first widget is added. Opening the app and waking the screen while the app is active also recalculate and refresh task state.

Android does not allow a manifest receiver for screen-on broadcasts, and widget periodic updates are limited to once every 30 minutes. The screen-on listener is therefore registered only while the app activity is started; with the app closed, deadline alarms and the widget provider's periodic refresh are the supported fallbacks. Doze, battery restrictions, force-stop, and denied notification permission can delay or prevent background delivery. Exact alarms are used only when Android grants the app exact-alarm access; otherwise the scheduler falls back to inexact alarms.

## Data behavior

Active tasks use the `active_tasks_v1` preference key. Activity records use `task_history_v1`. Deleting or clearing active tasks writes a deletion event to history; deleting history only rewrites the history key. Relative countdowns use elapsed realtime while the device is running and are rebased from their saved deadline after reboot. Local-time reminders retain their local date/time and IANA time-zone ID; blank time-zone selection follows the device zone. Storage remains local and is retained across app restarts and reboots.

## Current implementation notes

The To-Do and Calendar widget font selectors support importing local `.ttf`, `.otf`, and `.ttc` files on Android 9 and newer. The app copies selected files into its private storage and applies them to widget text; the font files are not uploaded or shared. Imported fonts stay available for either widget until app data is cleared or the app is uninstalled. Android 8 and older continue to use the built-in widget font options.

The widget uses a scrollable collection to show the full task list while keeping its count panel, clear-completed control, and Add task action fixed. Widget task titles open the app, while the checkbox toggles completion. Use the up/down controls in the app to reorder tasks within the incomplete or completed group; the widget follows that order. Long-press any task in the app for Edit or Delete actions; deletion requires confirmation. Widget countdowns use the same formatter as the Tasks screen and include minutes. Android limits periodic widget refreshes to at least 30 minutes; the widget also refreshes on task changes, reminder milestones, and system updates. While the app is foregrounded, it refreshes displayed countdowns once per minute. Widget colors, completed-task fading, font weight, and italic task text can be adjusted in Settings. The app and widget use the bundled Nunito variable fonts for normal and italic styles. Font source: Google Fonts Nunito, licensed under SIL Open Font License 1.1 (see `licenses/Nunito-NOTICE.md`).
