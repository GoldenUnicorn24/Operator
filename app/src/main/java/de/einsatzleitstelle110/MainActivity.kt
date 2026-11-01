package de.einsatzleitstelle110

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.util.Log

/**
 * Temporary Alpha 0.2 host. v1.0 should harden WebView origins,
 * add a proper audio / data / map integration, and remove deprecated APIs.
 */
class MainActivity : Activity() {
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingExport: String? = null
    private val uploadRequest = 501
    private val exportRequest = 502

    inner class AndroidBridge {
        @JavascriptInterface
        fun saveJson(data: String) {
            if (data.length > 15_000_000) return
            runOnUiThread {
                pendingExport = data
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "Einsatzleitstelle110-Spielstand.json")
                }
                startActivityForResult(intent, exportRequest)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val browser = WebView(this)
        browser.webViewClient = WebViewClient()
        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: return false
                    startActivityForResult(intent, uploadRequest)
                    true
                } catch (e: Exception) {
                    uploadCallback?.onReceiveValue(null)
                    uploadCallback = null
                    false
                }
            }
        }
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.cacheMode = WebSettings.LOAD_DEFAULT
        browser.settings.allowFileAccess = true
        browser.settings.allowContentAccess = true
        browser.settings.setSupportZoom(false)
        browser.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        setContentView(browser)
        browser.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Legacy compatibility; migrate to ActivityResultContracts for v1.0")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            uploadRequest -> {
                uploadCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                uploadCallback = null
            }
            exportRequest -> {
                val json = pendingExport
                val destination = data?.data
                if (resultCode == RESULT_OK && destination != null && json != null) {
                    try {
                        contentResolver.openOutputStream(destination)?.use { stream ->
                            stream.write(json.toByteArray(Charsets.UTF_8))
                        }
                    } catch (e: Exception) {
                        Log.e("EL110", "Export failed", e)
                    }
                }
                pendingExport = null
            }
        }
    }
}
