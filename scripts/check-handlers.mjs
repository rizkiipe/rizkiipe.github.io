#!/usr/bin/env node
/**
 * Pengecekan statis www/index.html (tanpa dependensi, hanya Node):
 *  1. Sintaks semua <script> inline (node --check)
 *  2. Handler tombol: setiap fungsi yang dipanggil lewat onclick/onchange/oninput/... atau lewat string
 *     seperti pmBt('Label', 'namaFungsi()') HARUS terdaftar di Object.assign(window, {...}),
 *     karena script utama berjenis module (fungsi di dalamnya tidak otomatis global).
 *     Kalau tidak terdaftar, tombolnya "mati" (ReferenceError) dan baru ketahuan di HP.
 *
 * Pakai:  node scripts/check-handlers.mjs [www/index.html]      (exit 1 bila ada masalah)
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const file = process.argv[2] || 'www/index.html';
// Data riwayat perubahan (disalin dari README) hanya teks, bukan kode: jangan ikut dipindai
const html = fs.readFileSync(file, 'utf8').replace(/\/\*CHANGELOG_START\*\/[\s\S]*?\/\*CHANGELOG_END\*\//g, '[]');
const problems = [];

// ---------- kumpulkan <script> inline ----------
const scripts = [...html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
  .filter(m => !/\bsrc\s*=/.test(m[1]))
  .map((m, i) => ({ i, module: /type\s*=\s*["']module["']/.test(m[1]), code: m[2] }))
  .filter(s => s.code.trim());

// ---------- 1) sintaks ----------
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'chk-'));
for (const s of scripts) {
  const f = path.join(tmp, `s${s.i}.${s.module ? 'mjs' : 'js'}`);
  fs.writeFileSync(f, s.code);
  const r = spawnSync(process.execPath, ['--check', f], { encoding: 'utf8' });
  if (r.status !== 0) problems.push(`Sintaks error pada <script> #${s.i + 1}${s.module ? ' (module)' : ''}:\n${(r.stderr || '').split('\n').slice(0, 6).join('\n')}`);
}
fs.rmSync(tmp, { recursive: true, force: true });

// ---------- 2) handler ----------
const moduleCode = scripts.filter(s => s.module).map(s => s.code).join('\n');
const classicCode = scripts.filter(s => !s.module).map(s => s.code).join('\n');
const ident = '[A-Za-z_$][\\w$]*';

// yang terdaftar di window
const exported = new Set();
for (const m of html.matchAll(/Object\.assign\(\s*window\s*,\s*\{/g)) {
  let depth = 1, i = m.index + m[0].length;
  const start = i;
  while (i < html.length && depth > 0) { const c = html[i++]; if (c === '{') depth++; else if (c === '}') depth--; }
  html.slice(start, i - 1).split(',').forEach(chunk => { const t = new RegExp('^\\s*(' + ident + ')').exec(chunk.replace(/\/\/.*$/gm, '')); if (t) exported.add(t[1]); });
}
for (const m of html.matchAll(new RegExp('window\\.(' + ident + ')\\s*=', 'g'))) exported.add(m[1]);
// global dari script biasa (non-module)
for (const m of classicCode.matchAll(new RegExp('(?:^|\\n)\\s*(?:async\\s+)?(?:function|var|let|const)\\s+(' + ident + ')', 'g'))) exported.add(m[1]);

const builtin = new Set(['if', 'for', 'while', 'switch', 'return', 'function', 'event', 'this', 'confirm', 'alert', 'prompt', 'setTimeout', 'setInterval',
  'parseInt', 'parseFloat', 'Number', 'String', 'Boolean', 'isNaN', 'encodeURIComponent', 'decodeURIComponent', 'document', 'window', 'console',
  'Math', 'JSON', 'Date', 'Array', 'Object', 'location', 'history', 'navigator', 'localStorage', 'stopPropagation', 'preventDefault', 'click', 'remove', 'focus']);

// fungsi yang didefinisikan di dalam module
const defs = new Set();
for (const m of moduleCode.matchAll(new RegExp('(?:async\\s+)?function\\s+(' + ident + ')\\s*\\(', 'g'))) defs.add(m[1]);
for (const m of moduleCode.matchAll(new RegExp('(?:const|let|var)\\s+(' + ident + ')\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|' + ident + ')\\s*=>', 'g'))) defs.add(m[1]);

const missing = new Map();   // nama -> asal
const note = (n, why) => { if (!exported.has(n) && !builtin.has(n) && !missing.has(n)) missing.set(n, why); };

// (a) atribut on*="fn(...)" di HTML maupun di dalam string/template JS (\"...\")
for (const re of [/\bon[a-z]+=\\?"([^"\\]*)/g, /\bon[a-z]+=\\?'([^'\\]*)/g]) {
  for (const m of html.matchAll(re)) {
    for (const f of m[1].matchAll(new RegExp('(?<![\\w.$])(' + ident + ')\\s*\\(', 'g'))) note(f[1], 'dipanggil dari atribut on*=');
  }
}
// (b) nama fungsi module yang dilewatkan sebagai string, mis. pmBt('Label', 'pmFungsi()')
for (const m of moduleCode.matchAll(new RegExp('[\'"`]\\s*(' + ident + ')\\(', 'g'))) { if (defs.has(m[1])) note(m[1], 'dilewatkan sebagai string handler'); }

for (const [n, why] of missing) problems.push(`Handler "${n}" (${why}) belum didaftarkan di Object.assign(window, {...}) -> tombolnya tidak akan jalan.`);

// ---------- hasil ----------
console.log(`${file}: ${scripts.length} script, ${exported.size} nama global/terdaftar, ${defs.size} fungsi module diperiksa.`);
if (problems.length) {
  console.error('\nGAGAL:\n- ' + problems.join('\n- '));
  console.error('\nPerbaikan: tambahkan nama fungsi ke daftar Object.assign(window, {...}) di akhir script module www/index.html.');
  process.exit(1);
}
console.log('OK: sintaks dan semua handler tombol terdaftar.');
