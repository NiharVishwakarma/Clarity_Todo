# Repository map

Navigation aid, not a specification: verify paths and relevant source before editing.
Paths below are repository-relative; directory prefixes are explicitly identified.
Keep this map focused on structure and checks, not change logs or copied source.

## Architecture and entry points

- Native Kotlin Android app: one `:app` Gradle module, Jetpack Compose UI,
  SharedPreferences/local files, AlarmManager reminders, and RemoteViews widgets.
- `app/src/main/AndroidManifest.xml` registers application/activity entry points,
  four widget providers, reminder/recovery receivers, and the task collection service.
- Under `app/src/main/java/com/example/darktodo/`:
  - `MainActivity.kt`: launcher `FragmentActivity`, `TodoApp`, screen routing,
    task/history/calendar/settings/vault UI, permissions, and incoming intents.
  - `ClarityApplication.kt`: installs backup preference listeners at startup.
  - `navigation/ModuleNavigation.kt` and `navigation/ModulePrefs.kt`: module routes,
    back/swipe navigation, and last-module persistence.
  - `ui/ClarityUi.kt`: shared Compose controls, icons, and tab bar;
    `ui/ClarityScreens.kt`: placeholder, support, and about screens.
- Quotes and Wallpapers modules are placeholders; widget wallpaper editing is implemented.
  Support Clarity is a UI preview, not a payment integration.

## Feature → implementation → related tests

Implementation paths in this table are relative to
`app/src/main/java/com/example/darktodo/`.
Test paths are relative to `app/src/test/java/com/example/darktodo/`.
Tests listed are related JVM coverage, not end-to-end guarantees.

| Feature | Main implementation paths | Related tests |
|---|---|---|
| Tasks, ordering, history | `MainActivity.kt`, `data/TaskStore.kt`, `data/HistoryDateFormatter.kt` | `data/HistoryDateFormatterTest.kt`; no store/UI integration tests |
| Calendar/date filtering | `MainActivity.kt`, `data/CalendarDates.kt`, `widget/CalendarWidget.kt`, `data/CalendarWidgetPrefs.kt` | `data/CalendarDatesTest.kt`, `data/CalendarWidgetDefaultsTest.kt` |
| Reminders, countdowns, time zones | `reminders/ReminderScheduler.kt`, `reminders/ReminderTiming.kt`, `data/TaskTimeFormatter.kt`, `data/CountdownEditPolicy.kt`, `data/TimeZoneCatalog.kt` | `reminders/ReminderTimingTest.kt`, `data/TaskTimeFormatterTest.kt`, `data/CountdownEditPolicyTest.kt`, `data/TimeZoneCatalogTest.kt` |
| To-Do widget and task clicks | `widget/TodoWidget.kt`, `widget/TodoWidgetService.kt`, `widget/TaskActionActivity.kt`, `widget/WidgetRefreshScheduler.kt` | `widget/WidgetResourceContractTest.kt`, `data/WidgetCardTransparencyTest.kt` |
| Notes (legacy “Sticker” names) | `ui/NotesScreen.kt`, `ui/NoteSettings.kt`, `data/StickerStore.kt`, `data/StickerStyle.kt`, `data/NoteAppearance.kt` | `data/StickerOpsTest.kt`, `data/StickerStyleTest.kt`, `data/NoteContrastTest.kt` |
| Notes widget selection/rendering | `widget/StickerWidget.kt`, `widget/StickerWidgetConfigActivity.kt` | `widget/WidgetResourceContractTest.kt` |
| Vault, authentication, audit | `MainActivity.kt`, `VaultSecurityScreen.kt`, `data/LocalVaultStore.kt`, `data/VaultAudit.kt`, `widget/VaultWidget.kt` | `data/VaultAuditTest.kt`; no crypto/biometric/UI integration tests |
| Appearance, fonts, backgrounds | `data/WidgetPrefs.kt`, `data/CalendarWidgetPrefs.kt`, `data/AppTypographySettings.kt`, `data/WidgetTypography.kt`, `data/LocalWidgetFonts.kt`, `data/WidgetBackgrounds.kt`, `data/StyleSnapshot.kt`, `WidgetBackgroundEditor.kt`, `ui/ColorPickerDialog.kt`, `widget/WidgetFontLayouts.kt` | `widget/WidgetFontLayoutsTest.kt`, `data/WidgetCardTransparencyTest.kt` |
| Navigation | `navigation/ModuleNavigation.kt`, `navigation/ModulePrefs.kt`, `MainActivity.kt` | `navigation/ModuleNavigationTest.kt` |
| Google Drive backup/restore | `BackupScreens.kt`, `backup/BackupManager.kt`, `backup/BackupCodec.kt`, `backup/BackupScheduler.kt`, `backup/DriveClient.kt` | `backup/BackupCodecTest.kt`; no Drive/restore integration tests |

Widget resources: `app/src/main/res/layout/` (live/preview RemoteViews),
`app/src/main/res/xml/` (provider metadata), `app/src/main/res/drawable/`
(backgrounds/icons), and `app/src/main/res/values/` (strings/themes/dimensions).
Start with the provider's referenced resources rather than scanning all artwork.

## Shared state and data flow

Source paths here use the same implementation prefix as the table.

- `data/TaskStore.kt` owns synchronized task/history JSON persistence in `todo_store`
  (`active_tasks_v1`, `task_history_v1`); To-Do appearance settings share that file.
  Task mutations persist state, coordinate reminder scheduling/cancellation, and
  refresh affected widgets. Inspect this boundary before changing task behavior.
- `data/CalendarWidgetPrefs.kt` owns `calendar_widget_settings`;
  `widget/CalendarWidget.kt` holds per-widget navigation in `calendar_widget_state`.
- `data/StickerStore.kt` owns notes and per-note styles in `sticker_store`.
  Notes retain legacy Sticker identifiers for compatibility; saves refresh the Notes widget.
- `data/AppTypographySettings.kt`, `data/LocalWidgetFonts.kt`, and
  `data/WidgetBackgrounds.kt` own app typography, private imported fonts, and
  private background images/preferences. `data/StyleSnapshot.kt` owns saved styles.
- `data/LocalVaultStore.kt` owns encrypted vault files in Android private storage
  and device-bound biometric wrapping via Android Keystore.
- `ClarityApplication.kt` → `backup/BackupScheduler.kt` installs preference listeners
  and debounces eligible changes into network-constrained WorkManager work.
  `backup/BackupManager.kt` defines the backup preference/file allow-list and restore
  flow; follow it for data inclusion, validation, and post-restore refresh behavior.

## External/platform boundaries

Source paths use the implementation prefix from the feature table.

- `backup/BackupManager.kt`: Google Identity Services authorization (Drive app-data
  and email scopes); `backup/DriveClient.kt`: Drive v3 REST over HTTPS using
  `HttpURLConnection`, restricted to `appDataFolder`. Preferences are not
  application-encrypted in the backup; the vault blob remains encrypted.
  Imported fonts and device-bound biometric keys are not backed up.
- `reminders/ReminderScheduler.kt`: AlarmManager, notifications, and system-event
  recovery receivers; exact-alarm and notification permissions affect delivery.
- `widget/`: AppWidgetProvider/RemoteViews and launcher interactions.
  Gallery/font pickers, wallpaper access, biometrics, and Keystore cross Android APIs.
  These paths need device/emulator verification; no instrumentation test tree exists.

## Build, test, and lint

Run from the repository root. Definitions checked against `app/build.gradle.kts`,
`gradle/wrapper/gradle-wrapper.properties`, and `.github/workflows/android-apk.yml`.
Prerequisites: JDK 17, Gradle 8.9, Android SDK platform 35/build-tools 35.0.0.
Wrapper scripts/JAR are not checked in; CI generates them:

```sh
gradle wrapper --gradle-version 8.9
chmod +x gradlew
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

- The block above is the existing CI test/debug-build command, not a claim of a
  successful build in the current workspace.
- Targeted JVM example (same Android Gradle Plugin test task):
  `gradle --no-daemon :app:testDebugUnitTest --tests 'com.example.darktodo.navigation.ModuleNavigationTest'`
- Android lint: `gradle --no-daemon :app:lintDebug` (standard task supplied by
  `com.android.application`; not currently run by CI).
- No separate documentation lint/test configuration exists. For docs-only changes,
  verify referenced paths and links, keep this map at most 150 lines, and run
  `git diff --check`; no Android build is needed.
- `README.md` covers setup/signing; `docs/PROJECT_MEMORY.md` provides deeper context
  but may contain historical notes. Source/build configuration remains authoritative.
