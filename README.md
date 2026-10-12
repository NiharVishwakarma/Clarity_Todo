# Dark Todo Widget

Native Kotlin Android app and home-screen widget. The Codemagic workflow is in `codemagic.yaml` and creates an unsigned-by-you, debug-signed APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Build with Codemagic

1. Put this folder in a Git repository and push it to a provider supported by Codemagic.
2. Add the repository in Codemagic and select the `android-debug` workflow.
3. Start a build and download `app-debug.apk` from the build artifacts.
4. Install the APK on Android. If Android asks, allow installation from the browser or file manager used to open it.
5. Long-press the home screen, open **Widgets**, find **Dark Todo**, and place it.

No local Android SDK is needed. The included workflow generates the Gradle wrapper on the cloud worker before assembling the APK.

## Data behavior

Active tasks use the `active_tasks_v1` preference key. Activity records use `task_history_v1`. Deleting or clearing active tasks writes a deletion event to history; deleting history only rewrites the history key. Timestamps are stored as epoch milliseconds and displayed in the device's local timezone. Storage remains on the device and is retained across app restarts and reboots.

## Current implementation notes

The widget lists up to five tasks at once; the app shows the full list. Widget task titles open the app, while the checkbox toggles completion. Long-press an incomplete task in the app to delete it. Widget colors, completed-task fading, and Nunito weights can be adjusted in Settings. Nunito is bundled for the app UI. Android Glance widgets cannot load custom app font resources, so the widget uses the closest system sans-serif weight. Font source: Google Fonts Nunito, licensed under SIL Open Font License 1.1 (see `licenses/Nunito-NOTICE.md`).
