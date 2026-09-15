package com.glon.athlete;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Хост WebView для Athlete_Glon.
 *
 * Приложение грузится не через file://, а через WebViewAssetLoader по адресу
 * https://appassets.androidplatform.net/ — это даёт странице настоящий origin,
 * без которого localStorage и IndexedDB работают ненадёжно.
 */
public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL = "rest";
    private WebView web;
    private ActivityResultLauncher<String[]> picker;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        createChannel();
        askNotifications();

        picker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) readBackup(uri); });

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        web.setWebChromeClient(new WebChromeClient());

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });

        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);

        ViewCompat.setOnApplyWindowInsetsListener(web, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        web.setBackgroundColor(0xFF191B20);
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack(); else finish();
            }
        });
    }

    public class Bridge {

        @JavascriptInterface
        public void export(final String json, final String filename) {
            runOnUiThread(() -> {
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    Uri uri;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AthleteGlon");
                        uri = getContentResolver().insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    } else {
                        uri = getContentResolver().insert(
                                MediaStore.Files.getContentUri("external"), cv);
                    }
                    if (uri == null) throw new IllegalStateException("no uri");
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    toast("Сохранено: Download/AthleteGlon/" + filename);
                } catch (Exception e) {
                    toast("Не удалось сохранить: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void importFile() {
            runOnUiThread(() -> {
                try {
                    picker.launch(new String[]{"application/json", "text/plain", "*/*"});
                } catch (Exception e) {
                    toast("Не удалось открыть выбор файла: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void restAlarm(long millisFromNow) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            PendingIntent pi = restIntent();
            long at = System.currentTimeMillis() + Math.max(1000, millisFromNow);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
                }
            } catch (SecurityException e) {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }

        @JavascriptInterface
        public void cancelAlarm() {
            ((AlarmManager) getSystemService(ALARM_SERVICE)).cancel(restIntent());
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(1);
        }

        @JavascriptInterface
        public String platform() { return "android"; }
    }

    private PendingIntent restIntent() {
        Intent i = new Intent(this, RestReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(this, 1, i, flags);
    }

    private void readBackup(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String js = "window.onImport && window.onImport("
                    + org.json.JSONObject.quote(sb.toString()) + ")";
            web.evaluateJavascript(js, null);
        } catch (Exception e) {
            toast("Не удалось прочитать файл: " + e.getMessage());
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Таймер отдыха", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }
}
