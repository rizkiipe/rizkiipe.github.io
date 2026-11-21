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
