# Clarity

Native Kotlin Android task app with To-Do, Calendar, Notes, and locked Vault home-screen widgets. GitHub Actions builds and uploads the debug APK as `dark_todo.apk`.

## Do I need development tools locally?

No. GitHub Actions installs Temurin JDK 17, Gradle 8.9, and the Android SDK for each build. You can trigger the workflow from the repository's **Actions** tab and download its APK artifact without installing Java, Gradle, or Android Studio on your computer.

If you do want to build the app locally, use JDK 17 and also install/configure Gradle 8.9 and Android SDK platform/build tools 35. The repository does not include the Gradle wrapper scripts or JAR.

## Build with GitHub Actions

The workflow in `.github/workflows/android-apk.yml` runs on pushes and pull requests to `main`, and can also be started manually from the repository's **Actions** tab. It installs Java 17 and the Android SDK, generates the Gradle wrapper on the runner, and builds the APK.

To download it, open a successful **Android APK Build** run in GitHub Actions and download the `dark-todo-debug-apk` artifact. Extract it and install `dark_todo.apk` on Android. If Android asks, allow installation from the browser or file manager used to open it. Then long-press the home screen, open **Widgets**, find **Clarity**, and place the To-Do, Calendar, or Vault widget.

### Stable debug signing for Google OAuth

The routine workflow builds a debug APK. Without a configured debug keystore, Gradle uses the runner's default debug key; hosted runners can have different keys, so Android OAuth fingerprint matching may fail. Use a dedicated, non-production debug keystore and register its SHA-1 in a Google Cloud Android OAuth client for package `com.example.darktodo`.

To create a key with JDK `keytool` (it prompts for passwords and certificate details):

```powershell
keytool -genkeypair -v -keystore clarity-debug.jks -alias clarity-debug -keyalg RSA -keysize 2048 -validity 10000
keytool -list -v -keystore clarity-debug.jks -alias clarity-debug
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.\clarity-debug.jks'))
```

Add these four repository Actions secrets under **Settings → Secrets and variables → Actions**: `ANDROID_DEBUG_KEYSTORE_BASE64`, `ANDROID_DEBUG_KEYSTORE_PASSWORD`, `ANDROID_DEBUG_KEY_ALIAS`, and `ANDROID_DEBUG_KEY_PASSWORD`. The workflow uses them only to sign the debug APK. Register the displayed SHA-1 in Google Cloud Console as a second Android OAuth client for `com.example.darktodo`; keep the existing production/release OAuth client and key separate. The debug keystore can also be kept outside Git and configured locally with `debugStoreFile`, `debugStorePassword`, `debugKeyAlias`, and `debugKeyPassword` in ignored `keystore.properties`.

When changing the signing certificate on an already-installed app, Android may reject the APK as an update. Do not uninstall until any local data you need has been safely backed up; uninstalling removes app data and widgets.

### Release signing

The app module retains a separate release signing configuration using `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`, or corresponding ignored `keystore.properties` entries. Routine CI currently does not build or sign a release APK.

No Codemagic account or local Android SDK is needed for the GitHub-hosted build.

## Countdown and widget refresh behavior

The task screen, widget, and reminder progress notifications use the same deadline formatter. Relative countdowns use the saved elapsed-realtime deadline during the current boot, with the saved wall-clock deadline as recovery data across reboot; local-time reminders use their saved date/time and selected time zone. Positive durations round up to the next minute, while expired duration deadlines show `OVERDUE`.

Task changes persist before their alarms and widget are updated. The existing reminder alarm is also the deadline refresh trigger: it refreshes the widget when a deadline is reached, while hourly progress reminders continue to refresh it before then. Alarms are restored after reboot, app replacement, clock/date/time-zone changes, and when the first widget is added. Opening the app and waking the screen while the app is active also recalculate and refresh task state.

Android does not allow a manifest receiver for screen-on broadcasts, and widget periodic updates are limited to once every 30 minutes. The screen-on listener is therefore registered only while the app activity is started; with the app closed, deadline alarms and the widget provider's periodic refresh are the supported fallbacks. Doze, battery restrictions, force-stop, and denied notification permission can delay or prevent background delivery. Exact alarms are used only when Android grants the app exact-alarm access; otherwise the scheduler falls back to inexact alarms.

## Data behavior

Active tasks use the `active_tasks_v1` preference key. Activity records use `task_history_v1`. Deleting or clearing active tasks writes a deletion event to history; deleting history only rewrites the history key. Relative countdowns use elapsed realtime while the device is running and are rebased from their saved deadline after reboot. Local-time reminders retain their local date/time and IANA time-zone ID; blank time-zone selection follows the device zone. Storage remains local and is retained across app restarts and reboots.

## Current implementation notes

The To-Do and Calendar widget font selectors support importing local `.ttf`, `.otf`, and `.ttc` files on Android 9 and newer. The app copies selected files into its private storage and applies them to widget text; the font files are not uploaded or shared. Imported fonts stay available for either widget until app data is cleared or the app is uninstalled. Android 8 and older continue to use the built-in widget font options.

The To-Do widget uses a scrollable collection to show the full task list while keeping its count panel, refresh action, and Add task action fixed. It does not have a clear-completed control; clear completed tasks from the app. Tapping a task row, title, or checkbox toggles completion. Use the up/down controls in the app to reorder tasks within the incomplete or completed group; the widget follows that order. Long-press any task in the app for Edit or Delete actions; deletion requires confirmation. Widget countdowns use the same formatter as the Tasks screen and include minutes. Android limits periodic widget refreshes to at least 30 minutes; the widget also refreshes on task changes, reminder milestones, and system updates. While the app is foregrounded, it refreshes displayed countdowns once per minute.

To-Do and Calendar appearance settings provide five Android system font families and support importing local `.ttf`, `.otf`, and `.ttc` files on Android 9 and newer. The app also has a separate typography selector. Imported fonts are copied to private app storage; they are not uploaded or shared. The repository contains a Nunito license notice but does not currently bundle Nunito font files or provide Nunito weight/italic controls.

The Vault widget is only a locked shortcut into the app. Vault credentials are not displayed in the widget. The vault is encrypted and stored locally outside device backup; there is no cloud sync or vault recovery service.
