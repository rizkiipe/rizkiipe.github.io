/**
 * Tes logika murni dari www/index.html (tanpa browser, tanpa dependensi): node --test tests/logic.test.mjs
 * Fungsi diambil langsung dari kode aplikasi (bukan salinan), jadi tes ini ikut gagal bila logikanya berubah.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const FILE = process.env.APP_HTML || 'www/index.html';
const html = fs.readFileSync(FILE, 'utf8');
const moduleSrc = [...html.matchAll(/<script type="module">([\s\S]*?)<\/script>/g)].map(m => m[1]).join('\n');

// ---------- pengambil potongan kode (function / const) ----------
function skipString(s, i, q) { for (i++; i < s.length; i++) { if (s[i] === '\\') i++; else if (s[i] === q) return i; } return i; }
function skipRegex(s, i) { let cls = false; for (i++; i < s.length; i++) { const c = s[i]; if (c === '\\') i++; else if (c === '[') cls = true; else if (c === ']') cls = false; else if (c === '/' && !cls) return i; } return i; }
// cari indeks penutup: mode 'brace' = '}' penutup dari '{' pertama; mode 'stmt' = ';' pada kedalaman 0
function scanEnd(s, from, mode) {
  let depth = 0, prev = '', started = mode === 'stmt';
  for (let i = from; i < s.length; i++) {
    const c = s[i];
    if (c === '"' || c === "'" || c === '`') { i = skipString(s, i, c); prev = c; continue; }
    if (c === '/' && s[i + 1] === '/') { while (i < s.length && s[i] !== '\n') i++; continue; }
    if (c === '/' && s[i + 1] === '*') { i = s.indexOf('*/', i + 2) + 1; continue; }
    if (c === '/' && (prev === '' || '(,=:[!&|?{};'.includes(prev))) { i = skipRegex(s, i); prev = '/'; continue; }
    if (c === '(' || c === '[' || c === '{') { depth++; started = true; }
    else if (c === ')' || c === ']' || c === '}') { depth--; if (mode === 'brace' && started && depth === 0 && c === '}') return i + 1; }
    else if (c === ';' && mode === 'stmt' && depth === 0) return i + 1;
    if (!/\s/.test(c)) prev = c;
  }
  throw new Error('akhir potongan tidak ditemukan');
}
function extract(name) {
  const fn = new RegExp('(?:^|\\n)((?:async\\s+)?function\\s+' + name + '\\s*\\()').exec(moduleSrc);
  if (fn) { const st = fn.index + fn[0].indexOf(fn[1]); return moduleSrc.slice(st, scanEnd(moduleSrc, st, 'brace')); }
  const cn = new RegExp('(?:^|\\n)((?:const|let|var)\\s+' + name + '\\s*=)').exec(moduleSrc);
  if (cn) { const st = cn.index + cn[0].indexOf(cn[1]); return moduleSrc.slice(st, scanEnd(moduleSrc, st, 'stmt')); }
  throw new Error('tidak ketemu di index.html: ' + name);
}

const ctx = vm.createContext({ console, Date, Math, JSON, Number, String, Object, Array, parseInt, parseFloat, isNaN, Set });
const load = names => names.forEach(n => vm.runInContext(extract(n), ctx, { filename: n }));
const run = code => vm.runInContext(code, ctx);

load(['pmWeek', 'planWeekNo', 'pmBatchTotal', 'pmNorm', 'pmBase', 'pmCode', 'pmN', 'pmCookMapOf', 'histNorm', 'BOX_MON',
  'boxExpireKey', 'boxToStored', 'reportBoxCode', 'boxParse', 'pmKeyOf', 'pmCookedBatches', 'pmPlanCalc']);
// stub data aplikasi yang dibaca fungsi-fungsi di atas
run(`var PM = { d: { tgl: '2026-11-25', shift: 'Shift 1', grp: 'A', wk: '' }, procs: [], plan: [] };
     var PMLOG = {}; var pmWC = { wk: 0, at: 0, files: {} };
     var pmProcView = p => ({ items: p.items || [] }); var pmWk = () => pmWeek(PM.d.tgl);`);

// ================= Week (Minggu - Sabtu) =================
test('pmWeek: Week 48 2026 = Minggu 22 Nov s/d Sabtu 28 Nov', () => {
  for (const d of ['2026-11-22', '2026-11-23', '2026-11-25', '2026-11-28']) assert.equal(run(`pmWeek('${d}')`), 48, d);
  assert.equal(run(`pmWeek('2026-11-29')`), 49);   // Minggu = awal week berikutnya
  assert.equal(run(`pmWeek('2026-11-21')`), 47);   // Sabtu sebelumnya
});
test('pmWeek: W44 = 25-31 Okt 2026', () => {
  assert.equal(run(`pmWeek('2026-10-25')`), 44);
  assert.equal(run(`pmWeek('2026-10-31')`), 44);
  assert.equal(run(`pmWeek('2026-11-01')`), 45);
});
test('pmWeek: pergantian tahun (2026 punya 53 week)', () => {
  assert.equal(run(`pmWeek('2026-12-27')`), 53);
  assert.equal(run(`pmWeek('2027-01-02')`), 53);
  assert.equal(run(`pmWeek('2027-01-03')`), 1);
  assert.equal(run(`pmWeek('2026-01-03')`), 1);
  assert.equal(run(`pmWeek('2026-01-04')`), 2);
});
test('pmWeek: tiap Minggu s/d Sabtu satu week yang sama, dan week naik 1 tiap Minggu (2026-2028)', () => {
  const iso = d => d.toISOString().slice(0, 10);
  let prevWeek = null;
  for (let t = Date.UTC(2026, 0, 4); t < Date.UTC(2029, 0, 1); t += 7 * 864e5) {   // 4 Jan 2026 = Minggu
    const sun = new Date(t), weeks = new Set();
    for (let k = 0; k < 7; k++) weeks.add(run(`pmWeek('${iso(new Date(t + k * 864e5))}')`));
    assert.equal(weeks.size, 1, `week tidak seragam mulai Minggu ${iso(sun)}`);
    const w = [...weeks][0];
    if (prevWeek !== null) assert.ok(w === prevWeek + 1 || w === 1, `lompatan week di ${iso(sun)}: ${prevWeek} -> ${w}`);
    prevWeek = w;
  }
});
test('planWeekNo: nama tab sheet Planning', () => {
  assert.equal(run(`planWeekNo('W48')`), 48);
  assert.equal(run(`planWeekNo('Week 5')`), 5);
  assert.equal(run(`planWeekNo('W04')`), 4);
  assert.equal(run(`planWeekNo('Rekap')`), 0);
});

// ================= Planning & batch =================
test('pmBatchTotal: angka setelah garis miring', () => {
  assert.equal(run(`pmBatchTotal('0 / 9')`), '9');
  assert.equal(run(`pmBatchTotal('3/12,5')`), '12,5');
  assert.equal(run(`pmBatchTotal('')`), '');
  assert.equal(run(`pmBatchTotal('0 / 0')`), '');
});
test('kode produk: angka ukuran diabaikan', () => {
  assert.equal(run(`pmCode('CHM 250')`), 'CHM');
  assert.equal(run(`pmCode('PLM 1000 (H)')`), 'PLM');
  assert.equal(run(`pmCode('BLCS')`), 'BLCS');
});
test('pmCookMapOf: hanya aktivitas Masak, batch kosong dihitung 1', () => {
  const m = run(`pmCookMapOf([{t:'m',prod:'CHM 200',b:'2'},{t:'m',prod:'chm',b:''},{t:'c'},{t:'a',txt:'Cip Homo'},{t:'m',prod:'PLM',b:'1'}])`);
  assert.deepEqual({ ...m }, { CHM: 3, PLM: 1 });
});
test('pmPlanCalc: batch dimasak mengurangi baris planning berkode sama, berurutan; di luar week diabaikan', () => {
  run(`var PLAN = [{p:'CHM 200',b:'5',c:'CHM'},{p:'CHM 1000',b:'3',c:'CHM'},{p:'PLM 200',b:'2',c:'PLM'},{p:'TRS',b:'4',c:'TRS'}];
       var FILES = {
         a: { key: '2026-11-24|Shift 1|A', map: { CHM: 6, PLM: 1 } },      // week 48 -> dihitung
         b: { key: '2026-10-27|Shift 1|A', map: { CHM: 99, TRS: 99 } }     // week 44 -> TIDAK dihitung
       };
       var R = pmPlanCalc(PLAN, 48, FILES, false);`);
  assert.equal(run('R[0].cooked'), 5); assert.equal(run('R[0].sisa'), 0);   // CHM 200 habis
  assert.equal(run('R[1].cooked'), 1); assert.equal(run('R[1].sisa'), 2);   // sisa CHM masuk CHM 1000
  assert.equal(run('R[2].cooked'), 1); assert.equal(run('R[2].sisa'), 1);
  assert.equal(run('R[3].cooked'), 0); assert.equal(run('R[3].sisa'), 4);   // TRS: file W44 tidak ikut
});
test('pmPlanCalc: form Entry dihitung hanya bila formIn = true', () => {
  run(`PM.procs = [{ items: [{t:'m',prod:'CHM',b:'2'}] }];
       var P2 = [{p:'CHM 200',b:'5',c:'CHM'}];
       var WITH = pmPlanCalc(P2, 48, {}, true)[0].cooked, WITHOUT = pmPlanCalc(P2, 48, {}, false)[0].cooked;
       PM.procs = [];`);
  assert.equal(run('WITH'), 2); assert.equal(run('WITHOUT'), 0);
});

// ================= Kode box =================
test('boxParse: lot, Mc, PB (nol di depan dibuang), expire', () => {
  const p = run(`boxParse('23 Juni 2027 L-270826-52B NPH- PB-01664')`);
  assert.equal(p.lot, '270826-52B'); assert.equal(p.mc, '52'); assert.equal(p.pb, '1664'); assert.equal(p.exp, '23-jun-2027');
});
test('boxParse: penulisan dengan titik dan tanpa titik dianggap sama', () => {
  const a = run(`boxParse('20 May 2027 L-24.07.26-Mc 52-A NPH- PB 01417')`), b = run(`boxParse('20 May 2027 L-240726-52A NPH- PB 01417')`);
  const pick = ({ lot, lotDate, mc, pb, exp }) => ({ lot, lotDate, mc, pb, exp });   // 'raw' (teks asli) memang berbeda
  assert.deepEqual(pick(a), pick(b));
});
test('boxExpireKey: nama bulan Indonesia/Inggris disamakan', () => {
  for (const [a, b] of [['20 Mei 2027', '20 May 2027'], ['5 Agu 2027', '5 Aug 2027'], ['1 Okt 2027', '1 Oct 2027'], ['9 Des 2027', '9 December 2027']])
    assert.equal(run(`boxExpireKey('${a}')`), run(`boxExpireKey('${b}')`), `${a} vs ${b}`);
});
test('boxParse: format tidak standar -> lot kosong; kode kosong -> null', () => {
  assert.equal(run(`boxParse('ABC 123').lot`), '');
  assert.equal(run(`boxParse('   ')`), null);
});
