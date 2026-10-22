# Change History

## 2026-10-22 - Fix Compose compilation for Notes and Quotes

- Removed invalid explicit imports of the internal Compose `weight` property and opted the Notes screen into the experimental `combinedClickable` API used by its existing note cards.
- CI reported these Kotlin compilation errors; local Gradle validation remains unavailable.

## 2026-10-22 - Add random quotes mode to the Notes widget

- Extended the existing Notes widget setup to offer a configured note or random local quotes. Quote mode reuses the widget renderer and selected Notes topics, avoids recently shown quotes, and shows another quote when tapped; existing note-mode click behavior is preserved.
- Added a private action receiver and widget contract coverage. No additional widget provider or network dependency was introduced. Android build and launcher validation remain pending.

## 2026-10-22 - Add local Quotes section under Notes

- Added a separately collapsible Quotes section beneath the existing Notes list, with persisted expansion/topic/custom-interest state, a bundled locally attributed collection, recent-quote avoidance, Favorites, history browsing, copy and share actions, and a lightweight layered dark presentation.
- Kept existing note storage and widget behavior unchanged; no API requests or new dependencies were added. Categories without licensed bundled matches show a useful empty state. Android build/test validation remains unavailable locally.

## 2026-10-22 - Recover Wallpaper widget after interrupted work

- Removed the receiver's persistent running-flag tap gate, which could remain stuck after process death and prevent later wallpaper requests. The Worker now sets the running state when it actually starts; unique `KEEP` work still prevents duplicate concurrent applies.
- Preserved visible enqueue/apply errors and added a contract check against stale-state tap blocking. Android Gradle/device validation is unavailable in this workspace.

## 2026-10-22 - Fit Clarity artwork within widget icon areas

- Switched the Vault and Wallpaper widget icons, plus the Vault appearance preview, from the adaptive launcher drawable to the unchanged original PNG. The adaptive background filled the PNG's existing transparent edge margins.
- Set explicit FIT_CENTER/ContentScale.Fit scaling and modest additional insets (1dp for the 38dp Wallpaper icon and 2dp for the 62dp Vault icon); kept widget styling and launcher icon resources unchanged.
- Updated widget resource contracts. Local Gradle/Android launcher validation is unavailable in this workspace.

## 2026-10-22 - Fix wallpaper widget contract test

- Corrected the widget contract test to read the action label from the button's Android `text` attribute instead of XML text content, resolving the CI test failure.
- CI is the available Android test/build validation because this workspace has no Gradle executable or wrapper.

## 2026-10-22 - Fix onboarding UI token import

- Fixed the missing `UiTokens` import reported by the main-branch Android CI Kotlin compilation for first-launch permission onboarding.
- Local Gradle verification is unavailable in this workspace; XML/source checks and the GitHub Actions build remain the validation path.

## 2026-10-22 - Add procedural Wallpaper Studio and apply widget

- Replaced the Wallpapers placeholder with an offline Canvas-based generator shared by the Compose Studio and a new RemoteViews widget. Added 12 seeded styles, color/palette/customization controls, local saved PNGs, export and home/lock/both apply actions.
- Added a private widget-action receiver and unique expedited WorkManager work so a deliberate widget tap generates a fresh image and only reports success after WallpaperManager succeeds. Widget refresh does not apply wallpapers. Kept the Worker name/constructor for minified builds and declared SET_WALLPAPER.
- Added first-launch notification explanation/request and a separate exact-alarm settings offer only when existing reminders need it and access is missing. Skips are allowed; reminders retain the existing inexact fallback.
- Added focused model/resource contract tests and updated project memory/repository navigation. Android build/device validation remains pending because the local Gradle executable/wrapper is unavailable.

## 2026-10-21 - Portable backup assets and automatic backup control

- Extended snapshots to version 2 with an explicit size/SHA-256 asset manifest. Added original imported-font registry metadata/files, System Monitor/Vault appearance, System Monitor wallpaper and historical snapshot, saved styles and navigation preferences. Kept version-1 restore support and existing encrypted Vault/Notes/wallpaper coverage.
- Validate font references/headers and Android font parsing, image dimensions and monitor JSON before replacement. Restore writes assets to private managed paths, removes obsolete registered assets, invalidates rendering caches, refreshes all widget types and labels restored monitor values until refreshed. Existing verified cloud/local Safety copies and rollback remain; process death during a multi-store restore can still require manual Safety recovery.
- Added the persistent Automatic backup switch using the existing unique, 20-second debounced, network-constrained WorkManager job. Off cancels queued work, preserves cloud backups and manual operations, and is rechecked by workers before backup/retry. An in-flight upload may finish safely. Explicit Off is not overridden by manual backup/restore; existing paused-before-setup behavior is retained.
- Keystore/biometric keys and wrappers remain non-exportable; users unlock with their original master password and re-enable biometrics. Disposable caches, other apps' private data and external Downloads/SAF exports remain excluded because Clarity has no managed copies. Updated backup help, README, project memory and repository map to match actual packaging and restore paths.
- Validation: 41 focused JVM tests cover typed preference/schema/asset round trips, font IDs/references, legacy compatibility, integrity/rejection cases, retention and scheduling/UI source contracts. This is not an Android build or live WorkManager/Drive test. No local Gradle/Android SDK is available; the existing Actions workflow is unchanged. Device backup/restore, biometric re-enrollment, reboot scheduling and launcher rendering remain unverified.

## 2026-10-21 - Reuse the app's adaptive icon in Vault widgets

- Switched Vault's live widget, picker fallback and Compose appearance preview from the raw PNG to the existing `ic_clarity_suite` adaptive launcher resource. The Compose preview rasterizes that drawable because `painterResource` does not support adaptive icons.
- Kept the 62dp icon area, aspect fit, layout and unlock action unchanged. No app launcher assets, adaptive layers or manifest entries were changed. Updated the resource contract and branding documentation; device rendering remains unverified.

## 2026-10-21 - Apply saved fonts to the live System Monitor widget

- Wired all live monitor lettering to the existing bitmap-text renderer, following Vault's RemoteViews pattern: built-in/imported font family, title/value weight, size and color now reach the launcher, not just the settings preview. Kept native TextViews for initial/picker layout only.
- Added bounded multiline rendering for details, values and the essential summary; original metric containers and bitmap action images retain supported PendingIntents, and text images expose full content descriptions.
- Updated monitor contracts for every text/image pair, font selection/weight wiring and interactive images; updated project memory/map. No dependencies, new font catalog, polling, permissions or preference schema changes. Local XML/source/whitespace checks are separate from CI compilation and physical-launcher font verification.

## 2026-10-21 - Prefer wide System Monitor placement

- Requested a five-column default width (320dp fallback on older launchers), keeping the existing 240dp initial height, three-cell height and 220dp resize minima. Launchers control the actual span; existing instances are not forcibly resized.
- Updated the metadata contract and project memory. XML and whitespace checks are available; no Android build or launcher placement test was performed.

## 2026-10-21 - Wire monitor surfaces and responsive sizing; remove nested launcher tile

- Changed only the adaptive wrapper (90dp centered artwork, opaque charcoal full-bleed backing) to prevent a small rounded-square icon inside the launcher's black-filled mask. Shared PNG/Vault artwork is unchanged; Android may mask the outer tile. Desktop circular/rounded-mask inspection is not an OEM device test.
- Replaced static metric-card drawable fills with shared rendered card ImageViews, applying saved color, opacity and radius. Outer preview/widget rendering now shares one path; wallpaper opacity applies to the composed background once. Choosing a solid color switches off wallpaper without deleting it; a persisted switch restores it. Black is a valid stored color; missing outer color reuses the Calendar default, with 100% opacity. Existing stored choices remain intact.
- Reworked monitor layout into weighted two-column metric rows, full-width OS/security details, separate battery status and a bottom timestamp/action area. Initial height is 240dp, resize minimum is 220dp, default grid is 4x3. Compact/font-scaled layouts link explicitly to omitted OS/security details in Info; very limited scaled height uses a RAM/battery summary with All details and a separate cache action. Applied saved native text sizes, retained Refresh/Downloads/cache confirmation and serialized rendering to prevent older appearance updates overwriting newer ones.
- Extended resource, sizing, color/alpha (0/59/100%) and preference-flow contracts; updated repository map and project memory. Local Gradle debug build is blocked by the missing Gradle runner/wrapper, and the editor test tool discovers no Kotlin tests. XML/reference/diff and desktop asset checks are available; CI and physical-device persistence/resize/action checks remain required.

## 2026-10-21 - Fix monitor RemoteViews targets and icon test compilation

- Fixed invalid text actions on the System Monitor's storage and battery `LinearLayout` click containers. Values now update only their child `TextView`s, preserving the settings actions and displaying charging status with battery percentage.
- Missing Clarity cache measurements show `Unavailable` instead of a fabricated zero. Added explicit widget update/collection error logging without changing the refresh schedule.
- Replaced desktop-only `javax.imageio.ImageIO` in the launcher contract with `DataInputStream` checks of the PNG signature and 1024px RGBA header. This addresses the attached CI's `compileDebugUnitTestKotlin` failure without dependencies. Pixel-level transparency/centering/mask checks remain desktop visual/asset checks, not this header contract.
- Added a monitor contract checking all current RemoteViews text/color targets against layout node types and retaining the clickable containers. Updated project memory and test navigation. Local validation is limited to XML/source/PNG and diff checks; no local Android build or Gradle test execution.

## 2026-10-21 - Clean icon boundary and share official branding with Vault

- Cleaned only the shared 1024px PNG's edge alpha (near-transparent noise and tiny silhouette spurs), retaining interior RGB/alpha, proportions, C emblem, red swoosh and shading. Recentered the silhouette by a two-pixel vertical translation.
- Removed the opaque black/red adaptive-background painting while retaining existing resource names and adaptive-icon wiring. Reduced the centered foreground from 70dp to 60dp after circular-mask simulation exposed corner clipping; the complete artwork now fits the 66dp adaptive safe circle. OEM launcher masks, shadows or backplates remain outside app control; physical-launcher verification is still needed.
- Reused `clarity_suite_icon.png` in the Vault live widget, drawable picker preview and appearance preview, and removed the unreferenced safe illustration. Kept icon areas, aspect fitting, layout, text, styling and unlock behavior unchanged.
- Updated launcher/widget resource contracts, repository map and project memory. Lightweight PNG/XML/reference/diff checks and desktop size/mask visual simulations are used; no local Android build or device-rendering success is claimed.

## 2026-10-21 - Fix Info header compilation import

- Added the missing `androidx.compose.ui.Alignment` import used by the Info header row, fixing the `Unresolved reference 'Alignment'` error reported by GitHub Actions at `:app:compileDebugKotlin`.
- Checked the targeted source diff and whitespace. No local Gradle build was attempted; compilation and subsequent tests require a new CI run. Workflow and existing pending launcher-artwork changes are unchanged.

## 2026-10-21 - Replace launcher source with centered Clarity icon

- Replaced `app/src/main/res/drawable/clarity_suite_icon.png` with the prepared 1024 x 1024 transparent PNG, preserving the supplied artwork's proportions, colors, metallic shading and red swoosh.
- Kept the resource name, adaptive-icon XML, centered 70dp foreground sizing, background and separate Vault widget artwork unchanged. Updated project memory; repository paths and responsibilities are unchanged.
- Verified the replacement matches the prepared PNG by SHA-256, PNG decoding/dimensions/transparency, and the adaptive resource references. No local Android build or device verification was performed.

## 2026-10-21 - System Monitor widget redesign, cache monitoring, and customization

- Redesigned System Monitor home-screen widget to a compact, production-ready dark-surface card layout based on Clarity visual language:
  - Header with prominent title, model/subtitle, and compact Refresh action.
  - 2-column grid layout for RAM, storage, battery, and Clarity app cache.
  - Added cache monitoring with genuine `StorageStatsManager` Clarity UID cache reading and formatting; no fabricated metrics.
  - Added compact "Clear cache ›" action launching `SystemMonitorActionActivity` with explicit user confirmation and clear boundary explanation (deleting only Clarity disposable cacheDir and codeCacheDir without touching user data or other apps' caches).
  - Footer with last update status and compact Downloads navigation.
- Created `SystemMonitorAppearance` and `SystemMonitorAppearancePrefs` for full customization reuse (font family, private imported fonts, sizes, weights, colors, card surface opacity, background opacity, wallpaper image via `WidgetBackgrounds.SYSTEM_MONITOR`, and rounded corners).
- Added `SystemMonitorAppearanceScreen` in Jetpack Compose, wired to Info screen header via `SettingsGearButton` and module navigation back stack.
- Updated `system_monitor_widget.xml`, `system_monitor_widget_preview.xml`, `system_monitor_card_background.xml`, and registered `SystemMonitorActionActivity` in `AndroidManifest.xml`.
- Extended `SystemMonitorWidgetTest` and `ModuleNavigationTest` with regression test cases covering appearance defaults, manifest registration, and navigation routing.
- Documentation updated in `REPO_MAP.md`, `PROJECT_MEMORY.md`, and `CHANGE_HISTORY.md`. Local checks run with git status/diff. No local Gradle build attempted per instructions.

## 2026-10-20 - Settings > Data layout and help

- Reordered Settings > Data (account, Back up now, Backup history, Backup settings, secondary status card, Disconnect) and removed the redundant Restore backup row; restore remains in Backup history. Delete backup history moved into a "More" menu with the existing confirmation (button now reads "Delete all backups").
- Replaced the long explanatory paragraph with a help button beside the Data heading (new optional `trailing` slot on `SettingsCategoryHeader`). Added account, read-only Backup settings and status-details dialogs; the paused-reason text states that the reason is not recorded.
- UI-only: no changes to backup format, encryption, OAuth, scopes, storage, retention or restore logic. Not compiled or device-tested locally; verification is via the GitHub Actions debug workflow.

## 2026-10-20 - Google Drive backup history and selectable restore points

- Replaced single-file overwrites with immutable dated snapshots in the existing private Drive app-data folder, retaining OAuth scopes and version-1 payloads. Added stable reserved IDs, durable retry payloads/operation IDs, paginated discovery, explicit metadata and download/checksum/format verification before success timestamps or retention.
- Added Settings > Data history with local-time dates, manual/automatic/Safety labels, size/version/ID, loading/empty/error/retry states and exact selected-point confirmation. Existing `clarity-backup.json` is discovered non-destructively, with its Drive modification date labelled and unavailable metadata left unknown.
- Retain the latest 10 verified new snapshots, including Safety points; automatically prune only after a successful verified normal upload. Legacy, unverified and unsupported/unknown-format points are preserved. Restore never prunes history, and deferred cleanup explicitly reports that extra copies were kept.
- Validate selected payloads before writes, create a verified cloud Safety point and identical local safety file, use atomic file replacement and attempt rollback on application errors. Reload the app and refresh affected widgets after successful restore. Individual writes are not a process-death-safe multi-store transaction; retained Safety points provide manual recovery. Vault authentication still requires the master password at unlock; secrets/keys are not uploaded. Older snapshots without Notes preserve current Notes, and absent vaults preserve the current vault.
- Added history metadata/order/retention/legacy/checksum tests, strict snapshot validation tests and retry/selected-restore/UI source contracts; updated README, project memory and repository map, including this entry. No dependencies, signing, workflows, font/catalog, settings-collapse, or unrelated module behavior changed.
- Validation: lightweight source-contract/whitespace/diff checks only; added Kotlin/JUnit tests require CI (the editor runner does not discover them). No Android Studio/Gradle/SDK installation or local Android build was attempted. The existing workflow runs unit tests and a debug build on pushes to main, PRs targeting main, or manual dispatch. Actual compilation, Google authorization, upload/retry/pagination, legacy restore, retention deletion and device/widget recovery still require CI/device testing.

## 2026-10-20 — Collapsed settings defaults and Google authorization recovery

- Replaced inconsistent saved/initially-expanded appearance state with shared transient `rememberSettingsSectionExpanded` across all 17 app, To-Do, Calendar, Notes and Vault sections. Recomposition preserves manual toggles; new compositions/activity launches start collapsed without restoring expanded saved-instance values. Wallpaper editors have no collapsible sections. Animations, retained content, preferences and the resolved font catalog/picker remain unchanged.
- Fixed authorization callbacks lost on activity recreation: Settings saves the pending action enum instead of a lambda, Welcome saves only its pending authorization flag, and the activity restores its current page to keep the Settings result launcher available. The account refreshes immediately after authorization; pending actions cannot be double-started. Cancellation, synchronous launcher/client failures, absent resolution intents and blank tokens now produce explicit UI feedback.
- Confirmed the Drive HTTP Bearer header is already correct (tool output redacts it); no Drive client, scopes, encrypted-vault backup, dependencies, signing keys or workflow changes. The earlier build warning establishes that stable debug signing was absent for that run, not that Google Cloud is configured correctly. Added precise package/SHA-1/Drive API/consent/Play-services troubleshooting to README. External OAuth and real connection success remain unverified.
- Added authorization source-contract tests and expanded existing settings contracts. Updated project memory and the repository map, including this entry. Local history has only baseline commit `07550b9`, so upstream regression chronology is unavailable. No Android Studio/tool installation or local Android build was attempted for this change.
- Validation: PowerShell source-contract checks passed for all 17 transient section states, restored pending authorization intent/page, visible failure/cancellation guards, account-update ordering, unchanged font/persistence/Drive/build/workflow files, and exact workflow triggers/tasks. `git diff --check` passed. Added JUnit contracts are for CI; no local Kotlin/JUnit runner is installed, so they were not executed here. Force-stop/relaunch, activity recreation during Google consent, real OAuth/Drive access and Android compilation remain unverified; no Actions success is claimed.

## 2026-10-20 — Deduplicate the shared imported-font catalog

- Traced the shared document-picker import and `widget_fonts/imported_fonts` registry: every import previously generated a new UUID file and appended metadata without comparing content. Pickers replaced their lists rather than accumulating entries, and there is only one private font directory/registry; duplicate copies, not recomposition or repeated directory discovery, caused the observed entries.
- Added SHA-256 identity and serialized import registration in `LocalWidgetFonts`, reusing matching stored assets before promoting a temporary file or adding a registry record. The catalog groups existing duplicate files/records without deleting files, changing saved IDs/settings, or rewriting old metadata; new imports append to the original JSON to retain unknown fields.
- Added cached, bounded OpenType/TTC name reading with full-name or family/style labels and a stored-filename fallback. Distinct contents remain separate regardless of filename or display name; system families and rendering/weight behavior are unchanged.
- Wired legacy-ID aliases through app, To-Do, Calendar and Vault preference validation, shared labels, Notes choices and Vault selection indicators. Existing widget renderers continue loading original selected IDs.
- Added `ImportedFontCatalogTest` and updated `REPO_MAP.md` and `PROJECT_MEMORY.md`, including this history entry.
- Validation: Kotlin 2.0.21/JVM 17 compilation and all 24 focused JUnit tests passed (`ImportedFontCatalogTest`, `VaultAppearanceResourceTest`, `WidgetResourceContractTest`, `SettingsOrganizationContractTest`). Changed-file whitespace/conflict-marker checks passed. The VS Code test runner did not discover Kotlin tests, so they ran directly through Kotlin/JUnit. After restoring missing Gradle 8.9 outside the project, the targeted Android unit-test/`compileDebugKotlin` invocation failed during task dependency setup because no Android SDK location is installed/configured. No Android compilation, `WidgetFontLayoutsTest` execution, picker interaction, emulator restart, or actual widget rendering is claimed; those require an SDK/device. The workspace has no Git metadata, so no Git diff check was available.

## 2026-10-18 — Fix settings-section compilation imports

- Fixed the `:app:compileDebugKotlin` failure on `main` (Android APK Build job 117694452019, commit `aabc90f`) in [`ClarityUi.kt`](../app/src/main/java/com/example/darktodo/ui/ClarityUi.kt): `clipToBounds` now comes from `androidx.compose.ui.draw` instead of the nonexistent `androidx.compose.foundation.clipToBounds`, and the missing `androidx.compose.ui.semantics.role` import was added for `role = Role.Button`. The remaining reported errors around the collapsed-content `Box` were cascades from the unresolved `clipToBounds`.
- No behavior, visuals, semantics, retained-content mechanism, preferences, dependencies or checks changed. Updated the verification note in `PROJECT_MEMORY.md` and added this entry.
- Validation: `git diff --check` passed. Local `gradle wrapper --gradle-version 8.9` (system Gradle 9.8.1) failed during plugin resolution because `dl.google.com` does not resolve in the sandbox (AGP `8.7.3` unavailable), so compilation, unit tests, `assembleDebug` and `lintDebug` could not run locally; the pull-request Android APK Build run is the compile/test check. No device checks were performed.

## 2026-10-18 — Organize appearance settings

- Added a shared outlined, animated settings-section card with a 56dp accessible header, optional wrapping subtitle, decorative accent icon, expanded/collapsed semantics, and saveable expansion state. Section contents remain composed but clipped and absent from accessibility semantics while collapsed; collapse clears focus to dismiss the keyboard without discarding drafts, dialogs, or import launchers.
- Regrouped existing To-Do, Calendar, app-font, per-note, and Vault appearance controls under truthful Fonts, Colors, Background, Layout, and Readability headings. Completion fade, Calendar status dots, Vault corner radius, and reset/default actions remain directly available; no controls or preference keys were added or removed.
- Preserved existing save/refresh paths, note-specific debounced save and close-time flush, Vault immediate versus slider-release writes, font/image handling, and widget scope. Info/System Monitor, Note widget selection, and Vault security remain unchanged.
- Added `SettingsOrganizationContractTest` to guard section composition/accessibility and existing controls/save callbacks; updated project memory and the repository map, including this entry.
- Validation: focused tests were attempted with `gradle --no-daemon :app:testDebugUnitTest --tests 'com.example.darktodo.ui.SettingsOrganizationContractTest'` and Gradle 8.9; the full `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` task set was also attempted with Gradle 8.9. All stopped before compilation because Android Gradle Plugin `8.7.3` could not be resolved from configured repositories. Equivalent source-contract assertions and `git diff --check` passed. `adb` is not installed, so device/UI checks were not performed.

## 2026-10-18 — Info/System Monitor and Vault widget appearance

- Navigation/activity/shared icons: replaced only the active Quotes slot with the plain `Info` tab in the existing shared styling, retained the legacy Quotes route, migrated old saved selection, and added cold/warm Info intent handling plus a separate Vault appearance child route/gear.
- Added `monitor/` models, public-API collection/local snapshots, bounded authorized-document inspection, TXT/CSV/native-PDF reports and confirmed Clarity-only cache maintenance; added the seven-section Compose Info UI with unavailable/partial/error states and system settings shortcuts. No broad permissions, invented battery rankings or OS install dates.
- Added System Monitor RemoteViews widget implementation/state, live/provider/background and API-26 picker-preview resources, manifest registration/narrow handler queries and persisted-worker R8 rule. Its supported periodic scheduling is independent of countdown alarms; actions use immutable PendingIntents, navigation targets activities directly, and failed refreshes retain timestamps/snapshots. Sizing contracts reserve space for controls and optional expanded details.
- Added isolated Vault appearance preferences/settings/tests and updated the Vault live layout/renderer for real font/size/weight/colors/background-only opacity/rounding/reset. Font imports run off-main; sliders preview locally and refresh all widgets when the drag finishes. Uploaded artwork, locked/setup content and Vault authentication/encryption/data are preserved.
- Added focused navigation, metric/format/cache, monitor widget and Vault appearance/resource tests. Build/test/lint status and pending runtime checks are recorded in `PROJECT_MEMORY.md`; unavailable infrastructure is not a passing test result.
- Updated `REPO_MAP.md` for new entry points/data flow/tests, corrected relevant stale WorkManager/navigation facts in `PROJECT_MEMORY.md`, and added this `CHANGE_HISTORY.md` entry covering implementation, tests and these documentation edits. No backup allowlist, permission or dependency changes.

This log begins with the repository snapshot available when these project notes were created. The workspace did not expose Git history, so the baseline below is descriptive rather than a claim about the original commit date.

## Initial project baseline

- Native Kotlin Android application with a Jetpack Compose UI, local task/history storage, reminders, and home-screen widgets.
- Android application ID `com.example.darktodo`; Gradle project name `DarkTodoWidget`; displayed app name Clarity.
- Baseline platform configuration: minimum API 26, target/compile SDK 35, Java/Kotlin target 17, app version `1.0` (`versionCode` 1).
- Current app includes Tasks, History, Calendar, Vault, Settings/Permissions, three widgets (To-Do, Calendar, locked Vault), and an encrypted local password vault.
- GitHub Actions builds and tests a debug APK for `main` pushes/PRs and manual runs, then uploads `dark_todo.apk` as a 14-day artifact.
- Detailed architecture, persistence, build, tests, limitations, and roadmap context are in [PROJECT_MEMORY.md](./PROJECT_MEMORY.md).

## Current version summary

### 2026-10-18 — Use uploaded Vault widget artwork

- Wired the live Vault widget and drawable picker preview to the existing `vault_widget_icon.png`, preserving sizing and locked/setup shortcut behavior without displaying vault contents.
- Extended `WidgetResourceContractTest` to verify the uploaded PNG exists and is nonempty, the live/preview icon references match, and provider metadata selects those resources.
- Updated project memory and the repository map to reflect the artwork and test coverage, including this history entry.
- Validation: all four `WidgetResourceContractTest` tests passed via Kotlin/JUnit; `git diff --check` passed. Gradle targeted tests and `assembleDebug` were blocked before compilation because the sandbox could not resolve Google Maven (`dl.google.com`) to download Android Gradle Plugin 8.7.3. Device rendering remains unverified.

### 2026-10-18 — Use uploaded launcher artwork without a competing variant

- Verified that the manifest's normal and round icons share the adaptive foreground and dark/red background, with API 26 matching the app's minimum SDK. Kept the existing manifest, XML, and uploaded `app/src/main/res/drawable/clarity_suite_icon.png` unchanged.
- Removed the different same-named PNG from `drawable-nodpi`, which could override the uploaded artwork during Android resource selection.
- Added JVM resource-contract tests for the manifest/adaptive/foreground chain, PNG decoding, and a single artwork resource location. Updated the repository map and project memory with the verified wiring and upload location, including this history entry.

### 2026-10-18 — Add lightweight repository navigation

- Added `docs/REPO_MAP.md` with source-verified entry points, feature-to-file/test mappings, shared state, service boundaries, and build/test/lint guidance.
- Updated `.github/copilot-instructions.md` to consult and maintain the map while preserving existing project guidance.
- Recorded these documentation changes, including this history entry; application/runtime code is unchanged.

### 2026-10-18 — Repair Notes picker and refine widget presentation

- Removed the unsupported plain `View` divider from the Notes `previewLayout`; rebuilt the preview as a compact two-line card using only RemoteViews-supported classes. Existing provider, manifest metadata and configuration activity registration were already correct. Earlier label and bitmap-size fixes did not address preview inflation.
- Changed Notes default sizing to a launcher-controlled 4x1 request (`300dp` x `70dp`) with horizontal/vertical resizing. Compact live notes use one title/body line with bounded spacing; note selection, editing, style, contrast and private background images stay on the existing store/rendering path. Empty widgets now route to Notes, and warm note intents refresh the activity route.
- Enlarged the existing Vault widget artwork from 52dp to 62dp (19.2%), proportionally fitted/centered in live and picker resources; kept enough minimum resize height for padding. No launcher icon changes.
- Made the To-Do card the sole task-area color layer, behind transparent task rows; disabled ListView edge fading, overscroll and opaque scroll caches in all system/imported font layouts. Preserved completed-task text/status fade and wallpaper-only darkening.
- Added `Task card background transparency` with percentage slider, stored independently of RGB color in `todo_store`; alpha is applied only to the card background ImageView. Removed the wallpaper-dependent hard-coded alpha. Reset and saved-default handling include the setting.
- Removed only the Notes navigation-label emoji in `ModuleNavigation`; retained its icon, tab styling and Notes screen content.
- Added JVM regression tests for resource contracts, transparency endpoints/independence and the plain navigation label. Updated project memory, including this change-history entry. Passed 80 static source/resource assertions, 41 resource-reference checks and whitespace validation. VS Code found no diagnostics but discovered no Kotlin tests; Android build/JUnit execution, Actions for these changes and launcher rendering remain unverified. Use the existing GitHub Actions workflow and phone checklist in project memory.

### 2026-10-18 — Handle blank optional signing secrets

- Updated Gradle signing configuration to treat blank environment variables as unset before falling back to local properties. This prevents GitHub Actions' declared-but-empty optional debug secrets from triggering the partial-signing guard. Release and debug signing still require complete credential sets when any value is supplied.
- CI log confirmed the failure occurred during Gradle configuration before unit tests or APK compilation; rerun the workflow after this fix is pushed.

### 2026-10-18 — Repair Drive backup authorization and restore safety

- Confirmed the app uses Google Identity Services `AuthorizationClient` with `drive.appdata`/email scopes and Drive v3 `appDataFolder`, not Firebase. Documented the actual likely debug-build OAuth mismatch: Cloud setup is bound to the persistent certificate, while routine CI previously used an ephemeral debug certificate.
- Added optional stable, non-production debug keystore support to Gradle/Actions; the production release key remains separate. CI reports the public APK signer fingerprints. Register an Android OAuth client for package `com.example.darktodo` and the stable debug SHA-1; external Google Cloud configuration is still required.
- Mapped OAuth developer/network errors to actionable messages and made user cancellation a neutral, non-destructive notice. Prevented duplicate authorization launches.
- Fixed the CI-reported Kotlin type mismatch by accepting `Throwable` in the authorization error formatter used by task listeners.
- First-launch detection now treats any backed-up local preferences as existing data. A no-backup result offers explicit Start fresh or Back choices; welcome restore explains that it replaces local state and requires a second explicit confirmation.
- Snapshot settings and Base64 blobs are parsed before any local writes, so malformed backup payloads fail before restore mutation. Drive 403 errors now mention the API/scope configuration.
- Not built or device-tested here; verify with the stable debug OAuth client and existing CI workflow.

### 2026-10-18 — Use supplied source art for launcher and Vault widget

- Copied the supplied 1254×1254 transparent PNGs unchanged to Android-valid resource names: `clarity_suite_icon.png` and `vault_icon.png`.
- Wired `vault_icon.png` to the actual Vault widget ImageView and static `previewImage`; the provider's `previewLayout` already uses that same live layout. Kept the title, lock-state text, click path, and widget behavior unchanged.
- Wired the suite PNG into a centered, 70dp adaptive safe-area foreground used by both launcher icon variants and referenced the suite adaptive resources from the manifest; the Vault widget art remains separate.
- Source PNGs are RGBA with transparent top-left corners. Resource diagnostics only; Android build, launcher masking, and on-device rendering remain unverified.

### 2026-10-18 — Keep Vault widget branding separate from app launcher

- Restored the main app manifest to the existing Clarity launcher icon resources; the Vault widget remains independently branded.
- The newly attached Vault image is not present as a source file in the workspace, so no substitute artwork was added. The Vault widget and preview still need the exact supplied raster before this request can be completed.

### 2026-10-18 — Suite launcher icon

- Replaced the manifest-referenced adaptive launcher foreground with a safe-zone-centered 2x2 Clarity suite mark for To-Do, Calendar, Vault, and Notes. Retained the near-black/crimson adaptive background and left Vault widget art, labels, and behavior unchanged.
- The inline reference image was not present as a source file in the workspace; the adaptive foreground is a hand-authored vector based on the attached reference, not a re-used PNG. Verified resource diagnostics only; no Android build/device check yet.

### 2026-10-18 — Fix Notes widget delivery and Vault widget navigation

- Added explicit widget receiver labels in addition to provider metadata labels to improve picker naming across launcher implementations.
- Reduced the Notes widget bitmap payload cap from 600,000 to 120,000 pixels to keep `RemoteViews` image updates comfortably below Binder transaction limits; Notes continues to render the selected real note.
- Vault widget launches now bypass the first-run backup gate and open the existing Vault screen. A warm MainActivity is recreated for a fresh intent route; no authentication is bypassed and no vault state is reset.
- Google authorization cancellation now clears busy/error state without presenting a failure message; genuine authorization failures remain visible.
- Source diagnostics only; no local Android toolchain/device is available, so CI and launcher verification remain pending.

### 2026-10-18 — Keep routine CI debug-only

- Removed the sequential minified release build, signing-secret requirement, signature verification, and release artifact upload from the routine workflow. Debug unit tests/build and the 14-day debug APK artifact are unchanged; release build configuration remains in the app module for launch preparation.

### 2026-10-18 — Distinct widget labels and Vault identity

- Assigned string-resource picker labels to To-Do, Calendar, Notes, and Vault; confirmed Notes already has a registered provider, selectable note storage, configuration activity, preview resources, and real RemoteViews rendering.
- Replaced the Vault wheel/spoke graphic with a metallic safe-door shield and keyhole. Added adaptive icon resources referenced by the manifest; preserved the existing general Clarity task-list icon artwork.
- Refined the Vault RemoteViews layout and preview; it shows a setup prompt when no vault exists and the locked tap-to-unlock prompt when one does. Authentication continues through MainActivity.
- Added a Notes visual fallback to the initial widget layout and retained its metadata-referenced configuration activity name through R8 repackaging.
- Source inspection did not identify which provider/device condition caused the reported "Couldn't add widget" message; no relevant launcher exception log was supplied. No speculative provider lifecycle change was made.

### 2026-10-18 — Release security hardening

- Scoped the persistent APK signing key to release builds only; debug builds remain signed with the standard debug key.
- Added R8 side-effect rules to strip Android log calls from optimized release builds while retaining debug diagnostics.
- Made the minified release build a required CI step. Pull requests build it unsigned; trusted non-PR runs verify the release APK signature before upload.
- Added ignore patterns for common Android signing key formats. No vault format or encryption behavior changed.
- Configured full-mode R8 renaming, source/line retrace metadata, English resource filtering, release-only non-debuggable/no-platform-backup behavior, and a narrow keep rule for WorkManager's persisted `BackupWorker` class name and constructor. The manifest omits `android:debuggable` so AGP assigns it per build type.
- Not locally built: this workspace has no `gradlew.bat`, system Gradle, Android SDK, JADX, or ADB. CI must confirm the optimized variant and signing behavior; device smoke tests remain pending.

### 2026-10-18 — Refreshed Tasks and Notes widget previews

- Reworked the Tasks widget picker layout to show a 5-task count, realistic task titles, completed/in-progress/pending status marks, and the existing refresh/add actions.
- Reworked the Notes widget picker layout into a readable yellow note card with a title, four example note lines, and a note icon; improved both widgets' `previewImage` fallback artwork.
- Kept these as native Android preview resources because widget pickers consume provider `previewLayout` and `previewImage`, rather than the app's Compose UI. Not compiled or device-tested.

### 2026-10-17 — Fix second CI compile failure (sticker style wiring)

- Added the missing `style` field to `Sticker`; gave `StickerEditor` its `globalStyle`/`book`/`onBook` parameters and a 5-arg `onSave`; renamed the local `override` to `customStyle` and added the missing imports. Not yet re-verified by CI.

### 2026-10-17 — Fix CI compile error in StickerSettings

- Removed a stray invalid `Modifier.horizontalScroll` stub in StickerSettings.kt that broke `compileDebugKotlin`. Later compile errors, if any, are not yet known.

### 2026-10-17 — Sticker widget, To-Do layout controls, Save As Default for To-Do/Calendar

- Added the Sticker home-screen widget (StickerWidgetReceiver, sticker_widget layout/info, manifest receiver) with tap-to-cycle and auto-refresh from StickerStore.
- Added To-Do widget Padding/Row spacing controls and presets; added Save As Default / Restore default for To-Do and Calendar widgets (StyleSnapshot).
- Updated [PROJECT_MEMORY.md](./PROJECT_MEMORY.md). Not compiled or device-tested; no Android SDK available locally.

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

## 2026-10-17 - Sticker fonts, border and settings headings (not compiled or device-tested)

- Stickers: new stickers no longer freeze a copy of the global style, so font/style changes reach the home-screen widget; "Save As Default Style" now makes stickers without their own style use the global colours too.
- Added imported custom fonts (shared `LocalWidgetFonts`) and a Border section (thickness 0-12 dp, HEX colour) to `StickerStyle`, the app preview and the widget bitmap renderer.
- Removed the Recent colours row; sticker settings groups now carry headings that say what they change.
- Added an optional Background image for stickers (Sticker settings > Background image), reusing `WidgetBackgroundEditor` and `WidgetBackgrounds` (key `sticker`, new `renderPx`): gallery/device/generated source, zoom/rotate, and a 0-100% dark fade for text legibility. Applies to all stickers in the app and the widget; not included in Drive backup. Not compiled or device-tested.

## 2026-10-18 - Stickers become per-note Notes (not compiled or device-tested)

- Renamed the user-facing feature to "Notes" (tab "Notes", widget "Clarity Note", "Create note", "No Notes Yet"); code, storage keys and JSON names keep the "sticker" names for data safety.
- Removed pins (`Sticker.pinned`, `StickerStyle.pin`, pin glyph, pin colour setting) and the global Sticker settings screen/icon and "Save As Default Style". Old stored `pinned`/`pin` fields are ignored.
- Per-note styling: each note stores a full `StickerStyle` plus its own background image (`WidgetBackgrounds.noteKey`); settings open from a gear on each note card (`NoteSettingsScreen`) and save only that note, debounced, refreshing its widget. New options: auto contrast, text shadow, glow. Long-press a card for Rename / Duplicate / Delete.
- Migration (`StickerStore.migrate`, flag `notes_per_note_v1`): every existing note keeps its current look as its own style with auto contrast off; the old shared image is copied to each note and removed.
- Auto contrast: `NoteContrast` / `NoteAppearance.effectiveStyle` choose light or dark text from the background colour or image after fade; used by both the app and the widget.
- Widget: fill, image and fade are drawn through one rounded rectangle (no black corners; border uses the same bounds). Tapping the widget opens the note in the editor (`MainActivity.ACTION_OPEN_NOTE`, note id, focus and keyboard on open). A configuration activity (`StickerWidgetConfigActivity`) picks which note a widget shows; the old tap-to-cycle behaviour was removed.
- Backup now includes each note's image (`wallpaper_note_<id>`). Tests updated/added (`StickerOpsTest`, `StickerStyleTest`, `NoteContrastTest`, `ModuleNavigationTest`).

## 2026-10-17 - Sticker backup, tests and docs (not compiled or device-tested)

- Added `sticker_store` and the sticker wallpaper to the Drive backup snapshot (`BackupManager.PREF_FILES`/`blobFiles`); restore refreshes the sticker widget. Backups made earlier do not contain them, so restoring one leaves local stickers as they are (the sticker wallpaper file is removed, as for the other wallpapers).
- Added `StickerStyleTest` cases for global colours, per-sticker colour override with global typography/border, and font-file/border JSON round trip.
- Updated the Stickers and performance notes in `PROJECT_MEMORY.md`.

## 2026-10-17 - Sticker colours and opacity fixes (not compiled or device-tested)

- `StickerStyle.resolve`: once the global card/text/pin colours differ from the defaults they apply to every sticker without its own colours (previously only when "Save As Default Style" was on, so Sticker settings colours appeared to do nothing).
- In-app `StickerSurface`: Opacity now fades only the card fill and background image, not the text, matching the widget.

## 2026-10-17 - Performance pass 1 (not compiled or device-tested)

- `TaskStore.tasks()` caches the parsed list keyed by the stored JSON string; `saveTasks` clears it and only refreshes the Calendar widget when an id/date/completed value changed.
- `LocalWidgetFonts.list()` caches the parsed font registry (file-exists check still runs each call); the To-Do row factory loads settings once per data refresh instead of per row.
- Whole-number settings sliders skip commits that do not change the integer; Sticker settings saves are debounced 150 ms and flushed when the screen closes.
- Task list group positions, calendar status lookup, vault search filtering and History grouping are computed once per data change (`remember`) instead of per item/recomposition; `TimeZoneCatalog.popular/other` are lazy.
- `ReminderScheduler.restore` now runs on a background thread at launch and on resume (UI refreshes before and after it finishes).
- `WidgetBackgrounds` reuses the decoded wallpaper per saved-file version; `LocalWidgetFonts.renderText` caches rendered bitmaps (LRU 128) keyed by every input.
- Added an optional `release` build type (R8 + resource shrinking, same persistent signing) and a non-blocking CI step that uploads `dark-todo-release-apk`; the debug APK flow is unchanged. Release build is not device-tested; add keep rules in `app/proguard-rules.pro` if it misbehaves. Vault key-derivation threading and quote pruning were deliberately left unchanged.

## 2026-10-17 - Sticker fonts always follow global settings

- `StickerStyle.resolve` now always takes font family/file, size, weight, line height and text scale from the global sticker style, so stickers holding an older per-sticker style no longer ignore the selected (custom) font on the widget. The per-sticker editor no longer shows font controls. Added `StickerWidget` debug logs (tag `StickerWidget`). Not device-tested.

## 2026-10-17 - Sticker widget picker preview

- Added a yellow sticker preview (`widget_preview_sticker.xml`, `sticker_widget_preview.xml`) as `previewImage`/`previewLayout` of the sticker widget, which previously showed an empty preview. Not device-tested.

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


## 2026-10-17 - UI/UX refinement and Sticker customization

- History moved under Tasks as a sub-tab; removed redundant page titles and the To-Do widget preview; shared gear button; Typography renamed Fonts Customization; widget wallpapers moved from global Settings into Tasks/Calendar settings; Vault scrolling and Password/Fingerprint labels.
- Added Sticker Settings (HEX colours, fonts, layout, widget size, live preview, Save As Default Style) with per-sticker overrides; added StickerStyleTest and updated ModuleNavigationTest.
- Not compiled or run locally (no Gradle/Android SDK available); see PROJECT_MEMORY.md for gaps.
