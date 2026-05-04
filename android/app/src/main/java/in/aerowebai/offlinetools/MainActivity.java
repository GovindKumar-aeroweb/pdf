package in.aerowebai.offlinetools;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://aerowebai.in/";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new SaveBridge(), "AndroidSave");
        webView.setWebViewClient(new OfflineClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException error) {
                    fileChooserCallback = null;
                    return false;
                }
                return true;
            }
        });
        loadLocalUrl(HOME_URL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) {
            return;
        }

        Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        fileChooserCallback.onReceiveValue(results);
        fileChooserCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private void loadLocalUrl(String url) {
        Uri uri = Uri.parse(url);
        String assetPath = toAssetPath(uri);
        try {
            InputStream stream = getAssets().open(assetPath);
            byte[] bytes = readBytes(stream);
            stream.close();
            String html = new String(bytes, StandardCharsets.UTF_8);
            webView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url);
        } catch (IOException error) {
            webView.loadDataWithBaseURL(HOME_URL, "<h1>Page not found</h1>", "text/html", "UTF-8", HOME_URL);
        }
    }

    private byte[] readBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }
        return buffer.toByteArray();
    }

    private String toAssetPath(Uri uri) {
        String path = uri.getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            return "public/index.html";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path += "index.html";
        } else if (!path.contains(".")) {
            path += "/index.html";
        }
        return "public/" + path;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "download";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]+", "-").trim();
    }

    public class SaveBridge {
        @JavascriptInterface
        public void saveBase64(String filename, String mimeType, String base64Data) {
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) {
                    dir = getFilesDir();
                }
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                File output = new File(dir, sanitizeFileName(filename));
                int duplicate = 1;
                while (output.exists()) {
                    String safeName = sanitizeFileName(filename);
                    int dot = safeName.lastIndexOf('.');
                    String base = dot > 0 ? safeName.substring(0, dot) : safeName;
                    String ext = dot > 0 ? safeName.substring(dot) : "";
                    output = new File(dir, base + "-" + duplicate + ext);
                    duplicate++;
                }

                OutputStream stream = new FileOutputStream(output);
                stream.write(bytes);
                stream.close();

                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Saved: " + output.getAbsolutePath(),
                        Toast.LENGTH_LONG
                ).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Save failed",
                        Toast.LENGTH_LONG
                ).show());
            }
        }
    }

    private class OfflineClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(
                    "(function(){"
                            + "if(window.__aeroSaveBridgeInstalled||!window.AndroidSave)return;"
                            + "window.__aeroSaveBridgeInstalled=true;"
                            + "function saveBlob(url,name){"
                            + "fetch(url).then(function(r){return r.blob();}).then(function(blob){"
                            + "var reader=new FileReader();"
                            + "reader.onloadend=function(){"
                            + "var result=String(reader.result||'');"
                            + "var comma=result.indexOf(',');"
                            + "var data=comma>=0?result.slice(comma+1):result;"
                            + "window.AndroidSave.saveBase64(name||'download',blob.type||'application/octet-stream',data);"
                            + "};"
                            + "reader.readAsDataURL(blob);"
                            + "});"
                            + "}"
                            + "document.addEventListener('click',function(event){"
                            + "var node=event.target&&event.target.closest?event.target.closest('a[download]'):null;"
                            + "if(node&&node.href&&node.href.indexOf('blob:')===0){"
                            + "event.preventDefault();"
                            + "saveBlob(node.href,node.getAttribute('download')||'download');"
                            + "}"
                            + "},true);"
                            + "var originalClick=HTMLAnchorElement.prototype.click;"
                            + "HTMLAnchorElement.prototype.click=function(){"
                            + "if(this.href&&this.href.indexOf('blob:')===0){"
                            + "saveBlob(this.href,this.download||this.getAttribute('download')||'download');"
                            + "return;"
                            + "}"
                            + "return originalClick.call(this);"
                            + "};"
                            + "})();",
                    null
            );
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isLocalHost(uri)) {
                loadLocalUrl(uri.toString());
                return true;
            }
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isLocalHost(uri)) {
                loadLocalUrl(url);
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!isLocalHost(uri)) {
                return emptyResponse();
            }

            String assetPath = MainActivity.this.toAssetPath(uri);
            try {
                InputStream stream = getAssets().open(assetPath);
                return new WebResourceResponse(mimeType(assetPath), "UTF-8", stream);
            } catch (IOException ignored) {
                try {
                    InputStream stream = getAssets().open("public/404.html");
                    return new WebResourceResponse("text/html", "UTF-8", stream);
                } catch (IOException missing404) {
                    return emptyResponse();
                }
            }
        }

        private boolean isLocalHost(Uri uri) {
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String normalized = host.toLowerCase(Locale.US);
            return normalized.equals("aerowebai.in")
                    || normalized.equals("www.aerowebai.in")
                    || normalized.equals("aeorwebai.in")
                    || normalized.equals("areowebai.in");
        }

        private String mimeType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".xml")) return "application/xml";
            if (path.endsWith(".json") || path.endsWith(".webmanifest")) return "application/json";
            if (path.endsWith(".svg")) return "image/svg+xml";
            String extension = MimeTypeMap.getFileExtensionFromUrl(path);
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            return type != null ? type : "application/octet-stream";
        }

        private WebResourceResponse emptyResponse() {
            return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
        }
    }
}
