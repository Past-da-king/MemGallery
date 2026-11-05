# Material 3 Expressive redesign (Nov 2026)

Branch `redesign/m3-expressive`. Visual redesign of every main screen to the approved v4 mock-ups. No feature was removed.

## Versions
- Compose BOM `2026.05.01` (ui/foundation 1.11.x) with **material3 `1.5.0-alpha18`** pinned in `gradle/libs.versions.toml`.
  - material3 1.4.0 keeps the Expressive APIs `internal`, and 1.5.0-rc01 needs AGP 9.1. alpha18 is the newest build that has them and still runs on AGP 8.x.
- Kotlin 2.2.21, KSP 2.2.21-2.0.5, Room 2.8.1, AGP 8.12.3 (unchanged), compileSdk 36.
- Font: Google Sans Flex (OFL), bundled as `res/font/google_sans_flex.ttf` (variable, weight axis and ROND).

## Where things are
- `ui/theme`: `Color.kt` (the one token source, light and dark), `Theme.kt` (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, shape scale), `Type.kt`.
- `ui/design`: shared components. `Header.kt` (blob header, stat tiles, activity dots, search pill), `Nav.kt` (floating nav, FAB menu), `Filters.kt` (connected button groups), `Tiles.kt` (memory tiles), `Sheets.kt` (the only sheet container), `Empty.kt`, `Shapes.kt` (cookie, clover, burst...), `Insets.kt`, `Art.kt`.
- Screens: `ui/screens`. Stateless content composables (`GalleryContent`, `TasksContent`, `MemoryDetailContent`, `ProcessingContent`, `ChatLayout`) are what the screenshot tests draw.
- Phone rule: every choice, form and confirmation is a `MemSheet` (slide-up). No centred `AlertDialog` is left.

## Dynamic colour
The default is now OFF so the app keeps its own violet palette. The Settings toggle still turns it on.

## Screenshot proofs
`app/src/test/.../proof` renders every screen light and dark at 412 x 915 dp with Roborazzi + Robolectric on generated sample data:

```
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
```
PNGs are written to `Downloads/MemGallery/build-proof`.
