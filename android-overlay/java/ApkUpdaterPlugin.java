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
import android.view.View;
import android.content.res.Resources;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.util.concurrent.Executor;

/**
 * Update APK dari dalam app: unduh (DownloadManager) lalu buka installer Android.
 * Dipanggil dari JS: canInstall(), openInstallSettings(), download({url}), install().
 */
@CapacitorPlugin(name = "ApkUpdater")
public class ApkUpdaterPlugin extends Plugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private File apkFile;

    @PluginMethod
    public void getInsets(final PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                double top = 0, bottom = 0, left = 0, right = 0;
                try {
                    Resources res = getContext().getResources();
                    float d = res.getDisplayMetrics().density;
                    View decor = getActivity().getWindow().getDecorView();
                    WindowInsetsCompat wi = ViewCompat.getRootWindowInsets(decor);
                    if (wi != null) {
                        Insets bars = wi.getInsets(
                                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                        top = bars.top / d;
                        bottom = bars.bottom / d;
                        left = bars.left / d;
                        right = bars.right / d;
                    } else {
                        int id = res.getIdentifier("status_bar_height", "dimen", "android");
                        if (id > 0) {
                            top = res.getDimensionPixelSize(id) / d;
                        }
                    }
                } catch (Exception ignored) {
                }
                JSObject r = new JSObject();
                r.put("top", top);
                r.put("bottom", bottom);
                r.put("left", left);
                r.put("right", right);
                call.resolve(r);
            }
        });
    }

    @PluginMethod
    public void biometricAvailable(PluginCall call) {
        int r = BiometricManager.from(getContext())
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        JSObject o = new JSObject();
        o.put("value", r == BiometricManager.BIOMETRIC_SUCCESS);
        call.resolve(o);
    }

    @PluginMethod
    public void biometricAuth(final PluginCall call) {
        final String title = call.getString("title", "Buka U-Track");
        final String subtitle = call.getString("subtitle", "Gunakan sidik jari atau wajah");
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    FragmentActivity act = (FragmentActivity) getActivity();
                    Executor executor = ContextCompat.getMainExecutor(getContext());
                    BiometricPrompt prompt = new BiometricPrompt(act, executor,
                            new BiometricPrompt.AuthenticationCallback() {
                                @Override
                                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                    call.resolve();
                                }

                                @Override
                                public void onAuthenticationError(int errorCode, CharSequence errString) {
                                    call.reject(String.valueOf(errString));
                                }
                            });
                    BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                            .setTitle(title)
                            .setSubtitle(subtitle)
                            .setNegativeButtonText("Batal")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            .build();
                    prompt.authenticate(info);
                } catch (Exception e) {
                    call.reject(String.valueOf(e.getMessage()));
                }
            }
        });
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
