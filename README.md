# Clarity

Native Kotlin Android task app with To-Do, Calendar, Notes, Quotes, and locked Vault home-screen widgets. GitHub Actions builds and uploads the debug APK as `dark_todo.apk`.

## Quotes Widget

Place a **Clarity Quotes** widget directly from your home-screen widget picker.
Open **Notes > Quotes tile > gear** to configure its topics, attribution, appearance,
fonts and rotation. Settings apply globally to Quotes widgets and existing legacy
Random quotes instances; ordinary notes and note widgets are unchanged.
Quotes are bundled and work entirely offline.

Changes update the live preview; **Apply Changes** persists them and refreshes existing
quote widgets without removing/re-adding them. **Refresh widgets** advances quotes
using saved settings. **Reset to defaults** loads the default preview; Apply to save it.
Defaults are All Topics, sourced authors only, author visible/source hidden, manual
rotation, a dark solid card, light sans-serif 20sp text, left alignment, 24dp corners,
20dp padding, a subtle border and quotation marks.

The outer widget background and inner quote card have independent solid/gradient/
transparent styles, colors, gradient directions, opacity, borders, radii and padding.
Typography includes line spacing. Older saved card settings keep their appearance;
the outer layer defaults to transparent with no padding or border.
The Quotes tile previews a current widget quote (or an eligible bundled quote before
placement). Its gear opens the quote-only settings, including the shared background
image picker/crop/fade editor. Image edits save immediately; other settings use Apply
Changes. Quote images are device-local and do not change note images or styles.

Manual/Both mode lets you tap for the next quote. Scheduled rotation uses Android
WorkManager (15 minutes minimum), survives reboot, and can be delayed by Doze or
battery restrictions. Force-stop pauses background work until the app is reopened.
Missing topic matches show all eligible quotes with an explanation. Topic aliases
reuse bundled categories rather than promising new content. Long quotes shrink and
then ellipsize in small cells; resize the widget for more text. Unsafe color contrast
is corrected for opaque cards in both preview and widget. Transparent/translucent
cards and image backgrounds retain your chosen ink; use image fade for contrast. The preview uses
a sample dark surface, not your launcher wallpaper. Quotes Widget preferences and
per-instance quote cycles are device-local and separate from Notes preferences.

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

### Google connection troubleshooting

The app uses Google Identity Services authorization from Google Play services, with
`drive.appdata` and `email` scopes. This Android authorization flow identifies the
app by package and signing certificate; it does not use a web redirect URI or a
client secret embedded in the app.

- In the same Google Cloud project, enable the **Google Drive API**, configure the
  OAuth consent screen/audience, and allow the requested scopes. If the project is
  in Testing, add the Google account being used as a test user; organization policies
  can also restrict access.
- Create/check an **Android OAuth client** with package `com.example.darktodo` and
  the exact **SHA-1** certificate fingerprint printed by the workflow's
  `apksigner verify --print-certs` command for the APK actually installed. It also
  prints SHA-256; Android OAuth client matching here uses SHA-1, not SHA-256.
- A workflow warning about a missing stable debug keystore means that run used
  an ephemeral debug certificate. Set the four signing secrets above to reuse
  one certificate across builds, then register that certificate's SHA-1. Do not
  replace the release OAuth client, share keystore passwords, or commit keys.
- On the device, check internet access and that Google Play services is installed,
  enabled and current. Read the connection error displayed under Settings > Data.
  A successful APK build alone does not verify OAuth or Drive access.

Cancellation leaves local data unchanged. Successful authorization updates the
account immediately; checking/uploading the private backup can still fail
separately and displays an error. The last-backup time changes only after upload.

### Backup history and restore points

Under **Settings > Data**, use **Backup now** to create a separate snapshot and
**Backup history** (also opened by **Restore backup**) to refresh the list and
choose a particular restore point. Dates use the device's local time zone.
Manual, automatic and pre-restore Safety points show their type, size and format
when available. Automatic backups keep the existing change-triggered, debounced,
network-constrained schedule; this is not a periodic/daily schedule.

The **Automatic backup** switch beside the backup controls persists across app
restarts. Turning it off cancels queued automatic work without deleting backups
or disabling manual backup/restore. An upload already in progress may finish
safely. Turning it on requests the existing unique, network-constrained job;
repeated changes do not create parallel scheduled jobs. The existing setup
behavior is retained: backup starts paused before setup; the first successful
manual backup/restore enables automation only if no explicit choice was saved.
Manual actions never override an explicit Off choice.
The first-launch "No backup found", then "Start fresh" path also retains its existing
automation setup; choosing Start Fresh when a backup was found leaves it off.

Snapshots use unique Google Drive file IDs in the existing private `appDataFolder`,
with dated names and checksum metadata. New snapshots use version-2 JSON with a
size/SHA-256 asset manifest; version-1 backups remain restorable.
Retries reuse a durable reserved ID and payload rather than appending duplicates.
The old `clarity-backup.json` appears as a Legacy backup without migration or
deletion; its displayed date is Drive's last modification time, not an invented
snapshot time. Unknown metadata is labelled as unavailable.

The default retention limit is **10 verified new snapshots**, including Safety
points. Older eligible snapshots are deleted only after another upload has been
downloaded, checksum/format-validated and marked verified. Legacy, unverified and
unknown-format snapshots are kept separately. Restore does not prune history;
it can temporarily exceed the limit until the next successful normal backup.
Failed cleanup keeps extra copies and reports that cleanup was deferred.
The limit is a single constant in
[`BackupHistory.kt`](app/src/main/java/com/example/darktodo/backup/BackupHistory.kt),
not a configurable setting or a storage-quota guarantee.

Restore asks for confirmation of the selected ID and date, then downloads that
exact file. Unsupported/incomplete JSON, invalid typed preferences, malformed
nested task/note JSON, invalid encoded files and checksum failures are rejected
before applying data. A verified cloud Safety point and matching local safety file
are required before replacing preferences/files. An application failure attempts
rollback; recovery errors explicitly identify the retained Safety point. This is
not a cross-file transaction: process termination during application can require
manually restoring the Safety point. Keep the app open until it finishes.

The vault stays encrypted; master passwords and biometric keys are not uploaded.
Unlock a restored vault with its original master password. Without that password,
preflight can check its header and transport integrity, not authenticate/decrypt
its contents. A snapshot without a vault preserves the current vault; legacy
snapshots without Notes preserve current Notes. Version-1 backups also preserve
newer appearance/font stores and their assets when those sections are absent.
Successful restore reloads the app and refreshes its affected widgets.
Offline/access-expiry failures display an error; reconnect/refresh and retry.
**Delete backup history** explicitly deletes all Clarity restore points, including
legacy and Safety copies, and pauses automatic backup; it does not delete local
data or unrelated Drive files.

Version-2 backups include tasks/history, Notes, app and widget appearance settings
(including System Monitor and Vault), saved default styles, navigation preferences,
imported font registry metadata and actual font files, and private
To-Do/Calendar/Notes/System Monitor wallpaper files. Stable font IDs and logical
asset names preserve references without depending on another device's paths;
font/image validation happens before replacement and rendering caches are cleared.
Repeated restores replace these assets rather than appending duplicate records.

The last saved System Monitor snapshot is included as historical data, not live
statistics for the destination device. It is labelled as restored until refreshed.
Monitor polling does not itself schedule cloud uploads; the saved snapshot travels
with manual backups or existing change-triggered backups.

Biometric credentials and Android Keystore keys cannot be transferred. After a
restore, unlock with the original vault master password and enable biometric
unlock again in Vault Security. Disposable caches, other apps' private data,
launcher-specific widget placements, Downloads and reports exported through the
document picker are not packaged. Clarity does not maintain private managed
copies of those external Downloads/reports or request broader storage permissions.
Vault widget appearance currently has no background-image setting.

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

The To-Do and Calendar widget font selectors support importing local `.ttf`, `.otf`, and `.ttc` files on Android 9 and newer. The app copies selected files into its private storage and applies them to widget text; optional Drive backups include these private copies and their registry. Imported fonts stay available for either widget until app data is cleared or the app is uninstalled, and version-2 backups can restore them. Android 8 and older continue to use the built-in widget font options.

The To-Do widget uses a scrollable collection to show the full task list while keeping its count panel, refresh action, and Add task action fixed. It does not have a clear-completed control; clear completed tasks from the app. Tapping a task row, title, or checkbox toggles completion. Use the up/down controls in the app to reorder tasks within the incomplete or completed group; the widget follows that order. Long-press any task in the app for Edit or Delete actions; deletion requires confirmation. Widget countdowns use the same formatter as the Tasks screen and include minutes. Android limits periodic widget refreshes to at least 30 minutes; the widget also refreshes on task changes, reminder milestones, and system updates. While the app is foregrounded, it refreshes displayed countdowns once per minute.

To-Do and Calendar appearance settings provide five Android system font families and support importing local `.ttf`, `.otf`, and `.ttc` files on Android 9 and newer. The app also has a separate typography selector. Imported fonts are copied to private app storage and included in optional Drive backups, not publicly shared. The repository contains a Nunito license notice but does not currently bundle Nunito font files or provide Nunito weight/italic controls.

The Vault widget is only a locked shortcut into the app. Vault credentials are not displayed in the widget. The vault is encrypted and stored locally outside Android device backup; optional Clarity Drive snapshots include the encrypted file. There is no cloud sync or master-password recovery service.
