/*
 * 부읽카 디자인 빌더 — Figma 로컬(개발용) 플러그인, UI 없음.
 *
 * 파일에 이미 있는 Pleos 변수, 텍스트 스타일, 그림자 스타일, 아이콘을 그대로 쓰고 아래를 만든다.
 *   ① Pleos 디자인 시스템: 04 타이포·그림자·모션·아이콘 보드, 05 컴포넌트 명세 보드
 *   ② 컴포넌트: Pleos 컴포넌트와 부읽카 전용 컴포넌트 (색과 치수는 변수에 연결)
 *   ③ 부읽카 화면: 가로·세로 화면, 다크 화면, 2560×1440 디스플레이 배치
 * 다시 실행하면 이 플러그인이 만든 노드만 지우고 새로 만든다.
 */
'use strict';

const NS = 'bureadcar';
const IDS = {
  light: 'VariableCollectionId:8:3',
  dark: 'VariableCollectionId:9:2',
  dim: 'VariableCollectionId:9:3',
  pages: { docs: '0:1', comps: '9:417', screens: '9:418' },
  iconsFrame: '13:2',
};
const ICON_IDS = {
  play: '13:5', pause: '13:9', skip_next: '13:13', replay: '13:17', close: '13:20', schedule: '13:24',
  settings: '13:29', back: '13:32', add: '13:35', check: '13:38', chevron_right: '13:41', chevron_down: '13:44',
  route: '13:48', bookmark: '13:51', lock: '13:55', info: '13:59', car: '13:64', broadcast: '13:68', drag: '13:76',
  cat_subscription: '13:80', cat_price_index: '13:84', cat_trade: '13:88', cat_rent: '13:92', cat_transport: '13:96',
  cat_development: '13:99', cat_redevelopment: '13:103', cat_policy: '13:106', cat_rate: '13:111', cat_unsold: '13:115',
};
const COMP_NAMES = {
  button: 'Button', iconButton: 'Icon button', textButton: 'Text button', toggle: 'Toggle button',
  tabItem: 'Tab item', tabs: 'Tabs', switch: 'Switch', radio: 'Radio', checkbox: 'Checkbox',
  listRow: 'List row', dropdown: 'Dropdown', dropdownItem: 'Dropdown item', dropdownMenu: 'Dropdown menu',
  appIcon: 'App icon', pageNav: 'Page navigation', toast: 'Toast', popup: 'System popup', indicator: 'Indicator',
  progress: 'Segment progress', chip: 'Region chip', weekDay: 'Week day', categoryTile: 'Category tile',
  slot: 'Schedule slot', programCard: 'Program card', routeStop: 'Route stop',
};
const SPACE = { 4: 'spacing/04', 8: 'spacing/08', 12: 'spacing/12', 16: 'spacing/16', 24: 'spacing/24', 32: 'spacing/32', 40: 'spacing/40', 48: 'spacing/48', 64: 'spacing/64' };
const RADIUS = { 8: 'radius/08', 16: 'radius/16', 24: 'radius/24', 32: 'radius/32', 48: 'radius/48' };
const CORNERS = ['topLeftRadius', 'topRightRadius', 'bottomLeftRadius', 'bottomRightRadius'];
const IW = 1408; // inner width of a 1600 board with 96 padding
const CMD = figma.command || 'all';

// ---------------------------------------------------------------- app data
const CATEGORIES = [
  { key: 'subscription', label: '분양·청약', short: '분양', live: true, icon: 'cat_subscription', desc: '청약 일정과 경쟁률' },
  { key: 'price_index', label: '주택가격지수·증감률', short: '지수', live: true, icon: 'cat_price_index', desc: '주간 매매가격지수 변화' },
  { key: 'trade', label: '실거래가·신고가', short: '실거래', live: true, icon: 'cat_trade', desc: '이번 달 실거래와 신고가' },
  { key: 'rent', label: '전월세 시세', short: '전월세', live: true, icon: 'cat_rent', desc: '전세·월세 거래 흐름' },
  { key: 'transport', label: '교통 호재', short: '교통', live: true, icon: 'cat_transport', desc: '철도·도로 사업 소식' },
  { key: 'development', label: '개발 호재', short: '개발', live: true, icon: 'cat_development', desc: '신도시·산업단지 소식' },
  { key: 'redevelopment', label: '재건축·재개발', short: '재건축', live: true, icon: 'cat_redevelopment', desc: '정비사업 진행 단계' },
  { key: 'policy', label: '정책·규제', short: '정책', live: true, icon: 'cat_policy', desc: '대출·세금·규제 지역' },
  { key: 'rate', label: '금리·대출', short: '금리', live: true, icon: 'cat_rate', desc: '기준금리와 주택담보대출 금리' },
  { key: 'unsold', label: '미분양·입주물량', short: '미분양', live: true, icon: 'cat_unsold', desc: '미분양과 입주 예정 물량' },
];
const CAT = {};
for (const c of CATEGORIES) CAT[c.key] = c;
// WeeklySchedule.DEFAULT in the app
const WEEK = [
  { day: '월', slots: ['subscription', 'policy'] },
  { day: '화', slots: ['trade', 'unsold'] },
  { day: '수', slots: ['rent', 'redevelopment'] },
  { day: '목', slots: ['transport', 'rate'] },
  { day: '금', slots: ['price_index', 'development'] },
]
// DEMO_ROUTE districts in order (정자동 → 시흥동 → 세곡동 → 서초동 → 역삼동)
const ROUTE = ['분당구', '수정구', '강남구', '서초구'];
const ROUTE_NOW = 1;
// The screen shows only the sentence being spoken, so nothing is cut off.
const SCRIPT = '1순위 청약 접수는 11월 18일 수요일에 시작합니다.';

// ---------------------------------------------------------------- state
let VL, VD, VM, VBYID, SUFFIX, LIGHT, DARK;
let TSL = [], ESL = [];
const ICONS = {}, PAGES = {}, C = {};
const LOG = [], WARNED = new Set();

function log(m) { LOG.push(m); console.log('[부읽카] ' + m); }
function warn(m) {
  if (WARNED.has(m)) return;
  WARNED.add(m); LOG.push('⚠️ ' + m); console.warn('[부읽카] ' + m);
}
function errText(e) { return e && e.message ? e.message : String(e); }
function tag(n, v) { n.setPluginData(NS, v); return n; }
function removeTagged(page, pred) {
  for (const n of page.children.slice()) {
    const t = n.getPluginData(NS);
    if (t && pred(t)) n.remove();
  }
}

// ---------------------------------------------------------------- setup
async function setup() {
  for (const style of ['ExtraBold', 'Bold', 'SemiBold', 'Regular']) {
    await figma.loadFontAsync({ family: 'Asta Sans', style });
  }
  try { await figma.loadFontAsync({ family: 'Inter', style: 'Regular' }); } catch (e) { /* default font only */ }

  const colls = await figma.variables.getLocalVariableCollectionsAsync();
  const pick = (id, name) => colls.find(c => c.id === id) || colls.find(c => c.name === name);
  const light = pick(IDS.light, 'Pleos · Color Light');
  const dark = pick(IDS.dark, 'Pleos · Color Dark');
  const dim = pick(IDS.dim, 'Pleos · Dimension');
  if (!light || !dark || !dim) throw new Error('Pleos 변수 컬렉션(Color Light / Color Dark / Dimension)을 찾지 못했어요');
  LIGHT = light.id; DARK = dark.id;
  VL = new Map(); VD = new Map(); VM = new Map(); VBYID = new Map(); SUFFIX = new Map();
  for (const v of await figma.variables.getLocalVariablesAsync()) {
    VBYID.set(v.id, v);
    if (v.variableCollectionId === light.id) VL.set(v.name, v);
    else if (v.variableCollectionId === dark.id) VD.set(v.name, v);
    else if (v.variableCollectionId === dim.id) VM.set(v.name, v);
  }
  for (const k of VL.keys()) {
    const s = k.split('/').pop();
    if (!SUFFIX.has(s)) SUFFIX.set(s, k);
  }
  TSL = await figma.getLocalTextStylesAsync();
  ESL = await figma.getLocalEffectStylesAsync();

  const pageHints = { docs: '디자인 시스템', comps: '컴포넌트', screens: '화면' };
  for (const k of Object.keys(IDS.pages)) {
    let p = await figma.getNodeByIdAsync(IDS.pages[k]);
    if (!p || p.type !== 'PAGE') p = figma.root.children.find(x => x.name.indexOf(pageHints[k]) >= 0);
    if (!p) throw new Error('페이지를 찾지 못했어요: ' + pageHints[k]);
    await p.loadAsync();
    PAGES[k] = p;
  }
  for (const k of Object.keys(ICON_IDS)) {
    let n = await figma.getNodeByIdAsync(ICON_IDS[k]);
    if (!n || n.type !== 'COMPONENT') n = PAGES.comps.findOne(x => x.type === 'COMPONENT' && x.name === 'Icon/' + k);
    if (n) ICONS[k] = n; else warn('아이콘 컴포넌트 없음: ' + k);
  }
  log(`준비 완료: 색 변수 ${VL.size}개, 치수 변수 ${VM.size}개, 텍스트 스타일 ${TSL.length}개, 아이콘 ${Object.keys(ICONS).length}개`);
}

// ---------------------------------------------------------------- tokens
function vname(t) { return VL.has(t) ? t : (SUFFIX.get(t) || null); }
function P(t, dark) {
  const n = vname(t);
  const v = n ? (dark ? VD : VL).get(n) : null;
  if (!v) { warn('색 변수 없음: ' + t); return { type: 'SOLID', color: { r: 1, g: 0, b: 1 } }; }
  return figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', v);
}
function bind(node, field, name) {
  const v = VM.get(name);
  if (!v) { warn('치수 변수 없음: ' + name); return; }
  try { node.setBoundVariable(field, v); } catch (e) { warn(`변수 연결 실패 ${field} ← ${name}: ${errText(e)}`); }
}
function setNum(node, field, px, table) {
  try { node[field] = px; } catch (e) { warn(`${field} 설정 실패: ${errText(e)}`); return; }
  const name = table ? table[px] : null;
  if (name && VM.has(name)) { try { node.setBoundVariable(field, VM.get(name)); } catch (e) { /* value already set */ } }
}
function radius(node, px) {
  if (px === 'full') {
    for (const k of CORNERS) {
      node[k] = 9999;
      if (VM.has('radius/full')) { try { node.setBoundVariable(k, VM.get('radius/full')); } catch (e) { /* value set */ } }
    }
    return;
  }
  for (const k of CORNERS) setNum(node, k, px, RADIUS);
}
function stroke(n, color, w, align, dark) {
  n.strokes = [P(color, dark)];
  n.strokeWeight = w || 1;
  n.strokeAlign = align || 'INSIDE';
}
async function shadow(node, level) {
  const s = ESL.find(x => x.name === 'Shadow/Level ' + level);
  if (!s) { warn('그림자 스타일 없음: Level ' + level); return; }
  await node.setEffectStyleIdAsync(s.id);
}

// ---------------------------------------------------------------- layout
function applyAL(f, dir, o) {
  o = o || {};
  f.layoutMode = dir;
  f.primaryAxisSizingMode = 'AUTO';
  f.counterAxisSizingMode = 'AUTO';
  f.fills = [];
  f.clipsContent = false;
  if (o.name) f.name = o.name;
  if (o.gap != null) setNum(f, 'itemSpacing', o.gap, SPACE);
  if (o.pad != null) {
    const p = Array.isArray(o.pad) ? o.pad : [o.pad, o.pad, o.pad, o.pad];
    setNum(f, 'paddingTop', p[0], SPACE); setNum(f, 'paddingRight', p[1], SPACE);
    setNum(f, 'paddingBottom', p[2], SPACE); setNum(f, 'paddingLeft', p[3], SPACE);
  }
  if (o.align) f.counterAxisAlignItems = o.align;
  if (o.justify) f.primaryAxisAlignItems = o.justify;
  if (o.fill) f.fills = (Array.isArray(o.fill) ? o.fill : [o.fill]).map(x => P(x, o.dark));
  if (o.radius != null) radius(f, o.radius);
  if (o.stroke) { stroke(f, o.stroke, o.strokeWeight || 1, o.strokeAlign, o.dark); if (o.dash) f.dashPattern = o.dash; }
  if (o.wrap) {
    f.layoutWrap = 'WRAP';
    setNum(f, 'counterAxisSpacing', o.wrapGap != null ? o.wrapGap : (o.gap || 0), SPACE);
  }
  if (o.parent) o.parent.appendChild(f);
  if (o.w != null || o.h != null) size(f, o.w, o.h);
  if (o.fillW) f.layoutSizingHorizontal = 'FILL';
  if (o.fillH) f.layoutSizingVertical = 'FILL';
  return f;
}
function AL(dir, o) {
  const f = figma.createFrame();
  f.name = dir === 'HORIZONTAL' ? 'row' : 'column';
  return applyAL(f, dir, o);
}
/** Fixed size on the given axes; null keeps the axis hugging its content. */
function size(f, w, h) {
  f.resize(w != null ? w : Math.max(f.width, 0.01), h != null ? h : Math.max(f.height, 0.01));
  if (f.layoutMode && f.layoutMode !== 'NONE') {
    f.layoutSizingHorizontal = w != null ? 'FIXED' : 'HUG';
    f.layoutSizingVertical = h != null ? 'FIXED' : 'HUG';
  }
}
function spacer(parent) { return AL('HORIZONTAL', { name: 'spacer', parent, fillW: true }); }
function add(parent, node, scale) {
  parent.appendChild(node);
  if (scale && scale !== 1) { try { node.rescale(scale); } catch (e) { warn('크기 조정 실패: ' + node.name); } }
  return node;
}

async function T(parent, chars, style, color, o) {
  o = o || {};
  const t = figma.createText();
  const st = TSL.find(s => s.name === style);
  if (st) await t.setTextStyleIdAsync(st.id);
  else { warn('텍스트 스타일 없음: ' + style); t.fontName = { family: 'Asta Sans', style: 'Regular' }; t.fontSize = 24; }
  t.characters = chars;
  t.fills = [P(color || 'text_dark_primary', o.dark)];
  t.name = o.name || chars.slice(0, 30);
  if (parent) parent.appendChild(t);
  if (o.align) t.textAlignHorizontal = o.align;
  if (o.w) { t.resize(o.w, Math.max(t.height, 1)); t.textAutoResize = 'HEIGHT'; }
  if (o.fillW) { t.textAutoResize = 'HEIGHT'; t.layoutSizingHorizontal = 'FILL'; }
  if (o.maxLines) { try { t.textTruncation = 'ENDING'; t.maxLines = o.maxLines; } catch (e) { warn('줄 수 제한을 못 걸었어요: ' + errText(e)); } }
  return t;
}

function tint(node, color, dark) {
  const kinds = ['VECTOR', 'ELLIPSE', 'RECTANGLE', 'BOOLEAN_OPERATION', 'LINE', 'POLYGON', 'STAR'];
  for (const v of node.findAll(n => kinds.indexOf(n.type) >= 0)) {
    if (Array.isArray(v.strokes) && v.strokes.length) v.strokes = [P(color, dark)];
    if (Array.isArray(v.fills) && v.fills.length) v.fills = [P(color, dark)];
  }
}
function ICON(name, sz, color, dark) {
  sz = sz || 48;
  const main = ICONS[name];
  if (!main) {
    const f = figma.createFrame(); f.name = 'icon/' + name + ' (없음)'; f.resize(sz, sz); f.fills = [];
    return f;
  }
  const i = main.createInstance();
  i.name = 'icon/' + name;
  if (sz !== 48) { try { i.rescale(sz / 48); } catch (e) { i.resize(sz, sz); } }
  tint(i, color || 'icon_dark_primary', dark);
  return i;
}
function dot(sizePx, color, name) {
  const e = figma.createEllipse();
  e.name = name || 'dot';
  e.resize(sizePx, sizePx);
  e.fills = [P(color)];
  return e;
}
function spinner(sz, color) {
  const f = figma.createFrame();
  f.name = 'loader'; f.resize(sz, sz); f.fills = []; f.clipsContent = false;
  const track = figma.createEllipse();
  track.name = 'track'; track.resize(sz, sz);
  track.arcData = { startingAngle: 0, endingAngle: 2 * Math.PI, innerRadius: 0.8 };
  track.fills = [P(color)]; track.opacity = 0.24; f.appendChild(track);
  const arc = figma.createEllipse();
  arc.name = 'arc'; arc.resize(sz, sz);
  arc.arcData = { startingAngle: -Math.PI / 2, endingAngle: Math.PI, innerRadius: 0.8 };
  arc.fills = [P(color)]; f.appendChild(arc);
  return f;
}

// ---------------------------------------------------------------- components: helpers
function comp(name) {
  const c = figma.createComponent();
  c.name = name; c.fills = []; c.clipsContent = false;
  tag(c, 'tmp');
  return c;
}
function parseVariant(name) {
  const o = {};
  for (const part of name.split(',')) {
    const kv = part.split('=');
    if (kv.length === 2) o[kv[0].trim()] = kv[1].trim();
  }
  return o;
}
function arrange(set, colProp) {
  const pad = 48, gx = 48, gy = 40;
  const kids = set.children.slice();
  const colOf = k => (colProp ? parseVariant(k.name)[colProp] : '_');
  const rowOf = k => {
    const p = parseVariant(k.name);
    return Object.keys(p).filter(x => x !== colProp).map(x => x + '=' + p[x]).join(',');
  };
  const cols = [], rows = [];
  for (const k of kids) {
    if (cols.indexOf(colOf(k)) < 0) cols.push(colOf(k));
    if (rows.indexOf(rowOf(k)) < 0) rows.push(rowOf(k));
  }
  const cw = cols.map(c => Math.max.apply(null, kids.filter(k => colOf(k) === c).map(k => k.width)));
  const rh = rows.map(r => Math.max.apply(null, kids.filter(k => rowOf(k) === r).map(k => k.height)));
  const cx = [], ry = [];
  let x = pad, y = pad;
  for (const w of cw) { cx.push(x); x += w + gx; }
  for (const h of rh) { ry.push(y); y += h + gy; }
  for (const k of kids) { k.x = cx[cols.indexOf(colOf(k))]; k.y = ry[rows.indexOf(rowOf(k))]; }
  set.resizeWithoutConstraints(x - gx + pad, y - gy + pad);
}
function makeSet(list, parent, name, description, colProp) {
  const set = figma.combineAsVariants(list, parent);
  set.name = name;
  set.description = description || '';
  set.fills = [P('basic_00')];
  stroke(set, 'basic_200', 1);
  try { radius(set, 24); } catch (e) { /* cosmetic */ }
  for (const v of set.children) tag(v, 'variant');
  arrange(set, colProp);
  return set;
}
function single(c, parent, description) {
  parent.appendChild(c);
  c.description = description || '';
  tag(c, 'component');
  return c;
}
function findIn(root, name) {
  const queue = root.children ? root.children.slice() : [];
  while (queue.length) {
    const n = queue.shift();
    if (n.name === name) return n;
    if (n.type !== 'INSTANCE' && n.children) for (const ch of n.children) queue.push(ch);
  }
  return null;
}
function ref(v, name, refs) {
  const n = findIn(v, name);
  if (!n) { warn(`속성 연결 대상 없음: ${name} (${v.name})`); return; }
  n.componentPropertyReferences = refs;
}
function expose(v, name) {
  const n = findIn(v, name);
  if (n && n.type === 'INSTANCE') { try { n.isExposedInstance = true; } catch (e) { /* optional */ } }
}
function iconPrefs() {
  return { preferredValues: Object.keys(ICONS).map(k => ({ type: 'COMPONENT', key: ICONS[k].key })) };
}
function swapProp(set, name, icon) {
  try { return set.addComponentProperty(name, 'INSTANCE_SWAP', iconId(icon), iconPrefs()); }
  catch (e) { warn(`선호 아이콘 목록 없이 만듦 (${name}): ${errText(e)}`); return set.addComponentProperty(name, 'INSTANCE_SWAP', iconId(icon)); }
}
async function family(root, title, desc) {
  const f = AL('VERTICAL', { name: title, gap: 32, parent: root });
  const head = AL('VERTICAL', { name: 'head', gap: 8, parent: f });
  await T(head, title, 'Headline/Small/Bold');
  if (desc) await T(head, desc, 'Body/Small/Normal', 'text_dark_secondary', { w: 1400 });
  return f;
}
function placeholder(name) {
  const f = figma.createFrame();
  f.name = '⚠️ ' + name; f.resize(240, 80);
  f.fills = [{ type: 'SOLID', color: { r: 1, g: 0.92, b: 0.92 } }];
  f.strokes = [{ type: 'SOLID', color: { r: 1, g: 0, b: 0 } }];
  f.dashPattern = [8, 8];
  return f;
}
function setProps(node, props) {
  if (!node || node.type !== 'INSTANCE') return node;
  const defs = node.componentProperties;
  const out = {};
  for (const k of Object.keys(props)) {
    const key = Object.keys(defs).find(d => d === k || d.split('#')[0] === k);
    if (key) out[key] = props[k]; else warn(`속성 없음: ${k} (${node.name})`);
  }
  try { node.setProperties(out); } catch (e) { warn(`속성 적용 실패 ${node.name}: ${errText(e)}`); }
  return node;
}
function setNested(node, name, props) {
  if (!node || node.type !== 'INSTANCE') return null;
  const n = node.findOne(x => x.type === 'INSTANCE' && x.name === name);
  if (!n) { warn(`안쪽 인스턴스 없음: ${name} (${node.name})`); return null; }
  return setProps(n, props);
}
function inst(key, variant, props) {
  const node = C[key];
  if (!node) { warn('컴포넌트 없음: ' + (COMP_NAMES[key] || key)); return placeholder(COMP_NAMES[key] || key); }
  let main = node;
  if (node.type === 'COMPONENT_SET') {
    const want = variant || {};
    main = node.children.find(ch => {
      const p = parseVariant(ch.name);
      return Object.keys(want).every(k => p[k] === want[k]);
    });
    if (!main) { warn(`변형 없음: ${node.name} ${JSON.stringify(want)}`); main = node.defaultVariant; }
  }
  const i = main.createInstance();
  if (props) setProps(i, props);
  return i;
}
function iconId(name) { return ICONS[name] ? ICONS[name].id : null; }

// ---------------------------------------------------------------- components: Pleos
const BTN = {
  Large: { h: 100, r: 32, px: 40, gap: 12, ts: 'Body/Medium/Strong', ic: 40, hv: 'size/button_large' },
  Medium: { h: 80, r: 24, px: 32, gap: 8, ts: 'Body/Small/Strong', ic: 36, hv: 'size/button_medium' },
  Small: { h: 64, r: 16, px: 24, gap: 8, ts: 'Label/Medium/Strong', ic: 28, hv: 'size/button_small' },
};
async function buttonBody(c, z, k, o) {
  applyAL(c, 'HORIZONTAL', { gap: z.gap, pad: [0, z.px, 0, z.px], align: 'CENTER', justify: 'CENTER', fill: k.bg, radius: z.r });
  if (k.stroke) stroke(c, k.stroke, 2);
  size(c, null, z.h);
  bind(c, 'height', z.hv);
  const pre = ICON(o.prefix, z.ic, k.ic); pre.name = 'prefix'; c.appendChild(pre);
  const t = await T(c, o.label, z.ts, k.fg, { name: 'label' });
  if (o.suffix) { const suf = ICON(o.suffix, z.ic, k.ic); suf.name = 'suffix'; c.appendChild(suf); }
  return t;
}

async function buildButton(root) {
  const fam = await family(root, 'Button', 'Pleos Basic button. Primary = basic(5% 배경), Secondary = filled(진한 배경). 높이 100 / 80 / 64, 모서리 32 / 24 / 16, 좌우 여백 40 / 32 / 24. 실제 기기에서는 누르면 95%로 줄어들고 배경이 바뀝니다. Loading은 로더를 띄우고 조작을 막습니다.');
  const LOOK = {
    Primary: {
      Enabled: { bg: 'button_basic_enabled', fg: 'text_dark_primary', ic: 'icon_dark_primary' },
      Pressed: { bg: 'button_basic_pressed', fg: 'text_dark_primary', ic: 'icon_dark_primary' },
      Disabled: { bg: 'button_basic_disabled', fg: 'text_dark_quaternary', ic: 'icon_dark_quaternary', stroke: 'alpha_dark_100' },
      Loading: { bg: 'button_basic_enabled', fg: 'text_dark_primary', ic: 'icon_dark_primary' },
    },
    Secondary: {
      Enabled: { bg: 'button_filled_enabled', fg: 'text_light_primary', ic: 'icon_light_primary' },
      Pressed: { bg: 'button_filled_pressed', fg: 'text_light_primary', ic: 'icon_light_primary' },
      Disabled: { bg: 'button_filled_disabled', fg: 'text_dark_quaternary', ic: 'icon_dark_quaternary' },
      Loading: { bg: 'button_filled_enabled', fg: 'text_light_primary', ic: 'icon_light_primary' },
    },
  };
  const list = [];
  for (const h of ['Primary', 'Secondary']) for (const s of ['Large', 'Medium', 'Small']) for (const st of ['Enabled', 'Pressed', 'Disabled', 'Loading']) {
    const c = comp(`Hierarchy=${h}, Size=${s}, State=${st}`);
    const k = LOOK[h][st];
    const label = await buttonBody(c, BTN[s], k, { prefix: 'play', suffix: 'chevron_right', label: '버튼' });
    if (st === 'Loading') { label.visible = false; c.appendChild(spinner(BTN[s].ic, k.ic)); }
    list.push(c);
  }
  const set = makeSet(list, fam, 'Button', 'Pleos Basic button · Hierarchy × Size × State', 'State');
  const kL = set.addComponentProperty('Label', 'TEXT', '버튼');
  const kPS = set.addComponentProperty('Show prefix icon', 'BOOLEAN', false);
  const kSS = set.addComponentProperty('Show suffix icon', 'BOOLEAN', false);
  const kP = swapProp(set, 'Prefix icon', 'play');
  const kS = swapProp(set, 'Suffix icon', 'chevron_right');
  for (const v of set.children) {
    ref(v, 'label', { characters: kL });
    ref(v, 'prefix', { visible: kPS, mainComponent: kP });
    ref(v, 'suffix', { visible: kSS, mainComponent: kS });
  }
  arrange(set, 'State');
  return set;
}

async function buildIconButton(root) {
  const fam = await family(root, 'Icon button', '누구나 바로 알아보는 아이콘에만 씁니다. Style Basic(5% 배경) / Plain(배경 없음) / Circle(원형). 크기 80 / 64 / 48, 아이콘 48 / 40 / 32. 눌림 10%, 비활성은 흰 20% 배경 + 아이콘 32%.');
  const SZ = { Large: { s: 80, r: 24, ic: 48 }, Medium: { s: 64, r: 16, ic: 40 }, Small: { s: 48, r: 12, ic: 32 } };
  const list = [];
  for (const style of ['Basic', 'Plain', 'Circle']) for (const s of ['Large', 'Medium', 'Small']) for (const st of ['Enabled', 'Pressed', 'Disabled']) {
    const z = SZ[s];
    let bg = null;
    if (st === 'Pressed') bg = 'button_basic_pressed';
    else if (style !== 'Plain') bg = st === 'Disabled' ? 'button_basic_disabled' : 'button_basic_enabled';
    const c = comp(`Style=${style}, Size=${s}, State=${st}`);
    applyAL(c, 'HORIZONTAL', { align: 'CENTER', justify: 'CENTER', fill: bg || undefined, radius: style === 'Circle' ? 'full' : z.r });
    size(c, z.s, z.s);
    if (s === 'Large') { bind(c, 'width', 'size/icon_button'); bind(c, 'height', 'size/icon_button'); }
    const ic = ICON('settings', z.ic, st === 'Disabled' ? 'icon_dark_quaternary' : 'icon_dark_primary');
    ic.name = 'icon'; c.appendChild(ic);
    list.push(c);
  }
  const set = makeSet(list, fam, 'Icon button', 'Pleos Icon button · Style × Size × State', 'State');
  const kI = swapProp(set, 'Icon', 'settings');
  for (const v of set.children) ref(v, 'icon', { mainComponent: kI });
  return set;
}

async function buildTextButton(root) {
  const fam = await family(root, 'Text button', '부가 정보로 가는 링크. 글자색 informative_positive(#0064FF), 앞·뒤 아이콘으로 뜻을 보강합니다. Size Medium / Small.');
  const SZ = {
    Medium: { h: 64, px: 16, gap: 8, ts: 'Body/Small/Strong', ic: 32, r: 16 },
    Small: { h: 48, px: 12, gap: 4, ts: 'Label/Medium/Strong', ic: 28, r: 12 },
  };
  const list = [];
  for (const s of ['Medium', 'Small']) for (const st of ['Enabled', 'Pressed', 'Disabled']) {
    const z = SZ[s];
    const fg = st === 'Disabled' ? 'text_dark_quaternary' : 'informative_positive';
    const ic = st === 'Disabled' ? 'icon_dark_quaternary' : 'informative_positive';
    const c = comp(`Size=${s}, State=${st}`);
    applyAL(c, 'HORIZONTAL', { gap: z.gap, pad: [0, z.px, 0, z.px], align: 'CENTER', justify: 'CENTER', fill: st === 'Pressed' ? 'alpha_dark_50' : undefined, radius: z.r });
    size(c, null, z.h);
    const pre = ICON('info', z.ic, ic); pre.name = 'prefix'; c.appendChild(pre);
    await T(c, '더 보기', z.ts, fg, { name: 'label' });
    const suf = ICON('chevron_right', z.ic, ic); suf.name = 'suffix'; c.appendChild(suf);
    list.push(c);
  }
  const set = makeSet(list, fam, 'Text button', 'Pleos Text button · Size × State', 'State');
  const kL = set.addComponentProperty('Label', 'TEXT', '더 보기');
  const kPS = set.addComponentProperty('Show prefix icon', 'BOOLEAN', false);
  const kSS = set.addComponentProperty('Show suffix icon', 'BOOLEAN', true);
  const kP = swapProp(set, 'Prefix icon', 'info');
  const kS = swapProp(set, 'Suffix icon', 'chevron_right');
  for (const v of set.children) {
    ref(v, 'label', { characters: kL });
    ref(v, 'prefix', { visible: kPS, mainComponent: kP });
    ref(v, 'suffix', { visible: kSS, mainComponent: kS });
  }
  arrange(set, 'State');
  return set;
}

async function buildToggle(root) {
  const fam = await family(root, 'Toggle button', '켜고 끄는 버튼. Off는 basic 배경, On은 button_switch 배경 + 그림자 Level 1(Floating). 비활성은 2px 테두리 #131417 10%.');
  const LOOK = {
    Off: {
      Enabled: { bg: 'button_basic_enabled', fg: 'text_dark_secondary', ic: 'icon_dark_secondary' },
      Pressed: { bg: 'button_basic_pressed', fg: 'text_dark_secondary', ic: 'icon_dark_secondary' },
      Disabled: { bg: 'button_basic_disabled', fg: 'text_dark_quaternary', ic: 'icon_dark_quaternary', stroke: 'alpha_dark_100' },
    },
    On: {
      Enabled: { bg: 'button_switch_enabled', fg: 'text_dark_primary', ic: 'icon_dark_primary', shadow: 1 },
      Pressed: { bg: 'button_switch_pressed', fg: 'text_dark_primary', ic: 'icon_dark_primary' },
      Disabled: { bg: 'button_switch_disabled', fg: 'text_dark_quaternary', ic: 'icon_dark_quaternary', stroke: 'alpha_dark_100' },
    },
  };
  const list = [];
  for (const tg of ['Off', 'On']) for (const s of ['Large', 'Medium', 'Small']) for (const st of ['Enabled', 'Pressed', 'Disabled']) {
    const c = comp(`Toggled=${tg}, Size=${s}, State=${st}`);
    const k = LOOK[tg][st];
    await buttonBody(c, BTN[s], k, { prefix: 'route', label: '토글' });
    if (k.shadow) await shadow(c, k.shadow);
    list.push(c);
  }
  const set = makeSet(list, fam, 'Toggle button', 'Pleos Toggle button · Toggled × Size × State', 'State');
  const kL = set.addComponentProperty('Label', 'TEXT', '토글');
  const kPS = set.addComponentProperty('Show prefix icon', 'BOOLEAN', false);
  const kP = swapProp(set, 'Prefix icon', 'route');
  for (const v of set.children) {
    ref(v, 'label', { characters: kL });
    ref(v, 'prefix', { visible: kPS, mainComponent: kP });
  }
  arrange(set, 'State');
  return set;
}

async function buildTabItem(root) {
  const fam = await family(root, 'Tab item', 'Tabs 안의 항목. Box: 높이 84, 선택 시 흰 배경(button_switch_enabled) + 그림자 Level 1. Line: 높이 80, 선택 시 아래 4px 표시.');
  const list = [];
  for (const type of ['Box', 'Line']) for (const st of ['Selected', 'Enabled', 'Pressed']) {
    const c = comp(`Type=${type}, State=${st}`);
    const box = type === 'Box';
    let bg;
    if (box) bg = st === 'Selected' ? 'button_switch_enabled' : st === 'Pressed' ? 'alpha_dark_100' : undefined;
    else bg = st === 'Pressed' ? 'alpha_dark_50' : undefined;
    applyAL(c, 'HORIZONTAL', { pad: [0, box ? 32 : 24, 0, box ? 32 : 24], align: 'CENTER', justify: 'CENTER', fill: bg, radius: box ? 24 : 16 });
    size(c, null, box ? 84 : 80);
    const fg = st === 'Selected' ? 'text_dark_primary' : (box || st === 'Pressed') ? 'text_dark_secondary' : 'text_dark_tertiary';
    await T(c, '탭', 'Body/Medium/Strong', fg, { name: 'label' });
    if (box && st === 'Selected') await shadow(c, 1);
    if (!box && st === 'Selected') {
      const ind = figma.createRectangle();
      ind.name = 'indicator';
      c.appendChild(ind);
      ind.layoutPositioning = 'ABSOLUTE';
      ind.resize(c.width, 4);
      ind.x = 0; ind.y = c.height - 4;
      ind.constraints = { horizontal: 'STRETCH', vertical: 'MAX' };
      ind.fills = [P('icon_dark_primary')];
    }
    list.push(c);
  }
  const set = makeSet(list, fam, 'Tab item', 'Pleos Tabs의 Tab item · Type × State', 'State');
  const kL = set.addComponentProperty('Label', 'TEXT', '탭');
  for (const v of set.children) ref(v, 'label', { characters: kL });
  return set;
}

async function buildTabs(root) {
  const fam = await family(root, 'Tabs', '2개 이상의 콘텐츠나 옵션을 전환합니다. Box: 컨테이너 100, 안쪽 여백 8, 배경 controller_tab_background(5%). Fitted = 항목이 너비를 균등하게 채움.');
  const list = [];
  for (const type of ['Box', 'Line']) for (const n of [2, 3]) {
    const c = comp(`Type=${type}, Items=${n}`);
    const box = type === 'Box';
    applyAL(c, 'HORIZONTAL', { pad: box ? 8 : 0, gap: 0, align: 'CENTER', fill: box ? 'controller_tab_background' : undefined, radius: box ? 32 : undefined });
    size(c, n === 2 ? 600 : 880, box ? 100 : 80);
    if (!box) {
      stroke(c, 'alpha_dark_100', 2);
      c.strokeTopWeight = 0; c.strokeLeftWeight = 0; c.strokeRightWeight = 0; c.strokeBottomWeight = 2;
    }
    for (let i = 1; i <= n; i++) {
      const it = inst('tabItem', { Type: type, State: i === 1 ? 'Selected' : 'Enabled' }, { Label: '탭 ' + i });
      it.name = 'tab ' + i;
      c.appendChild(it);
      it.layoutSizingHorizontal = 'FILL';
    }
    list.push(c);
  }
  const set = makeSet(list, fam, 'Tabs', 'Pleos Tabs · Type × Items (Fitted)', 'Items');
  for (const v of set.children) for (let i = 1; i <= 3; i++) expose(v, 'tab ' + i);
  return set;
}

async function buildSwitch(root) {
  const fam = await family(root, 'Switch', 'On/Off 전환. 트랙 80×48(모서리 16), 손잡이 32(모서리 8, 안쪽 8). Off #131417 30%, On informative_active(#02C265). 눌림은 10%를 겹칩니다.');
  const list = [];
  for (const ch of ['Off', 'On']) for (const st of ['Enabled', 'Pressed', 'Disabled']) {
    const c = comp(`Checked=${ch}, State=${st}`);
    c.resize(80, 48);
    radius(c, 16);
    const base = st === 'Disabled' ? 'switch_disabled' : ch === 'On' ? 'switch_on' : 'switch_off';
    c.fills = st === 'Pressed' ? [P(base), P('alpha_dark_100')] : [P(base)];
    bind(c, 'width', 'size/switch_width');
    bind(c, 'height', 'size/switch_height');
    const knob = figma.createRectangle();
    knob.name = 'handle';
    knob.resize(32, 32);
    radius(knob, 8);
    knob.x = ch === 'On' ? 40 : 8; knob.y = 8;
    knob.fills = [P(st === 'Disabled' ? 'switch_knob_disabled' : ch === 'On' ? 'switch_knob_on' : 'switch_knob_off')];
    c.appendChild(knob);
    list.push(c);
  }
  return makeSet(list, fam, 'Switch', 'Pleos Switch · Checked × State', 'State');
}

async function buildRadio(root) {
  const fam = await family(root, 'Radio', '하나만 고르는 선택. 48 원형, 테두리 4px #131417 20%. 선택 시 #02C265 + 흰 점.');
  const list = [];
  for (const sel of ['Off', 'On']) for (const st of ['Enabled', 'Pressed', 'Disabled']) {
    const c = comp(`Selected=${sel}, State=${st}`);
    applyAL(c, 'HORIZONTAL', { align: 'CENTER', justify: 'CENTER', radius: 'full' });
    size(c, 48, 48);
    if (sel === 'Off') {
      if (st === 'Pressed') c.fills = [P('alpha_dark_100')];
      if (st === 'Disabled') c.fills = [P('alpha_dark_50')];
      stroke(c, st === 'Disabled' ? 'alpha_dark_50' : 'alpha_dark_200', 4);
    } else {
      c.fills = st === 'Disabled' ? [P('alpha_dark_100')] : st === 'Pressed' ? [P('switch_on'), P('alpha_dark_100')] : [P('switch_on')];
      c.appendChild(dot(20, st === 'Disabled' ? 'static_light_300' : 'static_light_100', 'dot'));
    }
    list.push(c);
  }
  return makeSet(list, fam, 'Radio', 'Pleos Radio button · Selected × State', 'State');
}

async function buildCheckbox(root) {
  const fam = await family(root, 'Checkbox', '여러 개를 고르는 선택. 48, 모서리 12, 테두리 4px #131417 20%. 선택 시 #02C265 + 흰 체크.');
  const list = [];
  for (const ch of ['Off', 'On']) for (const st of ['Enabled', 'Pressed', 'Disabled']) {
    const c = comp(`Checked=${ch}, State=${st}`);
    applyAL(c, 'HORIZONTAL', { align: 'CENTER', justify: 'CENTER' });
    radius(c, 12);
    size(c, 48, 48);
    if (ch === 'Off') {
      if (st === 'Pressed') c.fills = [P('alpha_dark_100')];
      if (st === 'Disabled') c.fills = [P('alpha_dark_50')];
      stroke(c, st === 'Disabled' ? 'alpha_dark_100' : 'alpha_dark_200', 4);
    } else {
      c.fills = st === 'Disabled' ? [P('alpha_dark_100')] : st === 'Pressed' ? [P('switch_on'), P('alpha_dark_100')] : [P('switch_on')];
      const ic = ICON('check', 32, st === 'Disabled' ? 'static_light_300' : 'static_light_100');
      ic.name = 'check';
      c.appendChild(ic);
    }
    list.push(c);
  }
  return makeSet(list, fam, 'Checkbox', 'Pleos Checkbox · Checked × State', 'State');
}

async function buildListRow(root) {
  const fam = await family(root, 'List row', '설정 목록 한 줄. 앞 아이콘 + 제목·설명 + 뒤 컨트롤(Radio / Switch / Chevron / None). 최소 높이 112, 배경 surface_basic, 모서리 24.');
  const list = [];
  for (const tr of ['Radio', 'Switch', 'Chevron', 'None']) {
    const c = comp(`Trailing=${tr}`);
    applyAL(c, 'HORIZONTAL', { gap: 24, pad: [24, 32, 24, 32], align: 'CENTER', fill: 'surface_basic', radius: 24 });
    size(c, 760, null);
    try { c.minHeight = 112; } catch (e) { /* optional */ }
    const lead = ICON('route', 48, 'icon_dark_primary'); lead.name = 'leading'; c.appendChild(lead);
    const col = AL('VERTICAL', { name: 'text', gap: 4, parent: c, fillW: true });
    await T(col, '항목 제목', 'Body/Large/Strong', 'text_dark_primary', { name: 'title', fillW: true });
    await T(col, '부가 설명', 'Label/Medium/Normal', 'text_dark_tertiary', { name: 'description', fillW: true });
    if (tr === 'Radio') { const r = inst('radio', { Selected: 'Off', State: 'Enabled' }); r.name = 'control'; c.appendChild(r); }
    if (tr === 'Switch') { const s = inst('switch', { Checked: 'Off', State: 'Enabled' }); s.name = 'control'; c.appendChild(s); }
    if (tr === 'Chevron') { const ic = ICON('chevron_right', 40, 'icon_dark_tertiary'); ic.name = 'chevron'; c.appendChild(ic); }
    list.push(c);
  }
  const set = makeSet(list, fam, 'List row', '부읽카 설정 목록 행 · Trailing', 'Trailing');
  const kT = set.addComponentProperty('Title', 'TEXT', '항목 제목');
  const kD = set.addComponentProperty('Description', 'TEXT', '부가 설명');
  const kDS = set.addComponentProperty('Show description', 'BOOLEAN', true);
  const kLS = set.addComponentProperty('Show leading icon', 'BOOLEAN', true);
  const kLI = swapProp(set, 'Leading icon', 'route');
  for (const v of set.children) {
    ref(v, 'title', { characters: kT });
    ref(v, 'description', { characters: kD, visible: kDS });
    ref(v, 'leading', { visible: kLS, mainComponent: kLI });
    expose(v, 'control');
  }
  return set;
}

async function buildDropdown(root) {
  const fam = await family(root, 'Dropdown', '메뉴 이동이나 속성 변경. Medium 100 / Small 80, 테두리 2px #131417 10%, 열림 상태는 테두리 field_focused.');
  const SZ = {
    Medium: { h: 100, r: 24, px: 24, gap: 16, ts: 'Body/Large/Strong', ic: 40 },
    Small: { h: 80, r: 16, px: 24, gap: 12, ts: 'Body/Medium/Strong', ic: 36 },
  };
  const list = [];
  for (const s of ['Medium', 'Small']) for (const st of ['Enabled', 'Pressed', 'Opened', 'Disabled']) {
    const z = SZ[s];
    const c = comp(`Size=${s}, State=${st}`);
    const bg = st === 'Pressed' ? 'dropdown_pressed' : st === 'Disabled' ? 'button_basic_disabled' : 'dropdown_normal';
    applyAL(c, 'HORIZONTAL', { gap: z.gap, pad: [0, z.px, 0, z.px], align: 'CENTER', fill: bg, radius: z.r });
    stroke(c, st === 'Opened' ? 'field_focused' : 'alpha_dark_100', 2);
    size(c, 480, z.h);
    const ic = st === 'Disabled' ? 'icon_dark_quaternary' : 'icon_dark_primary';
    const pre = ICON('bookmark', z.ic, ic); pre.name = 'prefix'; c.appendChild(pre);
    await T(c, '선택하세요', z.ts, st === 'Disabled' ? 'text_dark_quaternary' : 'text_dark_primary', { name: 'label', fillW: true, maxLines: 1 });
    const chev = ICON('chevron_down', z.ic, ic); chev.name = 'chevron'; c.appendChild(chev);
    if (st === 'Opened') { try { chev.rotation = 180; } catch (e) { /* cosmetic */ } }
    list.push(c);
  }
  const set = makeSet(list, fam, 'Dropdown', 'Pleos Dropdown · Size × State', 'State');
  const kL = set.addComponentProperty('Label', 'TEXT', '선택하세요');
  const kPS = set.addComponentProperty('Show prefix icon', 'BOOLEAN', true);
  const kP = swapProp(set, 'Prefix icon', 'bookmark');
  for (const v of set.children) {
    ref(v, 'label', { characters: kL });
    ref(v, 'prefix', { visible: kPS, mainComponent: kP });
  }
  return set;
}

async function buildDropdownItem(root) {
  const fam = await family(root, 'Dropdown item', '드롭다운 목록 항목. 높이 100, 앞 아이콘 48, 눌림 5%, 선택 시 굵게 + 체크.');
  const list = [];
  for (const st of ['Enabled', 'Pressed', 'Selected']) {
    const c = comp(`State=${st}`);
    applyAL(c, 'HORIZONTAL', { gap: 16, pad: [0, 24, 0, 24], align: 'CENTER', radius: 24, fill: st === 'Pressed' ? 'alpha_dark_50' : undefined });
    size(c, 448, 100);
    const pre = ICON('route', 48, 'icon_dark_primary'); pre.name = 'prefix'; c.appendChild(pre);
    await T(c, '항목', st === 'Selected' ? 'Body/Medium/Strong' : 'Body/Medium/Normal', 'text_dark_primary', { name: 'label', fillW: true, maxLines: 1 });
    if (st === 'Selected') { const ck = ICON('check', 40, 'informative_positive'); ck.name = 'check'; c.appendChild(ck); }
    list.push(c);
  }
  const set = makeSet(list, fam, 'Dropdown item', 'Pleos Dropdown container item · State', 'State');
  const kL = set.addComponentProperty('Label', 'TEXT', '항목');
  const kPS = set.addComponentProperty('Show prefix icon', 'BOOLEAN', true);
  const kP = swapProp(set, 'Prefix icon', 'route');
  for (const v of set.children) {
    ref(v, 'label', { characters: kL });
    ref(v, 'prefix', { visible: kPS, mainComponent: kP });
  }
  return set;
}

async function buildDropdownMenu(root) {
  const fam = await family(root, 'Dropdown menu', '열린 드롭다운 목록. 배경 background_popup, 모서리 32, 안쪽 여백 16, 그림자 Level 2.');
  const c = comp('Dropdown menu');
  applyAL(c, 'VERTICAL', { pad: 16, gap: 0, fill: 'background_popup', radius: 32 });
  size(c, 480, null);
  await shadow(c, 2);
  const items = [['항목 1', 'Selected', 'route'], ['항목 2', 'Enabled', 'bookmark'], ['항목 3', 'Enabled', 'add']];
  items.forEach((d, i) => {
    const it = inst('dropdownItem', { State: d[1] }, { Label: d[0], 'Prefix icon': iconId(d[2]) });
    it.name = 'item ' + (i + 1);
    c.appendChild(it);
    it.layoutSizingHorizontal = 'FILL';
  });
  for (let i = 1; i <= 3; i++) expose(c, 'item ' + i);
  return single(c, fam, 'Pleos Dropdown container');
}

const APP_GLYPH = '<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">'
  + '<path d="M4 10.5L12 4l8 6.5V20a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z" stroke="#FFFFFF" stroke-width="2" stroke-linejoin="round"/>'
  + '<circle cx="9.5" cy="15" r="1.5" fill="#FFFFFF"/>'
  + '<path d="M12.5 12.5a3.5 3.5 0 0 1 0 5M15 10.5a6.5 6.5 0 0 1 0 9" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/></svg>';
function appGlyph(px) {
  const g = figma.createNodeFromSvg(APP_GLYPH);
  g.name = 'glyph';
  g.fills = [];
  if (px && px !== 48) g.rescale(px / 48);
  tint(g, 'static_light_100');
  return g;
}

async function buildAppIcon(root) {
  const fam = await family(root, 'App icon', 'Adaptive icon: Background(배경) 레이어와 Foreground(집 + 소리) 레이어를 분리. 브랜드별 마스크는 시스템이 적용하며, 여기서는 모서리 24로 미리 봅니다.');
  const c = comp('App icon');
  c.resize(80, 80);
  radius(c, 24);
  c.clipsContent = true;
  const bg = figma.createRectangle();
  bg.name = 'Background'; bg.resize(80, 80); bg.fills = [P('static_dark_100')];
  bg.constraints = { horizontal: 'SCALE', vertical: 'SCALE' };
  c.appendChild(bg);
  const fg = figma.createFrame();
  fg.name = 'Foreground'; fg.resize(80, 80); fg.fills = []; fg.clipsContent = false;
  fg.constraints = { horizontal: 'SCALE', vertical: 'SCALE' };
  c.appendChild(fg);
  const g = appGlyph(48);
  fg.appendChild(g);
  g.x = 16; g.y = 16;
  g.constraints = { horizontal: 'SCALE', vertical: 'SCALE' };
  return single(c, fam, '부읽카 앱 아이콘 · 80 × 80 (GNB, App library, App Market)');
}

async function buildPageNav(root) {
  const fam = await family(root, 'Page navigation', '앱 상단. Main = 앱 아이콘 + 제목, Depth = 뒤로 버튼 + 제목. 높이 128, 좌우 여백 48, 제목 Title/Medium, 부제 Label/Medium. 오른쪽 Suffix에 아이콘 버튼.');
  const list = [];
  for (const type of ['Main', 'Depth']) {
    const c = comp(`Type=${type}`);
    applyAL(c, 'HORIZONTAL', { gap: type === 'Main' ? 32 : 24, pad: [24, 48, 24, 48], align: 'CENTER' });
    size(c, 1700, 128);
    if (type === 'Main') {
      const ai = C.appIcon ? C.appIcon.createInstance() : placeholder('App icon');
      ai.name = 'app icon';
      c.appendChild(ai);
    } else {
      const back = inst('iconButton', { Style: 'Plain', Size: 'Large', State: 'Enabled' }, { Icon: iconId('back') });
      back.name = 'back';
      c.appendChild(back);
    }
    const col = AL('VERTICAL', { name: 'titles', gap: 4, parent: c, fillW: true });
    await T(col, '페이지 제목', 'Title/Medium/Bold', 'text_dark_primary', { name: 'title', fillW: true, maxLines: 1 });
    await T(col, '부가 정보', 'Label/Medium/Normal', 'text_dark_tertiary', { name: 'subtitle', fillW: true, maxLines: 1 });
    const suf = AL('HORIZONTAL', { name: 'suffix', gap: 16, align: 'CENTER', parent: c });
    const a1 = inst('iconButton', { Style: 'Basic', Size: 'Large', State: 'Enabled' }, { Icon: iconId('schedule') });
    a1.name = 'action 1'; suf.appendChild(a1);
    const a2 = inst('iconButton', { Style: 'Basic', Size: 'Large', State: 'Enabled' }, { Icon: iconId('settings') });
    a2.name = 'action 2'; suf.appendChild(a2);
    list.push(c);
  }
  const set = makeSet(list, fam, 'Page navigation', 'Pleos Page navigation · Type', 'Type');
  const kT = set.addComponentProperty('Title', 'TEXT', '페이지 제목');
  const kS = set.addComponentProperty('Subtitle', 'TEXT', '부가 정보');
  const kSS = set.addComponentProperty('Show subtitle', 'BOOLEAN', true);
  const kSU = set.addComponentProperty('Show suffix', 'BOOLEAN', true);
  for (const v of set.children) {
    ref(v, 'title', { characters: kT });
    ref(v, 'subtitle', { characters: kS, visible: kSS });
    ref(v, 'suffix', { visible: kSU });
    expose(v, 'back'); expose(v, 'action 1'); expose(v, 'action 2');
  }
  return set;
}

async function buildToast(root) {
  const fam = await family(root, 'Toast', '잠깐 띄우는 알림. 높이 80, 배경 #131417 84%(static_dark_200), 흰 글자. 앱 패널 안 좌우·아래 24 여백, 최대 너비 792, 메시지 최대 712.');
  const list = [];
  for (const type of ['Default', 'Icon']) {
    const c = comp(`Type=${type}`);
    applyAL(c, 'HORIZONTAL', { gap: 16, pad: [0, 40, 0, 40], align: 'CENTER', fill: 'static_dark_200', radius: 24 });
    size(c, null, 80);
    try { c.maxWidth = 792; } catch (e) { /* optional */ }
    if (type === 'Icon') { const ic = ICON('info', 40, 'static_light_100'); ic.name = 'icon'; c.appendChild(ic); }
    const m = await T(c, '알림 메시지', 'Body/Medium/Normal', 'static_light_100', { name: 'message' });
    try { m.maxWidth = 712; } catch (e) { /* optional */ }
    list.push(c);
  }
  const set = makeSet(list, fam, 'Toast', 'Pleos Toast · Type', 'Type');
  const kM = set.addComponentProperty('Message', 'TEXT', '알림 메시지');
  const kI = swapProp(set, 'Icon', 'info');
  for (const v of set.children) {
    ref(v, 'message', { characters: kM });
    if (parseVariant(v.name).Type === 'Icon') ref(v, 'icon', { mainComponent: kI });
  }
  return set;
}

async function buildPopup(root) {
  const fam = await family(root, 'System popup', 'Large 1200 / Medium 808, 모서리 48, 여백 48, 제목·설명 가운데 정렬. 버튼 높이 100, 확인(진한 버튼)이 왼쪽. 테두리 2px #131417 5%, 그림자 Level 3.');
  const list = [];
  for (const sz of ['Large', 'Medium']) for (const al of ['Single', 'Horizontal', 'Vertical']) {
    const c = comp(`Size=${sz}, Buttons=${al}`);
    applyAL(c, 'VERTICAL', { gap: 48, pad: 48, align: 'CENTER', fill: 'background_popup', radius: 48 });
    stroke(c, 'alpha_dark_50', 2, 'OUTSIDE');
    size(c, sz === 'Large' ? 1200 : 808, null);
    await shadow(c, 3);
    const tx = AL('VERTICAL', { name: 'text', gap: 24, align: 'CENTER', parent: c, fillW: true });
    await T(tx, '팝업 제목', 'Title/Medium/Bold', 'text_dark_primary', { name: 'title', fillW: true, align: 'CENTER' });
    await T(tx, '전할 내용을 짧게 적어요. 3줄을 넘기지 않아요.', 'Body/Large/Normal', 'text_dark_secondary', { name: 'description', fillW: true, align: 'CENTER', maxLines: 3 });
    const acts = AL(al === 'Vertical' ? 'VERTICAL' : 'HORIZONTAL', { name: 'actions', gap: 24, parent: c, fillW: true });
    const ok = inst('button', { Hierarchy: 'Secondary', Size: 'Large', State: 'Enabled' }, { Label: '확인' });
    ok.name = 'confirm'; acts.appendChild(ok); ok.layoutSizingHorizontal = 'FILL';
    if (al !== 'Single') {
      const no = inst('button', { Hierarchy: 'Primary', Size: 'Large', State: 'Enabled' }, { Label: '취소' });
      no.name = 'cancel'; acts.appendChild(no); no.layoutSizingHorizontal = 'FILL';
    }
    list.push(c);
  }
  const set = makeSet(list, fam, 'System popup', 'Pleos System popup · Size × Buttons', 'Buttons');
  const kT = set.addComponentProperty('Title', 'TEXT', '팝업 제목');
  const kD = set.addComponentProperty('Description', 'TEXT', '전할 내용을 짧게 적어요. 3줄을 넘기지 않아요.');
  const kDS = set.addComponentProperty('Show description', 'BOOLEAN', true);
  for (const v of set.children) {
    ref(v, 'title', { characters: kT });
    ref(v, 'description', { characters: kD, visible: kDS });
    expose(v, 'confirm'); expose(v, 'cancel');
  }
  return set;
}

async function buildIndicator(root) {
  const fam = await family(root, 'Indicator', '여러 값 중 지금 위치. 점 8, 간격 16, 지금 값 100% / 나머지 32%. Wide는 지금 값을 24 길이로 늘립니다.');
  const list = [];
  for (const sz of ['Narrow', 'Wide']) for (let p = 1; p <= 5; p++) {
    const c = comp(`Size=${sz}, Present=${p}`);
    applyAL(c, 'HORIZONTAL', { gap: 16, align: 'CENTER' });
    for (let i = 1; i <= 5; i++) {
      const r = figma.createRectangle();
      r.name = 'value ' + i;
      r.resize(sz === 'Wide' && i === p ? 24 : 8, 8);
      radius(r, 'full');
      r.fills = [P(i === p ? 'icon_dark_primary' : 'icon_dark_quaternary')];
      c.appendChild(r);
    }
    list.push(c);
  }
  return makeSet(list, fam, 'Indicator', 'Pleos Indicator · Size × Present', 'Present');
}

// ---------------------------------------------------------------- components: 부읽카
async function buildProgress(root) {
  const fam = await family(root, 'Segment progress', '방송 진행 막대. 들은 구간 icon_dark_primary, 지금 구간 radio_primary(Pleos 라디오 색), 남은 구간 alpha_dark_100. 높이 8.');
  const list = [];
  for (let cur = 1; cur <= 5; cur++) {
    const c = comp(`Current=${cur}`);
    applyAL(c, 'HORIZONTAL', { gap: 8, align: 'CENTER' });
    size(c, 800, 8);
    for (let i = 1; i <= 5; i++) {
      const r = figma.createRectangle();
      r.name = 'segment ' + i;
      r.resize(100, 8);
      radius(r, 'full');
      r.fills = [P(i < cur ? 'icon_dark_primary' : i === cur ? 'radio_primary' : 'alpha_dark_100')];
      c.appendChild(r);
      r.layoutSizingHorizontal = 'FILL';
    }
    list.push(c);
  }
  return makeSet(list, fam, 'Segment progress', '부읽카 방송 진행 · Current', 'Current');
}

async function buildChip(root) {
  const fam = await family(root, 'Region chip', '경로 위 지역. Default 5% 배경, Current 진한 배경 + 라디오 점, Passed 테두리만. 높이 56.');
  const list = [];
  for (const st of ['Default', 'Current', 'Passed']) {
    const c = comp(`State=${st}`);
    applyAL(c, 'HORIZONTAL', { gap: 8, pad: [0, 24, 0, 24], align: 'CENTER', radius: 'full', fill: st === 'Default' ? 'alpha_dark_50' : st === 'Current' ? 'button_filled_enabled' : undefined });
    if (st === 'Passed') stroke(c, 'alpha_dark_100', 2);
    size(c, null, 56);
    if (st === 'Current') c.appendChild(dot(12, 'radio_primary', 'on air'));
    await T(c, '분당구', 'Body/Small/Strong', st === 'Current' ? 'text_light_primary' : st === 'Passed' ? 'text_dark_tertiary' : 'text_dark_secondary', { name: 'label' });
    list.push(c);
  }
  const set = makeSet(list, fam, 'Region chip', '부읽카 경로 지역 칩 · State', 'State');
  const kL = set.addComponentProperty('Label', 'TEXT', '분당구');
  for (const v of set.children) ref(v, 'label', { characters: kL });
  return set;
}

async function buildWeekDay(root) {
  const fam = await family(root, 'Week day', '요일별 편성 요약. 오늘은 진한 배경.');
  const list = [];
  for (const today of ['False', 'True']) {
    const on = today === 'True';
    const c = comp(`Today=${today}`);
    applyAL(c, 'VERTICAL', { gap: 4, pad: [16, 16, 16, 16], align: 'CENTER', radius: 24, fill: on ? 'button_filled_enabled' : undefined });
    size(c, 200, null);
    await T(c, '월', 'Body/Large/Strong', on ? 'text_light_primary' : 'text_dark_primary', { name: 'day', fillW: true, align: 'CENTER' });
    await T(c, '분양 · 미분양', 'Label/Medium/Normal', on ? 'text_light_secondary' : 'text_dark_tertiary', { name: 'programs', fillW: true, align: 'CENTER', maxLines: 2 });
    list.push(c);
  }
  const set = makeSet(list, fam, 'Week day', '부읽카 주간 편성 요약 · Today', 'Today');
  const kD = set.addComponentProperty('Day', 'TEXT', '월');
  const kP = set.addComponentProperty('Programs', 'TEXT', '분양 · 미분양');
  for (const v of set.children) { ref(v, 'day', { characters: kD }); ref(v, 'programs', { characters: kP }); }
  return set;
}

async function buildCategoryTile(root) {
  const fam = await family(root, 'Category tile', '편성표에 끌어 놓는 소식 박스. Default 흰 배경 + 2px 테두리, Dragging 그림자 Level 3, Disabled 5% 배경 + 32% 글자.');
  const list = [];
  for (const st of ['Default', 'Dragging', 'Disabled']) {
    const c = comp(`State=${st}`);
    applyAL(c, 'VERTICAL', { gap: 8, pad: 16, align: 'CENTER', justify: 'CENTER', radius: 24, fill: st === 'Disabled' ? 'alpha_dark_50' : 'surface_basic' });
    size(c, 200, 120);
    if (st === 'Default') stroke(c, 'alpha_dark_50', 2);
    if (st === 'Dragging') { stroke(c, 'icon_dark_primary', 3); await shadow(c, 3); }
    const off = st === 'Disabled';
    const ic = ICON('cat_subscription', 40, off ? 'icon_dark_quaternary' : 'icon_dark_primary'); ic.name = 'icon'; c.appendChild(ic);
    await T(c, '분양', 'Label/Medium/Strong', off ? 'text_dark_quaternary' : 'text_dark_primary', { name: 'label', fillW: true, align: 'CENTER', maxLines: 1 });
    list.push(c);
  }
  const set = makeSet(list, fam, 'Category tile', '부읽카 소식 박스 · State', 'State');
  const kL = set.addComponentProperty('Label', 'TEXT', '분양');
  const kI = swapProp(set, 'Icon', 'cat_subscription');
  for (const v of set.children) { ref(v, 'label', { characters: kL }); ref(v, 'icon', { mainComponent: kI }); }
  return set;
}

async function buildSlot(root) {
  const fam = await family(root, 'Schedule slot', '편성표 칸. Empty 점선, Filled 소식 박스, Target 놓을 자리(informative_positive 4px), Invalid 같은 요일 중복(informative_negative 4px).');
  const list = [];
  for (const st of ['Empty', 'Filled', 'Target', 'Invalid']) {
    const c = comp(`State=${st}`);
    applyAL(c, 'VERTICAL', { gap: 8, align: 'CENTER', justify: 'CENTER', radius: 24, fill: st === 'Target' || st === 'Invalid' ? 'alpha_dark_50' : undefined });
    size(c, 280, 200);
    if (st === 'Empty') { stroke(c, 'alpha_dark_200', 2); c.dashPattern = [12, 8]; }
    if (st === 'Target') stroke(c, 'informative_positive', 4);
    if (st === 'Invalid') stroke(c, 'informative_negative', 4);
    if (st === 'Filled') {
      const t = inst('categoryTile', { State: 'Default' });
      t.name = 'program';
      c.appendChild(t);
      t.layoutSizingHorizontal = 'FILL'; t.layoutSizingVertical = 'FILL';
    } else {
      const color = st === 'Target' ? 'informative_positive' : st === 'Invalid' ? 'informative_negative' : 'icon_dark_tertiary';
      const ic = ICON(st === 'Invalid' ? 'close' : 'add', 40, color); ic.name = 'icon'; c.appendChild(ic);
      if (st === 'Invalid') await T(c, '이미 있어요', 'Label/Medium/Strong', 'informative_negative', { name: 'reason' });
    }
    list.push(c);
  }
  const set = makeSet(list, fam, 'Schedule slot', '부읽카 편성표 칸 · State', 'State');
  for (const v of set.children) expose(v, 'program');
  return set;
}

async function buildProgramCard(root) {
  const fam = await family(root, 'Program card', '오늘의 1부·2부 프로그램. 모노톤 카드(surface_basic + 그림자 Level 1)에 카테고리는 아이콘과 이름으로 구분합니다. Live = 실제 공공데이터, Sample = 예시 데이터.');
  const list = [];
  for (const status of ['Live', 'Sample']) {
    const c = comp(`Status=${status}`);
    applyAL(c, 'VERTICAL', { gap: 24, pad: 40, fill: 'surface_basic', radius: 32, justify: 'SPACE_BETWEEN' });
    size(c, 760, 480);
    await shadow(c, 1);
    const top = AL('HORIZONTAL', { name: 'top', gap: 16, align: 'CENTER', parent: c, fillW: true });
    const part = AL('HORIZONTAL', { name: 'part chip', pad: [8, 16, 8, 16], fill: 'alpha_dark_50', radius: 16, parent: top });
    await T(part, '1부', 'Label/Medium/Strong', 'text_dark_secondary', { name: 'part' });
    spacer(top);
    const circle = AL('HORIZONTAL', { name: 'icon circle', align: 'CENTER', justify: 'CENTER', fill: 'alpha_dark_50', radius: 'full', parent: top, w: 112, h: 112 });
    const ic = ICON('cat_subscription', 64, 'icon_dark_primary'); ic.name = 'icon'; circle.appendChild(ic);
    const bottom = AL('VERTICAL', { name: 'bottom', gap: 12, parent: c, fillW: true });
    await T(bottom, '분양·청약', 'Headline/Small/Bold', 'text_dark_primary', { name: 'title', fillW: true, maxLines: 2 });
    await T(bottom, '청약 일정과 경쟁률', 'Body/Medium/Normal', 'text_dark_secondary', { name: 'description', fillW: true, maxLines: 1 });
    const stRow = AL('HORIZONTAL', { name: 'status', gap: 8, align: 'CENTER', parent: bottom });
    if (status === 'Live') {
      stRow.appendChild(dot(12, 'informative_active', 'live'));
      await T(stRow, '실시간 데이터', 'Label/Medium/Strong', 'text_dark_secondary', { name: 'status label' });
    } else {
      stRow.appendChild(ICON('info', 28, 'icon_dark_tertiary'));
      await T(stRow, '예시 데이터', 'Label/Medium/Strong', 'text_dark_tertiary', { name: 'status label' });
    }
    list.push(c);
  }
  const set = makeSet(list, fam, 'Program card', '부읽카 오늘의 프로그램 카드 · Status', 'Status');
  const kP = set.addComponentProperty('Part', 'TEXT', '1부');
  const kT = set.addComponentProperty('Title', 'TEXT', '분양·청약');
  const kD = set.addComponentProperty('Description', 'TEXT', '청약 일정과 경쟁률');
  const kI = swapProp(set, 'Icon', 'cat_subscription');
  for (const v of set.children) {
    ref(v, 'part', { characters: kP });
    ref(v, 'title', { characters: kT });
    ref(v, 'description', { characters: kD });
    ref(v, 'icon', { mainComponent: kI });
  }
  return set;
}

async function buildRouteStop(root) {
  const fam = await family(root, 'Route stop', '경로 타임라인의 한 지역. 지난 곳은 흐리게, 지금 읽는 곳은 크게 + 라디오 색 점, 남은 곳은 보통.');
  const list = [];
  for (const st of ['Passed', 'Current', 'Upcoming']) {
    const c = comp(`State=${st}`);
    applyAL(c, 'HORIZONTAL', { gap: 20, align: 'CENTER' });
    size(c, 480, null);
    const rail = AL('VERTICAL', { name: 'rail', align: 'CENTER', parent: c, w: 40 });
    rail.layoutSizingVertical = 'FILL';
    const lineA = figma.createRectangle(); lineA.name = 'line above'; lineA.resize(4, 12); lineA.fills = [P('alpha_dark_100')];
    rail.appendChild(lineA); lineA.layoutSizingVertical = 'FILL';
    if (st === 'Current') rail.appendChild(dot(28, 'radio_primary', 'marker'));
    else if (st === 'Passed') rail.appendChild(dot(16, 'icon_dark_tertiary', 'marker'));
    else {
      const m = dot(16, 'icon_dark_quaternary', 'marker');
      m.fills = [];
      stroke(m, 'icon_dark_quaternary', 3);
      rail.appendChild(m);
    }
    const lineB = figma.createRectangle(); lineB.name = 'line below'; lineB.resize(4, 12); lineB.fills = [P('alpha_dark_100')];
    rail.appendChild(lineB); lineB.layoutSizingVertical = 'FILL';
    const col = AL('VERTICAL', { name: 'text', gap: 2, pad: [12, 0, 12, 0], parent: c, fillW: true });
    const style = st === 'Current' ? 'Title/Large/Bold' : st === 'Passed' ? 'Body/Medium/Normal' : 'Body/Large/Normal';
    const color = st === 'Current' ? 'text_dark_primary' : st === 'Passed' ? 'text_dark_tertiary' : 'text_dark_secondary';
    await T(col, '분당구', style, color, { name: 'district', fillW: true, maxLines: 1 });
    if (st === 'Current') await T(col, '지금 듣는 중', 'Label/Medium/Strong', 'radio_primary', { name: 'now' });
    list.push(c);
  }
  const set = makeSet(list, fam, 'Route stop', '부읽카 경로 타임라인 · State', 'State');
  const kD = set.addComponentProperty('District', 'TEXT', '분당구');
  const kA = set.addComponentProperty('Show line above', 'BOOLEAN', true);
  const kB = set.addComponentProperty('Show line below', 'BOOLEAN', true);
  for (const v of set.children) {
    ref(v, 'district', { characters: kD });
    ref(v, 'line above', { visible: kA });
    ref(v, 'line below', { visible: kB });
  }
  return set;
}

async function buildComponents() {
  const page = PAGES.comps;
  await figma.setCurrentPageAsync(page);
  removeTagged(page, () => true);
  const icons = await figma.getNodeByIdAsync(IDS.iconsFrame);
  const y0 = icons && icons.parent === page ? Math.round(icons.y + icons.height + 240) : 600;
  const root = AL('VERTICAL', { name: '부읽카 컴포넌트 라이브러리', gap: 200 });
  page.appendChild(root);
  root.x = 0; root.y = y0;
  tag(root, 'components');
  const order = [
    ['button', buildButton], ['iconButton', buildIconButton], ['textButton', buildTextButton], ['toggle', buildToggle],
    ['tabItem', buildTabItem], ['tabs', buildTabs], ['switch', buildSwitch], ['radio', buildRadio], ['checkbox', buildCheckbox],
    ['listRow', buildListRow], ['dropdown', buildDropdown], ['dropdownItem', buildDropdownItem], ['dropdownMenu', buildDropdownMenu],
    ['appIcon', buildAppIcon], ['pageNav', buildPageNav], ['toast', buildToast], ['popup', buildPopup], ['indicator', buildIndicator],
    ['progress', buildProgress], ['chip', buildChip], ['weekDay', buildWeekDay], ['categoryTile', buildCategoryTile],
    ['slot', buildSlot], ['programCard', buildProgramCard], ['routeStop', buildRouteStop],
  ];
  let ok = 0;
  for (const pair of order) {
    try {
      const node = await pair[1](root);
      if (node) { C[pair[0]] = node; tag(node, 'component'); ok++; }
    } catch (e) {
      LOG.push(`❌ 컴포넌트 ${COMP_NAMES[pair[0]]}: ${errText(e)}`);
      console.error(e);
    }
  }
  removeTagged(page, t => t === 'tmp');
  log(`컴포넌트 ${ok}/${order.length}개 완성`);
}

async function findComponents() {
  const found = PAGES.comps.findAll(n => (n.type === 'COMPONENT_SET' || n.type === 'COMPONENT') && n.getPluginData(NS) === 'component');
  for (const k of Object.keys(COMP_NAMES)) {
    const n = found.find(x => x.name === COMP_NAMES[k]);
    if (n) C[k] = n;
  }
  log(`기존 컴포넌트 ${Object.keys(C).length}개를 찾았어요`);
}

// ---------------------------------------------------------------- docs helpers
async function board(page, name, x, eyebrow, title, desc) {
  const b = AL('VERTICAL', { name, gap: 96, pad: [96, 96, 120, 96], fill: 'basic_00', radius: 32 });
  page.appendChild(b);
  size(b, 1600, null);
  b.x = x; b.y = 0;
  await sectionHead(b, eyebrow, title, desc);
  return b;
}
function sec(parent, name) { return AL('VERTICAL', { name: name || 'section', gap: 32, parent, fillW: true }); }
async function sectionHead(parent, eyebrow, title, desc) {
  const s = AL('VERTICAL', { name: 'head · ' + title, gap: 12, parent });
  await T(s, eyebrow, 'Label/Small/Strong', 'informative_positive');
  await T(s, title, 'Title/Large/Bold');
  if (desc) await T(s, desc, 'Body/Small/Normal', 'text_dark_secondary', { w: IW });
  return s;
}
async function bullet(parent, text, w) {
  const r = AL('HORIZONTAL', { name: 'bullet', gap: 12, parent });
  await T(r, '•', 'Body/Small/Normal', 'text_dark_tertiary');
  await T(r, text, 'Body/Small/Normal', 'text_dark_secondary', { w: w - 28 });
  return r;
}
async function card(parent, title, lines, w, o) {
  o = o || {};
  const c = AL('VERTICAL', { name: 'card · ' + (title || ''), gap: 16, pad: 40, fill: o.fill || 'surface_low', radius: 24, parent });
  size(c, w, null);
  if (o.fillW) c.layoutSizingHorizontal = 'FILL';
  if (title) await T(c, title, 'Body/Large/Strong', 'text_dark_primary', { w: w - 80 });
  for (const l of lines || []) await bullet(c, l, w - 80);
  return c;
}
async function table(parent, headers, rows, widths) {
  const t = AL('VERTICAL', { name: 'table', parent });
  radius(t, 16);
  stroke(t, 'basic_200', 1);
  t.clipsContent = true;
  const hdr = AL('HORIZONTAL', { name: 'header', fill: 'surface_low', parent: t });
  for (let i = 0; i < headers.length; i++) {
    const cell = AL('VERTICAL', { pad: [12, 16, 12, 16], parent: hdr, w: widths[i] });
    await T(cell, headers[i], 'Label/Small/Strong', 'text_dark_secondary', { w: widths[i] - 32 });
  }
  for (const r of rows) {
    const row = AL('HORIZONTAL', { name: 'row', parent: t });
    stroke(row, 'basic_200', 1);
    row.strokeTopWeight = 1; row.strokeBottomWeight = 0; row.strokeLeftWeight = 0; row.strokeRightWeight = 0;
    for (let i = 0; i < r.length; i++) {
      const cell = AL('VERTICAL', { pad: [16, 16, 16, 16], gap: 8, parent: row, w: widths[i] });
      cell.layoutSizingVertical = 'FILL';
      const v = r[i];
      if (typeof v === 'function') await v(cell, widths[i] - 32);
      else await T(cell, String(v), i === 0 ? 'Label/Medium/Strong' : 'Label/Medium/Normal', i === 0 ? 'text_dark_primary' : 'text_dark_secondary', { w: widths[i] - 32 });
    }
  }
  return t;
}
async function chipText(parent, text, on) {
  const c = AL('HORIZONTAL', { name: 'chip', pad: [8, 16, 8, 16], radius: 'full', fill: on ? 'button_filled_enabled' : 'alpha_dark_50', parent });
  await T(c, text, 'Label/Small/Strong', on ? 'text_light_primary' : 'text_dark_secondary');
  return c;
}

// ---------------------------------------------------------------- board 04
const TYPE_ORDER = [
  'Headline/Large/ExtraBold', 'Headline/Large/Bold', 'Headline/Medium/ExtraBold', 'Headline/Medium/Bold', 'Headline/Small/ExtraBold', 'Headline/Small/Bold',
  'Title/Large/ExtraBold', 'Title/Large/Bold', 'Title/Large/Regular', 'Title/Medium/ExtraBold', 'Title/Medium/Bold', 'Title/Medium/Regular',
  'Title/Small/ExtraBold', 'Title/Small/Bold', 'Title/Small/Regular',
  'Body/Large/Strong', 'Body/Large/Normal', 'Body/Medium/Strong', 'Body/Medium/Normal', 'Body/Small/Strong', 'Body/Small/Normal',
  'Label/Medium/Strong', 'Label/Medium/Normal', 'Label/Small/Strong', 'Label/Small/Normal',
];
const TYPE_USE = {
  'Headline/Large/Bold': '방송 중 카테고리 이름',
  'Headline/Small/Bold': '오늘의 프로그램 카드 제목',
  'Title/Large/Bold': '지금 읽는 지역',
  'Title/Medium/Bold': '페이지 제목, 팝업 제목',
  'Title/Small/Bold': '안내 문장',
  'Body/Large/Strong': '목록 제목, 요일',
  'Body/Large/Normal': '지금 읽는 문장, 팝업 설명',
  'Body/Medium/Strong': 'Large 버튼, 탭',
  'Body/Medium/Normal': '카드 설명, 토스트',
  'Body/Small/Strong': 'Medium 버튼, 지역 칩',
  'Label/Medium/Strong': '1부·2부, 소식 박스',
  'Label/Medium/Normal': '부제, 보조 설명',
  'Label/Small/Strong': '상태 칩',
};

async function buildBoard04() {
  const page = PAGES.docs;
  await figma.setCurrentPageAsync(page);
  removeTagged(page, t => t === 'board-04');
  const b = await board(page, '04 타이포 · 그림자 · 모션 · 아이콘', 7040, '04 · TYPE · SHADOW · MOTION · ICON', '타이포그래피 · 그림자 · 모션 · 아이콘',
    '정보의 위계는 글자 크기로, 깊이는 그림자 3단계로, 상태 변화는 짧은 모션으로 표현합니다. 아이콘, 앱 아이콘, 디자인 토큰 규칙도 이 보드에 모았습니다.');
  tag(b, 'board-04');

  // Typography
  let s = sec(b, 'typography');
  await sectionHead(s, 'TYPOGRAPHY', '타이포그래피', '중립적인 형태와 균일한 시인성을 가진 서체를 씁니다. 시스템 서체는 Asta Sans(한국어 · 영어)입니다. 모든 텍스트 스타일은 이 파일의 로컬 스타일로 등록되어 있습니다.');
  let row = AL('HORIZONTAL', { name: 'rules', gap: 24, parent: s });
  await card(row, '운영 가이드', ['정보 위계에 따라 컴포넌트 사이에 일관된 크기를 적용', '보조 텍스트는 위계를 구분해 사용', '브랜드 서체를 쓰더라도 캐릭터가 강한 서체는 지양'], 692);
  await card(row, '최소 크기', ['Android 접근성 기준 최소 12sp (160dpi에서 12px)', '가독성을 위해 1.5배 이상, 실제 높이 18~20px 이상 확보', '중요 정보는 높이 5mm 이상, 보조 정보는 3mm 이상', '부읽카의 가장 작은 글자: Label/Small 20px'], 692);
  const rows = [];
  for (const name of TYPE_ORDER) {
    const st = TSL.find(x => x.name === name);
    if (!st) { warn('텍스트 스타일 없음: ' + name); continue; }
    const lh = st.lineHeight && st.lineHeight.unit === 'PIXELS' ? st.lineHeight.value : '자동';
    rows.push([name, async (cell, w) => { await T(cell, '부동산 읽어주는 카', name, 'text_dark_primary', { w }); },
      `${st.fontSize} / ${lh} · ${st.fontName.style}`, TYPE_USE[name] || '—']);
  }
  await table(s, ['스타일', '견본', '크기 / 행간 · 굵기', '부읽카 사용처'], rows, [300, 640, 220, 248]);

  // Shadow
  s = sec(b, 'shadow');
  await sectionHead(s, 'SHADOW', '그림자', '높이(elevation)에 따라 3단계를 씁니다. 부읽카는 카드에 Level 1, 드롭다운 목록에 Level 2, 끌고 있는 박스와 팝업에 Level 3을 씁니다.');
  const panel = AL('HORIZONTAL', { name: 'shadow panel', gap: 48, pad: 64, fill: 'surface_low', radius: 24, parent: s, fillW: true });
  const SH = [
    [1, '가장 낮은 elevation · 카드', ['X 0 · Y 6 · Blur 24 · Spread 0', '#000000 8%']],
    [2, 'Level 1보다 높은 elevation · 목록', ['X 0 · Y 8 · Blur 32 · #000000 16%', '+ X 0 · Y 2 · Blur 2 · #000000 8%']],
    [3, '가장 높은 elevation · 팝업, 드래그', ['X 0 · Y 20 · Blur 40 · #000000 24%', '+ X 0 · Y 2 · Blur 4 · #000000 20%']],
  ];
  for (const d of SH) {
    const c = AL('VERTICAL', { name: 'Shadow/Level ' + d[0], gap: 12, pad: 40, fill: 'surface_basic', radius: 24, parent: panel, fillW: true });
    size(c, 400, 260);
    c.layoutSizingHorizontal = 'FILL';
    await shadow(c, d[0]);
    await T(c, 'Level ' + d[0], 'Title/Small/Bold');
    await T(c, d[1], 'Body/Small/Normal', 'text_dark_secondary', { fillW: true });
    for (const l of d[2]) await T(c, l, 'Label/Medium/Normal', 'text_dark_tertiary', { fillW: true });
  }

  // Interaction
  s = sec(b, 'interaction');
  await sectionHead(s, 'INTERACTION · MOTION', '인터랙션 · 모션', '버튼과 터치 가능한 모든 요소에는 shrink effect가 있습니다. 운전 중에는 동영상, 움직이는 이미지, 텍스트 자동 스크롤처럼 시선을 끄는 모션을 쓰지 않습니다.');
  const steps = AL('HORIZONTAL', { name: 'button interaction', gap: 24, pad: 40, fill: 'surface_low', radius: 24, parent: s, fillW: true });
  const STEPS = [
    ['Step 1 · Enabled', '누르기 전 기본 상태', 'Enabled', 1],
    ['Step 2 · Pressed', '95%로 줄고 모드별 배경색으로 바뀜. 터치 영역 밖으로 나가면 취소', 'Pressed', 0.95],
    ['Step 3 · Release', '영역 안에서 손을 떼면 실행(앱 실행, 상태 변경 등)', 'Enabled', 1],
  ];
  for (const d of STEPS) {
    const col = AL('VERTICAL', { name: d[0], gap: 16, parent: steps, fillW: true });
    const stage = AL('HORIZONTAL', { name: 'stage', align: 'CENTER', justify: 'CENTER', fill: 'surface_basic', radius: 16, parent: col, fillW: true, h: 180 });
    stage.layoutSizingHorizontal = 'FILL';
    add(stage, inst('button', { Hierarchy: 'Primary', Size: 'Large', State: d[2] }, { Label: '오늘 방송 듣기', 'Show prefix icon': true }), d[3]);
    await T(col, d[0], 'Body/Medium/Strong', 'text_dark_primary');
    await T(col, d[1], 'Label/Medium/Normal', 'text_dark_secondary', { fillW: true });
  }
  await table(s, ['Gesture', '설명'], [
    ['Pressed', '차량 설정, 공조, 앱을 누르면 연결된 영역을 실행하거나 닫음'],
    ['Long pressed', '2~3초 누르고 있으면 편집 모드'],
    ['Swipe', '누른 뒤 상하좌우 이동값이 있으면 지정된 동작, 일정 속도 이하는 Drag로 처리 (예: 윈도 바 아래로 스와이프 = 앱 종료)'],
    ['Drag & Drop', '누른 뒤 이동값이 있으면 지정된 동작, 일정 속도 이상은 Swipe로 처리 · 부읽카 편성표의 박스 옮기기'],
  ], [300, 1108]);
  row = AL('HORIZONTAL', { name: 'motion cards', gap: 24, parent: s });
  await card(row, 'Window 인터랙션', ['Drag start: 윈도 바를 누르면 창이 수축', 'Drag end: 손을 떼면 수축이 풀림', 'Reorder: 다른 창 너비의 50% 이상 옮기면 자리 교체', 'Close: 창 높이 기준 60% 이후에서 놓으면 종료, 0~59%는 제자리로'], 692);
  await card(row, '스크롤 끝 · Duration', ['스크롤 끝에서 Bounce effect로 끝을 알림 (페이지, 메뉴, 캐러셀, 리스트)', '오버스크롤 최대 범위는 전체 스크롤 영역의 약 2/5', 'Short: 작은 유틸리티 전환 · Medium: 화면 중간 영역 전환 · Long: Emphasized easing과 함께 큰 전환', '이징은 Pleos Tension 곡선을 따름 (공식 Motion 이미지)'], 692);
  const anim = AL('HORIZONTAL', { name: 'loading animation', gap: 24, pad: 40, fill: 'surface_low', radius: 24, parent: s, fillW: true, align: 'CENTER' });
  for (const d of [['Linear', '정적인 로딩이 필요할 때', 48], ['Tension', '페이지 로딩처럼 다소 길어질 때', 72]]) {
    const col = AL('HORIZONTAL', { name: d[0], gap: 24, align: 'CENTER', parent: anim, fillW: true });
    col.appendChild(spinner(d[2], 'icon_dark_primary'));
    const tc = AL('VERTICAL', { gap: 4, parent: col, fillW: true });
    await T(tc, 'Loading · ' + d[0], 'Body/Medium/Strong');
    await T(tc, d[1], 'Label/Medium/Normal', 'text_dark_secondary', { fillW: true });
  }
  await T(s, '부읽카 적용: 경로 소식을 모으는 동안 "오늘 방송 듣기" 버튼을 Loading 상태로 바꾸고, 응답이 0.25초를 넘으면 진행 중임을 보여 줍니다.', 'Body/Small/Normal', 'text_dark_secondary', { w: IW });

  // Iconography
  s = sec(b, 'iconography');
  await sectionHead(s, 'ICONOGRAPHY', '아이콘', '아이콘은 누구나 직관적으로 알아볼 수 있어야 하며, 텍스트 라벨과 함께 써서 이해를 돕습니다.');
  row = AL('HORIZONTAL', { name: 'icon rules', gap: 24, parent: s });
  await card(row, '제작 가이드', ['Grid: 기본 48 × 48dp 그리드로 제작', 'Style: 시인성에 따라 Filled / Outlined 2가지', 'Variant: 1벌을 만든 뒤 필요한 크기로 조정'], 692);
  await card(row, '부읽카 아이콘 세트', ['48dp, 24 기준 2px 외곽선(Outlined), 재생 계열은 Filled', '색은 icon_dark_* / icon_light_* 변수에 연결', '카테고리 10종은 색 대신 모양으로 구분'], 692);
  const grid = AL('HORIZONTAL', { name: 'icon grid', gap: 16, wrap: true, wrapGap: 16, parent: s, fillW: true });
  for (const k of Object.keys(ICONS)) {
    const cell = AL('VERTICAL', { name: k, gap: 12, pad: [24, 8, 20, 8], align: 'CENTER', fill: 'surface_low', radius: 16, parent: grid, w: 162 });
    cell.appendChild(ICON(k, 48));
    await T(cell, k, 'Label/Small/Normal', 'text_dark_secondary', { w: 146, align: 'CENTER' });
  }
  const sizes = AL('HORIZONTAL', { name: 'icon sizes', gap: 48, pad: 40, align: 'MAX', fill: 'surface_low', radius: 24, parent: s, fillW: true });
  for (const px of [48, 40, 36, 32, 28]) {
    const col = AL('VERTICAL', { gap: 12, align: 'CENTER', parent: sizes });
    col.appendChild(ICON('broadcast', px));
    await T(col, px + '', 'Label/Small/Strong', 'text_dark_tertiary');
  }

  // App icon
  s = sec(b, 'app icon');
  await sectionHead(s, 'APP ICON', '앱 아이콘', 'GNB, App library, App Market에서 서비스를 대표하는 이미지입니다. Android Adaptive Icon 형식으로 Foreground(로고·그래픽)와 Background(배경)를 분리해 만들고, 브랜드별 마스크는 시스템이 적용합니다.');
  const ai = AL('HORIZONTAL', { name: 'adaptive icon', gap: 40, pad: 48, align: 'CENTER', fill: 'surface_low', radius: 24, parent: s, fillW: true });
  const layer = async (label, build) => {
    const col = AL('VERTICAL', { name: label, gap: 16, align: 'CENTER', parent: ai });
    const box = AL('HORIZONTAL', { name: 'box', align: 'CENTER', justify: 'CENTER', parent: col, w: 192, h: 192 });
    stroke(box, 'alpha_dark_200', 2); box.dashPattern = [8, 6];
    await build(box);
    await T(col, label, 'Label/Medium/Strong', 'text_dark_secondary');
  };
  await layer('Background', async box => {
    const r = figma.createRectangle(); r.resize(192, 192); r.fills = [P('static_dark_100')]; box.appendChild(r);
  });
  await T(ai, '+', 'Title/Large/Bold', 'text_dark_tertiary');
  await layer('Foreground', async box => {
    box.fills = [P('alpha_dark_100')];
    const safe = figma.createEllipse(); safe.name = 'safe zone'; safe.resize(128, 128); safe.fills = [];
    stroke(safe, 'alpha_dark_300', 2); safe.dashPattern = [6, 6];
    box.appendChild(safe); safe.layoutPositioning = 'ABSOLUTE'; safe.x = 32; safe.y = 32;
    box.appendChild(appGlyph(116));
  });
  await T(ai, '=', 'Title/Large/Bold', 'text_dark_tertiary');
  const res = AL('VERTICAL', { name: 'result', gap: 16, align: 'CENTER', parent: ai });
  const sizesRow = AL('HORIZONTAL', { name: 'sizes', gap: 24, align: 'MAX', parent: res });
  if (C.appIcon) {
    add(sizesRow, C.appIcon.createInstance(), 192 / 80);
    add(sizesRow, C.appIcon.createInstance(), 1);
    add(sizesRow, C.appIcon.createInstance(), 0.6);
  }
  await T(res, '마스크 적용 · 192 / 80 / 48', 'Label/Medium/Strong', 'text_dark_secondary');
  await T(s, '그리드와 차량 브랜드별 마스킹 스타일은 Pleos Connect UI Design Guide의 Design Resources(Figma 템플릿)를 따릅니다. 부읽카 아이콘: 집 + 소리 물결, 배경 #131417, 전경 흰색.', 'Body/Small/Normal', 'text_dark_secondary', { w: IW });

  // Tokens & theme
  s = sec(b, 'tokens');
  await sectionHead(s, 'DESIGN TOKEN · THEME', '디자인 토큰 · 테마', '토큰은 색, 글꼴, 간격, 그림자 같은 스타일 값을 코드로 추상화한 단위이며, 디자이너와 개발자의 공통 언어이자 Single Source of Truth입니다.');
  const flow = AL('HORIZONTAL', { name: 'token flow', gap: 16, align: 'CENTER', parent: s, fillW: true });
  const FLOW = [
    ['Raw', 'Pleos · Raw', 'gray/00~10, accent, alpha 원본 값'],
    ['Scale', 'basic · alpha · static', 'basic_00~900, alpha_dark/light_50~500, static_light/dark_100~400'],
    ['System', 'system/그룹/토큰', '19개 그룹 118개 의미 토큰 (text, button, surface …)'],
    ['Component', 'Button, Switch …', '컴포넌트는 System 토큰에만 연결'],
  ];
  for (let i = 0; i < FLOW.length; i++) {
    const c = AL('VERTICAL', { name: FLOW[i][0], gap: 8, pad: 32, fill: i === 2 ? 'button_filled_enabled' : 'surface_low', radius: 24, parent: flow, fillW: true });
    await T(c, FLOW[i][0], 'Body/Large/Strong', i === 2 ? 'text_light_primary' : 'text_dark_primary');
    await T(c, FLOW[i][1], 'Label/Medium/Strong', i === 2 ? 'text_light_secondary' : 'text_dark_secondary', { fillW: true });
    await T(c, FLOW[i][2], 'Label/Medium/Normal', i === 2 ? 'text_light_secondary' : 'text_dark_tertiary', { fillW: true });
    if (i < FLOW.length - 1) await T(flow, '→', 'Title/Medium/Bold', 'text_dark_tertiary');
  }
  row = AL('HORIZONTAL', { name: 'token rules', gap: 24, parent: s });
  await card(row, '이름 규칙', ['Prefix: ivi · Type: snake_case', '줄임말 금지: Do vertical / Don’t v, Do vehicle_setting / Don’t vs', '이름만 보고 어디에 쓰는지 알 수 있어야 함', '예: ivi_text_dark_primary, ivi_button_basic_enabled, ivi_spacing_24_dp'], 692);
  await card(row, '테마 · Light / Dark', ['테마는 아이콘, 글꼴, 색으로 브랜드 아이덴티티를 유지', '같은 이름의 변수를 Pleos · Color Light / Color Dark 두 컬렉션에 둠 (Starter 플랜은 컬렉션당 모드 1개)', '다크 화면은 Light 변수를 같은 이름의 Dark 변수로 다시 연결해 만듦', '주간·야간 조명에 따라 밝기와 대비를 자동 조정'], 692);
  await card(s, 'Dimension 토큰 · Pleos · Dimension', ['spacing: 04 · 08 · 12 · 16 · 24 · 32 · 40 · 48 · 64', 'radius: 08 · 16 · 24 · 32 · 48 · full', 'size: button_large 100 · button_medium 80 · button_small 64 · icon_button 80 · icon_grid 48 · switch 80 × 48', '컴포넌트의 여백, 간격, 모서리, 높이는 이 변수에 연결되어 있음'], IW);

  // 3D
  s = sec(b, '3d');
  await sectionHead(s, '3D ASSETS', '3D 에셋', '직관적인 사용성을 위해 3D 모델 데이터를 쓸 수 있습니다. 부읽카는 3D 에셋을 쓰지 않습니다.');
  row = AL('HORIZONTAL', { name: '3d cards', gap: 24, parent: s });
  await card(row, '모델 · 엔진', ['Lowpoly 차량 데이터 (내외장 포함 3Ds Max 30MB, FBX 6MB 기준)', 'Highpoly 도면 데이터는 Retopology, Drawcall 최소화', 'glTF 포맷 · Google Filament(PBR) 엔진'], 692);
  await card(row, '속성 · 용례', ['glTF animation은 Naming과 속성 값으로 제어', 'Part naming 예: Door_F_L, Trunk, Wheel_F_R, Seat_F_L_Top', '용례: 공조, 스티어링 휠, 시트 포지션'], 692);
}

// ---------------------------------------------------------------- board 05
const SPECS = [
  { name: 'Basic button', ko: '기본 버튼', built: 'button', def: '명확한 영역과 텍스트로 행동을 이끄는 가장 기본적인 버튼.',
    anatomy: ['Root', 'Label', 'Icon(Prefix)', 'Icon(Suffix)'], states: ['Enabled', 'Pressed', 'Disabled', 'Loading'],
    props: ['Hierarchy: Primary(basic) / Secondary(filled)', 'Size: Large / Medium / Small'],
    spec: ['높이 100 / 80 / 64 · 모서리 32 / 24 / 16 · 좌우 여백 40 / 32 / 24', '눌림: 95% 축소 + 모드별 배경색, 영역 밖으로 나가면 취소'],
    preview: pv => {
      add(pv, inst('button', { Hierarchy: 'Secondary', Size: 'Large', State: 'Enabled' }, { Label: '오늘 방송 듣기', 'Show prefix icon': true }));
      add(pv, inst('button', { Hierarchy: 'Primary', Size: 'Medium', State: 'Enabled' }, { Label: '취소' }));
    } },
  { name: 'Toggle button', ko: '토글 버튼', built: 'toggle', def: '켜짐/꺼짐이 있는 버튼. 여러 옵션의 상태를 알려 줄 때 씁니다.',
    anatomy: ['Root', 'Label', 'Icon(Prefix)', 'Icon(Suffix)'], states: ['Toggled (기본 False)', 'Pressed', 'Disabled'],
    props: ['Floating: 배경에 따라 그림자 또는 색으로 표현', 'Size: Large / Medium / Small'],
    spec: ['Off: button_basic_* · On: button_switch_* + 그림자 Level 1', '비활성: 2px 테두리 #131417 10%'],
    preview: pv => {
      add(pv, inst('toggle', { Toggled: 'On', Size: 'Medium', State: 'Enabled' }, { Label: '자동 시작' }));
      add(pv, inst('toggle', { Toggled: 'Off', Size: 'Medium', State: 'Enabled' }, { Label: '직접 시작' }));
    } },
  { name: 'Text button', ko: '텍스트 버튼', built: 'textButton', def: '부가 정보로 가는 링크. 앞·뒤 아이콘으로 뜻을 보강합니다.',
    anatomy: ['Root', 'Label', 'Icon(Prefix)', 'Icon(Suffix)'], states: ['Pressed', 'Disabled', 'Loading'],
    props: ['Size: Medium / Small'], spec: ['글자색 informative_positive (#0064FF)'],
    preview: pv => { add(pv, inst('textButton', { Size: 'Medium', State: 'Enabled' }, { Label: '시연 주행' })); } },
  { name: 'Icon button', ko: '아이콘 버튼', built: 'iconButton', def: '누구나 바로 알아보는 아이콘일 때만 씁니다.',
    anatomy: ['Root', 'Icon'], states: ['Pressed', 'Disabled'],
    props: ['Style: Basic / Plain / Circle', 'Size: Large / Medium / Small'],
    spec: ['80 / 64 / 48 · 아이콘 48 / 40 / 32', '배경 5% → 눌림 10% · 비활성 흰 20% + 아이콘 32%'],
    preview: pv => {
      add(pv, inst('iconButton', { Style: 'Basic', Size: 'Large', State: 'Enabled' }, { Icon: iconId('schedule') }));
      add(pv, inst('iconButton', { Style: 'Plain', Size: 'Large', State: 'Enabled' }, { Icon: iconId('back') }));
      add(pv, inst('iconButton', { Style: 'Circle', Size: 'Large', State: 'Enabled' }, { Icon: iconId('replay') }));
      add(pv, inst('iconButton', { Style: 'Basic', Size: 'Large', State: 'Disabled' }, { Icon: iconId('settings') }));
    } },
  { name: 'Keypad', ko: '키패드', def: '숫자, 텍스트 등 정보를 입력합니다.',
    anatomy: ['Root', 'Label', 'Icon(Prefix)', 'Icon(Suffix)'], states: ['Pressed', 'Disabled'],
    props: ['Keypad: Primary / Secondary / Special', 'Keyboard: Text / Voice'],
    spec: ['keyboard_normal_* · keyboard_static_* 토큰', '부읽카는 주행 중 텍스트 입력을 쓰지 않아 미사용'] },
  { name: 'Bar', ko: '바', def: '앱 창의 크기·위치를 조정하거나 콘텐츠를 탐색합니다.',
    anatomy: ['Root', 'Control', 'Handle'], states: ['Variant: Light / Dark', 'Dragging', 'Pressed', 'Disabled (손잡이 숨김)'],
    props: ['Type: Window bar(창 크기) / Scroll bar(콘텐츠 탐색)'], spec: ['Window bar는 시스템이 그리므로 앱 화면에 넣지 않음'] },
  { name: 'Dropdown', ko: '드롭다운', built: 'dropdown', def: '메뉴를 이동하거나 원하는 속성으로 바꿉니다.',
    anatomy: ['Root', 'Label', 'Prefix', 'Suffix', 'Container'], states: ['Opened', 'Pressed', 'Disabled'],
    props: ['Prefix: App icon / Icon', 'Suffix: Switch / Icon', 'Size: Medium / Small'],
    spec: ['높이 100 / 80 · 테두리 2px #131417 10% · 목록 항목 100', '열린 목록: background_popup + 그림자 Level 2'],
    preview: pv => { add(pv, inst('dropdown', { Size: 'Medium', State: 'Enabled' }, { Label: '정자동 → 역삼동' })); } },
  { name: 'Controllers', ko: '컨트롤러', def: '최솟값과 최댓값 사이의 값(볼륨, 온도 등)을 슬라이더나 버튼으로 조절합니다.',
    anatomy: ['Root', 'Control', 'Handle', 'Track', 'Icon(Prefix/Suffix)', 'Stepper'], states: ['Pressed / Dragging'],
    props: ['Type: Continuous slider / Centered slider / Stepper', 'Options: Disabled(최소·최대 도달), MinValue, MaxValue, Step', 'Slider: Basic / Floating(밝은 배경용)'],
    spec: ['볼륨 단계: Max 100 · High 76~100 · Mid 26~75 · Low 1~25 · Min 0%', 'controller_slider_* 토큰'] },
  { name: 'Number field', ko: '숫자 필드', def: '숫자 정보를 입력합니다.',
    anatomy: ['Root', 'Label', 'Cursor', 'Field'], states: ['Value', 'Focused', 'Disabled', 'Read only', 'Invalid'], props: ['Size: Large / Medium'] },
  { name: 'Text field', ko: '텍스트 필드', def: '텍스트, 비밀번호, 이메일 등을 입력합니다.',
    anatomy: ['Root', 'Field', 'Cursor', 'Placeholder', 'Suffix(선택)'], states: ['Value', 'Focused', 'Disabled', 'Read only', 'Invalid'],
    props: ['Type: Text / Tel / Url / Email / Password', 'Size: Medium / Small'], spec: ['field_background · field_focused(basic_700) 토큰'] },
  { name: 'Indicator', ko: '인디케이터', built: 'indicator', def: '여러 값 가운데 지금 위치를 보여 줍니다.',
    anatomy: ['Root', 'Present value', 'Next · Prev value'], states: ['Present'], props: ['Size: Wide / Narrow'],
    spec: ['점 8 · 간격 16 · 지금 값 100% / 나머지 32%'],
    preview: pv => {
      add(pv, inst('indicator', { Size: 'Wide', Present: '2' }));
      add(pv, inst('indicator', { Size: 'Narrow', Present: '3' }));
    } },
  { name: 'System notification', ko: '시스템 알림', def: 'OS가 기본으로 제공하는 알림입니다.',
    anatomy: ['Root', 'Imagery', 'Header', 'Action'], states: ['—'],
    props: ['Type: App icon / Profile / Image / Action', 'Button align: Single / Vertical / Horizontal'],
    spec: ['운전자에게 필요한 경우에만 알림, 광고 금지'] },
  { name: 'Vehicle notification', ko: '차량 알림', def: '주행 중 차량 상태를 알리는 시스템 알림입니다.',
    anatomy: ['Root', 'Telltale', 'Title text', 'Description text', 'Guide image'], states: ['—'], spec: ['시스템 전용 · 앱은 그리지 않음'] },
  { name: 'Page navigation', ko: '페이지 내비게이션', built: 'pageNav', def: '앱 서비스의 주요 이동 경로를 제공하는 상단 영역입니다.',
    anatomy: ['Root', 'App icon', 'Primary menu', 'Secondary menu(선택)', 'Suffix'], states: ['—'],
    props: ['Type: Main / Depth(뒤로 버튼)', 'Subtitle: 부가 정보', 'Secondary: 두 번째 메뉴'],
    spec: ['높이 128 · 앱 아이콘 80 · 제목 Title/Medium · 부제 Label/Medium', '부읽카: 홈·방송 중 = Main, 편성표·설정 = Depth'],
    preview: pv => {
      const n = inst('pageNav', { Type: 'Depth' }, { Title: '이번 주 편성표', Subtitle: '박스를 끌어서 놓으세요', 'Show suffix': false });
      pv.appendChild(n); n.layoutSizingHorizontal = 'FILL';
    } },
  { name: 'System popup', ko: '시스템 팝업', built: 'popup', def: '앱 안에서 기본으로 쓰는 팝업. 맥락에 맞춰 Body에 콘텐츠를 넣을 수 있습니다.',
    anatomy: ['Root', 'Title', 'Description(선택)', 'Confirm action', 'Cancel action(선택)'], states: ['—'],
    props: ['Header: Icon / SubTitle / Description', 'Body: 콘텐츠 영역', 'Size: Large / Medium', 'Button align: Single / Vertical / Horizontal'],
    spec: ['너비 1200 / 808 · 모서리 48 · 여백 48 · 간격 24 · 버튼 높이 100', '확인(진한 버튼)이 왼쪽 · 테두리 2px #131417 5% · 그림자 Level 3'],
    preview: pv => { add(pv, inst('popup', { Size: 'Medium', Buttons: 'Horizontal' }, { Title: '편성을 처음으로 되돌릴까요?', Description: '이번 주 편성표가 기본 편성으로 바뀌어요.' }), 0.62); } },
  { name: 'Toast · Toast popup', ko: '토스트', built: 'toast', def: '앱을 쓰는 중 잠깐 띄우는 알림입니다.',
    anatomy: ['Root', 'Toast message'], states: ['—'],
    spec: ['앱 패널 안 좌우·아래 24 여백', '최대 너비 792 · 메시지 최대 712 · 높이 80', '배경 #131417 84% · 흰 글자'],
    preview: pv => { add(pv, inst('toast', { Type: 'Default' }, { Message: '같은 요일에 같은 소식은 넣을 수 없어요' }), 0.8); } },
  { name: 'Tabs', ko: '탭', built: 'tabs', def: '2개 이상의 콘텐츠나 기능 옵션을 전환합니다.',
    anatomy: ['Root', 'Tab item', 'Label', 'Selected indicator', 'Tab container'], states: ['Enable', 'Pressed', 'Selected'],
    props: ['Type: Box / Line', 'Fitted: 너비에 맞춰 균등 / 왼쪽 정렬'],
    spec: ['Box: 컨테이너 100 · 안쪽 8 · 선택 항목 84 흰 배경', 'Line: 선택 항목 아래 4px'],
    preview: pv => {
      const t = inst('tabs', { Type: 'Box', Items: '2' });
      pv.appendChild(t); t.layoutSizingHorizontal = 'FILL';
      if (t.type === 'INSTANCE') { setNested(t, 'tab 1', { Label: '내 경로' }); setNested(t, 'tab 2', { Label: '저장된 경로' }); }
    } },
  { name: 'Checkbox', ko: '체크박스', built: 'checkbox', def: '여러 개를 함께 고를 수 있는 선택입니다.',
    anatomy: ['Root', 'Control', 'Icon'], states: ['Checked', 'Pressed', 'Disabled'],
    spec: ['48 · 모서리 12 · 테두리 4px #131417 20%', '선택 #02C265(informative_active) + 흰 체크'],
    preview: pv => {
      add(pv, inst('checkbox', { Checked: 'Off', State: 'Enabled' })); add(pv, inst('checkbox', { Checked: 'On', State: 'Enabled' }));
      add(pv, inst('checkbox', { Checked: 'On', State: 'Disabled' }));
    } },
  { name: 'Radio button', ko: '라디오 버튼', built: 'radio', def: '여러 옵션 중 하나만 고릅니다.',
    anatomy: ['Root', 'Control'], states: ['Value(Selected)', 'Pressed', 'Disabled', 'Invalid'], spec: ['48 원형 · 테두리 4px 20% · 선택 #02C265 + 흰 점'],
    preview: pv => {
      add(pv, inst('radio', { Selected: 'Off', State: 'Enabled' })); add(pv, inst('radio', { Selected: 'On', State: 'Enabled' }));
      add(pv, inst('radio', { Selected: 'On', State: 'Disabled' }));
    } },
  { name: 'Switch', ko: '스위치', built: 'switch', def: 'On/Off로 상태를 바꿉니다.',
    anatomy: ['Root', 'Handle', 'Track'], states: ['Checked', 'Pressed', 'Disabled', 'Invalid'],
    spec: ['트랙 80 × 48 · 모서리 16 · 손잡이 32 (안쪽 8)', 'Off #131417 30% · On #02C265'],
    preview: pv => {
      add(pv, inst('switch', { Checked: 'Off', State: 'Enabled' })); add(pv, inst('switch', { Checked: 'On', State: 'Enabled' }));
      add(pv, inst('switch', { Checked: 'On', State: 'Disabled' }));
    } },
  { name: 'Spinner', ko: '스피너', def: '드래그로 여러 옵션 중 하나를 고릅니다.',
    anatomy: ['Root', 'Selection value', 'Contents'], states: ['—'], props: ['Type: Date / Time'] },
  { name: 'Widgets', ko: '위젯', def: '홈 화면에서 앱의 핵심 정보를 보여 줍니다.',
    anatomy: ['Root', 'Handle (끌어내려 숨김)', 'Pagination', 'Title / Action area', 'Information area'], states: ['—'] },
];
const APP_SPECS = [
  { name: 'Program card', ko: '오늘의 프로그램', built: 'programCard', def: '오늘 1부·2부 카테고리. 색 대신 아이콘과 이름으로 구분하고, 데이터 상태(실시간/예시)를 표시합니다.',
    anatomy: ['1부·2부 칩', '카테고리 아이콘', '제목', '설명', '데이터 상태'], states: ['Status: Live / Sample'],
    preview: pv => { add(pv, inst('programCard', { Status: 'Sample' }, { Part: '2부', Title: '미분양·입주물량', Description: '미분양과 입주 예정 물량', Icon: iconId('cat_unsold') }), 0.68); } },
  { name: 'Category tile', ko: '소식 박스', built: 'categoryTile', def: '편성표에 끌어 놓는 박스. 끄는 동안 그림자 Level 3.',
    anatomy: ['아이콘', '짧은 이름'], states: ['Default', 'Dragging', 'Disabled'],
    preview: pv => {
      add(pv, inst('categoryTile', { State: 'Default' }, { Label: '금리', Icon: iconId('cat_rate') }));
      add(pv, inst('categoryTile', { State: 'Dragging' }, { Label: '교통', Icon: iconId('cat_transport') }));
    } },
  { name: 'Schedule slot', ko: '편성표 칸', built: 'slot', def: '요일 × 1부·2부 칸. 놓을 수 있는 칸은 파랑, 같은 요일 중복은 빨강으로 알려 줍니다.',
    anatomy: ['칸', '소식 박스 또는 +'], states: ['Empty', 'Filled', 'Target', 'Invalid'],
    preview: pv => {
      add(pv, inst('slot', { State: 'Empty' }), 0.55); add(pv, inst('slot', { State: 'Target' }), 0.55); add(pv, inst('slot', { State: 'Invalid' }), 0.55);
    } },
  { name: 'Route stop', ko: '경로 지역', built: 'routeStop', def: '방송 중 화면의 경로 타임라인. 지금 읽는 지역만 크게 보여 줍니다.',
    anatomy: ['레일', '표시 점', '지역 이름'], states: ['Passed', 'Current', 'Upcoming'],
    preview: pv => {
      pv.layoutMode = 'VERTICAL'; pv.counterAxisAlignItems = 'MIN';
      const d = [['Passed', '분당구'], ['Current', '수정구'], ['Upcoming', '강남구']];
      d.forEach((x, i) => add(pv, inst('routeStop', { State: x[0] }, { District: x[1], 'Show line above': i > 0, 'Show line below': i < d.length - 1 })));
    } },
  { name: 'Region chip · Week day', ko: '지역 칩 · 요일', built: 'chip', def: '홈의 경로 지역과 주간 편성 요약.',
    anatomy: ['라벨', '라디오 점(지금)'], states: ['Default', 'Current', 'Passed', 'Today'],
    preview: pv => {
      add(pv, inst('chip', { State: 'Passed' }, { Label: '분당구' })); add(pv, inst('chip', { State: 'Current' }, { Label: '수정구' }));
      add(pv, inst('weekDay', { Today: 'True' }, { Day: '월', Programs: '분양 · 미분양' }), 0.8);
    } },
  { name: 'Segment progress', ko: '방송 진행', built: 'progress', def: '방송 구간 진행. 지금 구간만 Pleos 라디오 색(radio_primary)을 씁니다.',
    anatomy: ['구간'], states: ['Current: 1~5'],
    preview: pv => { const p = inst('progress', { Current: '2' }); pv.appendChild(p); p.layoutSizingHorizontal = 'FILL'; } },
  { name: 'List row', ko: '설정 행', built: 'listRow', def: '설정 목록 한 줄. 라디오, 스위치, 이동 화살표를 붙입니다.',
    anatomy: ['앞 아이콘', '제목', '설명', '뒤 컨트롤'], states: ['Trailing: Radio / Switch / Chevron / None'],
    preview: pv => {
      const r = inst('listRow', { Trailing: 'Switch' }, { Title: '안내가 시작되면 자동으로 듣기', Description: '내비 길 안내를 시작하면 바로 틀어요', 'Leading icon': iconId('play') });
      pv.appendChild(r); r.layoutSizingHorizontal = 'FILL';
    } },
  { name: 'App icon', ko: '앱 아이콘', built: 'appIcon', def: 'Adaptive icon. Background #131417 + Foreground 집과 소리 물결.',
    anatomy: ['Background', 'Foreground'], states: ['—'],
    preview: pv => { if (C.appIcon) { add(pv, C.appIcon.createInstance(), 1.2); add(pv, C.appIcon.createInstance(), 0.8); } } },
];

async function kv(parent, k, v) {
  const r = AL('VERTICAL', { name: k, gap: 6, parent, fillW: true });
  await T(r, k, 'Label/Small/Strong', 'text_dark_tertiary');
  await T(r, v, 'Label/Medium/Normal', 'text_dark_primary', { fillW: true });
}
async function specCard(parent, sp) {
  const built = sp.built && C[sp.built];
  const c = AL('VERTICAL', { name: 'spec · ' + sp.name, gap: 20, pad: 40, fill: 'surface_low', radius: 24, parent, w: 692 });
  const head = AL('HORIZONTAL', { name: 'head', gap: 12, align: 'CENTER', parent: c, fillW: true });
  const tc = AL('VERTICAL', { name: 'title', gap: 2, parent: head, fillW: true });
  await T(tc, sp.name, 'Title/Small/Bold');
  await T(tc, sp.ko, 'Label/Medium/Normal', 'text_dark_tertiary');
  await chipText(head, built ? '컴포넌트 구현' : '문서 정리', !!built);
  await T(c, sp.def, 'Body/Small/Normal', 'text_dark_secondary', { fillW: true });
  if (built && sp.preview) {
    const pv = AL('HORIZONTAL', { name: 'preview', gap: 16, pad: 32, align: 'CENTER', justify: 'CENTER', fill: 'surface_basic', radius: 16, parent: c, fillW: true });
    try { await sp.preview(pv); } catch (e) { warn(`미리보기 실패 ${sp.name}: ${errText(e)}`); }
  }
  await kv(c, 'Anatomy', sp.anatomy.map((a, i) => (i + 1) + '. ' + a).join('   '));
  await kv(c, 'States', sp.states.join(' · '));
  if (sp.props) await kv(c, 'Properties', sp.props.join('\n'));
  if (sp.spec) await kv(c, '적용 수치', sp.spec.join('\n'));
  return c;
}

async function buildBoard05() {
  const page = PAGES.docs;
  await figma.setCurrentPageAsync(page);
  removeTagged(page, t => t === 'board-05');
  const b = await board(page, '05 컴포넌트 명세', 8800, '05 · COMPONENTS', '컴포넌트 명세',
    'Pleos 컴포넌트를 구조(Anatomy), 상태(States), 속성(Properties), 적용 수치로 정리했습니다. "컴포넌트 구현" 표시는 ② 컴포넌트 페이지에 변수와 연결된 실제 컴포넌트가 있다는 뜻이고, 미리보기는 그 컴포넌트의 인스턴스입니다.');
  tag(b, 'board-05');
  const legend = AL('HORIZONTAL', { name: 'legend', gap: 16, align: 'CENTER', parent: b });
  await chipText(legend, '컴포넌트 구현', true);
  await chipText(legend, '문서 정리', false);
  await T(legend, `Pleos 컴포넌트 ${SPECS.length}종 중 ${SPECS.filter(x => x.built && C[x.built]).length}종 구현`, 'Label/Medium/Normal', 'text_dark_secondary');
  let grid = AL('HORIZONTAL', { name: 'pleos components', gap: 24, wrap: true, wrapGap: 24, parent: b, fillW: true });
  for (const sp of SPECS) await specCard(grid, sp);
  await card(b, '시스템 UI', ['Status bar, GNB, App library, Driving view, Gleo AI는 시스템이 그리는 UI라 02 레이아웃 보드에 정리했습니다.', '부읽카 화면의 2560 × 1440 배치 예시는 ③ 부읽카 화면 페이지 맨 아래에 있습니다.'], IW);
  const s = sec(b, 'app components');
  await sectionHead(s, 'BUREADCAR', '부읽카 전용 컴포넌트', 'Pleos 토큰과 컴포넌트 위에 만든 앱 전용 컴포넌트입니다. 색은 모노톤을 기본으로 하고, 강조는 Pleos의 informative(상태)와 radio_primary(방송 중) 토큰만 씁니다.');
  grid = AL('HORIZONTAL', { name: 'bureadcar components', gap: 24, wrap: true, wrapGap: 24, parent: s, fillW: true });
  for (const sp of APP_SPECS) await specCard(grid, sp);
}

// ---------------------------------------------------------------- screens
function screenFrame(name, w, h) {
  const f = figma.createFrame();
  f.name = name;
  applyAL(f, 'VERTICAL', { gap: 0, fill: 'surface_low', radius: 32 });
  size(f, w, h);
  f.clipsContent = true;
  return f;
}
function nav(parent, type, title, subtitle, actions) {
  const props = { Title: title, Subtitle: subtitle || ' ', 'Show subtitle': !!subtitle, 'Show suffix': actions.length > 0 };
  const n = inst('pageNav', { Type: type }, props);
  n.name = 'page navigation';
  parent.appendChild(n);
  n.layoutSizingHorizontal = 'FILL';
  if (n.type === 'INSTANCE') {
    ['action 1', 'action 2'].forEach((nm, i) => {
      const a = n.findOne(x => x.type === 'INSTANCE' && x.name === nm);
      if (!a) return;
      if (actions[i]) setProps(a, { Icon: iconId(actions[i].icon), State: actions[i].state || 'Enabled' });
      else a.visible = false;
    });
  }
  return n;
}
function body(f, o) {
  o = o || {};
  return AL('VERTICAL', { name: 'body', gap: o.gap != null ? o.gap : 32, pad: o.pad || [8, 48, 48, 48], parent: f, fillW: true, fillH: true });
}
function absolute(f, node, x, y) {
  f.appendChild(node);
  node.layoutPositioning = 'ABSOLUTE';
  node.x = Math.round(x); node.y = Math.round(y);
  return node;
}
function toast(f, msg, icon) {
  const t = inst('toast', { Type: icon ? 'Icon' : 'Default' }, { Message: msg });
  if (icon) setProps(t, { Icon: iconId(icon) });
  absolute(f, t, (f.width - t.width) / 2, f.height - t.height - 24);
  t.constraints = { horizontal: 'CENTER', vertical: 'MAX' };
  return t;
}
function relBox(node, frame) {
  const a = node.absoluteBoundingBox, b = frame.absoluteBoundingBox;
  return { x: a.x - b.x, y: a.y - b.y, w: a.width, h: a.height };
}

async function weekStrip(bd, today, portrait) {
  const foot = AL('HORIZONTAL', { name: 'week', gap: portrait ? 8 : 12, align: 'CENTER', parent: bd, fillW: true });
  for (const d of WEEK) {
    const wd = inst('weekDay', { Today: d.day === today ? 'True' : 'False' }, { Day: d.day, Programs: d.slots.map(k => CAT[k].short).join(' · ') });
    foot.appendChild(wd);
    if (portrait) wd.layoutSizingHorizontal = 'FILL';
  }
  if (!portrait) {
    spacer(foot);
    foot.appendChild(inst('textButton', { Size: 'Medium', State: 'Enabled' }, { Label: '시연 주행' }));
  }
  return foot;
}

async function homeScreen(o) {
  const portrait = !!o.portrait;
  const f = screenFrame(o.name, portrait ? 840 : 1700, 1184);
  const day = o.weekend ? '토' : '월';
  const lock = o.driving ? 'Disabled' : 'Enabled';
  nav(f, 'Main', `${day}요일 부동산 라디오`, o.weekend ? '오늘은 쉬는 날이에요' : '내 경로 지역의 소식',
    [{ icon: 'schedule', state: lock }, { icon: 'settings', state: lock }]);
  const bd = body(f, { gap: 32 });
  if (o.weekend) {
    await T(bd, '듣고 싶은 소식을 골라 보세요', 'Title/Small/Bold', 'text_dark_primary');
    const grid = AL('VERTICAL', { name: 'categories', gap: 24, parent: bd, fillW: true, fillH: true });
    const per = portrait ? 2 : 5;
    for (let i = 0; i < CATEGORIES.length; i += per) {
      const r = AL('HORIZONTAL', { name: 'row', gap: 24, parent: grid, fillW: true, fillH: true });
      for (const cat of CATEGORIES.slice(i, i + per)) {
        const t = inst('categoryTile', { State: 'Default' }, { Label: cat.label, Icon: iconId(cat.icon) });
        r.appendChild(t);
        t.layoutSizingHorizontal = 'FILL'; t.layoutSizingVertical = 'FILL';
      }
    }
  } else {
    const cards = AL(portrait ? 'VERTICAL' : 'HORIZONTAL', { name: 'today', gap: 24, parent: bd, fillW: true, fillH: true });
    const today = WEEK[0];
    for (let i = 0; i < 2; i++) {
      const cat = CAT[today.slots[i]];
      const card = inst('programCard', { Status: cat.live ? 'Live' : 'Sample' }, { Part: (i + 1) + '부', Title: cat.label, Description: cat.desc, Icon: iconId(cat.icon) });
      cards.appendChild(card);
      card.layoutSizingHorizontal = 'FILL'; card.layoutSizingVertical = 'FILL';
    }
    const route = AL('HORIZONTAL', { name: 'route', gap: 12, align: 'CENTER', parent: bd, fillW: true, wrap: portrait, wrapGap: 12 });
    await T(route, '오늘 경로', 'Label/Medium/Strong', 'text_dark_tertiary');
    for (const d of ROUTE) route.appendChild(inst('chip', { State: 'Default' }, { Label: d }));
    const btn = inst('button', { Hierarchy: 'Secondary', Size: 'Large', State: 'Enabled' }, { Label: '오늘 방송 듣기', 'Show prefix icon': true, 'Prefix icon': iconId('play') });
    btn.name = 'listen';
    bd.appendChild(btn);
    btn.layoutSizingHorizontal = 'FILL';
  }
  await weekStrip(bd, day, portrait);
  if (o.driving) toast(f, '주행 중에는 편성표와 설정을 열 수 없어요', 'lock');
  return f;
}

async function nowPlayingScreen(o) {
  const portrait = !!o.portrait;
  const f = screenFrame(o.name, portrait ? 840 : 1700, 1184);
  const cat = CAT.subscription;
  nav(f, 'Main', '방송 중', '1부 · ' + cat.label, [{ icon: 'schedule', state: o.driving ? 'Disabled' : 'Enabled' }, { icon: 'close' }]);
  const bd = body(f, { gap: 40 });
  const main = AL(portrait ? 'VERTICAL' : 'HORIZONTAL', { name: 'main', gap: 48, parent: bd, fillW: true, fillH: true });
  const left = AL('VERTICAL', { name: 'now', gap: 32, justify: 'CENTER', parent: main, fillW: true, fillH: !portrait });
  const top = AL('HORIZONTAL', { name: 'category', gap: 24, align: 'CENTER', parent: left });
  const circle = AL('HORIZONTAL', { name: 'icon circle', align: 'CENTER', justify: 'CENTER', fill: 'alpha_dark_50', radius: 'full', parent: top, w: portrait ? 96 : 120, h: portrait ? 96 : 120 });
  circle.appendChild(ICON(cat.icon, portrait ? 56 : 64));
  const tc = AL('VERTICAL', { name: 'title', gap: 4, parent: top });
  await T(tc, '1부', 'Label/Medium/Strong', 'text_dark_tertiary');
  await T(tc, cat.label, portrait ? 'Headline/Small/Bold' : 'Headline/Large/Bold');
  const reg = AL('HORIZONTAL', { name: 'region', gap: 16, align: 'CENTER', parent: left });
  reg.appendChild(dot(16, 'radio_primary', 'on air'));
  await T(reg, `지금 ${ROUTE[ROUTE_NOW]} 소식`, 'Title/Large/Bold');
  if (o.driving) {
    const lockRow = AL('HORIZONTAL', { name: 'driving notice', gap: 12, pad: [16, 24, 16, 24], align: 'CENTER', fill: 'alpha_dark_50', radius: 16, parent: left });
    lockRow.appendChild(ICON('lock', 32, 'icon_dark_secondary'));
    await T(lockRow, '주행 중에는 소리로만 들려드려요', 'Body/Medium/Strong', 'text_dark_secondary');
  } else {
    await T(left, SCRIPT, 'Body/Large/Normal', 'text_dark_secondary', { name: 'current sentence', fillW: true });
  }
  const badge = AL('HORIZONTAL', { name: 'data', gap: 8, align: 'CENTER', parent: left });
  badge.appendChild(dot(12, 'informative_active', 'live'));
  await T(badge, '실시간 데이터', 'Label/Medium/Strong', 'text_dark_secondary');
  if (portrait) {
    const rr = AL('HORIZONTAL', { name: 'route', gap: 12, align: 'CENTER', wrap: true, wrapGap: 12, parent: main, fillW: true });
    ROUTE.forEach((d, i) => rr.appendChild(inst('chip', { State: i < ROUTE_NOW ? 'Passed' : i === ROUTE_NOW ? 'Current' : 'Default' }, { Label: d })));
  } else {
    const rc = AL('VERTICAL', { name: 'route', gap: 16, pad: 40, fill: 'surface_basic', radius: 32, parent: main, w: 520, fillH: true });
    rc.layoutSizingVertical = 'FILL';
    await shadow(rc, 1);
    await T(rc, '내 경로', 'Label/Medium/Strong', 'text_dark_tertiary');
    const list = AL('VERTICAL', { name: 'stops', gap: 0, parent: rc, fillW: true });
    ROUTE.forEach((d, i) => {
      const st = i < ROUTE_NOW ? 'Passed' : i === ROUTE_NOW ? 'Current' : 'Upcoming';
      const r = inst('routeStop', { State: st }, { District: d, 'Show line above': i > 0, 'Show line below': i < ROUTE.length - 1 });
      list.appendChild(r);
      r.layoutSizingHorizontal = 'FILL';
    });
  }
  const pr = inst('progress', { Current: '2' });
  bd.appendChild(pr);
  pr.layoutSizingHorizontal = 'FILL';
  const ctr = AL('HORIZONTAL', { name: 'controls', gap: 48, align: 'CENTER', justify: 'CENTER', parent: bd, fillW: true });
  ctr.appendChild(inst('iconButton', { Style: 'Circle', Size: 'Large', State: 'Enabled' }, { Icon: iconId('replay') }));
  const play = AL('HORIZONTAL', { name: 'pause', align: 'CENTER', justify: 'CENTER', fill: 'button_filled_enabled', radius: 'full', parent: ctr, w: 128, h: 128 });
  play.appendChild(ICON('pause', 64, 'icon_light_primary'));
  ctr.appendChild(inst('iconButton', { Style: 'Circle', Size: 'Large', State: 'Enabled' }, { Icon: iconId('skip_next') }));
  return f;
}

async function scheduleScreen(o) {
  const portrait = !!o.portrait;
  const f = screenFrame(o.name, portrait ? 840 : 1700, 1184);
  nav(f, 'Depth', '이번 주 편성표', '박스를 끌어서 놓으세요. 빼려면 칸 밖으로 끌어내세요', []);
  const bd = body(f, { gap: 24 });
  const grid = AL('VERTICAL', { name: 'grid', gap: 16, parent: bd, fillW: true, fillH: true });
  const LW = portrait ? 64 : 96;
  const head = AL('HORIZONTAL', { name: 'days', gap: 16, align: 'CENTER', parent: grid, fillW: true });
  AL('HORIZONTAL', { name: 'corner', parent: head, w: LW, h: 8 });
  for (const d of WEEK) {
    const on = d.day === '월';
    const cell = AL('HORIZONTAL', { name: d.day, align: 'CENTER', justify: 'CENTER', radius: 'full', fill: on ? 'button_filled_enabled' : undefined, parent: head, fillW: true, h: 64 });
    cell.layoutSizingHorizontal = 'FILL';
    await T(cell, d.day, 'Body/Large/Strong', on ? 'text_light_primary' : 'text_dark_primary');
  }
  let targetSlot = null;
  const dragKey = o.mode === 'dup' ? 'subscription' : 'rate';
  for (let s = 0; s < 2; s++) {
    const row = AL('HORIZONTAL', { name: (s + 1) + '부', gap: 16, align: 'CENTER', parent: grid, fillW: true, fillH: true });
    const lab = AL('HORIZONTAL', { name: 'label', parent: row, w: LW });
    await T(lab, (s + 1) + '부', 'Body/Medium/Strong', 'text_dark_secondary');
    for (let di = 0; di < WEEK.length; di++) {
      let key = WEEK[di].slots[s];
      let state = 'Filled';
      if (o.mode === 'drag' && di === 2 && s === 1) { state = 'Target'; key = null; }
      if (o.mode === 'dup' && di === 0 && s === 1) { state = 'Invalid'; key = null; }
      const slot = inst('slot', { State: state });
      slot.name = WEEK[di].day + ' ' + (s + 1) + '부';
      row.appendChild(slot);
      slot.layoutSizingHorizontal = 'FILL'; slot.layoutSizingVertical = 'FILL';
      if (key) setNested(slot, 'program', { Label: CAT[key].short, Icon: iconId(CAT[key].icon) });
      if (state !== 'Filled') targetSlot = slot;
    }
  }
  await T(bd, '소식 박스', 'Label/Medium/Strong', 'text_dark_tertiary');
  const pal = AL('VERTICAL', { name: 'palette', gap: 16, parent: bd, fillW: true });
  const per = portrait ? 5 : 10;
  for (let i = 0; i < CATEGORIES.length; i += per) {
    const r = AL('HORIZONTAL', { name: 'row', gap: 16, parent: pal, fillW: true, h: portrait ? 104 : 112 });
    r.layoutSizingHorizontal = 'FILL';
    for (const cat of CATEGORIES.slice(i, i + per)) {
      const t = inst('categoryTile', { State: 'Default' }, { Label: cat.short, Icon: iconId(cat.icon) });
      r.appendChild(t);
      t.layoutSizingHorizontal = 'FILL'; t.layoutSizingVertical = 'FILL';
    }
  }
  if (targetSlot) {
    const box = relBox(targetSlot, f);
    const fl = inst('categoryTile', { State: 'Dragging' }, { Label: CAT[dragKey].short, Icon: iconId(CAT[dragKey].icon) });
    const w = Math.round(box.w * 0.86), h = Math.round(box.h * 0.72);
    if (fl.type === 'INSTANCE') fl.resize(w, h);
    absolute(f, fl, box.x + box.w * 0.22, box.y + box.h * 0.2);
    try { fl.rotation = -4; } catch (e) { /* cosmetic */ }
    fl.name = 'dragging';
  }
  if (o.mode === 'dup') toast(f, '같은 요일에 같은 소식은 넣을 수 없어요');
  return f;
}

async function settingsScreen(o) {
  const portrait = !!o.portrait;
  const f = screenFrame(o.name, portrait ? 840 : 1700, 1184);
  nav(f, 'Depth', '설정', null, []);
  const bd = body(f, { gap: 40 });
  const cols = AL(portrait ? 'VERTICAL' : 'HORIZONTAL', { name: 'columns', gap: 48, parent: bd, fillW: true });
  const c1 = AL('VERTICAL', { name: 'where', gap: 16, parent: cols, fillW: true });
  await T(c1, '어디의 소식을 들을까요', 'Body/Medium/Strong', 'text_dark_tertiary');
  const r1 = inst('listRow', { Trailing: 'Radio' }, { Title: '내 경로 지역', Description: '내비 안내 중인 경로의 소식', 'Leading icon': iconId('route') });
  c1.appendChild(r1); r1.layoutSizingHorizontal = 'FILL';
  const r2 = inst('listRow', { Trailing: 'Radio' }, { Title: '저장된 경로', Description: '집과 회사처럼 정해 둔 경로의 소식', 'Leading icon': iconId('bookmark') });
  c1.appendChild(r2); r2.layoutSizingHorizontal = 'FILL';
  setNested(r2, 'control', { Selected: 'On' });
  const dd = inst('dropdown', { Size: 'Medium', State: 'Enabled' }, { Label: '정자동 → 역삼동', 'Prefix icon': iconId('bookmark') });
  c1.appendChild(dd); dd.layoutSizingHorizontal = 'FILL';
  const c2 = AL('VERTICAL', { name: 'when', gap: 16, parent: cols, fillW: true });
  await T(c2, '언제 시작할까요', 'Body/Medium/Strong', 'text_dark_tertiary');
  const r3 = inst('listRow', { Trailing: 'Switch' }, { Title: '안내가 시작되면 자동으로 듣기', Description: '내비 길 안내를 시작하면 바로 틀어요', 'Leading icon': iconId('play') });
  c2.appendChild(r3); r3.layoutSizingHorizontal = 'FILL';
  setNested(r3, 'control', { Checked: 'On' });
  await T(c2, '참고용 정보이며 투자 권유가 아닙니다.', 'Label/Medium/Normal', 'text_dark_tertiary', { fillW: true });
  return f;
}

async function consentScreen(o) {
  const f = await homeScreen({ name: o.name });
  const dim = figma.createRectangle();
  dim.name = 'dim';
  absolute(f, dim, 0, 0);
  dim.resize(f.width, f.height);
  dim.fills = [P('overlay_default')];
  dim.constraints = { horizontal: 'STRETCH', vertical: 'STRETCH' };
  const pop = inst('popup', { Size: 'Medium', Buttons: 'Single' }, {
    Title: '내 경로의 소식을 들으려면',
    Description: '차량 위치로 지금 어느 시군구인지 확인해요. 결과는 저장하지 않고 주행 기록도 남기지 않아요.',
  });
  setNested(pop, 'confirm', { Label: '동의하고 시작' });
  absolute(f, pop, (f.width - pop.width) / 2, (f.height - pop.height) / 2);
  return f;
}

function toDark(root) {
  const nodes = [root].concat(root.findAll(() => true));
  let n = 0;
  for (const node of nodes) {
    for (const key of ['fills', 'strokes']) {
      if (!(key in node)) continue;
      const ps = node[key];
      if (!Array.isArray(ps) || !ps.length) continue;
      let changed = false;
      const out = ps.map(p => {
        const b = p.boundVariables && p.boundVariables.color;
        if (p.type !== 'SOLID' || !b) return p;
        const lv = VBYID.get(b.id);
        if (!lv || lv.variableCollectionId !== LIGHT) return p;
        const dv = VD.get(lv.name);
        if (!dv) return p;
        changed = true;
        return figma.variables.setBoundVariableForPaint(p, 'color', dv);
      });
      if (changed) { try { node[key] = out; n++; } catch (e) { /* locked layer */ } }
    }
  }
  return n;
}

async function displayContext(name, windows) {
  const f = figma.createFrame();
  f.name = name;
  f.resize(2560, 1440);
  f.fills = [P('background_primary')];
  f.clipsContent = true;
  // Status bar
  const sb = AL('HORIZONTAL', { name: 'Status bar', gap: 24, pad: [0, 40, 0, 40], align: 'CENTER' });
  f.appendChild(sb); size(sb, 1720, 88); sb.x = 840; sb.y = 0;
  sb.appendChild(ICON('lock', 36, 'icon_dark_secondary'));
  const search = AL('HORIZONTAL', { name: 'search', gap: 8, pad: [0, 24, 0, 24], align: 'CENTER', fill: 'alpha_dark_50', radius: 'full', parent: sb, h: 56 });
  await T(search, '검색', 'Label/Medium/Normal', 'text_dark_tertiary');
  spacer(sb);
  await T(sb, 'LTE', 'Label/Medium/Strong', 'text_dark_secondary');
  await T(sb, '08:42', 'Body/Large/Strong');
  // Driving view
  const dv = AL('VERTICAL', { name: 'Driving view', gap: 24, pad: 48, align: 'CENTER', justify: 'CENTER', fill: 'surface_basic', radius: 32 });
  f.appendChild(dv); size(dv, 808, 1264); dv.x = 16; dv.y = 16;
  const speed = await T(dv, '62', 'Headline/Large/ExtraBold');
  speed.fontSize = 180; speed.lineHeight = { unit: 'PIXELS', value: 200 };
  await T(dv, 'km/h', 'Title/Medium/Bold', 'text_dark_tertiary');
  const gear = AL('HORIZONTAL', { name: 'gear', gap: 24, parent: dv });
  for (const g of ['P', 'R', 'N', 'D']) await T(gear, g, 'Title/Large/Bold', g === 'D' ? 'text_dark_primary' : 'text_dark_quaternary');
  dv.appendChild(ICON('car', 192, 'icon_dark_secondary'));
  await T(dv, 'Driving view (시스템)', 'Label/Medium/Normal', 'text_dark_tertiary');
  // App windows
  for (const w of windows) {
    const c = w.frame.clone();
    f.appendChild(c);
    c.x = w.x; c.y = 96;
  }
  // GNB
  const gnb = AL('HORIZONTAL', { name: 'GNB', gap: 32, pad: [0, 64, 0, 64], align: 'CENTER', fill: 'surface_basic' });
  f.appendChild(gnb); size(gnb, 2560, 144); gnb.x = 0; gnb.y = 1296;
  await T(gnb, '22.5°', 'Title/Medium/Bold');
  gnb.appendChild(ICON('car', 48, 'icon_dark_secondary'));
  spacer(gnb);
  for (const k of ['route', 'broadcast', 'app', 'add']) {
    const slot = AL('VERTICAL', { name: k === 'app' ? '부읽카' : k, gap: 8, align: 'CENTER', parent: gnb });
    if (k === 'app' && C.appIcon) {
      add(slot, C.appIcon.createInstance(), 1.2);
      slot.appendChild(dot(8, 'radio_primary', 'running'));
    } else {
      const b = AL('HORIZONTAL', { align: 'CENTER', justify: 'CENTER', fill: 'alpha_dark_50', radius: 24, parent: slot, w: 96, h: 96 });
      b.appendChild(ICON(k === 'app' ? 'broadcast' : k, 48));
      slot.appendChild(dot(8, 'alpha_dark_50', 'spacer dot'));
    }
  }
  spacer(gnb);
  gnb.appendChild(ICON('car', 48, 'icon_dark_secondary'));
  await T(gnb, '22.5°', 'Title/Medium/Bold');
  return f;
}
async function navWindow(name) {
  const f = figma.createFrame();
  f.name = name; f.resize(840, 1184); f.fills = [P('surface_high')];
  radius(f, 32); f.clipsContent = true;
  const svg = '<svg width="840" height="1184" viewBox="0 0 840 1184" fill="none" xmlns="http://www.w3.org/2000/svg">'
    + '<path d="M140 1060 C 220 900, 420 880, 460 720 S 380 420, 560 300 S 700 160, 720 120" stroke="#0064FF" stroke-width="16" stroke-linecap="round"/></svg>';
  const r = figma.createNodeFromSvg(svg);
  r.name = 'route line';
  tint(r, 'informative_positive');
  f.appendChild(r); r.x = 0; r.y = 0;
  const card0 = AL('VERTICAL', { name: 'eta', gap: 4, pad: 32, fill: 'surface_basic', radius: 24 });
  f.appendChild(card0); card0.x = 40; card0.y = 40;
  await T(card0, '역삼동까지 32분', 'Title/Medium/Bold');
  await T(card0, '내비게이션 (예시)', 'Label/Medium/Normal', 'text_dark_tertiary');
  return f;
}

async function buildScreens() {
  const page = PAGES.screens;
  await figma.setCurrentPageAsync(page);
  removeTagged(page, () => true);
  const GAP = 160;
  const place = async (frame, x, y, caption) => {
    page.appendChild(frame);
    frame.x = x; frame.y = y;
    tag(frame, 'screen');
    frame.name = caption;
    return frame;
  };
  const header = async (title, desc, y) => {
    const t1 = await T(null, title, 'Headline/Medium/Bold');
    page.appendChild(t1); t1.x = 0; t1.y = y; tag(t1, 'caption');
    if (desc) {
      const t2 = await T(null, desc, 'Body/Medium/Normal', 'text_dark_secondary', { w: 2400 });
      page.appendChild(t2); t2.x = 0; t2.y = y + 88; tag(t2, 'caption');
    }
  };
  const steps = [];
  const run = async (label, fn) => {
    try { return await fn(); } catch (e) { LOG.push(`❌ 화면 ${label}: ${errText(e)}`); console.error(e); return null; }
  };

  // Row 1 · Light landscape
  let y = 0;
  await header('Light · Landscape 1700 × 1184', '앱 1개가 2:0으로 뜰 때의 기본 창. 모노톤을 기본으로 하고, 카테고리는 아이콘과 이름으로 구분합니다. 강조색은 상태(informative)와 방송 중(radio_primary)에만 씁니다.', y - 300);
  const R1 = [
    ['01 홈 · 평일', () => homeScreen({ name: '01 홈 · 평일' })],
    ['02 홈 · 주말', () => homeScreen({ name: '02 홈 · 주말', weekend: true })],
    ['03 방송 중', () => nowPlayingScreen({ name: '03 방송 중' })],
    ['04 방송 중 · 주행 중', () => nowPlayingScreen({ name: '04 방송 중 · 주행 중', driving: true })],
    ['05 편성표 · 끌어 놓기', () => scheduleScreen({ name: '05 편성표 · 끌어 놓기', mode: 'drag' })],
    ['06 편성표 · 중복 안내', () => scheduleScreen({ name: '06 편성표 · 중복 안내', mode: 'dup' })],
    ['07 설정', () => settingsScreen({ name: '07 설정' })],
    ['08 위치 동의 팝업', () => consentScreen({ name: '08 위치 동의 팝업' })],
    ['09 홈 · 주행 중', () => homeScreen({ name: '09 홈 · 주행 중', driving: true })],
  ];
  const made = {};
  let x = 0;
  for (const r of R1) {
    const fr = await run(r[0], r[1]);
    if (fr) { await place(fr, x, y, r[0]); made[r[0]] = fr; steps.push(fr); }
    x += 1700 + GAP;
  }

  // Row 2 · Light portrait
  y += 1184 + 560;
  await header('Light · Portrait 840 × 1184', '앱 2개가 1:1로 뜰 때. 같은 정보 구조를 세로로 쌓습니다.', y - 300);
  const R2 = [
    ['P1 홈', () => homeScreen({ name: 'P1 홈', portrait: true })],
    ['P2 방송 중', () => nowPlayingScreen({ name: 'P2 방송 중', portrait: true })],
    ['P3 편성표', () => scheduleScreen({ name: 'P3 편성표', portrait: true, mode: 'drag' })],
    ['P4 설정', () => settingsScreen({ name: 'P4 설정', portrait: true })],
  ];
  x = 0;
  for (const r of R2) {
    const fr = await run(r[0], r[1]);
    if (fr) { await place(fr, x, y, r[0]); made[r[0]] = fr; steps.push(fr); }
    x += 840 + GAP;
  }

  // Row 3 · Dark
  y += 1184 + 560;
  await header('Dark · Landscape', 'Light 화면을 복제한 뒤 모든 색 변수를 같은 이름의 Pleos · Color Dark 변수로 다시 연결했습니다.', y - 300);
  x = 0;
  for (const key of ['01 홈 · 평일', '03 방송 중', '05 편성표 · 끌어 놓기', '07 설정']) {
    const src = made[key];
    if (!src) continue;
    const d = await run(key + ' Dark', async () => {
      const c = src.clone();
      c.name = key + ' · Dark';
      toDark(c);
      return c;
    });
    if (d) { await place(d, x, y, key + ' · Dark'); steps.push(d); }
    x += 1700 + GAP;
  }

  // Row 4 · Display context
  y += 1184 + 560;
  await header('Connect-S 17 · 2560 × 1440', '상태 표시줄, 드라이빙 뷰, GNB와 함께 본 실제 배치. 앱 창은 x 850, y 96부터 시작합니다.', y - 300);
  x = 0;
  if (made['01 홈 · 평일']) {
    const d1 = await run('디스플레이 가로', () => displayContext('D1 디스플레이 · 부읽카 2:0', [{ frame: made['01 홈 · 평일'], x: 850 }]));
    if (d1) { await place(d1, x, y, 'D1 디스플레이 · 부읽카 단독 (2:0)'); steps.push(d1); }
    x += 2560 + GAP;
  }
  if (made['P2 방송 중']) {
    const d2 = await run('디스플레이 세로', async () => {
      const nw = await navWindow('내비게이션 (예시)');
      const d = await displayContext('D2 디스플레이 · 부읽카 + 내비 1:1', [{ frame: made['P2 방송 중'], x: 850 }, { frame: nw, x: 1710 }]);
      nw.remove();
      return d;
    });
    if (d2) { await place(d2, x, y, 'D2 디스플레이 · 부읽카 + 내비 (1:1)'); steps.push(d2); }
  }
  log(`화면 ${steps.length}개 완성`);
}

// ---------------------------------------------------------------- log + main
function stamp() {
  const d = new Date();
  const p = n => (n < 10 ? '0' : '') + n;
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
async function writeLog(started) {
  const page = PAGES.docs;
  await figma.setCurrentPageAsync(page);
  removeTagged(page, t => t === 'log');
  const f = AL('VERTICAL', { name: '빌드 로그 (지워도 됨)', gap: 8, pad: 48, fill: 'surface_high', radius: 24 });
  page.appendChild(f);
  size(f, 1600, null);
  f.x = 0; f.y = -1600;
  tag(f, 'log');
  await T(f, `부읽카 디자인 빌더 · ${stamp()} · ${Math.round((Date.now() - started) / 1000)}초 · 명령 ${CMD}`, 'Body/Large/Strong');
  const lines = LOG.slice(0, 150);
  for (const l of lines) {
    const color = l.indexOf('❌') === 0 ? 'informative_negative' : l.indexOf('⚠️') === 0 ? 'text_dark_secondary' : 'text_dark_primary';
    await T(f, l, 'Label/Medium/Normal', color, { w: 1504 });
  }
  if (LOG.length > lines.length) await T(f, `… 외 ${LOG.length - lines.length}줄`, 'Label/Medium/Normal', 'text_dark_tertiary');
}

async function step(name, fn) {
  const t = Date.now();
  try { await fn(); log(`✅ ${name} (${((Date.now() - t) / 1000).toFixed(1)}초)`); }
  catch (e) { LOG.push(`❌ ${name}: ${errText(e)}`); console.error(e); }
}

async function main() {
  const started = Date.now();
  figma.notify('부읽카 디자인을 만드는 중이에요. 1~2분 걸릴 수 있어요.', { timeout: 6000 });
  await setup();
  if (CMD === 'all') await step('컴포넌트', buildComponents);
  else await step('기존 컴포넌트 찾기', findComponents);
  if (CMD === 'all' || CMD === 'docs') {
    await step('04 보드', buildBoard04);
    await step('05 보드', buildBoard05);
  }
  if (CMD === 'all' || CMD === 'screens') await step('화면', buildScreens);
  await step('빌드 로그', () => writeLog(started));
  const target = CMD === 'docs' ? PAGES.docs : PAGES.screens;
  await figma.setCurrentPageAsync(target);
  const shown = target.children.filter(n => {
    const t = n.getPluginData(NS);
    return t === 'screen' || t === 'board-04' || t === 'board-05';
  });
  if (shown.length) figma.viewport.scrollAndZoomIntoView(shown);
  const bad = LOG.filter(l => l.indexOf('❌') === 0).length;
  const warns = LOG.filter(l => l.indexOf('⚠️') === 0).length;
  figma.closePlugin(bad || warns ? `완료 · 오류 ${bad}개, 경고 ${warns}개 (① 페이지 위쪽 빌드 로그 확인)` : '부읽카 디자인 완성!');
}

main().catch(e => {
  console.error(e);
  figma.closePlugin('실패: ' + errText(e));
});
