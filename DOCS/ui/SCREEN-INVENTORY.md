# MemGallery - UI screen inventory

Read-only documentation pass, 5 November 2026. Nothing in the app was changed and no Gradle build was run.

Base: `MemGallery` module, versionName `0.3.4`, versionCode 20, package `com.example.memgallery`.
All Kotlin paths below are relative to `MemGallery/app/src/main/java/com/example/memgallery/`.

## Count

| Kind | Count |
|---|---|
| Registered nav routes | 18 |
| Distinct screen composables | 16 (`PostCaptureScreen` serves 3 routes) |
| In-app overlay capture surfaces | 1 (`OverlayService`) |
| Invisible activity shims (no UI of their own) | 5 |
| Home-screen widgets | 2 |
| Sheets, dialogs and full-screen overlays | 30 |
| **Total UI surfaces** | **54** |

## Routes (`navigation/AppNavigation.kt:17-73`)

(onboarding, gallery, settings, camera_capture, text_input, audio_capture, post_capture, post_capture_edit, detail, collection, bookmark_input, advanced_settings, agent_access, task_manager, post_capture_url, feedback, plus the two screen-only values)

## Screens
