// Renders the screens in this set (06 to 10): makes each -dark.html from its -light.html, then PNGs at 824x1830.
// Run: ~/bin/build-slot node render-b.mjs [slug-filter]
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'fs';
import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const dir = dirname(fileURLToPath(import.meta.url));
const req = createRequire(import.meta.url);
let chromium;
for (const p of ['playwright', '/home/impetix/scvns-local/node_modules/playwright', '/home/impetix/sei-sec/node_modules/.pnpm/playwright@1.63.0/node_modules/playwright']) {
  try { const c = req(p).chromium; if (existsSync(c.executablePath()) || existsSync(c.executablePath().replace('chrome-linux/headless_shell','chrome-headless-shell-linux64/chrome-headless-shell'))) { chromium = c; break; } } catch {}
}
if (!chromium) throw new Error('playwright not found: npm i playwright && npx playwright install chromium');

const SCREENS = [['06', 'daily-report'], ['07', 'people'], ['08', 'person'], ['09', 'nudge'], ['10', 'chat-week']];
const only = process.argv[2];
mkdirSync(join(dir, 'png'), { recursive: true });

// dark = same markup, class="dark" on <html>. kit.js resets the class from ?dark, so re-add it after the kit loads.
for (const [n, s] of SCREENS) {
  const light = readFileSync(join(dir, `${n}-${s}-light.html`), 'utf8');
  const dark = light
    .replace('<html>', '<html class="dark">')
    .replace('<script src="_kit/kit2.js"></script>', '<script src="_kit/kit2.js"></script><script>document.documentElement.classList.add("dark")</script>');
  writeFileSync(join(dir, `${n}-${s}-dark.html`), dark);
}

const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
for (const [n, s] of SCREENS) {
  if (only && !s.includes(only)) continue;
  for (const t of ['light', 'dark']) {
    await p.goto('file://' + join(dir, `${n}-${s}-${t}.html`));
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(500);
    const out = join(dir, 'png', `${s}-${t}.png`);
    await p.screenshot({ path: out });
    const buf = readFileSync(out);
    const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
    console.log(`${s}-${t}.png ${w}x${h} ${w === 824 && h === 1830 ? 'ok' : 'WRONG SIZE'}`);
  }
}
await b.close();
