package com.utrack.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.PendingIntent;
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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Scope;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
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

    // ---------- Login Google native (tanpa tab Chrome) ----------
    private ActivityResultLauncher<IntentSenderRequest> authLauncher;
    private PluginCall authCall;

    @Override
    public void load() {
        try {
            authLauncher = getActivity().registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            PluginCall c = authCall;
                            authCall = null;
                            if (c == null) {
                                return;
                            }
                            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                                c.reject("DIBATALKAN");
                                return;
                            }
                            try {
                                AuthorizationResult ar = Identity.getAuthorizationClient(getActivity())
                                        .getAuthorizationResultFromIntent(result.getData());
                                resolveAuth(c, ar);
                            } catch (Exception e) {
                                c.reject(describe(e));
                            }
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private String describe(Exception e) {
        if (e instanceof ApiException) {
            int code = ((ApiException) e).getStatusCode();
            if (code == CommonStatusCodes.CANCELED) {
                return "DIBATALKAN";
            }
            return "KODE " + code + ": " + e.getMessage();
        }
        return String.valueOf(e.getMessage());
    }

    private void resolveAuth(PluginCall call, AuthorizationResult ar) {
        String token = ar.getAccessToken();
        if (token == null) {
            call.reject("Token Google kosong");
            return;
        }
        JSObject o = new JSObject();
        o.put("accessToken", token);
        call.resolve(o);
    }

    @PluginMethod
    public void googleAuthorize(final PluginCall call) {
        String scopesStr = call.getString("scopes", "");
        List<Scope> scopes = new ArrayList<Scope>();
        for (String sc : scopesStr.split("\\s+")) {
            if (!sc.isEmpty()) {
                scopes.add(new Scope(sc));
            }
        }
        if (scopes.isEmpty()) {
            call.reject("Scope kosong");
            return;
        }
        if (authLauncher == null) {
            call.reject("KODE 0: peluncur login belum siap");
            return;
        }
        AuthorizationRequest request = AuthorizationRequest.builder().setRequestedScopes(scopes).build();
        Identity.getAuthorizationClient(getActivity()).authorize(request)
                .addOnSuccessListener(ar -> {
                    if (ar.hasResolution()) {
                        PendingIntent pi = ar.getPendingIntent();
                        if (pi == null) {
                            call.reject("KODE 0: izin butuh tampilan tetapi tidak tersedia");
                            return;
                        }
                        authCall = call;
                        try {
                            authLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
                        } catch (Exception e) {
                            authCall = null;
                            call.reject(describe(e));
                        }
                    } else {
                        resolveAuth(call, ar);
                    }
                })
                .addOnFailureListener(e -> call.reject(describe(e)));
    }

    // ---------- Catatan crash aplikasi sebelumnya ----------
    @PluginMethod
    public void readCrash(PluginCall call) {
        JSObject o = new JSObject();
        try {
            File f = new File(getContext().getFilesDir(), "last_crash.txt");
            if (f.exists()) {
                byte[] buf = new byte[(int) f.length()];
                FileInputStream in = new FileInputStream(f);
                int off = 0;
                while (off < buf.length) {
                    int n = in.read(buf, off, buf.length - off);
                    if (n < 0) {
                        break;
                    }
                    off += n;
                }
                in.close();
                o.put("text", new String(buf, 0, off, "UTF-8"));
                f.delete();
            }
        } catch (Exception ignored) {
        }
        call.resolve(o);
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
