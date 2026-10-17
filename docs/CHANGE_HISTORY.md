# Change History

This log begins with the repository snapshot available when these project notes were created. The workspace did not expose Git history, so the baseline below is descriptive rather than a claim about the original commit date.

## Initial project baseline

- Native Kotlin Android application with a Jetpack Compose UI, local task/history storage, reminders, and home-screen widgets.
- Android application ID `com.example.darktodo`; Gradle project name `DarkTodoWidget`; displayed app name Clarity.
- Baseline platform configuration: minimum API 26, target/compile SDK 35, Java/Kotlin target 17, app version `1.0` (`versionCode` 1).
- Current app includes Tasks, History, Calendar, Vault, Settings/Permissions, three widgets (To-Do, Calendar, locked Vault), and an encrypted local password vault.
- GitHub Actions builds and tests a debug APK for `main` pushes/PRs and manual runs, then uploads `dark_todo.apk` as a 14-day artifact.
- Detailed architecture, persistence, build, tests, limitations, and roadmap context are in [PROJECT_MEMORY.md](./PROJECT_MEMORY.md).

## Current version summary

### 2026-10-17 — Phase 1: scrollable navigation, icons, Stickers, settings refactor

- Replaced the top navigation with a floating scrollable module tab bar, swipe between modules, remembered last tab, and hierarchical back handling with double-back-to-exit.
- Added Outlined icon system (`material-icons-extended`), Stickers module, Support Clarity placeholder screen, About screen, and Quotes/Wallpapers "coming soon" placeholders.
- Regrouped Settings into Personalization, Privacy & Security, Data, Support Clarity and About; all existing controls kept.
- Added `ModuleNavigationTest` and `StickerOpsTest`. Not compiled locally (no Android SDK); verify via GitHub Actions.

### 2026-10-17 — Improved reminder delivery reliability and diagnostics

- Reminder channel is now `task_reminders_v2` with `IMPORTANCE_HIGH` (old `task_reminders` channel deleted); notifications use high priority and `CATEGORY_REMINDER`.
- `ReminderScheduler.setAlarm` no longer rethrows on registration failure; inexact fallback is logged as a warning.
- Added `ReminderTiming` helper, lateness/zone/battery-optimization/exact-alarm logging in `schedule`/`handle`, and `ReminderTimingTest`.

### 2026-10-17 — Added project context and agent instructions

- Added [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) with incremental-analysis, relevant-file loading, and project-memory/change-log maintenance rules.
- Added [PROJECT_MEMORY.md](./PROJECT_MEMORY.md), documenting the inspected architecture, widgets, components, dependencies, build/CI, features, test coverage, known constraints, and proposed improvements.
- Added this [CHANGE_HISTORY.md](./CHANGE_HISTORY.md) with the source snapshot baseline and current version summary.
- Documentation-only change; application source, resources, build configuration, and version number were not changed.
- The current version remains `1.0` (`versionCode` 1). No Git tag or release history was available in the inspected workspace.

### 2026-10-17 — Corrected existing project documentation

- Updated [README.md](../README.md) and [APP_BLUEPRINT.txt](../APP_BLUEPRINT.txt) to match the implemented Clarity branding, app screens, To-Do widget interactions/controls, supported system/imported fonts, and encrypted local Vault and locked widget.
- Updated [Clarity_Feature_and_Pricing_Plan.txt](../Clarity_Feature_and_Pricing_Plan.txt) to classify the Vault as existing functionality rather than a future proposal, retaining security and recovery review as possible future work.
- Updated [PROJECT_MEMORY.md](./PROJECT_MEMORY.md) with the corrections and current documentation status.
- Documentation-only change; application source, resources, build configuration, dependencies, and app version were not changed.

### 2026-10-17 — Clarified optional local Java setup

- Updated [README.md](../README.md) and [PROJECT_MEMORY.md](./PROJECT_MEMORY.md) to state that GitHub Actions provisions Temurin JDK 17, Gradle 8.9, and Android SDK tools, so no local Java/Gradle/Android Studio installation is needed to use CI.
- Documented JDK 17, Gradle 8.9, and Android SDK requirements only for optional local builds; no machine-specific Java paths were added to the project.
- Documentation-only repository change; application source, build configuration, dependencies, and app version were not changed.

### 2026-10-17 — Secured Password Vault actions

- Compacted Password Vault entry cards while retaining the existing surface colors and styling; moved Show/Edit/Delete into a horizontal row beneath the saved details and kept Move up/Move down for list reordering.
- Added fresh Primary Unlock verification before Show, Edit, and Delete. Fingerprint is offered first when configured and available, with master-password verification as the fallback.
- Sensitive actions now lock and clear in-memory vault/form state while authentication is pending. Failed, canceled, timed-out, or stale authentication does not execute the action and leaves the vault locked with an authentication-required message.
- Updated [PROJECT_MEMORY.md](./PROJECT_MEMORY.md) with the new Vault behavior and the remaining device/UI-test coverage gap.
- No dependencies, persistence format, or app version were changed. Local Gradle/Android build tooling is unavailable in this workspace, so compilation and automated tests were not run.

### 2026-10-17 — Drag-to-reorder Password Vault entries

- Replaced the Move up/Move down text buttons with long-press-and-drag reordering on each entry card, including haptic feedback and auto-scroll near the list edges.
- Reordering now happens in memory and the vault is encrypted and saved once when the card is dropped, off the main thread; previously every click re-ran the 210,000-iteration key derivation, which made moves feel slow.
- Dragging is disabled while a search filter is active. Screen readers get "Move up"/"Move down" custom actions on each card.
- Updated [PROJECT_MEMORY.md](./PROJECT_MEMORY.md). Not yet compiled or tested on a device; verify through GitHub Actions and manual drag testing.

### 2026-10-17 � To-Do widget long-title wrapping

- Task titles in all seven row layouts now wrap to two lines (ellipsis only afterwards); imported-font rows render a word-wrapped two-line bitmap via `LocalWidgetFonts.renderText(maxLines)`. No marquee; colors and fonts unchanged.
- Widget row clicks now go through a new translucent `TaskActionActivity` (click template changed from a broadcast to an activity to avoid background-start limits). It toggles tasks as before, and for titles longer than two lines shows a dialog with the full title.
- Updated [PROJECT_MEMORY.md](./PROJECT_MEMORY.md). The optional fade-out effect was not implemented (RemoteViews cannot do it). Not compiled or device-tested; verify via GitHub Actions.

### 2026-10-17 � Calendar status-dot investigation

- Reviewed `CalendarDates.status`, `CalendarWidget` dot rendering, `TaskStore` refresh paths, and date derivation. The rules (none / all complete = green / none complete = red / otherwise orange) were already implemented correctly, filtered by each task's stored `taskDate` (local date, no time-zone conversion), and every `TaskStore` save refreshes the Calendar widget; no production code change was needed.
- The To-Do widget lists tasks of every date, while a calendar dot counts only tasks whose `taskDate` equals that day. The screenshot (green dot on the 16th, red on the 17th) is consistent with the completed task being dated the 16th; edit its date to the 17th to get orange.
- Added `CalendarDatesTest.statusDotMatchesCompletionRatios` covering the 1/1, 5/5, 0/1, 0/5, 1/2, 3/5, 7/10, and empty cases. Not run locally; CI runs it.

### 2026-10-17 � Dedicated Vault widget icon

- Added a new vector icon `ic_vault_door` (black circular vault door, metallic rim, bolts, red glowing locking ring, wheel spokes and keyhole hub, subtle glass highlight) and used it in the Vault widget instead of the small padlock; it fills roughly 60-70% of a 1-row widget's height, beside "Clarity Vault" / "Vault locked � Tap to unlock".
- Added `previewImage` (`vault_widget_preview`) and `previewLayout` to `vault_widget_info.xml` so the widget picker no longer falls back to the launcher icon.
- `ic_vault_lock` is no longer used by the widget. Not compiled or visually verified on a device; check the icon proportions after the CI build.

### 2026-10-17 � Upgrade phase 1: Vault widget width and Settings cleanup

- Vault widget provider now requests the same 4-cell width as the To-Do widget (`minWidth` 300dp, `minResizeWidth` 250dp, `minHeight` 80dp).
- Removed the placeholder "Appearance" and "Widget preferences" sections from global Settings; Typography and Permissions remain.
- Not yet implemented from the consolidated upgrade request: cloud backup/restore (Google Drive appDataFolder), restore/start-fresh onboarding, automatic sync, Vault Security settings (change master password, auto-lock, security audit), and widget wallpaper/fade controls. Planned order: wallpaper and fade controls, then backup and restore. Design constraint: the vault file lives in `noBackupFilesDir` and the biometric key is Keystore-bound, so after a restore biometrics must be re-enabled after one master-password unlock.
- Not compiled or device-tested.

### 2026-10-17 � Upgrade phase 2: widget wallpapers and fade controls

- Added `WidgetBackgrounds` (storage, fade, rounded/faded bitmap rendering, generated bundled backgrounds) and the `WidgetBackgroundEditor` Compose screen (gallery / device wallpaper / bundled source, pinch-zoom, drag, rotate, fit, live fade preview, intensity presets and 0-100% slider, save/remove).
- Global Settings gained a "Widget customization" section: To-Do wallpaper, Calendar wallpaper, and a confirmed reset of both.
- `TodoWidget` and `CalendarWidget` render the wallpaper into their background ImageViews (tint filter cleared); the To-Do task card is semi-transparent and its rows transparent while a wallpaper is set. The To-Do list adapter URI now includes the wallpaper state.
- Reading the device wallpaper may be denied by Android on newer versions; the editor then tells the user to pick it from the gallery. Not compiled or device-tested; verify in CI and on a device.

## 2026-10-17 - Vault Security settings (Phase 3)

- Added Settings > Vault Security (VaultSecurityScreen.kt): change master password, biometric status/turn-off, auto-lock timer, security audit.
- LocalVaultStore: changeMasterPassword, autoLockSeconds/setAutoLockSeconds. New VaultAudit.kt with VaultAuditTest.
- VaultScreen: idle auto-lock timer (pointer activity based).
- Fixed a malformed page-routing branch in MainActivity.kt left by the wallpaper phase (WidgetBackground/Permissions).
- Not yet compiled; CI is the first build.

## 2026-10-17 - Google Drive backup and restore (Phase 4)

- Added backup/ (BackupManager, DriveClient, BackupCodec, BackupScheduler + BackupWorker), ClarityApplication, BackupScreens.kt (Settings > Backup & Restore, Welcome Back gate). Dependencies: androidx.work:work-runtime 2.9.1, play-services-auth 21.2.0 (test: org.json). Manifest: INTERNET, ACCESS_NETWORK_STATE, application class.
- Backs up tasks, history, widget/calendar/app settings, vault auth prefs (auto-lock), wallpapers, and the already-encrypted vault file. Keys are never uploaded; biometric unlock is reset on restore.
- Auto backup after changes (WorkManager, network required, 20 s debounce); stays off after Start Fresh so the cloud copy is not overwritten.
- Vault intro text updated (no longer says excluded from backup). Added BackupCodecTest. Not compiled yet.
- Required setup (Google Cloud Console): create a project, enable Google Drive API, configure the OAuth consent screen (add yourself as a test user), create an OAuth client ID of type Android with package com.example.darktodo and the SHA-1 of the signing key. Use a persistent keystore (ANDROID_KEYSTORE_* secrets) so the SHA-1 stays stable. No client ID is placed in code.

## 2026-10-17 - CI compile fixes

- First CI build failed in :app:compileDebugKotlin. Fixed a missing line break in CalendarWidget.kt that merged the wallpaper block with the heading setText call, and added the missing androidx.compose.ui.graphics.nativeCanvas import in WidgetBackgroundEditor.kt.

## 2026-10-17 - Drive backup verified on device

- Documented the persistent keystore, GitHub secrets and Google OAuth setup (no client ID recorded). Connect Google account and automatic backup were confirmed on a device; restore is still untested.
