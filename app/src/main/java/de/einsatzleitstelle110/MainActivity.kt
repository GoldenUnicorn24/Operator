package de.einsatzleitstelle110

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.io.File

/** Offline Android shell. The only bridged page is the packaged, HTTPS asset origin. */
class MainActivity : ComponentActivity() {
    private lateinit var browser: WebView
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var foreground = false
    private var hasAudioFocus = false
    private var gameLoaded = false
    private var migrationFailed = false
    private val preferences by lazy { getSharedPreferences("host-migrations", MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val migrationBackup by lazy { AtomicFile(File(filesDir, "alpha02-origin-backup.json")) }
    private val exportFile by lazy { AtomicFile(File(filesDir, "pending-export.json")) }
    private val audioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener { change ->
                hasAudioFocus = change == AudioManager.AUDIOFOCUS_GAIN
                notifyWeb("el110:audio-focus", hasAudioFocus && foreground)
            }.build()
    }
    private val uploadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        uploadCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        uploadCallback = null
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { destination ->
        if (destination != null) {
            try {
                val data = exportFile.readFully()
                require(data.size <= MAX_JSON_BYTES)
                contentResolver.openOutputStream(destination, "wt")?.use { it.write(data) }
                    ?: error("Datei konnte nicht geöffnet werden")
            } catch (error: Exception) {
                showError("Export fehlgeschlagen", "Der lokale Spielstand bleibt erhalten. ${error.localizedMessage.orEmpty()}")
            }
        }
        exportFile.delete()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun saveJson(data: String) {
            if (data.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) return
            runOnUiThread {
                if (!gameLoaded || browser.url != GAME_URL) return@runOnUiThread
                try {
                    JSONObject(data) // Reject invalid JSON before opening the system picker.
                    writeAtomically(exportFile, data)
                    exportLauncher.launch("Einsatzleitstelle110-Spielstand.json")
                } catch (error: Exception) {
                    showError("Export fehlgeschlagen", error.localizedMessage ?: "Ungültige Daten")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browser = WebView(this)
        val debugHost = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        WebView.setWebContentsDebuggingEnabled(debugHost)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            setSupportZoom(false)
        }
        browser.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == ASSET_HOST && uri.path?.startsWith("/assets/") == true) {
                    return assetLoader.shouldInterceptRequest(uri)
                        ?: blockedResponse()
                }
                // The legacy origin is used only for the one-time localStorage read; no remote resources.
                if (!preferences.getBoolean(MIGRATION_DONE, false) && uri.toString() == LEGACY_URL) {
                    return WebResourceResponse("text/html", "utf-8", assets.open("legacy-storage.html"))
                }
                return blockedResponse()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.toString() !in setOf(GAME_URL, BOOTSTRAP_URL, LEGACY_URL)
            }

            override fun onPageFinished(view: WebView, url: String) {
                when (url) {
                    LEGACY_URL -> readLegacyOrigin()
                    BOOTSTRAP_URL -> restoreIntoAssetOrigin()
                    GAME_URL -> {
                        gameLoaded = true
                        notifyWeb("el110:lifecycle", foreground)
                        notifyWeb("el110:audio-focus", foreground && hasAudioFocus)
                    }
                }
            }
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (debugHost && message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    android.util.Log.e("EL110-WebView", "${message.message().take(1000)} (${message.sourceId()}:${message.lineNumber()})")
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) = request.deny()

            override fun onShowFileChooser(
                webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?
            ): Boolean {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = callback
                return try {
                    val intent = params?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    uploadLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    uploadCallback?.onReceiveValue(null)
                    uploadCallback = null
                    false
                }
            }
        }
        val content = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(7, 17, 31))
            addView(browser, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(content)
        if (preferences.getBoolean(MIGRATION_DONE, false)) loadGame()
        else browser.loadUrl(LEGACY_URL)
    }

    private fun readLegacyOrigin() {
        browser.evaluateJavascript("""
            (() => {
              try {
                const result = {};
                for (const key of ['el110-save-v2', 'el110-save']) {
                  const value = localStorage.getItem(key);
                  if (value !== null) result[key] = value;
                }
                return JSON.stringify(result);
              } catch (error) { return JSON.stringify({migrationError: String(error)}); }
            })()
        """.trimIndent()) { encoded ->
            try {
                val raw = JSONTokener(encoded).nextValue() as? String ?: error("Alte Daten nicht lesbar")
                val legacy = JSONObject(raw)
                require(!legacy.has("migrationError")) { "Alter Speicher ist nicht zugänglich" }
                require(raw.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES)
                // Retain an existing backup when a prior restore was interrupted.
                if (legacy.length() > 0 || !migrationBackup.baseFile.exists()) writeAtomically(migrationBackup, raw)
                browser.loadUrl(BOOTSTRAP_URL)
            } catch (error: Exception) {
                migrationError(error)
            }
        }
    }

    private fun restoreIntoAssetOrigin() {
        try {
            val raw = migrationBackup.readFully().toString(Charsets.UTF_8)
            val legacy = JSONObject(raw)
            val encoded = JSONObject.quote(legacy.toString())
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
            browser.evaluateJavascript("""
                (() => {
                  try {
                    const legacy = JSON.parse($encoded);
                    for (const key of ['el110-save-v2', 'el110-save']) {
                      if (typeof legacy[key] === 'string' && localStorage.getItem(key) === null)
                        localStorage.setItem(key, legacy[key]);
                      if (typeof legacy[key] === 'string' && localStorage.getItem(key) === null)
                        throw new Error('Speicherung fehlgeschlagen');
                    }
                    return true;
                  } catch (_) { return false; }
                })()
            """.trimIndent()) { result ->
                if (result == "true") {
                    if (preferences.edit().putBoolean(MIGRATION_DONE, true).commit()) loadGame()
                    else migrationError(IllegalStateException("Übernahme konnte nicht bestätigt werden"))
                } else migrationError(IllegalStateException("Neuer Speicher nicht beschreibbar"))
            }
        } catch (error: Exception) {
            migrationError(error)
        }
    }

    private fun loadGame() {
        browser.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        browser.loadUrl(GAME_URL)
    }

    private fun migrationError(error: Exception) {
        if (migrationFailed || isFinishing) return
        migrationFailed = true
        AlertDialog.Builder(this).setTitle("Spielstand konnte nicht übernommen werden")
            .setMessage("Die ursprünglichen Daten bleiben unverändert. ${error.localizedMessage.orEmpty()}")
            .setPositiveButton("Erneut versuchen") { _, _ ->
                migrationFailed = false
                browser.loadUrl(LEGACY_URL)
            }.setNegativeButton("Schließen") { _, _ -> finish() }.setCancelable(false).show()
    }

    private fun writeAtomically(target: AtomicFile, text: String) {
        val output = target.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            target.finishWrite(output)
        } catch (error: Exception) {
            target.failWrite(output)
            throw error
        }
    }

    private fun notifyWeb(event: String, active: Boolean) {
        if (!gameLoaded || !::browser.isInitialized) return
        browser.evaluateJavascript("window.dispatchEvent(new CustomEvent('$event',{detail:{active:$active}}));", null)
    }

    private fun showError(title: String, message: String) {
        if (!isFinishing) AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        browser.onResume()
        hasAudioFocus = audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        notifyWeb("el110:lifecycle", true)
        notifyWeb("el110:audio-focus", hasAudioFocus)
    }

    override fun onPause() {
        foreground = false
        notifyWeb("el110:lifecycle", false)
        notifyWeb("el110:audio-focus", false)
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        hasAudioFocus = false
        browser.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        browser.removeJavascriptInterface("AndroidBridge")
        browser.stopLoading()
        browser.destroy()
        super.onDestroy()
    }

    private fun blockedResponse() = WebResourceResponse(
        "text/plain", "utf-8", 403, "Offline only", emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    companion object {
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val GAME_URL = "https://$ASSET_HOST/assets/index.html"
        private const val BOOTSTRAP_URL = "https://$ASSET_HOST/assets/storage-bootstrap.html"
        private const val LEGACY_URL = "file:///android_asset/index.html"
        private const val MIGRATION_DONE = "asset-origin-v1"
        private const val MAX_JSON_BYTES = 15 * 1024 * 1024
    }
}
