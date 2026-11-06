/* MemGallery v4 kit: theme switch, M3E shape library (clip paths), generated artwork, small builders. */
const DARK = location.search.includes('dark');
document.documentElement.classList.toggle('dark', DARK);

/* ---------- shape library (objectBoundingBox clip paths) ---------- */
function pathFromR(fn, N = 360) {
  let d = '';
  for (let k = 0; k < N; k++) {
    const th = (2 * Math.PI * k) / N, r = fn(th);
    const x = 0.5 + 0.5 * r * Math.cos(th), y = 0.5 + 0.5 * r * Math.sin(th);
    d += (k ? 'L' : 'M') + x.toFixed(4) + ' ' + y.toFixed(4);
  }
  return d + 'Z';
}
function normMax(fn) { let m = 0; for (let k = 0; k < 360; k++) m = Math.max(m, fn(2 * Math.PI * k / 360)); return th => fn(th) / m; }
function polyR(m, rot, soft) { // rounded regular polygon
  return th => { const a = ((th - rot) % (2 * Math.PI / m) + 2 * Math.PI / m) % (2 * Math.PI / m) - Math.PI / m;
    const p = Math.cos(Math.PI / m) / Math.cos(a); return p * (1 - soft) + soft; };
}
const SHAPES = {
  cookie9: normMax(th => 1 + 0.075 * Math.cos(9 * th)),
  cookie7: normMax(th => 1 + 0.085 * Math.cos(7 * th)),
  clover4: normMax(th => 1 + 0.20 * Math.cos(4 * th - Math.PI / 4 * 0)),
  softburst: normMax(th => 1 + 0.07 * Math.cos(12 * th) + 0.015 * Math.cos(24 * th)),
  sunny: normMax(th => 1 + 0.11 * Math.pow(Math.abs(Math.cos(4 * th)), .6)),
  flower: normMax(th => 1 + 0.11 * Math.cos(8 * th) + 0.02 * Math.cos(16 * th)),
  puffy: normMax(th => 1 + 0.12 * Math.cos(5 * th - Math.PI / 2)),
  gem: normMax(polyR(6, Math.PI / 6, .55)),
  pent: normMax(polyR(5, -Math.PI / 2, .6)),
  diamond: normMax(polyR(4, Math.PI / 4, .55)),
  triangle: normMax(polyR(3, -Math.PI / 2, .62)),
};
function installShapes() {
  let s = '<svg width="0" height="0" style="position:absolute"><defs>';
  for (const k in SHAPES) s += `<clipPath id="s-${k}" clipPathUnits="objectBoundingBox"><path d="${pathFromR(SHAPES[k])}"/></clipPath>`;
  s += `<clipPath id="s-arch" clipPathUnits="objectBoundingBox"><path d="M0 .22C0 .08 .2 0 .5 0S1 .08 1 .22V.93Q1 1 .93 1H.07Q0 1 0 .93Z"/></clipPath>`;
  s += `<clipPath id="s-arch2" clipPathUnits="objectBoundingBox"><path d="M0 .5A.5 .5 0 0 1 1 .5V.9Q1 1 .9 1H.1Q0 1 0 .9Z"/></clipPath>`;
  s += '</defs></svg>';
  document.body.insertAdjacentHTML('afterbegin', s);
}
const clip = n => `clip-path:url(#s-${n});-webkit-clip-path:url(#s-${n});`;

/* ---------- helpers ---------- */
const ic = (n, cls = '') => `<span class="i ${cls}">${n}</span>`;
let _u = 0; const uid = () => 'u' + (++_u);
function rng(seed) { return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }

/* ---------- generated artwork (royalty-free, no real people or data) ---------- */
const grain = (id, w, h) => `<filter id="${id}n" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="4"/><feColorMatrix values="0 0 0 0 .5  0 0 0 0 .5  0 0 0 0 .5  0 0 0 .09 0"/></filter><rect width="${w}" height="${h}" filter="url(#${id}n)"/>`;
const svg = (w, h, inner) => `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="${h > w * 1.6 ? "xMidYMin" : "xMidYMid"} slice" xmlns="http://www.w3.org/2000/svg" font-family="'Google Sans Flex',sans-serif">${inner}</svg>`;
const ART = {};
ART.dunes = () => { const u = uid(); return [400, 300, svg(400, 300, `
<defs><linearGradient id="${u}a" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#24286b"/><stop offset=".5" stop-color="#b8506f"/><stop offset=".85" stop-color="#ff9a62"/><stop offset="1" stop-color="#ffd28a"/></linearGradient>
<radialGradient id="${u}b"><stop offset="0" stop-color="#fff2c9"/><stop offset=".3" stop-color="#ffd58a" stop-opacity=".8"/><stop offset="1" stop-color="#ff9a62" stop-opacity="0"/></radialGradient></defs>
<rect width="400" height="300" fill="url(#${u}a)"/><circle cx="270" cy="170" r="120" fill="url(#${u}b)"/><circle cx="270" cy="172" r="26" fill="#fff0c2"/>
<path d="M0 190C60 170 120 185 190 205S330 200 400 180V300H0Z" fill="#7b3560"/>
<path d="M0 225C70 200 150 215 220 235S340 225 400 215V300H0Z" fill="#4a2459"/>
<path d="M0 262C90 240 170 252 250 268S360 262 400 255V300H0Z" fill="#2a1646"/>${grain(u, 400, 300)}`)]; };
ART.peaks = () => { const u = uid(); return [400, 500, svg(400, 500, `
<defs><linearGradient id="${u}a" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6aa4f5"/><stop offset=".7" stop-color="#e8f1ff"/></linearGradient></defs>
<rect width="400" height="500" fill="url(#${u}a)"/>
<path d="M-20 330L90 170L150 250L230 120L330 260L420 190V500H-20Z" fill="#8fa9d8"/>
<path d="M230 120L200 165L225 160L240 185L262 158L282 190L330 260L290 230L260 215L240 200L215 190L200 165Z" fill="#fff" opacity=".9"/>
<path d="M90 170L65 215L85 208L100 228L118 205L150 250L120 235L90 215Z" fill="#fff" opacity=".85"/>
<path d="M-20 390L80 300L170 380L260 290L360 380L420 330V500H-20Z" fill="#587bb4"/>
<path d="M-20 440L110 370L200 430L300 360L420 430V500H-20Z" fill="#2f4d82"/>
<g fill="#0f2340">${Array.from({ length: 11 }, (_, i) => { const x = 10 + i * 38, h = 70 + (i * 37 % 40); return `<path d="M${x} 500L${x + 20} ${500 - h}L${x + 40} 500Z"/>`; }).join('')}</g>${grain(u, 400, 500)}`)]; };
ART.latte = () => { const u = uid(); return [400, 400, svg(400, 400, `
<defs><linearGradient id="${u}a" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#c99a6b"/><stop offset="1" stop-color="#8f6240"/></linearGradient>
<radialGradient id="${u}b"><stop offset="0" stop-color="#c88e58"/><stop offset="1" stop-color="#7d4a28"/></radialGradient></defs>
<rect width="400" height="400" fill="url(#${u}a)"/>${[0, 1, 2, 3, 4, 5, 6, 7].map(i => `<rect x="0" y="${i * 52}" width="400" height="2" fill="#00000018"/>`).join('')}
<ellipse cx="212" cy="222" rx="170" ry="166" fill="#00000026"/><circle cx="200" cy="210" r="160" fill="#f6f0e8"/><circle cx="200" cy="210" r="132" fill="#fff"/><circle cx="200" cy="210" r="112" fill="url(#${u}b)"/>
<path d="M200 300C150 262 140 220 168 192C186 175 200 190 200 204C200 190 214 175 232 192C260 220 250 262 200 300Z" fill="#f4e4cd"/>
<path d="M200 285C165 255 160 225 178 208" fill="none" stroke="#c88e58" stroke-width="3" opacity=".5"/>
<path d="M312 210a18 18 0 1 1 0 .1" fill="none" stroke="#f6f0e8" stroke-width="14"/>${grain(u, 400, 400)}`)]; };
ART.plant = () => { const u = uid(); const leaves = [[200, 300, -52, 1], [200, 300, -20, 1.1], [200, 300, 14, 1.15], [200, 300, 46, 1], [200, 300, -78, .8], [200, 300, 74, .85]]; return [400, 520, svg(400, 520, `
<defs><linearGradient id="${u}a" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#efe0cb"/><stop offset="1" stop-color="#d9bf9f"/></linearGradient>
<linearGradient id="${u}l" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#4e9a5d"/><stop offset="1" stop-color="#1f5a3a"/></linearGradient></defs>
<rect width="400" height="520" fill="url(#${u}a)"/><rect y="430" width="400" height="90" fill="#c9a77d"/>
${leaves.map(([x, y, a, s]) => `<g transform="translate(${x} ${y}) rotate(${a}) scale(${s})"><path d="M0 0C-70 -60 -60 -190 0 -230C60 -190 70 -60 0 0Z" fill="url(#${u}l)"/><path d="M0 0V-215" stroke="#bfe3c4" stroke-width="3" opacity=".6"/><path d="M0 -60L-30 -90M0 -110L-36 -140M0 -160L-26 -184M0 -60L30 -90M0 -110L36 -140M0 -160L26 -184" stroke="#bfe3c4" stroke-width="2" opacity=".45" fill="none"/></g>`).join('')}
<path d="M120 330H280L262 450Q260 462 248 462H152Q140 462 138 450Z" fill="#c4683c"/><rect x="112" y="318" width="176" height="26" rx="10" fill="#d97c4d"/>${grain(u, 400, 520)}`)]; };
ART.coast = () => { const u = uid(); return [400, 300, svg(400, 300, `
<defs><linearGradient id="${u}a" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#9ed8ee"/><stop offset="1" stop-color="#fff1d6"/></linearGradient>
<linearGradient id="${u}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2aa3b0"/><stop offset="1" stop-color="#0f6f86"/></linearGradient></defs>
<rect width="400" height="300" fill="url(#${u}a)"/><rect y="150" width="400" height="150" fill="url(#${u}s)"/>
<path d="M0 150H400" stroke="#fff" stroke-opacity=".5"/><path d="M-10 60C60 50 120 90 150 170L170 300H-10Z" fill="#3d5a43"/><path d="M-10 60C60 50 120 90 150 170" fill="none" stroke="#7fbf7a" stroke-width="8"/>
<g stroke="#fff" stroke-width="3" fill="none" opacity=".75" stroke-linecap="round"><path d="M180 240q20-10 40 0t40 0 40 0M230 270q22-10 44 0t44 0M200 205q14-6 28 0t28 0"/></g>
<path d="M290 140L290 98L322 140Z" fill="#fff"/><path d="M286 142H326L318 152H294Z" fill="#a03a60"/>${grain(u, 400, 300)}`)]; };
ART.city = () => { const u = uid(), r = rng(9); let b = ''; let x = -10; while (x < 410) { const w = 34 + r() * 36, h = 120 + r() * 220; b += `<rect x="${x}" y="${460 - h}" width="${w}" height="${h}" fill="#17163d"/>`; for (let wy = 460 - h + 12; wy < 450; wy += 16) for (let wx = x + 6; wx < x + w - 8; wx += 12) if (r() > .55) b += `<rect x="${wx}" y="${wy}" width="5" height="7" fill="#ffd58a" opacity="${.5 + r() * .5}"/>`; x += w + 2; }
  return [400, 460, svg(400, 460, `<defs><linearGradient id="${u}a" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#171a54"/><stop offset=".55" stop-color="#7a3d83"/><stop offset="1" stop-color="#ff8a7a"/></linearGradient></defs><rect width="400" height="460" fill="url(#${u}a)"/><circle cx="300" cy="90" r="22" fill="#fff4d8"/>${b}${grain(u, 400, 460)}`)]; };

/* screenshots of generic apps (200 x 430) */
const T = (x, y, s, t, o = {}) => `<text x="${x}" y="${y}" font-size="${s}" font-weight="${o.w || 500}" fill="${o.c || '#222'}" ${o.a ? `text-anchor="${o.a}"` : ''} letter-spacing="${o.ls || 0}">${t}</text>`;
const bar = (x, y, w, c = '#0002') => `<rect x="${x}" y="${y}" width="${w}" height="6" rx="3" fill="${c}"/>`;
const qr = (x, y, s, seed, fg = '#111', bg = '#fff') => { const r = rng(seed), n = 17, c = s / n; let o = `<rect x="${x}" y="${y}" width="${s}" height="${s}" fill="${bg}"/>`; for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) { const fin = (i < 5 && j < 5) || (i > 11 && j < 5) || (i < 5 && j > 11); if (!fin && r() > .52) o += `<rect x="${x + i * c}" y="${y + j * c}" width="${c}" height="${c}" fill="${fg}"/>`; }
  for (const [fx, fy] of [[0, 0], [12, 0], [0, 12]]) o += `<rect x="${x + fx * c}" y="${y + fy * c}" width="${5 * c}" height="${5 * c}" fill="${fg}"/><rect x="${x + (fx + 1) * c}" y="${y + (fy + 1) * c}" width="${3 * c}" height="${3 * c}" fill="${bg}"/><rect x="${x + (fx + 2) * c}" y="${y + (fy + 2) * c}" width="${c}" height="${c}" fill="${fg}"/>`; return o; };
ART.ticket = () => { const u = uid(); return [200, 430, svg(200, 430, `
<rect width="200" height="430" fill="#0f2b4d"/><rect y="0" width="200" height="56" fill="#0b2140"/>${T(14, 34, 14, 'Rail Pass', { w: 700, c: '#fff' })}<circle cx="176" cy="30" r="10" fill="#ffffff22"/>
<rect x="14" y="76" width="172" height="300" rx="16" fill="#f7f3ea"/>
${T(28, 108, 9, 'DEPARTS', { w: 700, c: '#8a8576', ls: 1 })}${T(28, 134, 26, '08:42', { w: 700, c: '#102a4c' })}${T(28, 152, 11, 'Porto Campanhã', { c: '#333' })}
<path d="M28 168H172" stroke="#c9c2b0" stroke-dasharray="3 4"/>${T(28, 192, 9, 'ARRIVES', { w: 700, c: '#8a8576', ls: 1 })}${T(28, 216, 26, '11:20', { w: 700, c: '#102a4c' })}${T(28, 234, 11, 'Lisbon Oriente', { c: '#333' })}
${T(122, 108, 9, 'PLATFORM', { w: 700, c: '#8a8576', ls: 1 })}${T(122, 134, 26, '4', { w: 700, c: '#d6513b' })}${T(122, 192, 9, 'SEAT', { w: 700, c: '#8a8576', ls: 1 })}${T(122, 216, 20, '14A', { w: 700, c: '#102a4c' })}
<circle cx="14" cy="258" r="8" fill="#0f2b4d"/><circle cx="186" cy="258" r="8" fill="#0f2b4d"/><path d="M26 258H174" stroke="#c9c2b0" stroke-dasharray="3 4"/>
${qr(60, 276, 80, 7, '#102a4c', '#f7f3ea')}${T(100, 366, 9, 'Fri 6 Nov  ·  Coach 6', { a: 'middle', c: '#555' })}`)]; };
ART.wifi = () => { const u = uid(); return [200, 430, svg(200, 430, `
<rect width="200" height="430" fill="#eef3ff"/>${T(14, 34, 15, 'Wi-Fi', { w: 700, c: '#1b2a49' })}<circle cx="170" cy="30" r="10" fill="#1b2a4915"/>
<rect x="14" y="60" width="172" height="330" rx="22" fill="#fff"/><circle cx="100" cy="112" r="26" fill="#dfe8ff"/><path d="M80 108q20-18 40 0M88 117q12-10 24 0" stroke="#3b5bdb" stroke-width="4" fill="none" stroke-linecap="round"/><circle cx="100" cy="127" r="3.5" fill="#3b5bdb"/>
${T(100, 168, 16, 'Cedar Guest', { w: 700, c: '#1b2a49', a: 'middle' })}${T(100, 184, 9, 'WPA2 · Connected', { c: '#667', a: 'middle' })}
<rect x="30" y="204" width="140" height="44" rx="12" fill="#f1f4fb"/>${T(40, 222, 8, 'PASSWORD', { w: 700, c: '#778', ls: 1 })}${T(40, 240, 14, 'tide-lamp-4821', { w: 600, c: '#1b2a49' })}
${qr(64, 266, 72, 3, '#1b2a49')}${T(100, 360, 9, 'Scan to join', { c: '#667', a: 'middle' })}`)]; };
ART.recipe = () => { const u = uid(); return [200, 430, svg(200, 430, `
<defs><radialGradient id="${u}p"><stop offset="0" stop-color="#f2c26b"/><stop offset=".7" stop-color="#d98b3a"/><stop offset="1" stop-color="#b56a22"/></radialGradient></defs>
<rect width="200" height="430" fill="#fff8ee"/><rect width="200" height="170" fill="#f3e0c4"/><circle cx="100" cy="86" r="62" fill="#fff"/><circle cx="100" cy="86" r="52" fill="url(#${u}p)"/><circle cx="82" cy="76" r="14" fill="#fff0c4"/><circle cx="116" cy="94" r="12" fill="#fff0c4"/><circle cx="96" cy="104" r="8" fill="#6c8f3a"/><circle cx="124" cy="70" r="6" fill="#6c8f3a"/>
${T(16, 202, 17, 'Lemon Ricotta', { w: 700, c: '#3b2a14' })}${T(16, 222, 17, 'Pancakes', { w: 700, c: '#3b2a14' })}${T(16, 242, 10, '25 min  ·  Serves 4  ·  Easy', { c: '#8a7a64' })}
${T(16, 274, 10, 'INGREDIENTS', { w: 700, c: '#b56a22', ls: 1 })}
${['250 g ricotta', '2 lemons, zest and juice', '3 eggs, separated', '100 g plain flour', '1 tbsp honey'].map((t, i) => `<circle cx="22" cy="${296 + i * 22}" r="3" fill="#d98b3a"/>${T(34, 300 + i * 22, 11, t, { c: '#4a3a24' })}`).join('')}
${bar(16, 410, 168, '#0000000f')}`)]; };
ART.quote = () => { const u = uid(); return [200, 430, svg(200, 430, `
<defs><linearGradient id="${u}a" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#ffcfa8"/><stop offset=".6" stop-color="#ff9fb2"/><stop offset="1" stop-color="#b79bff"/></linearGradient></defs>
<rect width="200" height="430" fill="url(#${u}a)"/>${T(22, 130, 70, '“', { w: 700, c: '#ffffff99' })}
${['Small steps,', 'taken daily,', 'move', 'mountains.'].map((t, i) => T(22, 200 + i * 34, 28, t, { w: 700, c: '#3a1d4a' })).join('')}
${T(22, 364, 11, '— Proverb', { c: '#3a1d4a', w: 500 })}`)]; };
ART.map = () => { const u = uid(); return [200, 430, svg(200, 430, `
<rect width="200" height="430" fill="#e9efe4"/>
<path d="M-10 90L210 60M-10 200L210 170M-10 300L210 280M40 -10L70 440M150 -10L130 440" stroke="#fff" stroke-width="12" fill="none"/>
<path d="M-10 140L210 120M95 -10L100 440" stroke="#fff" stroke-width="6" fill="none"/><rect x="108" y="212" width="70" height="46" rx="6" fill="#cfe3c2"/><rect x="-6" y="318" width="50" height="60" rx="6" fill="#cfe3c2"/>
<path d="M-10 240Q60 250 90 210T180 130" stroke="#aac7e8" stroke-width="18" fill="none" opacity=".7"/>
<path d="M100 330L98 230L140 170" stroke="#3b6cf0" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M140 128c-14 0-24 10-24 24 0 18 24 40 24 40s24-22 24-40c0-14-10-24-24-24z" fill="#d6413a"/><circle cx="140" cy="152" r="8" fill="#fff"/>
<rect x="10" y="318" width="180" height="96" rx="18" fill="#fff"/>${T(24, 346, 14, 'Blue Door Café', { w: 700, c: '#1b1b21' })}${T(24, 366, 10, '4.6 ★  ·  Coffee shop  ·  8 min walk', { c: '#667' })}${T(24, 386, 10, 'Open until 18:00', { c: '#1f6b44', w: 600 })}`)]; };

const art = (name, style = '') => { const [w, h, s] = ART[name](); return `<div class="tile" style="aspect-ratio:${w}/${h};${style}">${s}</div>`; };
const artFill = (name, style = '') => { const [w, h, s] = ART[name](); return `<div style="position:absolute;inset:0;${style}">${s}</div>`; };

/* ---------- builders ---------- */
const status = () => `<div class="status"><span>9:41</span><span class="r">${ic('wifi', 'f')}${ic('signal_cellular_4_bar', 'f')}${ic('battery_full', 'f')}</span></div>`;
const gesture = () => '<div class="gesture"></div>';
const chip = (l, o = {}) => `<span class="chip ${o.sel ? 'sel' : ''} ${o.cls || ''}">${o.sel ? ic('check') : (o.icon ? ic(o.icon) : '')}${l}</span>`;
/* Floating nav, same language as the Agent phone app (HorizontalFloatingToolbar, vibrant colours, icon toggle buttons) + clean FAB */
const NAV = { ask: ['chat', 'Ask'], mem: ['photo_library', 'Memories'], task: ['task_alt', 'Tasks'] };
function toolbar(active, fabIcon = 'add', fabCls = '') {
  return `<div class="fade"></div><div class="ftb"><div class="bar">${Object.entries(NAV).map(([k, [i, l]]) => `<div class="ti ${active === k ? 'on' : ''}">${ic(i, active === k ? 'f' : '')}${active === k ? l : ''}</div>`).join('')}</div>${fabIcon ? `<div class="fab ${fabCls}">${ic(fabIcon, 'b')}</div>` : ''}</div>`;
}
/* connected button group filter: selected item = filled tonal full pill with icon + label, others icon buttons */
const FILT = [['all', 'apps', 'All'], ['images', 'image', 'Images'], ['notes', 'sticky_note_2', 'Notes'], ['audio', 'mic', 'Audio'], ['bookmarks', 'bookmark', 'Bookmarks'], ['collections', 'folder', 'Collections']];
function filters(sel = 'all', omit = []) {
  return `<div class="fg">${FILT.filter(f => !omit.includes(f[0])).map(([k, i, l]) => `<div class="fi ${k === sel ? 'on' : ''}">${ic(i, k === sel ? 'f' : '')}${k === sel ? l : ''}</div>`).join('')}</div>`;
}
function mount(html, extra = '') {
  document.body.innerHTML = `<div class="phone">${html}${status()}${gesture()}</div>`;
  installShapes();
}
