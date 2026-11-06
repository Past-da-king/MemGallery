// Renders the widget set (11 to 14): makes each -dark.html from its -light.html, then PNGs at 412x915 @2x (824x1830).
import { readdirSync, readFileSync, writeFileSync, statSync } from 'fs';
import { createRequire } from 'module';
let chromium;
try { ({ chromium } = await import('playwright')); } catch { chromium = (await import('/home/impetix/demos/node_modules/playwright/index.mjs')).chromium; }
const dir = new URL('.', import.meta.url).pathname;
const lights = readdirSync(dir).filter(f => /^1[1-4]-widget-.*-light\.html$/.test(f));
for (const f of lights) writeFileSync(dir + f.replace('-light', '-dark'), readFileSync(dir + f, 'utf8').replace('<html>', '<html class="dark" data-dark="1">'));
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
for (const f of readdirSync(dir).filter(f => /^1[1-4]-widget-.*\.html$/.test(f))) {
  await p.goto('file://' + dir + f);
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(500);
  await p.screenshot({ path: dir + 'png/' + f.replace('.html', '.png') });
  console.log('png/' + f.replace('.html', '.png'));
}
await b.close();
