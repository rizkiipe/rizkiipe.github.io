# U-Track Android (Capacitor + GitHub Actions)

Web app di dalam APK (bukan sekadar pembungkus situs). Satu rilis = satu versi untuk web dan APK. Tersedia juga di browser lewat `https://USERNAME.github.io/www/`.

## Isi paket
- `www/` : aplikasi (index.html, ikon, sw.js). Ini yang dibundel ke dalam APK.
- `capacitor.config.json`, `package.json` : konfigurasi Capacitor 8.
- `android-overlay/` + `scripts/patch-android.py` : plugin update APK, ikon, splash, izin kamera, deep link login (ditempel otomatis ke proyek Android saat build).
- `oauth-callback.html` : halaman pengembali login Google (di-host GitHub Pages).
- `.github/workflows/build-apk.yml` : build, tanda tangan, dan rilis APK.
- `tools/buat-hash.html` : pembuat hash password / daftar email login.

## Setup (sekali saja)

1. **Bersihkan repo lama** (`USERNAME.github.io`): hapus folder `android/`, folder `.well-known/`, dan file lama di root (index.html lama, manifest.json, sw.js, icon-*.png) kecuali yang akan diganti. Folder `android/` lama WAJIB dihapus (Settings repo tidak perlu diubah).
2. Upload **isi** paket ini ke branch `main`. Kalau `.github` gagal ter-upload, pakai paket UPLOAD-MUDAH dan rename `dot-github` jadi `.github` di GitHub (lihat BACA-DULU.txt).
3. **Settings → Pages**: Deploy from a branch, `main`, `/ (root)`. Situs tetap hidup (hanya dipakai untuk `oauth-callback.html` dan versi web).
4. **Google Cloud Console → Credentials → OAuth 2.0 Client ID (Web) U-Track**:
   - **Authorized JavaScript origins**: `https://USERNAME.github.io` (untuk versi web di browser)
   - **Authorized redirect URIs**: `https://USERNAME.github.io/oauth-callback.html`  ← penting untuk login Google di APK
   - Simpan, tunggu beberapa menit.
5. **Actions → Build & Release APK → Run workflow**, isi versi `1.0.0`.
   Run pertama membuat keystore baru dan **belum merilis**. Dari run itu:
   - download artifact **keystore-SIMPAN**, simpan baik-baik;
   - buat secret repo `KEYSTORE_BASE64` (isi KEYSTORE_BASE64.txt) dan `KEYSTORE_PASSWORD` (isi KEYSTORE_PASSWORD.txt).
6. **Run workflow lagi** (versi `1.0.0`). Sekarang APK terbit di tab **Releases**. Install `U-Track.apk` dari sana (jangan dari run pertama, kuncinya berbeda).

## Rilis update
1. Commit perubahan di `www/index.html` seperti biasa.
2. **Actions → Build & Release APK → Run workflow** → isi versi baru (mis. `1.0.1`) dan catatan update.
3. Di HP: buka U-Track → muncul lembar "Update tersedia" → **Update** → unduh → installer Android terbuka → pasang. Data tetap.
Nomor versi diisi otomatis ke `www/index.html` (jangan diubah manual) dan tampil di bawah halaman.

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
