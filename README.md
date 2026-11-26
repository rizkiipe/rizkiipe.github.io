# U-Track Android (Capacitor + GitHub Actions + Firebase Hosting)

Web app dibundel di dalam APK. Situs **https://laporan-de-uht-62ebd.web.app** ikut mengikuti isi APK: setiap rilis, workflow men-deploy folder `www/` yang sama persis ke Firebase Hosting. Satu rilis = satu versi untuk APK dan situs.

## Isi paket
- `www/` : aplikasi (index.html, oauth-callback.html, ikon, sw.js). Dibundel ke APK dan di-deploy ke Firebase.
- `firebase.json`, `.firebaserc` : Firebase Hosting (hanya folder `www`, URL `/index.html` tetap sama).
- `capacitor.config.json`, `package.json` : konfigurasi Capacitor 8.
- `android-overlay/` + `scripts/patch-android.py` : plugin update APK, ikon, splash, izin kamera, deep link login.
- `.github/workflows/build-apk.yml` : build, tanda tangan, rilis APK, deploy situs.
- `tools/buat-hash.html` : pembuat hash password / daftar email login.

GitHub Pages TIDAK dipakai (tidak perlu diaktifkan).

## Setup (sekali saja)

1. Buat repo GitHub (public), upload isi paket ini ke branch `main`. Kalau `.github` gagal ter-upload, pakai paket UPLOAD-MUDAH dan rename `dot-github` jadi `.github` (lihat BACA-DULU.txt).
2. **Google Cloud Console → Credentials → OAuth 2.0 Client ID (Web) U-Track**:
   - **Authorized JavaScript origins**: `https://laporan-de-uht-62ebd.web.app` (kemungkinan sudah ada)
   - **Authorized redirect URIs**: `https://laporan-de-uht-62ebd.web.app/oauth-callback.html`  <- penting untuk login Google di APK
3. **Akun layanan Firebase untuk deploy otomatis**:
   - Firebase Console -> Project settings -> Service accounts -> **Generate new private key** (mengunduh file .json).
   - Google Cloud Console -> IAM -> cari akun `firebase-adminsdk-...@laporan-de-uht-62ebd.iam.gserviceaccount.com` -> Edit -> tambahkan peran **Firebase Hosting Admin**.
   - GitHub repo -> Settings -> Secrets and variables -> Actions -> New secret: nama `FIREBASE_SERVICE_ACCOUNT`, isi = seluruh isi file .json tadi. Jangan pernah meng-commit file .json itu.
4. **Actions -> Build & Release APK -> Run workflow**, isi versi `1.0.0`. Run pertama membuat keystore baru dan belum merilis/deploy. Dari run itu: download artifact **keystore-SIMPAN**, lalu buat secret `KEYSTORE_BASE64` (isi KEYSTORE_BASE64.txt) dan `KEYSTORE_PASSWORD` (isi KEYSTORE_PASSWORD.txt).
5. **Run workflow lagi** (versi `1.0.0`). APK terbit di tab **Releases** dan situs Firebase ter-update. Install `U-Track.apk` dari Releases (bukan dari run pertama).

PERHATIAN: deploy mengganti seluruh isi situs Firebase dengan isi `www/`. File lain yang dulu ada di situs itu akan hilang.

## Rilis update
1. Commit perubahan di `www/index.html` seperti biasa.
2. **Actions -> Build & Release APK -> Run workflow** -> isi versi baru (mis. `1.0.1`) dan catatan update.
3. Workflow: mengisi versi, build dan menandatangani APK, merilis di GitHub, men-deploy situs Firebase, lalu menyimpan nomor versi ke repo.
4. Di HP: buka U-Track -> lembar "Update tersedia" -> **Update** -> installer Android -> pasang. Data tetap.
Naikkan nomor versi setiap rilis (1.0.1, 1.0.2, 1.1.0, ...). Nomor harus selalu lebih besar dari sebelumnya.

## Fitur 1.1
- **Batasan akun Google**: daftar `AUTH_GOOGLE_ALLOWED` di `www/index.html` berisi hash email yang boleh masuk lewat tombol Google (sudah diisi 2 email pemilik). Tambah email lain lewat `tools/buat-hash.html` bagian bawah. Login username/password tetap sebagai cadangan.
- **Draf otomatis**: isian Entry disimpan di HP tiap beberapa detik (tanpa foto) dan dipulihkan bila app tertutup (maks. 14 jam). Dihapus setelah berhasil tersimpan di Drive.
- **Antrean offline**: bila Save gagal karena tidak ada koneksi, laporan ditandai "Tertunda" dan dikirim otomatis saat online/app dibuka lagi (maks. 3 percobaan).
- **Sidik jari / wajah** (Pengaturan, ketuk ikon pengguna di pojok atas): dua sakelar terpisah, keduanya mati secara bawaan. **Kunci app** meminta sidik jari/wajah saat app dibuka dan setelah ditinggal 5 menit; **Timpa laporan** memakai sidik jari/wajah (tanpa password) saat laporan yang sama sudah ada.
- **Pengingat shift**: notifikasi harian per shift di Pengaturan; jam bisa diubah (bawaan 13:30, 21:30, 05:30).

## Fitur 1.2

**Login Google tanpa tab Chrome**
App memakai dialog login Google bawaan Android; token diperbarui diam-diam. Syarat satu kali di Google Cloud Console (proyek yang sama dengan Web client):
1. Credentials -> Create credentials -> OAuth client ID -> **Android**.
2. Package name: `com.utrack.app`. SHA-1: ambil dari **Summary** run workflow (bagian "SHA-1 sertifikat"). Simpan.
3. Selesai, kode tidak perlu diubah. Kalau Android client belum ada, app otomatis memakai tab Chrome seperti sebelumnya.
Catatan: app mengikuti satu akun Google per HP. Untuk ganti akun, cabut akses U-Track di pengaturan akun Google HP (Keamanan -> Akses pihak ketiga), lalu login lagi.

**Pencatat error**
Error JavaScript dan crash aplikasi dicatat di HP. Buka Pengaturan (ikon pengguna) -> **Log error** -> **Bagikan log** untuk mengirim ke developer.

**Firebase App Distribution** (opsional, memudahkan rekan menginstal)
1. Firebase Console -> Project settings -> Your apps -> Add app -> Android, package `com.utrack.app`. Salin **App ID** (bentuk `1:123...:android:abc...`).
2. Firebase Console -> App Distribution -> Get started -> Testers & Groups -> buat grup dengan alias `tim` dan tambahkan email rekan.
3. Google Cloud Console -> IAM -> akun `firebase-adminsdk-...` -> tambahkan peran **Firebase App Distribution Admin**.
4. GitHub: Settings -> Secrets and variables -> Actions -> secret `FIREBASE_APP_ID` berisi App ID tadi. (Opsional: variable `FIREBASE_DIST_GROUPS` bila alias grup bukan `tim`.)
Setiap rilis, APK otomatis dikirim ke grup itu. Langkah ini dilewati bila secret `FIREBASE_APP_ID` kosong.

**Dependabot + cek build**
`.github/dependabot.yml` membuat pull request mingguan untuk Capacitor/plugin dan versi GitHub Actions. `.github/workflows/build-check.yml` menjalankan build percobaan di tiap pull request sehingga pembaruan yang merusak ketahuan sebelum digabung. Menggabungkan pull request tidak merilis apa pun; rilis tetap lewat workflow Build & Release.

## Perubahan 1.2.1
- App langsung terbuka tanpa login selama sudah pernah masuk (login tersimpan di HP). Kunci sidik jari hanya aktif bila dinyalakan sendiri di Pengaturan.
- Mengganti **Group** otomatis mengganti operator mengikuti jadwal group baru (tanggal yang sama).
- Lingkaran "AD" diganti ikon pengguna.
- Pengaturan: sakelar baru untuk menimpa laporan dengan sidik jari / wajah saja.

## Perubahan 1.2.2
- Semua kolom tanggal tampil **dd/mm/yyyy** (mis. 24/11/2026) di semua perangkat; isi data tetap yyyy-mm-dd sehingga file Drive tidak berubah.

## Fitur 1.3
- **Jejak audit**: tiap laporan mencatat siapa yang menyimpan (email Google / nama login). Riwayat Laporan menampilkan "oleh ..." dari data Google Drive, dan peringatan sebelum menimpa laporan yang sudah ada menyebut siapa dan kapan terakhir menyimpan.
- **Kirim ke WhatsApp**: tombol langsung di teks laporan, gambar laporan, dan gambar efisiensi (membuka pilihan kontak/grup WhatsApp; gambar ikut terlampir).
- **Foto di draf**: foto DE ikut tersimpan di draf otomatis (foto sudah dikompres otomatis ke 900 px saat dipilih/diambil).
- **Kunci versi paket**: rilis berikutnya membuat `package-lock.json` otomatis dan menyimpannya di repo; build selanjutnya memakai `npm ci` sehingga versi paket selalu sama persis.

## Fitur 1.4
- **Ikon notifikasi**: ikon kecil di bilah status berupa huruf U putih (Android hanya memakai bentuk), ikon besar di dalam notifikasi memakai ikon aplikasi berwarna. Notifikasi lama otomatis dijadwalkan ulang dengan ikon baru saat app dibuka.
- **Antrean offline halaman lain**: selain Filling-DE, simpan **Proses** dan **Rework Tracker** yang gagal karena tidak ada koneksi ditandai "Tertunda" dan dikirim otomatis saat online (maks. 3 percobaan). Sterilizer, Proses, dan Planning sudah menyimpan isian di HP setiap berubah; Rework Tracker kini ikut punya draf (dipulihkan maks. 24 jam, hanya bila ada perubahan yang belum masuk Drive).
- **Pengingat pintar**: notifikasi dijadwalkan untuk 4 hari ke depan dan hanya untuk shift yang laporannya belum tersimpan. Setelah laporan Filling-DE shift itu berhasil tersimpan (dalam 9 jam sebelum jam pengingat), pengingatnya dibatalkan. Jadwal diperbarui setiap app dibuka atau setelah menyimpan laporan.

## Fitur 1.4.3
- **Pintasan ikon app**: tekan lama ikon U-Track untuk langsung ke Filling-DE, Sterilizer, Proses, atau Planning.
- **Pemeriksa akses Drive** (ikon pengguna -> Pengaturan -> Cek akses Drive): menampilkan pemilik dan siapa saja yang punya akses ke folder laporan, Rework Tracker, dan sheet yang dipakai app, dengan peringatan bila ada yang terbuka untuk umum ("siapa saja dengan link").

## Keamanan (1.4.3)
Kode aplikasi dan situs bersifat publik, jadi tidak ada rahasia yang boleh tertulis di dalamnya.
- **Password riwayat** tidak lagi tertulis; hanya hash. Password lama tetap berlaku. Ganti lewat `tools/buat-hash.html` (bagian bawah).
- **Token skrip DSP** dihapus dari kode. Isi sekali per perangkat di Pengaturan (ikon pengguna -> Token skrip DSP). Token itu sebelumnya tertulis di kode publik, jadi **sebaiknya diganti** dengan nilai baru di Apps Script (dan di Pengaturan tiap perangkat).
- ID Google Sheet/Drive di kode bukan rahasia selama file-nya tidak dibuka untuk umum (app membacanya memakai login Google, bukan tautan umum). Pakai Cek akses Drive untuk memastikan.
- Untuk menggantikan token bersama dengan verifikasi akun, app juga mengirim token login Google (`accessToken`) ke skrip DSP. Contoh pengecekan di `doPost` Apps Script:

```js
function whoIs(accessToken){
  var r = UrlFetchApp.fetch('https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)',
    { headers: { Authorization: 'Bearer ' + accessToken }, muteHttpExceptions: true });
  return r.getResponseCode() === 200 ? ((JSON.parse(r.getContentText()).user || {}).emailAddress || '') : '';
}
// di doPost(e): var body = JSON.parse(e.postData.contents);
var email = whoIs(body.accessToken);
var allowed = (PropertiesService.getScriptProperties().getProperty('ALLOWED_EMAILS') || '').split(',');
if (allowed.indexOf(email) < 0) return ContentService.createTextOutput(JSON.stringify({ ok: false, error: 'forbidden' })).setMimeType(ContentService.MimeType.JSON);
```
  Isi properti skrip `ALLOWED_EMAILS` dengan email yang boleh (pisahkan koma).

## Perilaku khas Android (hanya di APK)
- Tidak ada tombol "Install aplikasi di HP" dan tombol "Muat Ulang"; riwayat/data dimuat dengan **tarik layar ke bawah** di tab terkait.
- Tombol Back Android: menutup panel, lalu kembali ke menu utama, lalu tekan dua kali untuk keluar.
- Bottom navigation gaya Material, efek ripple, area aman status bar/gesture bar, splash gelap, tanpa highlight teks sembarangan.
- Kamera: tombol **Buka kamera** memakai kamera Android (foto DE dan foto tabel PB).
- Simpan PNG/Excel/TXT/JSON: muncul menu bagikan Android (Simpan ke perangkat, WhatsApp, Drive, dll.).
- Login Google lewat Chrome Custom Tab (Google melarang login di WebView), lalu kembali otomatis ke app.

## Login
Username/password disimpan sebagai hash (`AUTH_SALT`, `AUTH_HASH` di `www/index.html`). Ganti lewat `tools/buat-hash.html`. Tombol **Masuk dengan Google** bisa dibatasi dengan `AUTH_GOOGLE_ALLOWED` (kosong = semua akun Google boleh).

## Catatan
- Jangan hilangkan keystore; tanpa kunci yang sama, update di tempat tidak bisa.
- Repo harus public (Pages gratis dan unduhan APK tanpa login).
- Token Google berlaku ±1 jam; saat habis app membuka Custom Tab sebentar untuk menyegarkan token.
