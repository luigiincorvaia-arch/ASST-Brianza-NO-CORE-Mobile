package it.asstbrianza.nocorepulizie;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(this), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public static class Bridge {
        private final SharedPreferences prefs;
        Bridge(Context context) { prefs = context.getSharedPreferences("no_core_mobile", Context.MODE_PRIVATE); }

        @JavascriptInterface public String loadState() {
            return prefs.getString("state", "");
        }
        @JavascriptInterface public void saveState(String json) {
            prefs.edit().putString("state", json == null ? "" : json).apply();
        }
        @JavascriptInterface public String appVersion() { return "1.0.0"; }

        @JavascriptInterface public String sync(String server, String code, String payload) {
            HttpURLConnection conn = null;
            try {
                if (server == null || server.trim().isEmpty()) return err("Inserisci l'indirizzo del PC");
                String base = server.trim();
                while (base.endsWith("/")) base = base.substring(0, base.length()-1);
                URL url = new URL(base + "/sync");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(12000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("X-Sync-Code", code == null ? "" : code.trim());
                conn.setDoOutput(true);
                byte[] body = (payload == null ? "{}" : payload).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(body); }
                int status = conn.getResponseCode();
                InputStream in = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
                String text = readAll(in);
                if (status >= 200 && status < 300) return text;
                return err("HTTP " + status + ": " + text);
            } catch (Exception e) {
                return err(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally { if (conn != null) conn.disconnect(); }
        }
        private static String readAll(InputStream in) throws Exception {
            if (in == null) return "";
            StringBuilder b = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) b.append(line);
            }
            return b.toString();
        }
        private static String err(String s) {
            if (s == null) s = "Errore sconosciuto";
            return "{\"ok\":false,\"error\":\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ") + "\"}";
        }
    }
}
