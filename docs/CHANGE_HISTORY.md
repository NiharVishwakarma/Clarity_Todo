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
