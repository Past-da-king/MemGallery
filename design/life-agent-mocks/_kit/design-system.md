# MemGallery v4: Material 3 Expressive design system

Phone mock-ups for v0.3.4 (Kotlin, Compose, Material 3). 412 x 915 dp, rendered at 2x (824 x 1830 px), light and dark.
Sources: `src/tokens.css` (all colour, shape, elevation, motion tokens), `src/components.css` (components), `src/kit.js` (shape library, generated art), `src/kit2.js` (memory tiles). Fonts and Material Symbols Rounded are bundled in `src/fonts`.

## Principles
1. **Content leads, colour supports.** Memories are the colour. Chrome stays tonal (surface containers); colour roles carry meaning:
   - primary = navigation and selection (selected chip, selected toolbar item, switches)
   - secondary = neutral tonal controls
   - tertiary (rose) = AI and creation (FAB, AI summary, "review new tasks")
   - sky / success / amber = memory kinds (voice, bookmark/done, home) and are harmonised to the seed
2. **Shape is the brand.** The shape library (cookie 9, clover, soft burst, sunny, puffy, gem, arch, pill) is used for avatars, image crops, selected states, empty states and the FAB, never as decoration only. Unselected controls sit on small radii; selected ones morph to full shapes.
3. **Big, confident type.** Google Sans Flex. Emphasized styles (heavier weight, tighter tracking) for display and headline; regular weights for body. Titles are 36 to 45 sp.
4. **Everything on a 4dp grid**, 16dp screen margins (20 to 24 for hero text), 48dp touch targets, 4dp gutters in the photo mosaic.
5. **Phone rule: no centred dialogs.** Every choice, form and confirmation is a slide-up sheet (screen 11 shows the pattern).
6. **Honest about privacy.** The code allows on-device AI (Local, Gemma 3) and optional cloud providers (Gemini, Groq, custom endpoint) with the user's own key. The mocks say "on-device" where Local is active and "stays on your device by default" at onboarding, not "no cloud, ever".

## Tokens
- **Seed**: wallpaper-style violet-indigo (hue 268). Primary `#5B46CF` light / `#C9C1FF` dark; tertiary rose `#A03A60` / `#FFB1C8`; secondary `#5D5B7D` / `#C7C3E2`.
- **Surfaces** (tonal, no flat greys): light `#FCF8FF` base, containers low `#F6F2FB`, `#F0ECF6`, high `#EAE6F0`, highest `#E4E0EA`. Dark `#131318` base, containers `#1B1B21`, `#1F1F25`, `#2A292F`, `#35343A`.
- **Containers with intent**: primary-container, tertiary-container, sky-container (voice), success-container (links, done), amber-container (home).
- **Shape scale**: 4, 8, 12, 16, 20, 28, 32 (extra-large increased), 48, full. Cards 28 to 32. Chips 12 (selected 18/pill).
- **Type**: display-m-em 45/50 (700), display-s-em 36/42, headline-m-em 28/34, headline-s-em 24/30, title-l-em 22/28, title-m-em 16/22, body 16/14/12, labels 14/12/11 (600).
- **Elevation**: tonal by default; shadow only for floating things (toolbar, FAB, menu items, sheet).
- **Motion** (specified, not visible in stills): spatial springs (damping .8, stiffness 380) on shape morph, press and sheet; effects springs (no overshoot) on colour and opacity. Press morphs a control to a smaller radius and scale .96 (shown on "Take photo" in screen 3). Selection morphs a rounded square to a circle (task check, collection tick).

## Components used (all drawn from M3 Expressive)
Large flexible top app bar (emphasized title, collapses), search bar and search view, connected button group for filters (shape morph on select), assist and tag chips, carousel (hero and multi-browse with shaped items), vibrant floating toolbar with a separate FAB, FAB menu, connected button group (single-select), split button, loading indicator (morphing polygon), wavy progress indicator, grouped list (4dp gaps, 28 outer / 6 inner corners), switch with check icon, modal bottom sheet with drag handle.

## Screens and the real app screens they map to
| # | Mock | Real code (`com.example.memgallery`) | Notes |
|---|------|------|-------|
| 01 (+ alt a, c) | Home (Memories) | `HomeScreen` pager page 1 -> `GalleryScreen`, `MemoryCard`, `ContributionGraph` | Chips are the real filters (All, Images, Notes, Audio, Bookmarks, Collections). Featured memory = "Show Highlighted Memory" setting. B uses the Memory Activity graph. Page switcher = the 3-page pager (Chat, Gallery, Tasks) as a toolbar. |
| 02 | Memory detail | `MemoryDetailScreen`, `FullscreenImageViewer`, `AudioPlayer` | Sections from strings: AI Summary, Image Analysis, Your Note, Tags, Suggested Actions, Add Manual Reminder; "1 of 2" = image counter. |
| 03 | Add memory | `AddMemorySheet` (Create New: Text Note, Upload Image, Take Photo, Record Audio, Save Bookmark), `AddMemoryTileService` | Real list of five items, as a FAB menu. |
| 04 | Processing | `PostCaptureScreen` ("New Memory Captured", Save Now), `MemoryProcessingService/Worker` | Steps are the analysis pipeline (text, description, tags, summary, tasks). Provider chip shows Local. |
| 05 | Search | `GalleryScreen` search ("Search memories...") + filter chips | Full search view with match highlights; "Ask about" hands over to Chat. |
| 06 | Ask | `ChatScreen`, `ChatTools`, `StreamingComponents` | History, new chat, attach, voice, send, tool indicator, Copy and Save as Note are real. Source thumbnails are a design addition. |
| 07 | Collections | Gallery "Collections" filter, `CollectionCard`, `CollectionDetailScreen`, `CreateCollectionSheet` | Description-driven auto-sort badge (the create form says the description helps AI sort). |
| 08 | Tasks | `TaskScreen`, `FullMonthCalendar`, `UnapprovedTasksSheet` | My Day / Tomorrow / Upcoming, Events and To-Do, EVENT/REMINDER, repeats WEEKLY, View Source Memory, "AI detected N new tasks". |
| 09 | Settings | `SettingsScreen`, `AdvancedSettingsScreen` | AI Configuration (Gemini, Groq, Local, Custom; Gemma 3), Appearance (theme mode, dynamic color, AMOLED), Features toggles. |
| 10 | Onboarding | `OnboardingScreen` | First page: "YOUR SECOND BRAIN", "Capture. Process. Recall.", Get Started, Skip. |
| 11 | Bottom sheet | `SelectCollectionSheet` (+ Gallery selection mode) | "Add to Collection", "Create New Collection". |
| 12 | Empty state | `TaskScreen` empty ("No tasks yet", "Capture a memory to generate tasks!") | Same pattern for "No collections yet". |

## Home, nav and filters (v4.1, Ayanda's direction)
- **Home = the "blob header" design** (`01-home`): "5 NOV · 412 KEPT" eyebrow, huge emphasized "Memories" title on a soft lavender blob with oversized cookie and clover shapes, round tonal icon button (activity) next to a cookie-shaped settings button, three stat tiles (38 This week in tonal lavender, 9 To review in tertiary, 5 Day streak filled primary), the Memory Activity dot heatmap, and a floating white search pill on the seam. This header is the quality bar and is reused on Collections, Tasks, Settings and the empty state (`blobHeader()` in `kit2.js`).
- **Floating nav**: matches the Agent phone app's `HomeBar` (`HorizontalFloatingToolbar` with vibrant colours): a detached pill in primary-container, icon-only toggle buttons (selected = filled on-primary-container button with a filled icon, others outlined, all 24dp, weight 500), and a clean primary FAB beside it (add on Memories and Tasks, create-folder on Collections, reminder on Detail). Icons: `chat`, `photo_library`, `task_alt`. Source: agent-android `feature/remote`, `ui/screens/Home.kt`.
- **Filters**: a connected button group replaces the old chips. The selected item is a filled secondary-container full pill with a filled icon and label; the others are icon buttons with 8dp inner corners and full outer corners. Icons: `apps` (All), `image`, `sticky_note_2`, `mic`, `bookmark`, `folder`.
- **Extra Home variants** (`01-home-alt-a`, `01-home-alt-c`): same header and nav; A swaps the carousel for an arch-cropped hero, C swaps it for a featured card and a justified 4dp mosaic.

## Compose mapping hints (for the build)
`MaterialExpressiveTheme`, `MaterialShapes` (Cookie9Sided, Clover4Leaf, SoftBurst, Sunny, Puffy, Gem, Arch, Pill) with `toShape()`, `HorizontalFloatingToolbar` / `FlexibleBottomAppBar`, `FloatingActionButtonMenu`, `ButtonGroup` + `ToggleButton`, `SplitButtonLayout`, `LoadingIndicator`, `LinearWavyProgressIndicator`, `HorizontalUncontainedCarousel` / `HorizontalMultiBrowseCarousel`, `LargeFlexibleTopAppBar`, `ModalBottomSheet`, `SearchBar` / `ExpandedFullScreenSearchBar`.

## Known limits of the mock-ups
Stills cannot show motion. Sample imagery is generated (no real photos or personal data). Google Sans Flex static weights are used; the real build should use the variable font with the ROND axis.
