/* shared memory tiles */
const TONE = { p: ['--primary-container', '--on-primary-container'], s: ['--secondary-container', '--on-secondary-container'], t: ['--tertiary-container', '--on-tertiary-container'], g: ['--success-container', '--on-success-container'], k: ['--sky-container', '--on-sky-container'], a: ['--amber-container', '--on-amber-container'] };
const tone = k => `background:var(${TONE[k][0]});color:var(${TONE[k][1]});`;
function imgTile(name, o = {}) {
  const r = o.r ?? 20;
  return `<div class="tile" style="border-radius:${r}px;${o.ratio ? `aspect-ratio:${o.ratio};` : ''}${o.style || ''}">${o.ratio ? artFill(name) : ART[name]()[2]}${o.badge ? `<span style="position:absolute;left:8px;bottom:8px;height:24px;padding:0 8px 0 6px;border-radius:12px;background:rgba(20,16,40,.55);color:#fff;display:flex;align-items:center;gap:4px;font:600 11px/16px var(--font);backdrop-filter:blur(8px)">${ic(o.badge[0], 's18 f')}${o.badge[1] || ''}</span>` : ''}${o.sel ? `<span style="position:absolute;left:8px;top:8px;width:24px;height:24px;border-radius:12px;background:var(--primary);color:var(--on-primary);display:grid;place-items:center">${ic('check', 's18 b')}</span>` : ''}</div>`;
}
function noteTile(title, text, t = 'p', o = {}) {
  return `<div style="border-radius:${o.r ?? 20}px;padding:16px;${tone(t)}${o.style || ''}"><div style="display:flex;align-items:center;gap:6px;opacity:.8;margin-bottom:8px">${ic('sticky_note_2', 'f s18')}<span class="label-s">NOTE</span></div><div class="title-m-em" style="margin-bottom:4px">${title}</div><div class="body-m" style="opacity:.85">${text}</div></div>`;
}
function wave(n, seed, h = 40, w = 3, gap = 3, col = 'currentColor') {
  const r = rng(seed); let s = ''; for (let i = 0; i < n; i++) { const v = (Math.sin(i / 3) * .35 + .55) * (.35 + r() * .65); s += `<rect x="${i * (w + gap)}" y="${(h - v * h) / 2}" width="${w}" height="${v * h}" rx="${w / 2}" fill="${col}"/>`; }
  return `<svg width="${n * (w + gap)}" height="${h}" viewBox="0 0 ${n * (w + gap)} ${h}">${s}</svg>`;
}
function audioTile(title, dur, t = 'k', o = {}) {
  return `<div style="border-radius:${o.r ?? 20}px;padding:16px;${tone(t)}${o.style || ''}"><div style="display:flex;align-items:center;gap:6px;opacity:.8;margin-bottom:12px">${ic('mic', 'f s18')}<span class="label-s">VOICE · ${dur}</span></div><div style="margin-bottom:12px">${wave(18, 5, 44, 3, 3.4)}</div><div class="title-m-em">${title}</div></div>`;
}
function linkTile(host, title, t = 'g', o = {}) {
  return `<div style="border-radius:${o.r ?? 20}px;padding:16px;${tone(t)}${o.style || ''}"><div style="display:flex;align-items:center;gap:6px;opacity:.8;margin-bottom:8px">${ic('link', 'b s18')}<span class="label-s">${host}</span></div><div class="title-m-em">${title}</div></div>`;
}

/* ---- Home-style blob header: eyebrow, huge title, lavender blob, round tonal buttons (one cookie), stat tiles, heatmap, floating slot ---- */
function heat() { const r = rng(21); let g = ''; for (let c = 0; c < 28; c++) { g += '<div style="display:flex;flex-direction:column;gap:3px">'; for (let q = 0; q < 6; q++) { const v = r(); const a = v > .8 ? .95 : v > .55 ? .6 : v > .3 ? .3 : .12; g += `<span style="width:10px;height:10px;border-radius:3.5px;background:color-mix(in srgb,var(--primary) ${a * 100}%,transparent)"></span>`; } g += '</div>'; } return g; }
const statTile = (n, l, st) => `<div style="flex:1;border-radius:20px;padding:10px 14px;${st}"><div class="title-l-em" style="line-height:24px">${n}</div><div class="label-s" style="opacity:.75">${l}</div></div>`;
const TILE3 = ['background:color-mix(in srgb,var(--on-primary-container) 10%,transparent)', 'background:var(--tertiary-container);color:var(--on-tertiary-container)', 'background:var(--primary);color:var(--on-primary)'];
const searchPill = (ph = 'Search memories…') => `<div style="height:56px;border-radius:28px;background:var(--surface-container-lowest);box-shadow:var(--e3);display:flex;align-items:center;gap:16px;padding:0 20px 0 18px;color:var(--on-surface-variant)">${ic('search')}<span class="body-l" style="flex:1">${ph}</span>${ic('tune')}</div>`;
function blobHeader(o) {
  const h = o.h || 318;
  const back = o.back ? `<div class="ib" style="position:absolute;left:4px;top:42px;color:var(--on-primary-container);z-index:3">${ic('arrow_back')}</div>` : '';
  const tiles = (o.tiles || []).map((t, i) => statTile(t[0], t[1], TILE3[i])).join('');
  const btns = o.nobtn ? '' : `<div style="display:flex"><div class="ib"><span style="position:absolute;inset:4px;border-radius:50%;background:color-mix(in srgb,var(--on-primary-container) 12%,transparent)"></span><span style="position:relative">${ic(o.icon1 || 'insights')}</span></div><div class="ib"><span style="position:absolute;inset:4px;background:var(--primary);${clip('cookie9')}"></span><span style="position:relative;color:var(--on-primary)">${ic(o.icon2 || 'settings', 'f')}</span></div></div>`;
  return `<div class="abs" style="top:0;left:0;right:0;height:${h}px;background:var(--primary-container);border-radius:0 0 48px 48px;overflow:hidden;color:var(--on-primary-container)">
  <div class="abs" style="right:-70px;top:-60px;width:260px;height:260px;background:color-mix(in srgb,var(--primary) 16%,transparent);transform:rotate(14deg);${clip('cookie9')}"></div>
  <div class="abs" style="left:-70px;top:${h - 88}px;width:170px;height:170px;background:color-mix(in srgb,var(--tertiary) 14%,transparent);${clip('clover4')}"></div></div>
${back}
<div class="abs" style="top:46px;left:${o.back ? 56 : 16}px;right:8px;display:flex;justify-content:space-between;align-items:flex-start;color:var(--on-primary-container)">
  <div><div class="label-l" style="letter-spacing:1.4px;opacity:.8">${o.eyebrow}</div><div class="${o.small ? 'headline-l-em' : 'display-m-em'}" style="margin-top:2px">${o.title}</div></div>${btns}
</div>
${tiles ? `<div class="abs" style="top:132px;left:16px;right:16px;display:flex;gap:8px;color:var(--on-primary-container)">${tiles}</div>` : ''}
${o.graph ? `<div class="abs" style="top:202px;left:16px;display:flex;gap:3px">${heat()}</div>` : ''}
${o.float ? `<div class="abs" style="top:${h - 24}px;left:16px;right:16px">${o.float}</div>` : ''}`;
}
const homeHeader = () => blobHeader({ eyebrow: '5 NOV · 412 KEPT', title: 'Memories', tiles: [['38', 'THIS WEEK'], ['9', 'TO REVIEW'], ['5', 'DAY STREAK']], graph: 1, float: searchPill() });
