// Renders set A (connections, collecting, review-deck, approve-all, caught-up) to png/ at 412x915 @2x.
// Dark files and the two derived variants are generated from their light source so the markup never drifts.
// run: ~/bin/build-slot node render-a.mjs
import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { createRequire } from 'module';
import { dirname, resolve } from 'path';
import { fileURLToPath } from 'url';

const here = dirname(fileURLToPath(import.meta.url));
let pw;
for (const base of [here + '/', '/home/impetix/demos/']) { try { pw = createRequire(base)('playwright'); break; } catch {} }
if (!pw) throw new Error('playwright not found');

const OPEN = '<html lang="en">';
const rd = f => readFileSync(resolve(here, f), 'utf8');
const wr = (f, s) => writeFileSync(resolve(here, f), s);

// derived light variants: same file, one attribute on <html>
wr('connections-sheet-light.html', rd('connections-light.html').replace(OPEN, '<html lang="en" data-sheet>').replace('<title>connections</title>', '<title>connections-sheet</title>'));
wr('approve-all-light.html', rd('review-deck-light.html').replace(OPEN, '<html lang="en" data-approve>').replace('<title>review-deck</title>', '<title>approve-all</title>'));

const slugs = ['connections', 'connections-sheet', 'collecting', 'review-deck', 'approve-all', 'caught-up'];
for (const s of slugs) {
  const light = rd(s + '-light.html');
  wr(s + '-dark.html', light.replace(/<html lang="en"/, '<html lang="en" class="dark" data-force-dark'));
}

mkdirSync(resolve(here, 'png'), { recursive: true });
const b = await pw.chromium.launch();
const p = await b.newPage({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
p.on('pageerror', e => console.log('PAGE ERROR', e.message));
for (const s of slugs) for (const t of ['light', 'dark']) {
  await p.goto('file://' + resolve(here, `${s}-${t}.html`));
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(600);
  await p.screenshot({ path: resolve(here, `png/${s}-${t}.png`) });
  console.log('rendered', `${s}-${t}`);
}
await b.close();
