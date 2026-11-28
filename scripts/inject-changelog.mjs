#!/usr/bin/env node
/**
 * Menyalin riwayat perubahan dari README.md ke www/index.html (halaman "Tentang" di Pengaturan).
 * Setiap judul "## ... x.y.z ..." di README (mis. "## Perubahan 1.4.16") menjadi satu entri.
 * Aman dijalankan berulang (idempoten): hanya mengganti isi di antara penanda CHANGELOG_START/END.
 *
 * Pakai:  node scripts/inject-changelog.mjs [www/index.html] [README.md]
 */
import fs from 'node:fs';

const htmlFile = process.argv[2] || 'www/index.html';
const readmeFile = process.argv[3] || 'README.md';
const readme = fs.readFileSync(readmeFile, 'utf8').replace(/\r\n/g, '\n');

// pecah README per "## judul"
const parts = readme.split(/^## +/m).slice(1);
const entries = [];
for (const p of parts) {
  const nl = p.indexOf('\n');
  const title = (nl < 0 ? p : p.slice(0, nl)).trim();
  const ver = /(\d+\.\d+(?:\.\d+)?)/.exec(title);
  if (!ver) continue;                                   // bagian tanpa nomor versi bukan riwayat
  const body = (nl < 0 ? '' : p.slice(nl + 1)).trim();
  if (!body) continue;
  entries.push({ v: ver[1], t: title, b: body });
}
const cmp = (a, b) => { const x = a.split('.').map(Number), y = b.split('.').map(Number); for (let i = 0; i < 3; i++) { const d = (y[i] || 0) - (x[i] || 0); if (d) return d; } return 0; };
entries.sort((a, b) => cmp(a.v, b.v));                  // terbaru di atas (urutan README dipertahankan bila versi sama)

// '<' di-escape supaya aman di dalam <script>
const json = JSON.stringify(entries).replace(/</g, '\\u003c');
const html = fs.readFileSync(htmlFile, 'utf8');
const re = /\/\*CHANGELOG_START\*\/[\s\S]*?\/\*CHANGELOG_END\*\//;
if (!re.test(html)) { console.error(`GAGAL: penanda CHANGELOG_START/END tidak ada di ${htmlFile}`); process.exit(1); }
fs.writeFileSync(htmlFile, html.replace(re, () => `/*CHANGELOG_START*/${json}/*CHANGELOG_END*/`));
console.log(`OK: ${entries.length} entri riwayat (${entries[0] ? 'terbaru v' + entries[0].v : 'kosong'}) disalin ke ${htmlFile}`);
