# MemGallery Design System

Status: proposed, awaiting sign-off by Ayanda. Written 5 Nov 2026.
Applies to: the Android app (`MemGallery/app`), its home-screen widgets (`ui/widget`), and every screen built after this date.
Supersedes: the default Material 3 theme currently in `ui/theme/`.
Token source of truth: when this document and the code disagree, the code is wrong. When the code and this document disagree because someone improved the code, update this document in the same commit.

Read this before touching a screen. If a screen needs something this document does not cover, add the token here first, then use it.

---

## 1. Principles

Seven rules that decide arguments. If two options are both "fine", the one that follows more of these wins.

**P1. Capture is the product.** Every screen is one gesture away from saving something, and every screen shows that way back. Nothing may sit between the thought and the saved memory.

**P2. One accent does the work.** Ember is the only colour that points at something. Everything else is paper, ink, or a status that must be honest. If a screen needs a second colour to be understood, the layout is wrong, not the palette.

**P3. Paper, not glass.** Depth comes from tone steps and hairline rules, never from glow, blur or heavy shadow. The app should read like a well-printed notebook, not a dashboard.

**P4. Pills mean press.** A fully rounded shape is reserved for things a thumb can press. A label, tag, count, date or fact is never drawn as a pill.

**P5. On the phone, everything that asks slides up.** Choices, forms and confirmations are bottom sheets in the app's own design. A centred stock dialog never appears on a phone.

**P6. Every number comes from a step.** Spacing, radius, type size, line height and duration all come from the scales in sections 4 and 5. No `6.dp`, no `18.dp`, no `320ms` invented at the call site.

**P7. Motion explains, never decorates.** Every animation answers "where did this come from" or "where did it go". Nothing bounces, nothing loops except a loading shimmer, nothing runs longer than 480ms.

---

## 2. Colour

### 2.1 The accent, and why it is Ember

The app's own accent is **Ember**, a burnt amber: `#8C4A10` on paper, `#F0B183` at night.

Why:

- Memories are sepia. Ember is the colour of old paper, film and lamplight, so the accent belongs to the subject matter instead of being borrowed from a template.
- It is the opposite of stock. The current build ships the Material baseline purple (`#6650A4`, `#D0BCFF`), which is exactly what an untouched Compose project looks like.
- It is ownable. Almost every capture app in the category runs blue, green or purple. Amber sits alone there.
- It works on both grounds. `#8C4A10` on warm paper `#FBF8F3` is 6.39:1; `#F0B183` on warm near-black `#17130E` is 9.97:1. Neither needs a border to be legible.
- It doubles as the capture colour, so the single accent is also the single action. The record button, the save confirmation, the streak fill and the selected filter all use the same ember, and nothing else competes with them.

Surfaces are **warm paper** (light) and **lamplight black** (dark). Both are slightly warm greys, never blue-grey. That is what separates this app from a stock Material screen more than the accent does.

Semantics sit far enough away in hue to survive colour blindness:
success green (145), warning ochre (48, used only with an icon and a container), error crimson (5), info ink-blue (210). Ember is 30. Nothing is distinguished by colour alone; every semantic also carries an icon or a word.

### 2.2 Light scheme (Paper)

| Role | Hex | Role | Hex |
|---|---|---|---|
| primary | `#8C4A10` | onPrimary | `#FFFFFF` |
| primaryContainer | `#FFDCC2` | onPrimaryContainer | `#2F1503` |
| inversePrimary | `#F0B183` | | |
| secondary | `#6B5A45` | onSecondary | `#FFFFFF` |
| secondaryContainer | `#F2E2CD` | onSecondaryContainer | `#241A0D` |
| tertiary | `#46625E` | onTertiary | `#FFFFFF` |
| tertiaryContainer | `#C8E9E3` | onTertiaryContainer | `#05201D` |
| error | `#B3261E` | onError | `#FFFFFF` |
| errorContainer | `#F9DEDC` | onErrorContainer | `#410E0B` |
| background | `#FBF8F3` | onBackground | `#1E1A14` |
| surface | `#FBF8F3` | onSurface | `#1E1A14` |
| surfaceVariant | `#E9E0D3` | onSurfaceVariant | `#4E463A` |
| surfaceTint | `#8C4A10` | scrim | `#000000` |
| inverseSurface | `#33302A` | inverseOnSurface | `#F6F1E8` |
| outline | `#7F7566` | outlineVariant | `#D1C7B6` |
| surfaceDim | `#DFD9CE` | surfaceBright | `#FBF8F3` |
| surfaceContainerLowest | `#FFFFFF` | surfaceContainerLow | `#F6F1E8` |
| surfaceContainer | `#F0EBE1` | surfaceContainerHigh | `#EAE4D9` |
| surfaceContainerHighest | `#E4DED2` | | |

### 2.3 Dark scheme (Lamplight)

| Role | Hex | Role | Hex |
|---|---|---|---|
| primary | `#F0B183` | onPrimary | `#4A2A08` |
| primaryContainer | `#6C3C0B` | onPrimaryContainer | `#FFDCC2` |
| inversePrimary | `#8C4A10` | | |
| secondary | `#D6C4AC` | onSecondary | `#3A2E1E` |
| secondaryContainer | `#524531` | onSecondaryContainer | `#F2E2CD` |
| tertiary | `#AECCC6` | onTertiary | `#14332F` |
| tertiaryContainer | `#2F4C47` | onTertiaryContainer | `#C8E9E3` |
| error | `#F2B8B5` | onError | `#601410` |
| errorContainer | `#8C1D18` | onErrorContainer | `#F9DEDC` |
| background | `#17130E` | onBackground | `#ECE5D9` |
| surface | `#17130E` | onSurface | `#ECE5D9` |
| surfaceVariant | `#4A4238` | onSurfaceVariant | `#CBC2B3` |
| surfaceTint | `#F0B183` | scrim | `#000000` |
| inverseSurface | `#ECE5D9` | inverseOnSurface | `#33302A` |
| outline | `#978D7E` | outlineVariant | `#4A4238` |
| surfaceDim | `#17130E` | surfaceBright | `#3A332A` |
| surfaceContainerLowest | `#110E0A` | surfaceContainerLow | `#1F1A14` |
| surfaceContainer | `#241F18` | surfaceContainerHigh | `#2F2921` |
| surfaceContainerHighest | `#3A332A` | | |

### 2.4 AMOLED

AMOLED is an opt-in variant of dark, not a separate scheme. Only the surface ramp and the hairlines change; text, accent and semantics stay identical.

| Role | AMOLED |
|---|---|
| surface, surfaceDim, surfaceContainerLowest, background | `#000000` |
| surfaceContainerLow | `#0D0B08` |
| surfaceContainer | `#14110D` |
| surfaceContainerHigh | `#1E1A15` |
| surfaceContainerHighest | `#29241D` |
| surfaceBright | `#29241D` |
| outlineVariant | `#3A342B` |

Rule: on pure black, no shadow is visible, so AMOLED drops shadows entirely and relies on the tone steps above plus hairlines.

### 2.5 Semantic extras (not Material roles)

These live in an app-level `MemColors` object, not in `ColorScheme`, because Material has no slot for them. They may only be used through that object.

| Token | Light | Dark | Use |
|---|---|---|---|
| success | `#1F6B3A` | `#8FD6A3` | PROCESSED / saved / done |
| onSuccess | `#FFFFFF` | `#0B2A16` | on success fill |
| successContainer | `#CDEBD6` | `#1F4B2C` | status chip background |
| onSuccessContainer | `#05301A` | `#C7EBD3` | status chip text |
| warning | `#7A5B00` | `#E4C65C` | PENDING / needs attention |
| onWarning | `#FFFFFF` | `#3A2C00` | |
| warningContainer | `#FFE9A8` | `#4A3B00` | |
| onWarningContainer | `#3A2C00` | `#FFE9A8` | |
| info | `#24527A` | `#A9C8E8` | PROCESSING / sync / explanatory |
| onInfo | `#FFFFFF` | `#0A2237` | |
| infoContainer | `#D3E4F7` | `#1B3A57` | |
| onInfoContainer | `#0A2237` | `#D3E4F7` | |
| capture | = primary | = primary | record, save, capture bar mic, streak fill |
| onCapture | = onPrimary | = onPrimary | |
| recording | `#B3261E` | `#F2B8B5` | the only red that is not an error: live recording dot |

Warning is never used as a bare tint. It always appears as a container chip or banner with an icon and a word.

### 2.6 Memory tints (content type and collections)

A memory has a kind. The kind is shown as a small tint on tags and tile badges, never as a full-card background. Fixed set of six, no free colour.

| Tint | Light container / on | Dark container / on | Maps to |
|---|---|---|---|
| text | `#E3E2F2` / `#221F3E` | `#2A2740` / `#E3E2F2` | `userText` only |
| photo | `#FFE6D2` / `#4A2A08` | `#4A2E18` / `#FFE6D2` | `imageUri`, `imageUris` |
| audio | `#FBE2DE` / `#4A211A` | `#472723` / `#FBE2DE` | `audioFilePath` |
| link | `#D5EDE8` / `#0B322B` | `#1D3F39` / `#D5EDE8` | `bookmarkUrl` |
| agent | `#D8E6F6` / `#102A44` | `#1E344C` / `#D8E6F6` | `type = CHAT`, agent activity |
| collection | `#F2E6D2` / `#3A2C12` | `#3B2F1B` / `#F2E6D2` | collection chips, collection covers |

Every pair above was measured: on-container text is 9.39:1 or better; each container is at least 1.13:1 against its surface, so it reads as a tint and not as a stain.

`CollectionEntity.color` is currently a free ARGB int. It becomes an index into this six-tint set. Null means `collection`.

### 2.7 Status mapping

| Status in the data | Token | Treatment |
|---|---|---|
| `PENDING` | warning | container chip, hourglass icon, "Waiting" |
| `PROCESSING` | info | container chip, spinner 12dp, "Reading" |
| `COMPLETED` | success | no chip; the card simply has no status mark |
| `FAILED` | error | container chip, refresh icon, tappable retry |

Collection status (`ACTIVE`, `PAUSED`, `DONE`) uses secondary / warning / success in the same container treatment.

### 2.8 Dynamic colour and custom seeds

**Dynamic colour (Material You) is off by default.** This app is zero-cloud and own-brand; a wallpaper should not be able to repaint it. The setting stays, off, for the people who want it, and when it is on the app accepts the system scheme unfiltered (no half-measures).

**Custom seed** is reduced from 20 raw Android palette colours to eight curated accents. Each still generates a full scheme through `harmonize`-free tonal generation. The eight are chosen so no seed can produce a blue-grey or magenta app: Ember (default), Clay, Moss, Ink, Slate, Plum Ink, Ocean, Sandstone. The current picker's default entry, `#6750A4`, is removed.

### 2.9 Colour rules

- No hex literal in a composable. Colour comes from `MaterialTheme.colorScheme` or `MemColors`.
- One filled ember element per screen region. Two competing primary fills on one screen is a bug.
- Text: `onSurface` for primary text, `onSurfaceVariant` for secondary. Never `outline` for text (light outline is 4.27:1, fine for hairlines and 3:1 icons, not for words).
- Disabled content is `onSurface` at 38% and is the only place a low-contrast text is allowed, because it is exempt and must look unreachable.
- No gradient except one: the image-protection scrim on a photo tile (section 6.15). The five random card gradients in `MemoryCard.kt` are retired.
- Every semantic is double-encoded: colour plus icon or word.
---

## 3. Type

### 3.1 Family

**Manrope**, bundled in the APK, static weights 400, 500, 600, 700. Licence: SIL Open Font License 1.1, which allows bundling inside a closed-source app as long as the licence file ships (put it in `assets/fonts/OFL.txt`).

Why Manrope:

- It is geometric but warm, with a slightly squarish bowl and open apertures. It reads as designed, not as a system font, which is the whole point of not using Roboto.
- It has a tall x-height, so 12sp labels are still readable on a memory card.
- Four weights are enough for the whole scale, which keeps the APK cost around 200 KB.
- It covers Latin Extended, so isiZulu, Afrikaans and English all render in the same typeface with no fallback jumps.

Usage split: Manrope does display, headline, title, body and label. **Numerals in live counters** (streak count, counts on filters, timers) request `FontFeatureSettings("tnum")` so digits do not jitter; if the bundled build turns out to lack `tnum`, those spans fall back to the system font, and that is recorded in the token file, not patched per screen.

Fallback chain: Manrope, then `FontFamily.Default` (Roboto). Never `FontFamily.Serif`, never `FontFamily.Cursive`, never `Monospace` outside code blocks in markdown.

Not allowed: Material's baseline type scale (Roboto, 400 everywhere), a second display face, italic (Manrope has no true italic; do not synthesise one), weight 300 (too thin at 12sp), weight 800 (shouting).

### 3.2 Scale

All sizes in sp, weights are the only four shipped. Letters are sp.

| Token | Size / line | Weight | Letter | Usage |
|---|---|---|---|---|
| displayLarge | 40 / 48 | 700 | -0.5 | One per app: the onboarding number, the year on a memory wall. Never in a list. |
| displayMedium | 36 / 44 | 700 | -0.4 | Big single statistic (total memories on the profile card) |
| displaySmall | 32 / 40 | 700 | -0.25 | Empty-state number, celebration count |
| headlineLarge | 28 / 36 | 700 | -0.2 | Onboarding step title, full-screen reader title |
| headlineMedium | 24 / 32 | 600 | -0.1 | Screen title when there is no app bar, sheet hero title |
| headlineSmall | 21 / 28 | 600 | 0 | Memory detail title, collection header |
| titleLarge | 20 / 28 | 600 | 0 | Top app bar title, sheet title, dialog title |
| titleMedium | 16 / 24 | 600 | 0.1 | Card title, list row primary line, section link |
| titleSmall | 14 / 20 | 600 | 0.1 | Section header above a row group, form group label |
| bodyLarge | 16 / 26 | 400 | 0.1 | Note text, memory body, chat messages. 26 line height: reading, not scanning. |
| bodyMedium | 14 / 22 | 400 | 0.15 | List row secondary line, helper text with content, sheet body |
| bodySmall | 12 / 18 | 400 | 0.25 | Timestamps, metadata, captions. Smallest size for reading text. |
| labelLarge | 14 / 20 | 600 | 0.1 | Button text, capture bar label, extended FAB |
| labelMedium | 12 / 16 | 600 | 0.4 | Chips, bottom navigation labels, tags, badges |
| labelSmall | 11 / 16 | 600 | 0.5 | Overline above a metadata group, tile type badge |

Hard floor: **11sp**. Nothing smaller exists in this app, including the contribution-graph legend that currently renders at 10sp.

Rules:

- Two body sizes only in content (bodyLarge for reading, bodyMedium for scanning). bodySmall is for metadata, never for a paragraph.
- Every screen has exactly one `titleLarge` (or one `headlineMedium` if it has no app bar). Two competing titles means one of them is a section header, so it is `titleSmall`.
- Line height never below size x 1.35 for anything over one line.
- Max measure: 68 characters. On tablets, body text sits in a 640dp column, not stretched full width.
- No ALL CAPS anywhere. No letter-spaced overline shouting. Weight and colour do the work.
- Numbers in rows are tabular (see 3.1) and never in a pill.

---

## 4. Space, shape, depth

### 4.1 Spacing scale

Base 4. Every margin, padding and gap is one of these. The named tokens are what the code should use.

| Token | dp | Where |
|---|---|---|
| space0 | 0 | reset only |
| space1 | 4 | icon to label inside a chip, badge inset, hairline offset |
| space2 | 8 | gap inside a compact row, between tag and text, tile gutter |
| space3 | 12 | gap between icon and two-line text, sheet inner blocks, field inner |
| space4 | 16 | default screen margin, card padding, list row horizontal inset |
| space5 | 20 | FAB inner padding, capture bar inner |
| space6 | 24 | gap between cards, sheet horizontal margin, section inner |
| space8 | 32 | gap above a section header, empty-state block spacing |
| space10 | 40 | gap before the first section after a hero |
| space12 | 48 | screen bottom clearance without a bar |
| space16 | 64 | onboarding hero offset, full-bleed media inset |

Retired values, all found in the current code: 5, 6, 9, 10, 14, 18. If a designer needs 10dp, it is 8 or 12.

Hairlines are **1dp**, not 2dp. The 2dp borders currently used for selection become 2dp of *primary ring* (deliberate), and structural dividers become 1dp `outlineVariant`.

`HorizontalDivider` default in Material is 1dp; use it. Divider insets: 16dp from start when it follows a leading icon, 0 when it separates full-bleed sections.

### 4.2 Layout grid

| Window | Margin | Columns | Gutter | Content max |
|---|---|---|---|---|
| Compact (<600dp) | 16 | 4 | 12 | full |
| Medium (600-840) | 24 | 8 | 16 | 840 |
| Expanded (>840) | 32 | 12 | 16 | 840 |

- Edge to edge drawing, insets from `WindowInsets.safeDrawing`. Never a hardcoded status bar height; the current code has 44dp and 56dp guesses that will break on a foldable.
- Lists: rows full width to the margin. Cards: full width minus margin on compact, two-up from 600dp.
- The image grid is a staggered grid with `minSize = 160dp`, 8dp gutter, and spans the full width minus the 16dp margin. Only the first tile of a day group may span full width.
- Bottom bars (capture bar, bottom navigation) are inset-aware and float 12dp above the gesture bar, never flush.
- Target sizes: 48dp minimum for anything a thumb presses (44dp in the current code becomes 48). 32dp minimum for a chip.

### 4.3 Radius scale

Seven steps. Six are described here; the seventh is reserved.

| Token | dp | Compose name | Purpose |
|---|---|---|---|
| rXS | 4 | `shapes.extraSmall` | tags, labels, tile type badges, tiny indicators |
| rSM | 8 | `shapes.small` | skeleton blocks, inner media crops, progress track |
| rMD | 12 | `shapes.medium` | small cards, text and search fields, grid tiles, snackbar |
| rLG | 16 | `shapes.large` | cards, list row thumbnails, FAB, stat blocks |
| rXL | 24 | `MemShapes.xl` (app constant) | large containers, media hero, floating capture bar |
| r2XL | 28 | `shapes.extraLarge` | bottom sheets (top corners), desktop dialog |
| rPill | 999 | `MemShapes.pill` | **clickable only**: buttons, filter chips, bottom nav indicator, record button |

Why six steps plus pill: the current code uses twelve radius values (2, 6, 8, 10, 12, 16, 18, 20, 24, 28, 32 and 50 percent) chosen per call site, which is why the app looks assembled rather than drawn. Six is enough to distinguish tile, card, container and sheet without inventing.

Nesting rule: a child's radius is never larger than its parent's minus its padding. A 16dp card with 8dp padding holds an 8dp child.

### 4.4 Depth and elevation

Depth is a **tone step**, not a shadow. Compose's tonal elevation is switched off where it would tint a surface: containers are painted explicitly from the surface-container ramp.

| Level | Surface role | Used for | Shadow |
|---|---|---|---|
| 0 | `surface` | screen background, app bar before scroll | none |
| 1 | `surfaceContainerLow` | grouped sections, sheet body | none |
| 2 | `surfaceContainer` | cards, list rows, stat blocks | none |
| 3 | `surfaceContainerHigh` | text fields, chips, capture bar, pressed rows | none |
| 4 | `surfaceContainerHighest` | pressed state of level 3, skeletons | none |
| overlay | as above | menu 3dp, FAB 3dp, sheet 8dp, dialog 8dp (desktop) | yes |

- Cards never have shadows and never have borders. If a card needs to separate from its background, the background moves, not the card.
- One hairline (`outlineVariant`, 1dp) is allowed per surface, used for structure: an app bar underline on scroll, a row divider, a field border when unfocused.
- No coloured glow, no coloured shadow, no blur behind sheets. The sheet uses a 32% black scrim and nothing else.
- Focus and selection do not raise a surface. They draw a 2dp `primary` ring outside the container so the content does not shift.

---

## 5. Motion

### 5.1 Durations

| Token | ms | What uses it |
|---|---|---|
| mInstant | 0 | anything when the system "remove animations" scale is 0 |
| mMicro | 80 | icon tint, checkbox tick, ripple settle, ripple release |
| mShort | 160 | chip select, button colour, focus ring, image fade-in, skeleton to content crossfade |
| mMedium | 240 | sheet exit, snackbar in and out, expand and collapse, selection ring, FAB morph |
| mLong | 320 | sheet enter, screen push, bottom navigation indicator, capture bar reveal |
| mXLong | 480 | full-screen media open, onboarding step change |
| mAmbient | 1400 | skeleton shimmer loop (only loop allowed) |
| mPulse | 1200 | recording pulse (only while actually recording) |
| mStagger | 40 per item, capped at 6 items | grid and list first render, total never over 240ms |

Nothing above 480ms except the two ambient loops. If a page transition needs 600ms to feel right, the layout is wrong.

### 5.2 Easings

| Name | Compose | Use |
|---|---|---|
| standard | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | moves within a screen, sheet, indicator, expand |
| decelerate | `CubicBezierEasing(0f, 0f, 0.2f, 1f)` | anything entering: sheet up, screen push, snackbar in |
| accelerate | `CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` | anything leaving: sheet down, screen pop, snackbar out |
| linear | `LinearEasing` | shimmer and spinner only |

No spring with damping below 1. No overshoot. No elastic. A capture app should feel like a shutter, not a toy.

### 5.3 What animates

- Sheet up and down (decelerate in, accelerate out), including the scrim at 32% to 0.
- Screen push and pop, horizontal, 320 in / 240 out. Settings-style screens push from the end; full-screen media fades through black at mXLong.
- The record button compressing 1.0 to 0.92 on press and the recording pulse (only when the microphone is actually capturing).
- Selection: the 2dp ring drawing in at mShort, and the multi-select count in the app bar counting up.
- Checkmark draw-in on a completed task, at mShort, path trim.
- Image load: 160ms fade from `surfaceContainerHigh`, no crossfade from a placeholder unless the placeholder was there for over 300ms.
- Skeleton to content: content fades in over 160ms while the skeleton fades out.
- Streak fill: each day fills in sequence at 40ms apart, once, on first appearance.
- Save confirmation: snackbar slides up 24dp with fade, and the memory card scales 0.98 to 1.0 at mMicro.

### 5.4 What must not animate

- Text colour for meaning (a status changing colour without a state change).
- List item heights during scroll, staggered grid item sizes, or anything that reflows while the thumb is scrolling.
- Shared elements between unrelated routes. A memory card is not shared into Settings.
- Blur, backdrop filters, animated gradients, shimmer on anything that is not loading.
- An indeterminate progress bar in a list row. If a row is working, it says so with a 12dp indeterminate spinner inside its status chip.
- Parallax on the image grid.
- Anything on scroll that is not the app bar's tonal step and hairline.

### 5.5 Reduced motion

Read the system animator scale. At 0: every duration becomes mInstant, the shimmer becomes a static `surfaceContainerHigh` block, the recording pulse becomes a static red dot, and every state still changes instantly. No screen may depend on an animation to reveal content.

### 5.6 Haptics

- Light tick: chip select, filter apply, day cell tap.
- Confirm: memory saved, task completed.
- Reject: failed save, blocked action.
Never on scroll, never on typing, never on navigation between tabs.
---

## 6. Components

Each spec is the complete anatomy: sizes, tokens, states, and the one rule that keeps it honest. Anything not written here is a defect, not a variation.

Naming convention for the section headers matches what the code should call the composable.

### 6.1 Card (`MemCard`)

- Container `surfaceContainer` (grid) or `surfaceContainerLow` (grouped list), radius rLG 16, padding space4 16, no shadow, no border.
- Title `titleMedium` 2 lines max, body `bodyMedium` 3 lines max, metadata `bodySmall` in `onSurfaceVariant`.
- Media inside the card: top corners 16, bottom corners 0, and the card clips it. Never a rounded image floating inside a square card.
- Selected (multi-select): 2dp `primary` ring outside the bounds plus a 24dp check circle top-end with `primary` fill. The container colour does not change, so the photo stays readable.
- Pressed: `surfaceContainerHigh`, mMicro.
- Tap target is the whole card. No tiny "open" affordance.
- Rule: a card always has a title. An image with no title is a tile (6.15), not a card.

### 6.2 List row (`MemRow`)

- Height 72dp with two lines, 56dp single line, min 72dp when it carries a trailing control.
- Insets: 16 start, 16 end, 12 vertical. Divider 1dp `outlineVariant`, inset 72dp from start when a leading icon or thumbnail exists, 16dp otherwise.
- Leading: 40dp thumbnail radius rMD 12, or 40dp icon container, or 24dp bare icon. Then 12dp gap.
- Primary line `titleMedium`, secondary `bodyMedium` in `onSurfaceVariant`, trailing `bodySmall` in `onSurfaceVariant` or a switch, chevron 24dp in `onSurfaceVariant`.
- Pressed: `surfaceContainerHigh`. Selected: `primaryContainer` at full height (no ring), which is the one place a row may be filled.
- Rule: no row is a pill. A row spans the width it deserves.

### 6.3 Chip and filter (`MemChip`)

- Height 32, radius rPill, label `labelMedium`, horizontal padding 12, icon 18 when selected, gap 8.
- Unselected: `surface` fill, 1dp `outlineVariant` border, `onSurfaceVariant` label. Selected: `primaryContainer` fill, no border, `onPrimaryContainer` label, check icon.
- One horizontal scrolling row of filters maximum, with 8dp gaps, and the first chip is always "All".
- Never wrap filters onto two lines; a scrolling row is honest, a wrapped block looks like a form.
- Rule: a chip is always pressable. If it is not pressable it is a tag (6.17).

### 6.4 Button

| Variant | Fill | Label | Use |
|---|---|---|---|
| filled | `primary` | `onPrimary` | the one committing action on a screen or sheet |
| tonal | `primaryContainer` | `onPrimaryContainer` | secondary action, retry, "keep editing" |
| text | none | `primary` | cancel, dismiss, low-stakes navigation |

- Height 48, radius rPill, label `labelLarge`, padding 24 horizontal, icon 18 with 8 gap.
- Min width 64. Two buttons side by side: equal weight, 12 gap, text button on the start, filled on the end.
- Disabled: fill = `onSurface` at 12%, label = `onSurface` at 38%, no shadow, still 48dp tall.
- Pressed overlay 8%, no elevation change, no scale.
- Rule: exactly one filled button per screen or sheet. If a screen needs two, one of them is tonal.

### 6.5 FAB and extended FAB

- Standard FAB 56dp, radius rLG 16 (a squircle, deliberately not a pill, so the pill language stays with buttons and chips), icon 24 `onPrimary` on `primary` fill, shadow 3dp.
- Extended FAB height 56, radius rLG 16, padding 20, icon 24 plus `labelLarge` with 12 gap, `surfaceContainerHigh` fill with `onSurface` label and 1dp `outlineVariant`, shadow 3dp.
- Placement: end, 16dp from margin, 16dp above the capture bar or bottom navigation.
- Only the primary action is `primary` filled. A screen may have one FAB, extended or not, never two stacked (the Gallery currently stacks two).
- On scroll down the FAB shrinks to 40dp icon-only at mMedium if it is an extended one, and returns at mShort.
- Rule: the FAB never overlaps content that cannot be scrolled past it.

### 6.6 Top app bar (`MemTopBar`)

- Height 64, container `surface` at rest, title `titleLarge`.
- On scroll: container becomes `surfaceContainer` and a 1dp `outlineVariant` hairline appears at the bottom, at mShort. It does not disappear again until the top is reached.
- Navigation icon 24 in `onSurfaceVariant`, 48dp target, 4dp from the margin. Actions 24dp icons with 4dp between, last one 8dp from the edge.
- Selection mode: the title is replaced by "3 selected" in `titleMedium`, leading becomes a close icon, actions become the allowed bulk actions, all at mShort.
- No centre-aligned title. No subtitle in the app bar (put it in the first row of content). No elevation, ever.
- Rule: the app bar never holds a filled button.

### 6.7 Bottom navigation (`MemBottomBar`)

Three to five destinations. The app currently has none; the intended set is **Gallery, Collections, Tasks, Chat** (Settings lives behind the avatar in the app bar).

- Height 80 plus gesture inset, container `surfaceContainer`, 1dp `outlineVariant` top hairline, no shadow.
- Item: 24dp icon plus `labelMedium` label, 4dp gap, unselected `onSurfaceVariant`.
- Selected: indicator pill 64x32 `primaryContainer` behind a 24dp `onPrimaryContainer` icon, label `onSurface`. Indicator animates width and position at mLong, standard easing.
- Never more than 5. Never a "more" item. Never hide labels to make room: if it does not fit at 12sp it does not belong in the bar.
- The bar hides on scroll in full-screen readers only, at mMedium, and is always present on the four top-level destinations.
- Rule: a destination in the bar is a place, not an action. Capture is the capture bar, not a tab.

### 6.8 Bottom sheet (`MemSheet`)

**This is the phone's dialog.** Every choice, form and confirmation on a phone opens as a bottom sheet.

- Radius r2XL 28 on the top two corners, `surfaceContainerLow`, scrim black 32%, drag handle 32x4 radius rXS 4 in `onSurfaceVariant` at 40%.
- Padding: 24 horizontal, 16 top under the handle, 24 bottom plus the navigation inset. Max height 90% of the window; beyond that it becomes a full screen with a back arrow.
- Title `titleLarge`, body `bodyMedium` in `onSurfaceVariant`, actions full-width filled button then a text button, 12 gap.
- Enter 320 decelerate from below, exit 240 accelerate. Drag to dismiss only when nothing is typed; a sheet with typed input asks before discarding.
- `skipPartiallyExpanded = true` for confirmations and short forms; allow partial expansion only for pickers that are browsed (collection picker, filter set).
- Focused text field in a sheet must scroll above the keyboard (imePadding), never sit under it.
- Rule: a sheet never contains another sheet, and a sheet never contains a dialog.

### 6.9 Dialog (`MemDialog`) - desktop only

MemGallery is a phone app; this exists because the Agent bridge and any future desktop surface need a confirmation pattern.

- Width 320 to 560, radius r2XL 28, container `surfaceContainerHigh`, shadow 8dp, scrim 32%.
- Title `titleLarge`, body `bodyMedium`, actions right aligned, text buttons, 8 gap, 24 padding.
- Never on a phone. `CreateCollectionDialog.kt`, the seven `AlertDialog` uses and the fourteen `Dialog(` uses in the current build are all phone surfaces and must migrate to `MemSheet`.

### 6.10 Snackbar

- Radius rMD 12 (not a pill: the message itself is not pressable), container `inverseSurface`, text `bodyMedium` in `inverseOnSurface`, action label `labelLarge` in `inversePrimary`.
- One line, two maximum. 16dp from the margins, floating 8dp above the capture bar or bottom navigation, never over it.
- Duration: 4s for information, 6s when it carries an action, indefinite only while an undo is genuinely available.
- One at a time. A new snackbar replaces the old instantly, no queue.
- Rule: a snackbar confirms or warns. It never carries the only route to an important action.
### 6.11 Text field (`MemTextField`)

Filled, quiet, no underline.

- Height 56 single line, min 56 growing for multiline, container `surfaceContainerHigh`, radius rMD 12 on all corners (no top-only radius), padding 16 horizontal, 8 vertical.
- Label floats from `bodyMedium` inside to `bodySmall` above on focus and when filled. Text `bodyLarge`. Placeholder `bodyLarge` in `onSurfaceVariant` at 70%.
- Unfocused: no border. Focused: 1.5dp `primary` border, cursor `primary` 2dp, label `primary`. Error: 1.5dp `error` border, helper text `bodySmall` in `error`, and the error state is announced for screen readers.
- Helper `bodySmall` in `onSurfaceVariant`, 4dp under the field. Counter, when there is a limit, right aligned in `bodySmall` and turns `error` at the limit.
- No leading icon unless the field's purpose is not obvious; no trailing icon except clear (18dp) or the microphone on the capture field.
- Multiline grows to 6 lines then scrolls. Never a fixed height that hides typing.
- Rule: every field has a visible label. A placeholder standing in for a label is a bug.

### 6.12 Search field (`MemSearchField`)

- Height 48, radius rPill (it is interactive, so the pill is allowed), container `surfaceContainerHigh`, leading search icon 20 in `onSurfaceVariant`, placeholder `bodyMedium`, clear button 18 when non-empty.
- Inactive state is collapsed into the app bar area: 48dp tall, full width minus margins, sitting under the title with 8dp gap. It does not expand into a full-screen Material `SearchBar`.
- Active: 1.5dp `primary` border, results replace the content below, filters stay visible above the results.
- Debounce 250ms. Show the last results until the new ones arrive, with a 2dp `primary` indeterminate line at the top of the list only.
- Empty query returns to the unsearched state; a query with no results is an empty state (6.13) with the query echoed and a "Clear" text button.
- Rule: search never occupies its own screen.

### 6.13 Empty state (`MemEmptyState`)

Every list, grid and collection has one, and it is designed.

- Centred column, max width 320, top padding space8 32 (or vertically centred when it is the whole screen).
- Icon 24 inside a 96dp circle of `surfaceContainerHigh`, icon tint `onSurfaceVariant`. One icon, never an illustration plus an icon.
- Title `titleMedium` in `onSurface`, one line. Body `bodyMedium` in `onSurfaceVariant`, two lines maximum, and it says what to do next, not what is missing.
- One action, text button, unless the empty state is the only thing on the screen, in which case one tonal button.
- Variants must be written per cause, not one generic string: nothing captured yet, nothing in this collection, no results for this search, nothing failed to sync, agent has done nothing yet.
- Rule: an empty state never says "No data".

### 6.14 Loading skeleton (`MemSkeleton`)

- Shown on first load of a grid or list, only when the load is expected to exceed 300ms. Under that, show nothing rather than a flash.
- Blocks: title bar 60% width x 14 height radius rSM, body 100% x 12, media block radius rMD. Container `surfaceContainerHigh`, shimmer linear gradient to `surfaceContainerHighest`, mAmbient 1400 loop, diagonal 45 degrees.
- The skeleton mirrors the real layout: same number of cards, same aspect ratios, same paddings. No generic grey page.
- Transition to content at mShort 160: skeleton fades out, content fades in. No layout shift between the two.
- In-place refresh (pull to refresh, filter change) uses no skeleton: the old content stays, a 2dp `primary` line runs at the top.
- Actions that take time inside a sheet use a 20dp `CircularProgressIndicator` inside the committing button, and the button keeps its width.
- Rule: no full-screen spinner after first paint. The 15 `CircularProgressIndicator` uses in the current build need this audit.

### 6.15 Image grid tile (`MemTile`)

- Staggered grid, `minSize = 160dp`, gutter 8, outer margin 16, tile radius rMD 12, clip on.
- Aspect follows the image (Coil measures it). A tile without a known ratio starts at 4:5 and settles when the image arrives, at mShort, no reflow of neighbours (fixed span).
- Text on the image only when needed: a bottom gradient scrim `onSurface` at 55% to 0 over the bottom 40% of the tile, title `titleMedium` in `onSurface`... on a photo the scrim is black: `#000000` 55% to 0, text `#FFFFFF`. This is the app's single gradient.
- Type badge: 20dp circle bottom-end with the memory tint (2.6) and a 12dp icon. Only when the tile does not already show that media.
- Selection: 2dp `primary` ring plus a 24dp check circle, and the rest of the grid dims to 60% only while selection mode is active.
- Missing media: `surfaceContainerHigh` with the type icon at 40%, never a random gradient.
- Rule: tiles have no border, no shadow, no rounded corners over 12. Density comes from gutter, not from decoration.

### 6.16 Capture bar (`MemCaptureBar`)

The signature component: a floating bar, not a FAB stack.

- Floating 12dp above the gesture inset and 12dp from each side, height 64, radius rXL 24, container `surfaceContainerHigh`, shadow 3dp, 1dp `outlineVariant` hairline optional on AMOLED.
- Contents, start to end: note icon, camera icon, link icon (each 48dp target, 24dp icon in `onSurfaceVariant`), then the record button.
- Record button: 48dp circle, `primary` fill, `onPrimary` 24dp mic icon. Pressing compresses to 0.92; capturing pulses at mPulse with a `recording` dot; the button becomes 56dp and turns `recording` while actually recording, with the elapsed time in `labelMedium` tabular to its start.
- Long-press any action = its quickest form (long-press mic starts recording immediately, long-press camera goes straight to the shutter).
- Present on Gallery and Collection detail. Hides on scroll down at mMedium, returns on scroll up at mShort, and is always visible when the grid is at the top.
- Rule: capture is reachable in one gesture from every top-level destination. If a screen cannot show the bar, it shows a 56dp capture FAB in the same place.

### 6.17 Tag and label (`MemTag`)

For facts, categories, counts, dates and AI tags. **Never a pill.**

- Height 24, radius rXS 4, padding 8 horizontal, label `labelSmall` (11sp) in `onSurfaceVariant`, container `surfaceContainerHigh` (or the memory tint container, in which case the text is the matching `on` colour).
- Borders: none. A tag is a quiet block, not an outlined control.
- Single line, ellipsis. Maximum 4 shown in a row group, then a `+3` tag that is itself pressable, so it is the only tag that becomes a chip.
- Attempting to press a non-interactive tag must do nothing at all. No ripple, no hover.
- Rule: if it has a full radius it is pressable. If it is not pressable it has radius 4.

### 6.18 Progress and streak (`MemProgress`, `MemStreak`)

- Linear progress: track 4dp height radius rSM, `surfaceContainerHighest`; indicator `primary`, radius rSM, determinate only. 600ms fill on first appearance, standard easing. No animation on every recomposition.
- Circular: 20dp inside a button, 12dp inside a status chip, 4dp stroke. Never a full-screen spinner.
- Streak: a row of 7 day cells, 8dp dots with 6dp gaps, or 24dp cells when it is the hero. Done = `primary` fill; missed = transparent with 1dp `outlineVariant`; today = 2dp `primary` ring with no fill until done.
- The streak count itself: `titleMedium` tabular, next to the dots, in `onSurface`. No flame emoji, no badge, no confetti.
- Contribution graph (the year view) keeps its GitHub-like grid but cells become 10dp with radius rXS 4, empty = `surfaceContainerHigh`, levels = `primary` at 20/40/60/80/100% alpha, and its legend text becomes `labelSmall` 11sp. It sits on `surface`, never on a card.
- Rule: progress shows real numbers. A progress bar with no value next to it is decoration.

### 6.19 Avatar (`MemAvatar`)

- Sizes 24 inline, 32 in a row, 40 in a list, 48 in a header, 96 on a profile. Always a circle.
- Image avatars fill and crop. Initial avatars: `secondaryContainer` fill with `onSecondaryContainer` initials in `labelLarge`. Agent avatars use `tertiaryContainer` so a machine is distinguishable from a person at a glance.
- 2dp `surface` ring when avatars overlap in a stack, overlapping by 25%.
- Status dot 8dp with a 2dp `surface` ring, bottom-end.
- Rule: no square avatars, no letter-in-a-rounded-rect, no generated identicon patterns.

---

## 7. Do and don't

1. Do let the surface ramp create depth. Don't add a shadow to a card, a row or a button.
2. Do keep exactly one filled `primary` element per screen. Don't let the record button and an action button both shout.
3. Do use a bottom sheet for every phone choice and confirmation. Don't use `AlertDialog` or `Dialog` on a phone.
4. Do use `rPill` only for pressable things. Don't draw a tag, a date, a count or a status as a pill.
5. Do use `labelSmall` 11sp for metadata. Don't go to 10sp to make something fit.
6. Do pick spacing from the scale. Don't write `6.dp`, `10.dp`, `14.dp` or `18.dp`.
7. Do double-encode every status with an icon or a word. Don't rely on colour to carry meaning.
8. Do design the empty, loading and error state before the happy path. Don't ship a blank screen or a bare spinner.
9. Do keep transitions at or under 320ms and one direction. Don't bounce, overshoot, blur or parallax.
10. Do put every new colour, radius, size and duration in the token file first. Don't hardcode a hex or a number in a composable.
---

## 8. Compose appendix

### 8.1 ColourScheme role map

Copy these straight into `ui/theme/Color.kt`. Every role is set; nothing is left to the Material default, because a single unset role is how a stock purple leaks back in.

| ColorScheme role | Light | Dark | AMOLED |
|---|---|---|---|
| primary | `#8C4A10` | `#F0B183` | same as dark |
| onPrimary | `#FFFFFF` | `#4A2A08` | same as dark |
| primaryContainer | `#FFDCC2` | `#6C3C0B` | same as dark |
| onPrimaryContainer | `#2F1503` | `#FFDCC2` | same as dark |
| inversePrimary | `#F0B183` | `#8C4A10` | same as dark |
| secondary | `#6B5A45` | `#D6C4AC` | same as dark |
| onSecondary | `#FFFFFF` | `#3A2E1E` | same as dark |
| secondaryContainer | `#F2E2CD` | `#524531` | same as dark |
| onSecondaryContainer | `#241A0D` | `#F2E2CD` | same as dark |
| tertiary | `#46625E` | `#AECCC6` | same as dark |
| onTertiary | `#FFFFFF` | `#14332F` | same as dark |
| tertiaryContainer | `#C8E9E3` | `#2F4C47` | same as dark |
| onTertiaryContainer | `#05201D` | `#C8E9E3` | same as dark |
| error | `#B3261E` | `#F2B8B5` | same as dark |
| onError | `#FFFFFF` | `#601410` | same as dark |
| errorContainer | `#F9DEDC` | `#8C1D18` | same as dark |
| onErrorContainer | `#410E0B` | `#F9DEDC` | same as dark |
| background | `#FBF8F3` | `#17130E` | `#000000` |
| onBackground | `#1E1A14` | `#ECE5D9` | same as dark |
| surface | `#FBF8F3` | `#17130E` | `#000000` |
| onSurface | `#1E1A14` | `#ECE5D9` | same as dark |
| surfaceVariant | `#E9E0D3` | `#4A4238` | `#332D25` |
| onSurfaceVariant | `#4E463A` | `#CBC2B3` | same as dark |
| surfaceTint | `#8C4A10` | `#F0B183` | same as dark |
| inverseSurface | `#33302A` | `#ECE5D9` | same as dark |
| inverseOnSurface | `#F6F1E8` | `#33302A` | same as dark |
| outline | `#7F7566` | `#978D7E` | `#8A8071` |
| outlineVariant | `#D1C7B6` | `#4A4238` | `#3A342B` |
| scrim | `#000000` | `#000000` | `#000000` |
| surfaceBright | `#FBF8F3` | `#3A332A` | `#29241D` |
| surfaceDim | `#DFD9CE` | `#17130E` | `#000000` |
| surfaceContainerLowest | `#FFFFFF` | `#110E0A` | `#000000` |
| surfaceContainerLow | `#F6F1E8` | `#1F1A14` | `#0D0B08` |
| surfaceContainer | `#F0EBE1` | `#241F18` | `#14110D` |
| surfaceContainerHigh | `#EAE4D9` | `#2F2921` | `#1E1A15` |
| surfaceContainerHighest | `#E4DED2` | `#3A332A` | `#29241D` |

Material fixed roles (`primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant` and the same for secondary and tertiary) are not used in this app. Set them equal to their container equivalents so nothing unexpected renders, and treat them as forbidden in code.

### 8.2 Shape tokens

Material 3 exposes five shape slots. MemGallery needs six plus pill, so one step is an app constant.

```kotlin
// ui/theme/Shape.kt
val MemShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // rXS  tag, badge
    small      = RoundedCornerShape(8.dp),   // rSM  skeleton, inner media
    medium     = RoundedCornerShape(12.dp),  // rMD  field, tile, small card
    large      = RoundedCornerShape(16.dp),  // rLG  card, row thumb, FAB
    extraLarge = RoundedCornerShape(28.dp)   // r2XL sheet, desktop dialog
)

// not a Material slot, hence its own constant
val MemRadiusXL   = 24.dp                       // rXL  floating bar, media hero
val MemPill       = RoundedCornerShape(percent = 50)  // pressable only
```

| Spec token | Kotlin |
|---|---|
| rXS 4 | `MaterialTheme.shapes.extraSmall` |
| rSM 8 | `MaterialTheme.shapes.small` |
| rMD 12 | `MaterialTheme.shapes.medium` |
| rLG 16 | `MaterialTheme.shapes.large` |
| rXL 24 | `MemRadiusXL` |
| r2XL 28 | `MaterialTheme.shapes.extraLarge` |
| rPill | `MemPill` |

Do not read the old `RoundedCornerShape(20.dp)`, `(18.dp)`, `(10.dp)` or `(2.dp)` values from the current code. Map them: 20 to `MemRadiusXL` or `shapes.large`, 18 to `shapes.large`, 10 to `shapes.medium`, 2 to `shapes.extraSmall`.

### 8.3 Typography tokens

```kotlin
// ui/theme/Type.kt  (family = Manrope from res/font)
val MemTypography = Typography(
    displayLarge   = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold,   fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium  = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold,   fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.4).sp),
    displaySmall   = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold,   fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineLarge  = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold,   fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.1).sp),
    headlineSmall  = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleLarge     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium    = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge      = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.1.sp),
    bodyMedium     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    bodySmall      = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp),
    labelLarge     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

// numerals in live counters
val Tabular = TextStyle(fontFeatureSettings = "tnum")
```

Font bundling: static TTFs as `app/src/main/res/font/manrope_regular.ttf`, `manrope_medium.ttf`, `manrope_semibold.ttf`, `manrope_bold.ttf` (resource names must be lower case with underscores), licence at `assets/fonts/OFL.txt`. Build the family once as a `val Manrope = FontFamily(...)` in `Type.kt` and reference it, never `FontFamily.Default`.

### 8.4 Non-Material token objects

```kotlin
// ui/theme/Dimens.kt
object Space { val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp
               val s5 = 20.dp; val s6 = 24.dp; val s8 = 32.dp; val s10 = 40.dp
               val s12 = 48.dp; val s16 = 64.dp }

// ui/theme/Motion.kt
object Motion {
    const val micro = 80; const val short = 160; const val medium = 240
    const val long = 320; const val xlong = 480; const val ambient = 1400
    val standard   = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

// ui/theme/MemColors.kt  (semantics + memory tints, 2.5 and 2.6)
```

### 8.5 Theme entry point

`MemGalleryTheme` keeps its four parameters but the defaults change and the scheme list is complete:

```kotlin
@Composable
fun MemGalleryTheme(
    dynamicColor: Boolean = false,   // was true
    appThemeMode: String = "SYSTEM",
    amoledMode: Boolean = false,
    customColor: Int = -1,           // -1 = Ember
    content: @Composable () -> Unit
)
```

Also set `surfaceTint` handling: pass `MemShapes` and `MemTypography` into `MaterialTheme`, and switch tonal elevation off by painting surfaces from the container ramp (do not rely on `surfaceColorAtElevation`).

Widget parity: `ui/widget/WidgetPalette.kt` must be generated from the same tokens (it currently copies hex values by hand with a "mirrors Theme.kt" comment, which is a second source of truth waiting to drift).
---

## 9. What the current build does that this system forbids

Verified by reading the code on 5 Nov 2026, not assumed. Each line is a real conflict to fix.

**Colour and theme**

1. `ui/theme/Color.kt` still exports the Material baseline purple (`Purple80 #D0BCFF`, `Purple40 #6650a4`, `PurpleGrey40 #625b71`, `Pink40 #7D5260`) and `Theme.kt` uses three of them as the real scheme. Stock purple is the shipped brand.
2. `Theme.kt` sets only `primary`, `secondary`, `tertiary` and `background`. Every remaining role falls back to the Material default (`surface #FFFBFE`, `onSurface #1C1B1F`), so the app renders stock M3 neutrals, not paper.
3. Dynamic colour is on by default in three places: `MemoGalleryTheme(dynamicColor = true)`, `MainActivity` reading `dynamicThemingEnabledFlow.collectAsState(initial = true)`, and `SettingsRepository` line 248 `?: true`. The rule is off by default.
4. The custom seed picker in `SettingsScreen` offers 20 raw Android palette colours, starting with the Material default `#6750A4`, plus pure `#000000`, `#FFEB3B` and `#9E9E9E`. Selection logic also treats "no choice" as `#6750A4`.
5. `MemoryCard.generateRandomGradientBrush` picks one of five loud gradient pairs by memory id (`#6A11CB` to `#2575FC`, `#FC00FF` to `#00DBDE`, `#F7971E` to `#FFD200`, `#EE0979` to `#FF6A00`, `#00C6FF` to `#0072FF`) and paints 180dp of it behind every text-only memory. That is five off-brand palettes shipped at random.
6. There are no success, warning, info, capture or memory-tint tokens at all. Status is carried by an icon only (an hourglass for `PENDING`).
7. AMOLED mode overrides six roles and sets `surfaceContainerHigh = #121212`, which is lighter than its `surface = #000000` in three of the five cases but leaves the rest of the ramp at the non-AMOLED values, so cards and sheets do not agree.
8. `values/themes.xml` paints the window `#FFFFFF` (light) and `#121212` (night) while the Compose surface is `#F7F5F8` / `#191022`. That mismatch is the flash on cold start.

**Type**

9. `Type.kt` overrides one style (`bodyLarge`, and even that uses `FontFamily.Default` with 0.5sp tracking). Every other role is Roboto at Material's defaults, which is the definition of "default Material type".
10. There is no `res/font` directory. Nothing is bundled, so the app cannot look non-stock on any device.

**Shape and space**

11. There is no `Shapes` override anywhere. Material's default radii apply, while individual composables override twelve different values: `RoundedCornerShape(12.dp)` x44, `16.dp` x30, `20.dp` x9, `24.dp` x6, `50` (percent pill) x4, `10.dp` x2, `28.dp` x2, `2.dp` x2, `6.dp`, `8.dp`, `18.dp`, and two asymmetric corner pairs (32/32 top, 24/24 top, and `20,20,4,20`).
12. Spacing is ad hoc: alongside the 4/8/12/16/24 spine there are `5.dp`, `6.dp`, `9.dp`, `10.dp`, `14.dp` and `18.dp` in shipped code, plus 2dp borders used where hairlines belong (24 uses of `2.dp`).
13. Tap targets below 48dp: 44dp icon buttons in `FullscreenImageViewer`, 40dp play button in `AudioPlayer`, 32dp sheet handles.

**Structure and interaction**

14. There is no bottom navigation in the app at all: zero `NavigationBar` and zero `NavigationBarItem` in the codebase (`NavigationBar` matches are all `navigationBarsPadding`). Gallery is a hub reached from a start destination, and Settings, Tasks and Chat are pushed routes.
15. Two FABs are stacked in `GalleryScreen` (lines 163 and 171), which breaks "one FAB, and it is the primary action".
16. Centred stock dialogs on a phone: `CreateCollectionDialog` uses `AlertDialog`, and the build has 7 `AlertDialog` and 14 `Dialog(` call sites against 32 `ModalBottomSheet` uses. The sheet direction is right, the dialogs are not.
17. `aiTags` are drawn as `SuggestionChip` (a pill) in `MemoryDetailScreen` line 511 though they are not pressable. Class A pill misuse, and it recurs wherever tags are rendered.
18. Loading is a spinner, not a skeleton: 15 `CircularProgressIndicator` and 2 `LinearProgressIndicator` uses, no shimmer, no mirrored skeleton.
19. `ContributionGraph` fakes its levels with `primaryColor.copy(alpha = ...)` on top of unknown surfaces, and renders its legend at `fontSize = 10.sp`, below the 11sp floor. It also computes columns from `LocalConfiguration.screenWidthDp` minus a hardcoded 32dp, which breaks on landscape and foldables.
20. `WidgetPalette.kt` re-declares the palette by hand (`WidgetDefaultAccent`, `WidgetBackgroundDark`, `WidgetBackgroundLight`, and six hardcoded on-surface hex values) with comments saying it mirrors `Theme.kt`. Two sources of truth guaranteed to drift, and it already has: it uses `#EDEDF2` where the theme has no such token.

Nothing above is a judgement call except item 3 and item 4, which need a decision (see 10).

---

## 10. Decisions this document needs from Ayanda

1. **Ember as the locked accent.** `#8C4A10` / `#F0B183`. Approve or name a different one; the schemes are generated so swapping the seed rewrites both tables.
2. **Dynamic colour off by default,** on as an opt-in. This inverts current behaviour, so it is a product decision, not a technical one.
3. **Custom accents reduced to eight curated seeds,** removing the 20-colour Android palette and the default purple.
4. **A bottom navigation with Gallery, Collections, Tasks, Chat.** This changes the information architecture (Settings moves behind the avatar, capture moves to the floating capture bar).
5. **`CollectionEntity.color` becomes an index into the six memory tints,** not a free ARGB value. Existing per-collection colours would be mapped on first run (nearest tint) and the column documented as an enum.
6. **Manrope bundled,** roughly 200 KB of APK across four weights, plus the OFL licence file in assets.
7. **AMOLED ramp values** get one on-device check on an OLED phone before they are treated as final; the values in 2.4 are computed, not yet seen on glass.

Until these are settled, sections 2 to 8 stand as written and every new screen follows them; the seven open items only affect the theme files themselves and the navigation shell.

---

## 11. Definition of done for any screen

A screen is finished when:

- It uses no colour, radius, size or duration outside these tokens (grep for `0xFF`, `.dp`, `ms` catches nearly everything).
- It has a real empty, loading and error state.
- Every choice or confirmation on it is a bottom sheet.
- One accent, one filled button, one title.
- It has been looked at, not just built: light and dark, 390dp and 1440dp, both with the keyboard up where a field exists, and with animations off.
- It reads correctly with the largest system font size (1.3x) without clipping.
---

## 12. The HTML mocks in `DOCS/ui/mocks` and this document

While this system was being written, a parallel helper built screen mocks (`board.html`, `p1` to `p7.html`) with a `fonts/Manrope-var.ttf` and a CSS variable set. The mocks and this document currently disagree on colour. Recorded here so the disagreement is decided on purpose rather than discovered later.

**What the mocks use:** primary `#5B4BFF` (an electric blue-violet), `#B9AEFF` in dark, secondary coral `#FF6B4A`, tertiary lime `#C6F24E`, surfaces `#FBFAF7` and `#0E0D12`.

**What this document specifies:** Ember `#8C4A10` / `#F0B183`, warm paper `#FBF8F3`, lamplight `#17130E`, one accent.

Measured on the mocks' own pairings:

| Pair | Ratio | Verdict |
|---|---|---|
| white on `#5B4BFF` | 5.39:1 | passes |
| `#5B4BFF` as text on `#FBFAF7` | 5.17:1 | passes |
| coral `#FF6B4A` as text on `#FBFAF7` | 2.70:1 | fails 4.5, only usable as a fill |
| lime `#C6F24E` as text on `#FBFAF7` | 1.24:1 | unusable except as a shape |

Two more things about the mocks: their `--scl` / `--sclow` naming is inverted against Material (`--scl` is the lightest surface, which Material calls `surfaceContainerLowest`), and `board.html` loads Manrope from the Google Fonts CDN. That CDN call is fine in a local mock but can never ship: this app is zero-cloud, and a font request is a network call and a tracking surface. The app bundles the TTFs.

**The rule:** this document governs code. The mocks are illustrations and follow it. To re-tint a mock, replace its `.lt` and `.dk` blocks with these (variable names kept, so nothing else in the file changes):

```css
.lt{--surface:#FBF8F3;--scl:#FFFFFF;--sclow:#F6F1E8;--sc:#F0EBE1;--sch:#EAE4D9;
    --on:#1E1A14;--onv:#4E463A;--out:#7F7566;--outv:#D1C7B6;
    --pri:#8C4A10;--onpri:#FFFFFF;--pric:#FFDCC2;--onpric:#2F1503;
    --sec:#6B5A45;--ter:#46625E;--err:#B3261E;--ok:#1F6B3A}

.dk{--surface:#17130E;--scl:#110E0A;--sclow:#1F1A14;--sc:#241F18;--sch:#2F2921;
    --on:#ECE5D9;--onv:#CBC2B3;--out:#978D7E;--outv:#4A4238;
    --pri:#F0B183;--onpri:#4A2A08;--pric:#6C3C0B;--onpric:#FFDCC2;
    --sec:#D6C4AC;--ter:#AECCC6;--err:#F2B8B5;--ok:#8FD6A3}
```

Then: any `.card` painted with `--sclow` becomes `--sc`; add `--warn:#7A5B00;--warnc:#FFE9A8;--inf:#24527A;--infc:#D3E4F7` (dark: `#E4C65C`, `#4A3B00`, `#A9C8E8`, `#1B3A57`) for status; and add the six memory tints from 2.6 if a mock shows tags.

**Decision for Ayanda:** ember or the mocks' indigo. Both are internally consistent; only one may ship. The argument for ember is in 2.1 and it is the one this document recommends: indigo-violet is the closer cousin of the stock purple the app is trying to escape, and the mocks' coral and lime fail contrast as text.
