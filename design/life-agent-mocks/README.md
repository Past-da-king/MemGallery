# MemGallery: Life Agent mocks (6 Nov 2026)

Design mocks ONLY. No app code, no Gradle, no Kotlin changes. These are high-fidelity phone stills for a new
direction: MemGallery's chat becomes a personal life agent fed by the user's own connected apps (read only), and
today's report also lives on the home screen as an interactive widget.

Ayanda's brief (6 Nov 2026, 14:13 to 14:19) is the source of truth for what is on each screen. Read
`SAMPLE-DATA.md` for the fictional people, tasks, mails and meetings: every screen uses that same set, so the
screens tell one story.

## The visual language (do not invent a new one)
The app shipped v0.4.0 in Material 3 Expressive, built from the v4 mocks. Those mocks are copied here as `_kit/`
and are the quality bar: violet-indigo seed, tonal surfaces, the blob header, shape library (cookie9, clover,
soft burst, sunny, puffy, gem, arch, pill), Google Sans Flex, big emphasized titles, floating nav pill, tonal
`MemTextField`, connected button groups, slide-up sheets.

- `_kit/tokens.css` holds every colour, radius, elevation and font token. Use only these tokens, never new hexes.
- `_kit/components.css` holds the components (`.ib`, `.chip`, `.btn`, `.shp`, `.abs`, grouped lists, switches...).
- `_kit/kit.js` and `_kit/kit2.js` hold `mount()`, `ic()` (Material Symbols), the shape library `clip()`,
  generated art `artFill()` and `blobHeader()`.
- Reference screens to copy patterns from: `_kit/01-home.html` (blob header, nav, filters), `_kit/06-ask.html`
  (chat with sources), `_kit/09-settings.html` (settings rows), `_kit/11-sheet.html` (slide-up sheet),
  `_kit/12-empty.html` (empty state), `_kit/08-tasks.html` (grouped lists).
- **Never edit `_kit/`.** Add your own `<group>.css` next to your screens and link it after the kit files.

## Rules
1. **412 x 915 dp, rendered at 2x (824 x 1830 px)**, light and dark of every screen.
2. **No centred dialogs.** Every choice, form and confirmation is a slide-up sheet from the bottom with a drag
   handle, in the app's own design language.
3. Fictional sample data only, exactly as `SAMPLE-DATA.md` has it. Never Ayanda's real mail, contacts or numbers.
4. Real Android patterns: status bar 40dp with time, 24dp gesture bar at the bottom, 48dp touch targets, 16dp
   screen margins, 4dp grid.
5. Dark is switched with `<html class="dark">`. Star: nothing may look grey and flat in dark; surfaces are
   `--surface-container-*` tones, text is `--on-surface` / `--on-surface-variant`.

## Screens and file names
Write `<slug>-light.html`, then generate `<slug>-dark.html` from it (same file with `class="dark"` on `<html>`).
Render both to `png/<slug>-<theme>.png`.

| # | slug | what it is |
|---|------|------------|
| 01 | `connections` | Settings > Advanced > Connections: the user's own Composio key (masked field with a "Change key" action), app rows (Gmail, Google Calendar, ClickUp, Slack, Drive) each showing connected or not and "last pulled 17:04", a read-only note, connect/disconnect in a slide-up sheet |
| 02 | `collecting` | The 17:00 daily collection running: step by step over the apps, per-app progress with what it found and what it dropped, cancel, "keep running in background" |
| 03 | `review-deck` | Proposed memories as swipe cards: the top card fully readable with its source app chip, the reason it was picked, and keep/drop affordances plus the next cards peeking behind; a counter ("4 of 11"); Approve all in the bar |
| 04 | `approve-all` | The Approve all confirmation as a slide-up sheet over the deck: what it will keep, what it will drop, and the review-them-first alternative |
| 05 | `caught-up` | Empty state: everything reviewed, what was learned ("you dropped 6 newsletters and 2 receipts"), next collection at 17:00 tomorrow |
| 06 | `daily-report` | The in-app Daily report screen: what to be aware of, due today, overdue, today's meetings, still open, with a date header and a "collected 17:04" line |
| 07 | `people` | People list: shaped avatars, who they are to you, last memory, open commitments, A-Z or recent grouping, search |
| 08 | `person` | One person page: header with avatar and role, open commitments both ways, recent memories with sources, their details (mail, phone), "ask about Nomsa" |
| 09 | `nudge` | A nudge as a real notification: lock screen with the banner plus the shade entry ("Send the signed lease to Nomsa is due by 16:00 tomorrow and still open"), snooze and mark done actions |
| 10 | `chat-week` | Chat answering "What do I need to handle this week?": a grouped answer with [1] [2] [3] sources (a mail, a task, a meeting), tappable source rows, follow-up chips |
| 11 | `widget-small` | 2x2 home-screen widget on a realistic wallpaper: count of things due plus the top one, with a tick and an Ask action |
| 12 | `widget-medium` | 4x2 widget: the top 3 to be aware of plus the next meeting |
| 13 | `widget-large` | 4x4 widget: the full report (due, overdue, meetings, open items) as a scrollable list, with a refresh button |
| 14 | `widget-taps` | Before and after of a tap, as a board: tick an item done (strikethrough, count drops), snooze a nudge (1h / tomorrow slide-up), keep or drop a proposed memory with the two buttons (mini review, card leaves), Ask opening chat with that item as context |

The widget is interactive (Jetpack Glance): mark an item done (held in MemGallery, writing back to ClickUp comes
later), snooze 1h or tomorrow, keep or drop a proposed memory without opening the app, a scrollable list on the
large size, refresh, and Ask (opens chat with the item as context). Tapping a row opens it in the app.

## Widget geometry (Android 4x4 grid)
Home screen is 412 x 915 dp: 16dp side margins, four columns of app icons (4 x 76dp cells + 12dp gaps), a dock of
four icons, a status bar and a date/clock block. Common inner dimensions:
- 2x2: about 164 x 164 dp (icon cell pair), radii 28dp.
- 4x2: about 348 x 164 dp.
- 4x4: about 348 x 348 dp.
Draw a realistic wallpaper: a soft photographic gradient (dusk sky, sand and sea, or a foliage blur) with a subtle
vignette, never a flat colour, and a few recognisable but generic app icons in the dock (phone, messages, camera,
browser) drawn as simple shapes in neutral tones. The widget must sit like a real Glance widget: tinted container,
tonal surfaces inside, `--surface-container*` at about 92% opacity, 28dp corners, real shadow.

## Rendering
Sources are plain HTML, so a headless browser makes the PNG. From the mock folder:

```bash
# preferred: playwright (install once per machine if missing: npm i -D playwright && npx playwright install chromium)
node render.mjs            # renders every *-light.html / *-dark.html at 412x915 @2x to png/
```

`render.mjs` shape (write it once in this folder):

```js
import { chromium } from 'playwright';
import { readdirSync } from 'fs';
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
for (const f of readdirSync('.').filter(f => f.endsWith('.html'))) {
  await p.goto('file://' + process.cwd() + '/' + f);
  await p.waitForTimeout(400);                       // fonts, generated art, symbols
  await p.screenshot({ path: 'png/' + f.replace('.html', '.png') });
}
await b.close();
```

Chrome fallback (no playwright): `--headless --hide-scrollbars --force-device-scale-factor=2 --window-size=412,915
--screenshot=png/x.png "file:///.../x-light.html"`. Check the PNG really is 824 x 1830 before you call it done.

## Done means
- Every screen in your set exists in light and dark, PNG at 824 x 1830, and you have LOOKED at each one yourself
  (crop-zoom on type, spacing and the sheets) and fixed what looked wrong.
- Committed on `design/life-agent-mocks` with a plain message, and pushed.
