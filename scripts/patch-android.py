#!/usr/bin/env python3
"""Dijalankan workflow setelah `npx cap add android`: salin overlay + tambahkan izin/intent-filter."""
import glob, os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID = os.path.join(ROOT, "android")
OVER = os.path.join(ROOT, "android-overlay")
MAIN = os.path.join(ANDROID, "app", "src", "main")

def die(msg):
    print("GAGAL:", msg); sys.exit(1)

# 1) Java: MainActivity + plugin updater
java_dir = os.path.join(MAIN, "java", "com", "utrack", "app")
os.makedirs(java_dir, exist_ok=True)
for f in ("MainActivity.java", "ApkUpdaterPlugin.java"):
    shutil.copy(os.path.join(OVER, "java", f), os.path.join(java_dir, f))
found = glob.glob(os.path.join(MAIN, "java", "**", "MainActivity.java"), recursive=True)
if len(found) != 1:
    die("MainActivity ganda/hilang: %s" % found)

# 2) file_paths.xml (untuk FileProvider)
os.makedirs(os.path.join(MAIN, "res", "xml"), exist_ok=True)
shutil.copy(os.path.join(OVER, "res", "xml", "file_paths.xml"), os.path.join(MAIN, "res", "xml", "file_paths.xml"))

# 3) Ikon launcher (PNG) dan hapus ikon adaptif bawaan
shutil.rmtree(os.path.join(MAIN, "res", "mipmap-anydpi-v26"), ignore_errors=True)
for d in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
    os.makedirs(os.path.join(MAIN, "res", "mipmap-" + d), exist_ok=True)
    for f in ("ic_launcher.png", "ic_launcher_round.png"):
        shutil.copy(os.path.join(OVER, "res", "mipmap-" + d, f), os.path.join(MAIN, "res", "mipmap-" + d, f))

# 4) Splash screen gelap
n = 0
for p in glob.glob(os.path.join(MAIN, "res", "drawable*", "splash.png")):
    shutil.copy(os.path.join(OVER, "res", "splash.png"), p); n += 1
print("splash diganti:", n)

# 5) AndroidManifest: izin + deep link login Google
mp = os.path.join(MAIN, "AndroidManifest.xml")
m = open(mp, encoding="utf-8").read()
if m.count("</activity>") != 1 or "</manifest>" not in m:
    die("Struktur AndroidManifest tidak dikenali")
perm = (
    '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n'
    '    <uses-permission android:name="android.permission.CAMERA" />\n'
    '    <uses-feature android:name="android.hardware.camera" android:required="false" />\n'
)
intent = (
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW" />\n'
    '                <category android:name="android.intent.category.DEFAULT" />\n'
    '                <category android:name="android.intent.category.BROWSABLE" />\n'
    '                <data android:scheme="com.utrack.app" android:host="oauth" />\n'
    '            </intent-filter>\n'
)
m = m.replace("</activity>", intent + "        </activity>")
m = m.replace("</manifest>", perm + "</manifest>")
open(mp, "w", encoding="utf-8").write(m)

# 6) Nomor versi dari workflow
version = os.environ.get("VERSION"); code = os.environ.get("VCODE")
if not version or not code:
    die("VERSION/VCODE belum diset")
bg = os.path.join(ANDROID, "app", "build.gradle")
g = open(bg, encoding="utf-8").read()
g, n1 = re.subn(r'versionCode\s+\d+', 'versionCode ' + code, g, count=1)
g, n2 = re.subn(r'versionName\s+"[^"]*"', 'versionName "%s"' % version, g, count=1)
if n1 != 1 or n2 != 1:
    die("versionCode/versionName tidak ditemukan di build.gradle")
open(bg, "w", encoding="utf-8").write(g)
print("OK versi", version, code)
