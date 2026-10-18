# Clarity ToDo Repository Map

**Last Verified**: October 2026 | **Root**: `app/src/main/java/com/example/darktodo/`

## Architecture Overview

Single-activity Jetpack Compose app with tab-based modular navigation and external Google Drive backup. Four architectural layers:
- **UI**: Compose-based screens with RemoteViews widgets
- **Data**: SharedPreferences (JSON-backed) + encrypted file store
- **Reminders**: AlarmManager + BroadcastReceivers
- **Backup**: Google Drive REST API + WorkManager background sync

---

## Module Inventory

### Core Entry Points

| Path | Purpose | Key Details |
|------|---------|------------|
| `MainActivity.kt` | Single Activity, hosts Compose navigation | Fragment-based, ~200+ lines |
| `ClarityApplication.kt` | App class, installs BackupScheduler listeners (lines 4-10) | Minimal lifecycle hook |

### Navigation & Module Routing

| Path | Responsibility |
|------|-----------------|
| `navigation/ModuleNavigation.kt` | Tab routing & back stack management (lines 1-80) |
| `navigation/ModulePrefs.kt` | Preferences for module-level settings |

**Module IDs** (lines 18-26): `TASKS`, `HISTORY`, `CALENDAR`, `VAULT`, `QUOTES`, `STICKERS`, `WALLPAPERS`, `SETTINGS`

---

## Data Layer (Persistence & State)

### Task Management
| Path | Persistence | Key Operations |
|------|-----------|-----------------|
| `data/TaskStore.kt` | SharedPreferences `todo_store` / key `active_tasks_v1` (JSON array) | `tasks()`, `addConfigured()`, `history()` |
| `data/TaskTimeFormatter.kt` | — | Boot count tracking, elapsed time calculations, timezone-aware countdown |
| `data/CountdownEditPolicy.kt` | — | Policies for editing countdown deadlines |

**Task data structure** (line 14): id, title, completed, reminderType (`"none"`, `"time"`, `"duration"`), reminderAt, deadlineAt, countdownStartedAt, countdownDurationMs, countdownBootCount, localDateTime, timeZoneId, followsDeviceTimeZone, taskDate.

**History append-only log** (line 21): Separate `task_history_v1` key, TaskEvent = {id, taskId, title, type, at}.

### Notes/Stickers
| Path | Persistence | Key Operations |
|------|-----------|-----------------|
| `data/StickerStore.kt` | SharedPreferences `sticker_store` / key `stickers_v1` (JSON array) | `load()`, `add()`, `edit()`, `duplicate()`, `delete()` |
| `data/StickerOps.kt` | — | Pure list operations, JSON encode/decode (lines 35-82) |
| `data/StickerStyle.kt` | Per-note styling (background, text color, autoContrast, glow, textShadow) | `StickerStyle.resolve()`, contrast validation |
| `data/StickerColor.kt` | 5 predefined colors (Yellow, Blue, Green, Pink, Purple) | — |

**Sticker data** (line 24-32): id, title, content, color, createdAt, updatedAt, style.

### Vault (Encrypted)
| Path | Persistence | Key Details |
|------|-----------|-------------|
| `data/LocalVaultStore.kt` | Encrypted file `clarity-vault.bin` in `noBackupFilesDir` (never synced) | PBKDF2-HMAC with 210k iterations, AES-256-GCM |
| `data/LocalVaultStore.kt` (Biometric) | Biometric key in Android Keystore + wrapper in `clarity-vault-biometric.bin` | `prepareBiometricUnlock()`, `saveBiometricWrapper()` (lines 47-60) |
| `data/VaultAudit.kt` | SharedPreferences `vault_auth` / key `primary` | Tracks auth mode (password/biometric) and unlock attempts |

**Vault entry**: {id, title, username, password, notes}.

### Widget & Appearance Settings
| Path | Persistence | Scope |
|------|-----------|-------|
| `data/WidgetPrefs.kt` | SharedPreferences `calendar_widget_settings` | Widget layout, text size, colors |
| `data/WidgetBackgrounds.kt` | File store for TODO/CALENDAR/STICKER backgrounds | Per-widget wallpaper images |
| `data/AppTypographySettings.kt` | SharedPreferences `app_typography` | Font family, size for main app UI |
| `data/LocalWidgetFonts.kt` | Font file cache | System and custom fonts for widgets |
| `data/WidgetTypography.kt` | Widget-specific typography | Separate from main app |
| `data/NoteAppearance.kt` | — | Note styling defaults |

### Time & Calendar Logic
| Path | Purpose |
|------|---------|
| `data/TimeZoneCatalog.kt` | Timezone resolution and LocalDateTime conversion |
| `data/TaskTimeFormatter.kt` | Boot count, elapsed uptime, countdown remaining time |
| `data/CalendarDates.kt` | Date range logic, calendar cell selection |
| `data/CalendarWidgetPrefs.kt` | Calendar widget configuration |
| `data/HistoryDateFormatter.kt` | Event history date display formatting |

---

## Backup & Sync

### Backup Management
| Path | Scope | Authentication |
|------|-------|-----------------|
| `backup/BackupManager.kt` | Orchestrates snapshot, upload, restore | OAuth2 (Google Identity, `DRIVE_SCOPE` = `drive.appdata` only) |
| `backup/BackupCodec.kt` | JSON encoding/decoding for backup payloads | Version-aware marshalling |
| `backup/BackupScheduler.kt` | Debounced background sync via WorkManager | Triggers on pref changes, 20s delay, exponential backoff |
| `backup/DriveClient.kt` | Minimal Drive v3 REST API wrapper | Blocking, off-main-thread (lines 1-73) |

**Backed-up files** (BackupManager line 27): `todo_store`, `calendar_widget_settings`, `app_typography`, `widget_backgrounds`, `vault_auth`, `sticker_store`.

**Blob files** (BackupManager lines 75-80): `vault` (encrypted), `wallpaper_todo`, `wallpaper_calendar`.

**Auth Token**: Silent renewal via `BackupManager.silentToken()` (line 58-61), throws `AuthExpiredException` on 401.

---

## Reminders & Scheduling

| Path | Mechanism | Key Details |
|------|-----------|------------|
| `reminders/ReminderScheduler.kt` | AlarmManager (exact or inexact based on API/permission) | Supports "time" (absolute) and "duration" (countdown) reminders |
| `reminders/ReminderTiming.kt` | Timing logic for countdown and time-based alarms | Boot count aware |
| `reminders/ReminderReceiver.kt` | BroadcastReceiver for alarm fire | Posts notification |
| `reminders/ReminderRecoveryReceiver.kt` | Recovers alarms on boot/timezone/date changes | Exported, responds to `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` |

**Notification channel**: `task_reminders_v2` (HIGH importance, replaces legacy `task_reminders`).

---

## Widgets (RemoteViews)

| Widget | Receiver | Service/Provider | Configurator |
|--------|----------|------------------|--------------|
| **Todo** | `TodoWidgetReceiver` | `TodoWidgetService` (RemoteViews adapter) | — (no config) |
| **Calendar** | `CalendarWidgetReceiver` | — | — |
| **Vault** | `VaultWidgetReceiver` | — | — |
| **Notes/Stickers** | `StickerWidgetReceiver` | — | `StickerWidgetConfigActivity` |

**Refresh coordination** (manifest lines 43, 55): `CalendarMidnightReceiver`, `WidgetRefreshReceiver`.

**Widget font layouts**: `widget/WidgetFontLayouts.kt` (platform-dependent rendering).

---

## UI/Screen Navigation

| Path | Route(s) | Feature |
|------|----------|---------|
| `ui/ClarityScreens.kt` | Route definitions for Compose navigation | — |
| `ui/ClarityUi.kt` | Core Compose component library | ~10KB |
| `ui/NotesScreen.kt` | Sticker/Note list and editing UI | — |
| `ui/NoteSettings.kt` | Per-note appearance editor | ~24KB, color picker, style options |
| `ui/ColorPickerDialog.kt` | Color selection dialog | — |
| `VaultSecurityScreen.kt` | Vault auth mode (password/biometric) configuration | — |
| `WidgetBackgroundEditor.kt` | Wallpaper picker for widgets | — |

---

## Test Coverage

**Unit Tests** (verify logic, no Android framework):
- `test/java/data/`: StickerOps, TaskTimeFormatter, CalendarDates, CountdownEditPolicy, HistoryDateFormatter, NoteContrast, VaultAudit
- `test/java/navigation/`: ModuleNavigation
- `test/java/reminders/`: ReminderTiming
- `test/java/widget/`: WidgetFontLayouts, WidgetResourceContract

**Identified Gaps** (no explicit test coverage):
- `BackupManager` snapshot/restore round-trip
- `BackupScheduler` debouncing behavior
- `DriveClient` error handling (403, 401, network)
- `ReminderScheduler` alarm scheduling & recovery flow
- `LocalVaultStore` encryption/decryption with various auth modes
- Widget update coordination across data changes

---

## External Dependencies & Boundaries

### Google APIs
- **Authentication**: `com.google.android.gms:play-services-auth` (Identity API, OAuth consent)
- **Drive Access**: Direct HTTP/REST (lines 13-14, `backup/DriveClient.kt`), limited to `appDataFolder`

### Android System Services
- **SharedPreferences**: Primary state store (6 files, see Data Layer)
- **AlarmManager**: Scheduled reminders
- **WorkManager**: Background backup sync
- **BiometricManager**: Vault authentication
- **Android Keystore**: Biometric key storage
- **AppWidgetManager**: Widget updates and configuration

### Manifest Permissions (AndroidManifest.xml)
- `INTERNET`, `ACCESS_NETWORK_STATE` (backup)
- `POST_NOTIFICATIONS` (reminders)
- `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM` (reminders recovery)

---

## Data Flow Summary

### Task Creation to Reminder
1. User creates task → `TaskStore.addConfigured()` writes to `todo_store` (SharedPreferences)
2. `ReminderScheduler.schedule()` called → `AlarmManager` schedules exact or inexact alarm
3. On alarm fire → `ReminderReceiver` posts notification, updates widget
4. `BackupScheduler` detects pref change → queues WorkManager job (20s delay)
5. `BackupWorker.doWork()` → `BackupManager.backupNow()` → snapshot to `clarity-backup.json` → upload via `DriveClient`

### Note Creation to Widget Display
1. User creates note → `StickerStore.add()` → `StickerStore.save()` writes to `sticker_store` (SharedPreferences)
2. `StickerWidget.updateAll()` called → `AppWidgetManager.notifyAppWidgetViewDataChanged()`
3. Launcher queries widget via RemoteViews adapter

### Vault Unlock to Access
1. User enters password or triggers biometric → `LocalVaultStore.unlock()` decrypts file
2. `VaultAudit` logs unlock attempt
3. Auto-lock timer runs; on timeout, key cleared from memory

---

## Important Caveats & Implementation Notes

1. **Backup is NOT encrypted over the wire**: Only the vault file itself is encrypted; all prefs (including partial vault keys if stored) are plaintext in backup. Relies on Google Drive's HTTPS + OAuth2 auth.

2. **Vault key is never backed up**: Keystore biometric key stays local; backup only syncs vault file (encrypted) and `vault_auth` mode preference.

3. **Countdown resume on device reboot**: Boot count (`TaskTimeFormatter.bootCount()`) used to detect reboot; elapsed time recalculated (lines 55-60, `reminders/ReminderScheduler.kt`).

4. **Calendar widget updates lazily**: Only triggers if date or completion status changed (lines 68-69, `data/TaskStore.kt`).

5. **Sticker/Note migration**: One-time migration from global style to per-note style (lines 103-117, `data/StickerStore.kt`). Unknown stored fields ignored (backward compat).

6. **SharedPreferences reads are cached**: `TaskStore` maintains `cachedRaw` + `cachedTasks` to avoid repeated JSON parsing (lines 24-25, 28-50).

7. **RemoteViews adapter URI includes font/wallpaper state**: `TodoWidgetService` URI encodes `fontFileId`, wallpaper presence to invalidate cached rows (line 53, `widget/TodoWidget.kt`).

8. **No explicit database (Room/SQLite)**: All persistence via SharedPreferences JSON or direct file I/O.

---

## File Structure at a Glance

```
app/src/main/java/com/example/darktodo/
├── MainActivity.kt                    [~130KB - Compose screens & logic]
├── ClarityApplication.kt              [12 lines]
├── VaultSecurityScreen.kt
├── WidgetBackgroundEditor.kt
│
├── navigation/
│   ├── ModuleNavigation.kt            [80 lines - routing & tab management]
│   └── ModulePrefs.kt
│
├── data/                              [~19 files, state & logic]
│   ├── TaskStore.kt                   [~250 lines - active tasks + history]
│   ├── TaskTimeFormatter.kt
│   ├── StickerStore.kt                [~200 lines - notes + migration]
│   ├── StickerOps.kt                  [~50 lines - pure list ops]
│   ├── LocalVaultStore.kt             [~180 lines - encrypted persistence]
│   ├── TimeZoneCatalog.kt
│   ├── CalendarDates.kt
│   ├── CountdownEditPolicy.kt
│   ├── WidgetPrefs.kt
│   ├── WidgetBackgrounds.kt
│   ├── AppTypographySettings.kt
│   └── [LocalWidgetFonts, NoteAppearance, VaultAudit, HistoryDateFormatter, etc.]
│
├── backup/                            [359 lines total]
│   ├── BackupManager.kt               [182 lines - snapshot, upload, restore, auth]
│   ├── BackupScheduler.kt             [60 lines - WorkManager debouncing]
│   ├── DriveClient.kt                 [73 lines - HTTP/REST API wrapper]
│   └── BackupCodec.kt                 [44 lines - JSON marshalling]
│
├── reminders/
│   ├── ReminderScheduler.kt           [~100 lines - AlarmManager coordination]
│   ├── ReminderTiming.kt
│   ├── ReminderReceiver.kt            [BroadcastReceiver for alarm fire]
│   └── ReminderRecoveryReceiver.kt    [Boot/timezone recovery]
│
├── widget/
│   ├── TodoWidget.kt                  [RemoteViews rendering, ~200 lines]
│   ├── TodoWidgetService.kt           [RemoteViews list adapter]
│   ├── TodoWidgetReceiver.kt          [AppWidgetProvider]
│   ├── CalendarWidget.kt
│   ├── VaultWidget.kt
│   ├── StickerWidget.kt
│   ├── StickerWidgetConfigActivity.kt [Config UI]
│   ├── TaskActionActivity.kt          [Action handling from widget taps]
│   ├── WidgetFontLayouts.kt           [Platform-dependent text rendering]
│   ├── WidgetRefreshScheduler.kt      [Coordination logic]
│   └── [CalendarMidnightReceiver, WidgetRefreshReceiver, etc.]
│
└── ui/
    ├── ClarityScreens.kt              [Route definitions]
    ├── ClarityUi.kt                   [Compose component library]
    ├── NotesScreen.kt
    ├── NoteSettings.kt                [~500 lines - styling editor]
    └── ColorPickerDialog.kt

app/src/test/java/com/example/darktodo/
├── data/
│   ├── StickerOpsTest.kt
│   ├── TaskTimeFormatterTest.kt
│   ├── CalendarDatesTest.kt
│   ├── CountdownEditPolicyTest.kt
│   ├── HistoryDateFormatterTest.kt
│   ├── VaultAuditTest.kt
│   ├── NoteContrastTest.kt
│   ├── StickerStyleTest.kt
│   ├── WidgetCardTransparencyTest.kt
│   ├── CalendarWidgetDefaultsTest.kt
│   └── TimeZoneCatalogTest.kt
├── navigation/
│   └── ModuleNavigationTest.kt
├── reminders/
│   └── ReminderTimingTest.kt
└── widget/
    ├── WidgetFontLayoutsTest.kt
    └── WidgetResourceContractTest.kt

app/src/main/AndroidManifest.xml
├── Activities: MainActivity, TaskActionActivity, StickerWidgetConfigActivity
├── Receivers: TodoWidgetReceiver, CalendarWidgetReceiver, VaultWidgetReceiver,
│              StickerWidgetReceiver, ReminderReceiver, ReminderRecoveryReceiver,
│              CalendarMidnightReceiver, WidgetRefreshReceiver
└── Services: TodoWidgetService
```

---

## Verification Notes

- **Source verified**: Inspected representative files from all modules, authenticated against source at lines cited.
- **No .github/agents accessed** (per constraints).
- **No entire-repo scanning** — focused on feature boundaries and data flow.
- **Existing docs** (`CHANGE_HISTORY.md`, `PROJECT_MEMORY.md`) not assumed authoritative; source code treated as canonical.
