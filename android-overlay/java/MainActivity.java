package com.utrack.app;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends BridgeActivity {

    // Ukuran status bar / gesture bar dalam dp (dibaca JS lewat ApkUpdater.getInsets)
    static volatile double insetTop = 0, insetBottom = 0, insetLeft = 0, insetRight = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Simpan jejak crash ke file; dibaca app saat dibuka lagi (Pengaturan -> Log error)
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String txt = new java.util.Date().toString() + " [" + t.getName() + "]\n" + sw.toString();
                    FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "last_crash.txt"), false);
                    fos.write(txt.getBytes("UTF-8"));
                    fos.close();
                } catch (Exception ignored) {
                }
                if (previous != null) {
                    previous.uncaughtException(t, e);
                }
            }
        });

        registerPlugin(ApkUpdaterPlugin.class);
        super.onCreate(savedInstanceState);

        final int dark = Color.parseColor("#0A0E17");
        Window w = getWindow();

        // Edge-to-edge: status bar & navigation bar transparan, tampilan app terlihat sampai ke tepi layar
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setBackgroundDrawable(new ColorDrawable(dark));
        w.getDecorView().setBackgroundColor(dark);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        WindowCompat.setDecorFitsSystemWindows(w, false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(w, w.getDecorView());
        controller.setAppearanceLightStatusBars(false);      // ikon status bar terang
        controller.setAppearanceLightNavigationBars(false);

        // Ukuran bar dikirim ke halaman (CSS --safe-area-inset-*) supaya header/menu tidak menabrak status bar.
        // Saat keyboard muncul, area app dipendekkan agar kolom isian tetap terlihat.
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            float d = getResources().getDisplayMetrics().density;
            boolean keyboard = ime.bottom > bars.bottom;
            insetTop = bars.top / d;
            insetLeft = bars.left / d;
            insetRight = bars.right / d;
            insetBottom = keyboard ? 0 : bars.bottom / d;
            v.setPadding(0, 0, 0, keyboard ? ime.bottom : 0);
            pushInsets();
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void pushInsets() {
        try {
            if (getBridge() != null && getBridge().getWebView() != null) {
                String js = "window.__setInsets&&window.__setInsets("
                        + insetTop + "," + insetBottom + "," + insetLeft + "," + insetRight + ")";
                getBridge().getWebView().evaluateJavascript(js, null);
            }
        } catch (Exception ignored) {
        }
    }
}
