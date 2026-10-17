# Clarity Project Memory

This document records the implementation present in this repository snapshot. It is a task-oriented reference, not a claim that roadmap ideas have shipped. Update it when architecture or user-facing features change.

## Project purpose

Clarity is a local-first Android task and widget app. It combines task entry, completion and ordering, date-based task browsing, reminders and notifications, activity history, customizable To-Do and Calendar home-screen widgets, and a locked local password vault. The app has no account, remote API, or sync implementation.

The Android application is branded **Clarity** in its UI and widget labels; Gradle's project name remains `DarkTodoWidget`, and the application ID is `com.example.darktodo`.

## Architecture summary

- **UI:** One `FragmentActivity`, [`MainActivity.kt`](../app/src/main/java/com/example/darktodo/MainActivity.kt), hosts the Jetpack Compose app. It owns the Tasks, History, Calendar, Settings, Permissions, and Vault screens, editing dialogs, lifecycle refreshes, and incoming widget/notification navigation.
- **Task and history data:** [`TaskStore.kt`](../app/src/main/java/com/example/darktodo/data/TaskStore.kt) is the synchronized local persistence and mutation boundary. Tasks and history are JSON in `SharedPreferences`; active task writes are committed before alarm changes.
- **Reminders:** [`ReminderScheduler.kt`](../app/src/main/java/com/example/darktodo/reminders/ReminderScheduler.kt) manages `AlarmManager` alarms, reminder notifications, missed-reminder recovery, and system-event restoration. A receiver dispatches work to a process-local single-thread executor. The app does not use WorkManager.
- **Widgets:** Android `AppWidgetProvider` receivers publish `RemoteViews`. The To-Do collection is backed by a `RemoteViewsService`; Calendar is rendered as a month grid; Vault is a locked app-entry shortcut.
- **Settings and fonts:** Separate preference stores hold To-Do, Calendar, app typography, and imported-font selections. User-selected font files are copied into private app storage and can be rendered to bitmaps for widget `RemoteViews`.
- **Vault:** [`LocalVaultStore.kt`](../app/src/main/java/com/example/darktodo/data/LocalVaultStore.kt) encrypts a local vault file. The Compose screen is in `MainActivity.kt`; the Vault widget never displays vault contents.
- **Backup layer (new, uncompiled):** [`backup/`](../app/src/main/java/com/example/darktodo/backup) holds `BackupManager` (snapshot/restore, Google authorization), `DriveClient` (Drive v3 REST over `HttpURLConnection`, appDataFolder only), `BackupCodec` (typed SharedPreferences JSON) and `BackupScheduler`/`BackupWorker` (WorkManager, 20 s debounce, network-constrained, exponential retry). [`ClarityApplication`](../app/src/main/java/com/example/darktodo/ClarityApplication.kt) registers change listeners on the backed-up preference files. UI is in [`BackupScreens.kt`](../app/src/main/java/com/example/darktodo/BackupScreens.kt).

## Widget architecture

| Widget | Provider / implementation | Behavior and refresh |
|---|---|---|
| To-Do | [`TodoWidget.kt`](../app/src/main/java/com/example/darktodo/widget/TodoWidget.kt), [`TodoWidgetService.kt`](../app/src/main/java/com/example/darktodo/widget/TodoWidgetService.kt), [`todo_widget_info.xml`](../app/src/main/res/xml/todo_widget_info.xml) | Shows open-task count and a scrollable collection of all tasks, completion controls, refresh, and add action. Rows wrap titles to two lines (ellipsis only after that, no marquee). Tapping the check circle or a title that fits toggles completion; tapping a title longer than two lines opens a dialog with the full title (via the non-exported `TaskActionActivity`, the widget's click template). Supports horizontal and vertical resizing. Declares a 30-minute platform update period; task changes and alarm paths also refresh it. |
| Calendar | [`CalendarWidget.kt`](../app/src/main/java/com/example/darktodo/widget/CalendarWidget.kt), [`CalendarDates.kt`](../app/src/main/java/com/example/darktodo/data/CalendarDates.kt), [`calendar_widget_info.xml`](../app/src/main/res/xml/calendar_widget_info.xml) | Month grid with month navigation, date selection, and status dots for no/open/mixed/complete tasks. Selecting a date opens the app filtered to that date. A midnight alarm refreshes the selected-today state. Its provider has no periodic update interval. |
| Vault | [`VaultWidget.kt`](../app/src/main/java/com/example/darktodo/widget/VaultWidget.kt), [`vault_widget_info.xml`](../app/src/main/res/xml/vault_widget_info.xml) | Locked shortcut that opens the in-app vault screen, with a dedicated circular vault-door icon ([`ic_vault_door.xml`](../app/src/main/res/drawable/ic_vault_door.xml)) filling most of the widget height and a custom picker preview. It does not show secrets and has no periodic update interval. |

Common widget details:

- Widget layouts and provider sizing/previews are in `app/src/main/res/layout/` and `app/src/main/res/xml/`; widget backgrounds and icons are in `app/src/main/res/drawable/`.
- The To-Do widget uses `WidgetRefreshScheduler` to set one inexact alarm for the next visible countdown-label transition. Android may delay alarms; widget periodic updates cannot be more frequent than every 30 minutes.
- The Calendar widget stores per-instance selected date/month state separately from task storage. Monday is the fixed first weekday in the current date-grid helper.
- `WidgetFontLayouts` maps five Android system font families to RemoteViews-compatible layouts. Imported fonts use separate imported-font layouts and text bitmaps because a launcher cannot resolve an app-process `Typeface`.

## Folder structure

```text
.github/
  copilot-instructions.md        Project-wide assistant guidance
  workflows/android-apk.yml      GitHub Actions build/test/artifact workflow
app/
  build.gradle.kts               Android plugin configuration and app dependencies
  src/main/
    AndroidManifest.xml          Activity, permissions, widget/reminder receivers, service
    java/com/example/darktodo/
      MainActivity.kt            Compose app and navigation
      data/                      Task, history, time, widget settings, fonts, vault
      reminders/                 Alarm and notification scheduling/recovery
      ui/                        Shared color-picker UI
      widget/                    AppWidget providers, service, layouts, refresh
    res/
      drawable*/                 Widget/app artwork, backgrounds, status indicators
      layout/                    App-widget RemoteViews and preview layouts
      mipmap*/                   Launcher icons
      values*/                   Strings, theme, phone/tablet dimensions
      xml/                       App-widget provider metadata
  src/test/                      JVM unit tests
docs/                             Project memory and change history
gradle/wrapper/                   Wrapper distribution properties (wrapper scripts are not checked in)
licenses/                         Font license/notice text
```

Root files include `README.md` for build/install and operational notes, `APP_BLUEPRINT.txt` for an implementation/product blueprint, `Clarity_Feature_and_Pricing_Plan.txt` for proposed product and pricing ideas, `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties`.

## Important files

| File | Responsibility |
|---|---|
| [`MainActivity.kt`](../app/src/main/java/com/example/darktodo/MainActivity.kt) | Compose navigation and UI, task/reminder forms, History, Settings, Calendar screen, Vault screen, font picker, lifecycle and notification-permission handling |
| [`TaskStore.kt`](../app/src/main/java/com/example/darktodo/data/TaskStore.kt) | Task/history model, JSON persistence, mutations, task-date normalization, ordering, reminder state updates |
| [`TaskTimeFormatter.kt`](../app/src/main/java/com/example/darktodo/data/TaskTimeFormatter.kt) | Shared app/widget countdown labels and minute-transition calculations |
| [`ReminderScheduler.kt`](../app/src/main/java/com/example/darktodo/reminders/ReminderScheduler.kt) | Reminder alarms, notification delivery, missed reminders, boot/time-change recovery |
| [`TodoWidget.kt`](../app/src/main/java/com/example/darktodo/widget/TodoWidget.kt) | To-Do RemoteViews rendering, collection click handling, widget update entry points |
| [`TodoWidgetService.kt`](../app/src/main/java/com/example/darktodo/widget/TodoWidgetService.kt) | RemoteViews task-row collection factory |
| [`CalendarWidget.kt`](../app/src/main/java/com/example/darktodo/widget/CalendarWidget.kt) | Calendar widget rendering, date/month actions, midnight refresh |
| [`VaultWidget.kt`](../app/src/main/java/com/example/darktodo/widget/VaultWidget.kt) | Locked Vault widget entry point |
| [`LocalVaultStore.kt`](../app/src/main/java/com/example/darktodo/data/LocalVaultStore.kt) | Encrypted local vault persistence and biometric wrapping |
| [`LocalWidgetFonts.kt`](../app/src/main/java/com/example/darktodo/data/LocalWidgetFonts.kt) | Import, validation, private storage, resolution, and bitmap rendering of selected font files |
| [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml) | App permissions and component registration |
| [`app/build.gradle.kts`](../app/build.gradle.kts) | SDK levels, version, Java/Kotlin targets, signing and dependencies |
| [`android-apk.yml`](../.github/workflows/android-apk.yml) | CI build, unit tests, and APK artifact |
| [`README.md`](../README.md) | User build/install, signing, reminder and data behavior |
| [`APP_BLUEPRINT.txt`](../APP_BLUEPRINT.txt) | Existing feature and implementation notes; check against source where stale |
| [`Clarity_Feature_and_Pricing_Plan.txt`](../Clarity_Feature_and_Pricing_Plan.txt) | Roadmap and pricing proposals, not a shipped-feature specification |

## Data and behavior

- Active task JSON is stored under `active_tasks_v1` in the `todo_store` preferences file; task activity events are stored separately under `task_history_v1`. Widget appearance keys share `todo_store` with a `widget_setting_` prefix.
- Calendar appearance is stored in `calendar_widget_settings`; Calendar instance navigation state is stored in `calendar_widget_state`.
- App font selections are in `app_typography`; widget font metadata is in `widget_fonts`; imported font files live under the app's private `filesDir/widget-fonts`.
- Task history records create/edit/complete/reopen/delete events and can be browsed by date/year. Removing history does not remove active tasks.
- Specific-time reminders retain local date/time, IANA zone ID, and whether they follow the device zone. Duration countdowns use elapsed realtime during the current boot and retain a wall-clock deadline for reboot recovery.
- Specific-time alarms are one-shot and consumed after successful notification delivery. Duration timers may issue hourly progress notifications and a deadline notification with a reschedule action. Missed specific-time reminders are summarized on recovery.
- Exact alarms are attempted when permitted; inexact allow-while-idle alarms are the fallback. Android notification permission and system/battery restrictions can affect delivery.

## Dependencies and platform baseline

From [`app/build.gradle.kts`](../app/build.gradle.kts):

- Android Gradle Plugin `8.7.3`; Kotlin Android and Compose plugins `2.0.21`.
- Android `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`; application ID and namespace `com.example.darktodo`.
- Java and Kotlin bytecode target 17; app `versionCode = 1`, `versionName = "1.0"`.
- Runtime libraries: AndroidX Core KTX `1.15.0`, Activity Compose `1.10.0`, Biometric `1.1.0`, Lifecycle Runtime Compose `2.8.7`, Compose BOM `2024.12.01`, Compose UI, UI tooling preview, animation, and Material 3.
- Tests use JUnit `4.13.2`; Compose UI tooling is a debug dependency.
- Gradle distribution is `8.9` from `gradle/wrapper/gradle-wrapper.properties`.
- There is no WorkManager dependency, Room/database library, billing library, networking library, or DI framework in the current dependency list.

The manifest registers one exported launcher Activity; three exported widget providers; internal Calendar-midnight, reminder, and widget-refresh receivers; an exported reminder-recovery receiver for system broadcasts; and the non-exported `TodoWidgetService` protected by `BIND_REMOTEVIEWS`, and the non-exported translucent `TaskActionActivity`. Declared permissions are `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `INTERNET`, and `ACCESS_NETWORK_STATE`; the application class is `ClarityApplication`.

## Build and CI/CD process

### Local build (optional)

No local development tools are needed when using GitHub Actions: the workflow provisions Temurin JDK 17, Gradle 8.9, and Android SDK platform/build tools 35 on its runner. In particular, a local JDK installation is not required to trigger a workflow or download its APK.

For an optional local build, use JDK 17 plus Gradle 8.9 and Android SDK platform/build tools 35. The repository contains wrapper distribution configuration but does not check in `gradlew`, `gradlew.bat`, or wrapper JAR files, so generate wrapper scripts before running the same verification/build tasks as CI:

```powershell
gradle wrapper --gradle-version 8.9
.\gradlew.bat --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`. If persistent signing is configured, Gradle reads `keystore.properties` or the `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` environment variables. Partial persistent-signing configuration fails the build. These are local-build settings only; GitHub Actions configures its Java/toolchain paths on the runner.

### GitHub Actions

[`android-apk.yml`](../.github/workflows/android-apk.yml) runs on pushes and pull requests targeting `main`, and on manual dispatch. It uses Ubuntu, Java 17, Android platform/build tools 35, and Gradle 8.9; generates the wrapper; runs `testDebugUnitTest` and `assembleDebug`; copies the APK to `dark_todo.apk`; and uploads `dark-todo-debug-apk` for 14 days.

Push and manual builds require all four signing secrets documented in README. Fork pull requests can build without those secrets and use the default debug signing key; those builds are not update-compatible with the persistent-key APK. This workflow builds a debug APK for testing/direct installation, not a Play Store release.

Drive backup setup (verified on a device 2026-10-17): the four `ANDROID_KEYSTORE_*` secrets hold a persistent keystore kept outside the repo (back it up; never commit it). Google Cloud project has Drive API enabled, an External consent screen in Testing mode with the `drive.appdata` scope and test users, and an Android OAuth client bound to `com.example.darktodo` plus the keystore SHA-1. Connecting and automatic backup were confirmed working. In Testing mode, sign-in may expire after about 7 days and only listed test users can connect. Restore on a fresh install is not yet confirmed on a device.
## Current features

- **Tasks:** Add, edit, complete/reopen, delete with confirmation, clear completed, and reorder tasks within incomplete/completed groups. Tasks have a date and optional reminder; list can be filtered by calendar date. Completion/order is reflected in the To-Do widget.
- **History:** Local activity log, date/year navigation, individual event deletion and scoped clearing, and clear-all confirmation.
- **Reminders:** No reminder, selected-time reminder with searchable time zones, and duration countdown. Task detail edits validate names, future times, zones, and non-zero duration. Notification permission is requested on Android 13+.
- **Calendar:** In-app month calendar and independently configurable home-screen month calendar with task completion-status dots and date-to-task navigation.
- **To-Do widget:** Count, all-task scrollable collection, check/tap completion, task ordering, refresh and add actions, countdown labels, and appearance preview in settings.
- **Vault:** Create/unlock a password-encrypted vault; add/search/edit/reorder/delete saved credentials; reveal/hide passwords; explicitly lock; optionally use strong biometrics as primary authentication with password fallback. Each Show, Edit, and Delete action now requires fresh biometric or master-password verification; the screen clears its in-memory vault state before requesting verification and remains locked if verification fails, is canceled, or expires after 60 seconds. Password tiles use compact cards with Show/Edit/Delete controls beneath their details; entries are reordered by long-pressing a card and dragging it (haptic feedback, edge auto-scroll, one encrypted save on drop; disabled while a search is active). TalkBack exposes "Move up"/"Move down" custom actions. The encrypted file is under `noBackupFilesDir`; the screen sets `FLAG_SECURE`. The implementation derives a 256-bit key with PBKDF2-HMAC-SHA256 (210,000 iterations) and uses AES-GCM; biometric access wraps the master password with an Android Keystore key.
- **Navigation (Phase 1, not yet compiled/device-tested):** A floating, horizontally scrollable `LazyRow` tab bar ([`ClarityUi.kt`](../app/src/main/java/com/example/darktodo/ui/ClarityUi.kt)) replaces the three-button row and header gear. Modules are registered in [`ModuleNavigation.kt`](../app/src/main/java/com/example/darktodo/navigation/ModuleNavigation.kt): Tasks, History, Calendar, Vault, Quotes, Stickers, Wallpapers, Settings (Quotes and Wallpapers are "coming soon" placeholders only). Swiping the content area horizontally moves to the neighbouring module; the last module is saved (`ModulePrefs`) and restored at launch (default Calendar, as before). Back handling: History drill-down → module screen → Tasks (home) → "Press back again to exit" (2 s window). Tabs map onto the existing `page`/`todoPage` state in `TodoApp`; no screens were removed.
- **Icons:** Material Icons Outlined (`material-icons-extended`, the Compose equivalent of Material Symbols Outlined) via `ClarityIcons`; white by default, accent only for the selected tab. The older canvas `ClarityIcon` still draws the per-widget settings buttons.
- **Stickers:** Grid of colored notes (Yellow/Blue/Green/Pink/Purple) with create, edit, delete, pin/unpin. Stored in the `sticker_store` SharedPreferences file ([`StickerStore.kt`](../app/src/main/java/com/example/darktodo/data/StickerStore.kt)); pinned first, then newest. Not included in Google Drive backup yet. No reminders, fonts, categories or widget yet.
- **Support Clarity / About:** Settings → Support Clarity is a UI-only preview (₹50/100/250/500, custom amount, optional-donation message); no payment integration. Settings is grouped into Personalization, Privacy & Security, Data, Support Clarity and About with icon headers.
- **Theme note:** the existing tokens (background `#0D0D10`, accent `#F20D24`) were kept rather than the `#121212`/`#6366F1` values in the Phase 1 brief, so as not to restyle the whole app.
- **Global settings:** App typography selection (font family/import), Widget Customization (per-widget wallpaper editor and reset), and Permissions. The former "Appearance" and "Widget preferences" placeholder sections were removed. Per-widget color, size, font, and dot customization remains on the To-Do and Calendar settings pages.
- **Backup & Restore (Google Drive appDataFolder):** Settings > Backup & Restore shows the account, status and last backup, with Backup now, Restore, Delete backup and Disconnect. Snapshot = preference files `todo_store` (tasks, history, To-Do widget settings), `calendar_widget_settings`, `app_typography`, `widget_backgrounds`, `vault_auth` (auto-lock), plus the encrypted vault file and the two wallpaper JPEGs, in one `clarity-backup.json`. Automatic backup runs (debounced, only on network) after any change to those prefs, vault saves and wallpaper changes, but only after the user enabled it: connecting with no existing backup, Backup now, or a restore enable it; "Start Fresh" and connecting when a backup already exists leave it paused so the cloud copy is never overwritten silently. First clean launch shows a Welcome screen (look for backup, then "Welcome Back" Restore Backup / Start Fresh; Start Fresh keeps the cloud backup). Restore forces password unlock and shows "Vault Restored Successfully".
- **Vault Security settings:** Settings → Vault Security ([`VaultSecurityScreen.kt`](../app/src/main/java/com/example/darktodo/VaultSecurityScreen.kt)) offers change-master-password (`LocalVaultStore.changeMasterPassword` re-encrypts with a fresh salt and turns biometric unlock off because that wrapper held the old password), biometric status with a turn-off action (enabling stays on the Vault screen; "Face" works only if the device offers strong face auth), an auto-lock timer (1/2/5/15 min or never, default 5 min; an idle timer in `VaultScreen`, and leaving the app still locks immediately), and a security audit ([`VaultAudit.kt`](../app/src/main/java/com/example/darktodo/data/VaultAudit.kt): weak, reused and missing passwords by title, after re-entering the master password). Not yet compiled or device-tested.
- **Widget wallpapers:** The To-Do and Calendar widgets each accept an optional wallpaper ([`WidgetBackgrounds.kt`](../app/src/main/java/com/example/darktodo/data/WidgetBackgrounds.kt), editor in [`WidgetBackgroundEditor.kt`](../app/src/main/java/com/example/darktodo/WidgetBackgroundEditor.kt)). Sources: gallery, device wallpaper (Android may deny reading it; gallery is the fallback), or four generated red/black backgrounds. The editor supports pinch-zoom, drag, 90° rotation, fit, and a live preview. The edited image is a private JPEG in `filesDir`; the black fade overlay (presets 25/40/60/80% or a 0-100% slider, 0% = original) is applied at widget render time. With a wallpaper, the To-Do task card is semi-transparent and rows have no opaque background.
- **Fonts:** Five system families are supported directly by widget layouts. User-picked `.ttf`, `.otf`, and `.ttc` files are validated and copied privately; imported-font rendering in widgets uses bitmaps.
- **Permissions/settings:** Notification and exact-alarm status/help and links to Android settings are available in the app.

## Tests

The current JVM unit tests cover Calendar grid/status/date defaults, countdown edit policy, history date formatting, task countdown formatting/transition boundaries, time-zone catalog/search/DST resolution, and system-font widget-layout mappings. There are no Android instrumentation/UI tests in the inspected tree. Alarm delivery, notification permissions, launcher behavior, biometric authentication, Vault action reauthentication, and actual widget sizing therefore require device/emulator validation beyond these unit tests.

## Known issues and operational constraints

- Alarm delivery may be delayed by Doze/battery restrictions, denied exact-alarm access, force-stop, or denied/disabled notification permission. The app's screen-on refresh listener is Activity-scoped; it cannot be a manifest receiver.
- The widget periodic-refresh limit is platform-controlled (30 minutes); minute-level display changes depend on the app's own alarm/foreground paths and system delivery.
- Backup is implemented but not compiled, device-tested, or connected to a Google Cloud OAuth client yet (see the setup steps in CHANGE_HISTORY 2026-10-17, Phase 4). Backup needs a persistently signed APK (the OAuth client is bound to the signing SHA-1; debug keys differ per CI run). Biometric unlock cannot be restored (device-bound Keystore key); after a restore the user unlocks with the master password and re-enables biometrics. Imported custom fonts are not backed up. Tasks/settings are uploaded unencrypted to the private Drive app folder; only the vault file is encrypted. "Disconnect" stops backup locally but does not revoke Google's grant. First-launch backup detection needs a Google sign-in first, so the Welcome screen asks before looking.
- The repository snapshot has no checked-in Gradle wrapper scripts/JAR; a local build must generate them or use Android Studio/installed Gradle.
- The repository has a Nunito license notice but no bundled Nunito font binaries or Nunito weight/italic controls. Current app/widget typography uses system font families and optionally imported local fonts. The README and blueprint now describe the implemented behavior.
- The feature/pricing plan is aspirational, but the local encrypted Vault and locked widget are already implemented. The plan now identifies them as existing foundation and reserves future work for security/recovery review.
- The app's Android package/project identifiers retain `com.example.darktodo` / `DarkTodoWidget` despite user-facing Clarity branding.
- The existing test suite does not exercise Android component lifecycles, WorkManager (not used), alarm/notification delivery, widget launcher rendering, or vault crypto/UI end to end.
- The Password Vault's Show/Edit/Delete reauthentication and compact-card behavior have no automated UI coverage yet; verify biometric success/failure/cancel/timeout and master-password fallback on a device or emulator when available.

## Future improvements

The feature/pricing plan is a proposal, not a commitment. Ideas recorded there include:

- Keep current To-Do and Calendar behavior stable and improve phone/tablet sizing.
- Add photo widget backgrounds with readable-text contrast and manual override.
- Productivity additions: Today/Upcoming and Agenda widgets, quick-add, filters, categories/priorities/tags/subtasks, recurrence, reminder presets/snooze, and calendar sync.
- Optional lightweight widgets/tools: quotes, facts, calculator, coin toss and other small games; future news/horoscope requires appropriate data rights, sources, refresh strategy, and cost controls.
- Consider DND and emergency-contact shortcuts only after platform permissions, target-SDK behavior, regional requirements, and safe user-confirmed handoffs are addressed.
- Review the Vault threat model: backup now uploads the encrypted vault file; there is still no password recovery (a forgotten master password cannot be reset).
- Reconcile the pricing proposal and roadmap with shipped functionality; validate demand and ongoing third-party costs before introducing subscriptions or lifetime purchases. No Play Billing integration is present.

## Source-of-truth documents

- [`README.md`](../README.md): user build/install instructions and implementation behavior.
- [`APP_BLUEPRINT.txt`](../APP_BLUEPRINT.txt): broad implementation notes, aligned with the current source for widget controls, screen coverage, and fonts.
- [`Clarity_Feature_and_Pricing_Plan.txt`](../Clarity_Feature_and_Pricing_Plan.txt): ideas and business hypotheses; proposed features/prices are not shipped functionality, while the Vault is identified as implemented.
- [`.github/copilot-instructions.md`](../.github/copilot-instructions.md): repository-specific agent workflow.
- [`CHANGE_HISTORY.md`](CHANGE_HISTORY.md): baseline and documented changes.

## Documentation correction (2026-10-17)

- Corrected README and blueprint claims about the To-Do widget: it has no clear-completed control, and tapping a task row/title or checkbox toggles completion.
- Replaced outdated bundled Nunito, variable-weight, and italic-control descriptions with the implemented system-font and user-imported-font behavior.
- Updated branding/navigation descriptions and documented the already-shipped encrypted Vault screen and locked Vault widget, including the local-only/no-backup boundary.
- Updated the feature/pricing plan so it no longer proposes the existing Vault as a future widget/feature; the roadmap now calls for reviewing its threat model and recovery limitations.
- Refreshed this memory's known-issue and source-document notes to reflect those corrections.
