package com.sewerpro.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.content.pm.PackageManager;
import android.webkit.ConsoleMessage;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_JSON = 701;
    private static final int REQUEST_SAVE_FILE = 702;
    private static final int REQUEST_MIC_PERMISSION = 703;
    private PermissionRequest pendingWebPermissionRequest;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private byte[] pendingSaveBytes;
    private String pendingSaveMime = "application/octet-stream";
    private String pendingSaveName = "SEWER_PRO_file";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF06264D);
        getWindow().setNavigationBarColor(0xFF06264D);
        webView = new WebView(this);
        WebView.setWebContentsDebuggingEnabled(true);
        webView.setBackgroundColor(0xFFEEF3F8);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.addJavascriptInterface(new SewerNativeBridge(), "SewerNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { super.onPageFinished(view, url); Log.i("ARKAN_WEB", "Page loaded: " + url); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) { super.onReceivedError(view, request, error); Log.e("ARKAN_WEB", "Load error: " + request.getUrl() + " / " + error.getDescription()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme()) || "about".equals(uri.getScheme())) return false;
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean audioRequested = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) audioRequested = true;
                        else { request.deny(); return; }
                    }
                    if (!audioRequested) { request.deny(); return; }
                    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        if (pendingWebPermissionRequest != null) pendingWebPermissionRequest.deny();
                        pendingWebPermissionRequest = request;
                        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_MIC_PERMISSION);
                    }
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermissionRequest == request) pendingWebPermissionRequest = null;
            }
            @Override public boolean onConsoleMessage(ConsoleMessage message) { Log.e("ARKAN_JS", message.message() + " -- " + message.sourceId() + ":" + message.lineNumber()); return true; }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] accepted = params.getAcceptTypes();
                if (accepted != null && accepted.length > 0) intent.putExtra(Intent.EXTRA_MIME_TYPES, accepted);
                try { startActivityForResult(intent, REQUEST_OPEN_JSON); }
                catch (ActivityNotFoundException e) { filePathCallback = null; callback.onReceiveValue(null); Toast.makeText(MainActivity.this, "تعذر فتح مستعرض الملفات", Toast.LENGTH_LONG).show(); }
                return true;
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class SewerNativeBridge {
        @JavascriptInterface public void saveFile(String fileName, String mimeType, String base64) {
            runOnUiThread(() -> {
                try {
                    pendingSaveBytes = Base64.decode(base64, Base64.DEFAULT);
                    pendingSaveMime = (mimeType == null || mimeType.isEmpty()) ? "application/octet-stream" : mimeType;
                    pendingSaveName = (fileName == null || fileName.isEmpty()) ? "SEWER_PRO_file" : fileName;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(pendingSaveMime);
                    intent.putExtra(Intent.EXTRA_TITLE, pendingSaveName);
                    startActivityForResult(intent, REQUEST_SAVE_FILE);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر تجهيز الملف للحفظ", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    String jobName = "SEWER PRO report";
                    printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), new PrintAttributes.Builder().build());
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذرت الطباعة على هذا الجهاز", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MIC_PERMISSION && pendingWebPermissionRequest != null) {
            PermissionRequest request = pendingWebPermissionRequest;
            pendingWebPermissionRequest = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                request.deny();
                Toast.makeText(this, "يجب السماح باستخدام المايكروفون لتفعيل التحدث الصوتي", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_JSON) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null) results = new Uri[]{data.getData()};
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        } else if (requestCode == REQUEST_SAVE_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveBytes != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IllegalStateException("No output stream");
                    out.write(pendingSaveBytes);
                    out.flush();
                    Toast.makeText(this, "تم حفظ الملف بنجاح", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "تعذر حفظ الملف", Toast.LENGTH_LONG).show();
                }
            }
            pendingSaveBytes = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("window.SewerAppBack ? window.SewerAppBack() : false", value -> {
                if (!"true".equals(value)) finish();
            });
        } else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
