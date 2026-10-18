# Copilot Instructions

## Repository navigation

- Read [docs/REPO_MAP.md](../docs/REPO_MAP.md) first to identify likely files.
- Treat the map as a navigation aid; source code is authoritative.
- Verify mapped paths and inspect relevant code before editing.
- Prefer targeted searches; broaden only when needed.
- Make the smallest complete change and run relevant checks.
- Update the map in the same change when paths, responsibilities, entry points, data flow, or test commands change.
- Do not add per-change logs or duplicate source code in the map.

## Project context

- Before changing project files, read [docs/PROJECT_MEMORY.md](../docs/PROJECT_MEMORY.md). For the first creation of that file, use the project source and existing documentation as the baseline.
- Do not scan the entire repository unless the task requires it. Load only the source, resources, tests, and build files relevant to the requested change.
- Use incremental analysis: inspect the narrowest relevant area first and expand only when dependencies or behavior require it.

## Keeping project documentation current

- Update `docs/PROJECT_MEMORY.md` whenever an architecture or user-facing feature changes.
- Add a dated entry to `docs/CHANGE_HISTORY.md` for every project modification, including documentation-only changes. When editing `CHANGE_HISTORY.md` itself, include the entry for that edit in the same change.
- Keep implementation facts distinct from proposals and link to the relevant source instead of duplicating large bodies of documentation.

## Project-specific guidance

- This is a native Kotlin Android application. Preserve the existing Jetpack Compose, SharedPreferences, `AppWidgetProvider`/`RemoteViews`, and `AlarmManager` patterns unless the task calls for an architectural change.
- Keep task persistence, reminders, and widget refresh behavior consistent across the app and all affected widgets.
- Do not describe proposed widgets, pricing, or roadmap items as shipped features. See [docs/PROJECT_MEMORY.md](../docs/PROJECT_MEMORY.md) for the current implementation and build/test references.
