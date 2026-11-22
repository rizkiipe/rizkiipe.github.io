package com.utrack.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;

/**
 * Update APK dari dalam app: unduh (DownloadManager) lalu buka installer Android.
 * Dipanggil dari JS: canInstall(), openInstallSettings(), download({url}), install().
 */
@CapacitorPlugin(name = "ApkUpdater")
public class ApkUpdaterPlugin extends Plugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private File apkFile;

    @PluginMethod
    public void getInsets(PluginCall call) {
        JSObject r = new JSObject();
        r.put("top", MainActivity.insetTop);
        r.put("bottom", MainActivity.insetBottom);
        r.put("left", MainActivity.insetLeft);
        r.put("right", MainActivity.insetRight);
        call.resolve(r);
    }

    @PluginMethod
    public void canInstall(PluginCall call) {
        boolean ok = Build.VERSION.SDK_INT < 26
                || getContext().getPackageManager().canRequestPackageInstalls();
        JSObject r = new JSObject();
        r.put("value", ok);
        call.resolve(r);
    }

    @PluginMethod
    public void openInstallSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void download(final PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("URL kosong");
            return;
        }
        File dir = getContext().getExternalFilesDir(null);
        File upd = new File(dir, "updates");
        upd.mkdirs();
        apkFile = new File(upd, "U-Track.apk");
        if (apkFile.exists()) {
            apkFile.delete();
        }

        final DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url))
                .setTitle("Update U-Track")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(getContext(), null, "updates/U-Track.apk");
        final long id = dm.enqueue(req);

        handler.post(new Runnable() {
            @Override
            public void run() {
                Cursor c = null;
                try {
                    c = dm.query(new DownloadManager.Query().setFilterById(id));
                    if (c != null && c.moveToFirst()) {
                        int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        long done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            call.resolve();
                            return;
                        }
                        if (status == DownloadManager.STATUS_FAILED) {
                            call.reject("Unduhan gagal");
                            return;
                        }
                        if (total > 0) {
                            JSObject p = new JSObject();
                            p.put("percent", (int) (done * 100 / total));
                            notifyListeners("progress", p);
                        }
                    } else {
                        call.reject("Unduhan tidak ditemukan");
                        return;
                    }
                } catch (Exception e) {
                    call.reject("Gagal memantau unduhan: " + e.getMessage());
                    return;
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                handler.postDelayed(this, 400);
            }
        });
    }

    @PluginMethod
    public void install(PluginCall call) {
        if (apkFile == null || !apkFile.exists()) {
            call.reject("APK belum diunduh");
            return;
        }
        Uri uri = FileProvider.getUriForFile(getContext(),
                getContext().getPackageName() + ".fileprovider", apkFile);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }
}
