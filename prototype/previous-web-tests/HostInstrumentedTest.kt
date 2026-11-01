package de.einsatzleitstelle110

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun packagedGameStartsOnSecureOfflineOriginAndPausesOnBackground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitGame(scenario)
            scenario.onActivity { activity ->
                val browser = findBrowser(activity.window.decorView)!!
                assertFalse(browser.settings.allowFileAccess)
                assertFalse(browser.settings.allowContentAccess)
                assertEquals(WebViewAssetUrl, browser.url)
            }
            val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertFalse("No networking permission required", permissions.contains(android.Manifest.permission.INTERNET))
            assertEquals("true", evaluate(scenario, "typeof window.AndroidBridge.saveJson === 'function'"))
            screenshot("android-start.png")
            evaluate(scenario, "window.addEventListener('el110:lifecycle', e => localStorage.setItem('android-lifecycle-probe', String(e.detail.active)))")
            scenario.moveToState(Lifecycle.State.STARTED)
            assertEquals("\"false\"", evaluate(scenario, "localStorage.getItem('android-lifecycle-probe')"))
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals("\"true\"", evaluate(scenario, "localStorage.getItem('android-lifecycle-probe')"))
            // An external location cannot replace the bundled page or gain access to the bridge.
            evaluate(scenario, "location.href = 'https://example.com/'")
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> assertEquals(WebViewAssetUrl, findBrowser(activity.window.decorView)!!.url) }
        }
    }

    @Test fun offlineStatewideDispatchAnd24HourReportRemainPlayableInAndroidWebView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitGame(scenario)
            val overview = json(scenario, """
                chooseGameMode('sandbox'); startNewGame(); planRegion('NI'); planShift('full24'); planStart(6); startShift(); showcaseCalls();
                var s=EL110.getState(); s.autoCalls=false;
                JSON.stringify({duration:s.shiftDurationMinutes,calls:s.incidents.filter(i=>i.regionId==='NI').length,
                  units:s.units.filter(u=>u.regionId==='NI').length,boundary:!!document.querySelector('[data-boundary="NI"]')});
            """.trimIndent())
            assertEquals(1440, overview.getInt("duration"))
            assertTrue(overview.getBoolean("boundary"))
            assertTrue(overview.getInt("calls") >= 3)
            assertTrue(overview.getInt("units") >= 20)
            screenshot("android-niedersachsen.png")
            val movement = json(scenario, """
                var s=EL110.getState(), call=s.incidents.find(i=>i.regionId==='NI'&&i.name.includes('Hannover'));
                var unit=s.units.filter(u=>u.regionId==='NI'&&u.type==='STREIFE')[1];
                var origin=unit.position.slice(); assign(call.id,unit.id); EL110.advance(10);
                JSON.stringify({moved:origin[0]!==unit.position[0]||origin[1]!==unit.position[1],assigned:unit.incidentId===call.id});
            """.trimIndent())
            assertTrue("Vehicle is assigned in the packaged game", movement.getBoolean("assigned"))
            assertTrue("Vehicle moves on simulated geography", movement.getBoolean("moved"))
            val report = json(scenario, """
                var s=EL110.getState(); EL110.advance(1439-s.elapsed); showcaseCalls(); EL110.advance(1);
                JSON.stringify({stage:s.stage,elapsed:s.elapsed,completed:s.completedShifts,
                  transferred:s.history[0].transferred,rotations:s.history[0].rotations,
                  reportVisible:document.getElementById('modalBody').textContent.includes('Tagesauswertung')});
            """.trimIndent())
            assertEquals("debrief", report.getString("stage"))
            assertEquals(1440, report.getInt("elapsed"))
            assertEquals(1, report.getInt("completed"))
            assertTrue("Open calls handed over", report.getInt("transferred") >= 3)
            assertTrue("24-hour duty rotates personnel", report.getInt("rotations") > 0)
            assertTrue(report.getBoolean("reportVisible"))
            screenshot("android-24h-report.png")
            assertEquals("2", evaluate(scenario, "nextDay(); EL110.getState().day"))
        }
    }

    @Test fun alphaFileOriginIsCopiedWithoutOverwritingNewerSave() {
        // Use the genuine unmodified Alpha model, changing only a value we can trace end to end.
        val fixture = JSONObject(instrumentation.context.assets.open("alpha02.json").bufferedReader().use { it.readText() })
        fixture.put("budget", 123456)
        val seed = fixture.toString()
        storageAtOrigin(WebViewAssetUrl, "localStorage.clear()")
        context.getSharedPreferences("host-migrations", Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteFile("alpha02-origin-backup.json")
        storageAtOrigin("file:///android_asset/index.html", "localStorage.clear(); localStorage.setItem('el110-save-v2', ${JSONObject.quote(seed)})")
        val newer = JSONObject(seed).put("budget", 654321).toString()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitGame(scenario)
            assertEquals(JSONObject.quote(seed), evaluate(scenario, "localStorage.getItem('el110-save-v2')"))
            assertEquals("3", evaluate(scenario, "EL110.getState().version"))
            assertEquals("123456", evaluate(scenario, "EL110.getState().budget"))
            assertEquals(fixture.getJSONArray("units").length().toString(), evaluate(scenario, "EL110.getState().units.length"))
            val backup = context.openFileInput("alpha02-origin-backup.json").bufferedReader().use { it.readText() }
            assertEquals(seed, JSONObject(backup).getString("el110-save-v2"))
            evaluate(scenario, "localStorage.setItem('el110-save-v2', ${JSONObject.quote(newer)})")
        }
        context.getSharedPreferences("host-migrations", Context.MODE_PRIVATE).edit().remove("asset-origin-v1").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitGame(scenario)
            assertEquals(JSONObject.quote(newer), evaluate(scenario, "localStorage.getItem('el110-save-v2')"))
            assertEquals("123456", evaluate(scenario, "EL110.getState().budget"))
        }
        storageAtOrigin(WebViewAssetUrl, "localStorage.clear()")
        storageAtOrigin("file:///android_asset/index.html", "localStorage.clear()")
    }

    /** Seed storage without loading the game's scripts or triggering its pagehide save. */
    private fun storageAtOrigin(origin: String, script: String) {
        val ready = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val seedView = WebView(context)
            seedView.settings.javaScriptEnabled = true
            seedView.settings.domStorageEnabled = true
            seedView.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse =
                    android.webkit.WebResourceResponse("text/html", "utf-8", "<!doctype html><html><body>Storage fixture</body></html>".byteInputStream())
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(script) {
                        view.destroy()
                        ready.countDown()
                    }
                }
            }
            seedView.loadUrl(origin)
        }
        assertTrue("Seed exact storage origin", ready.await(30, TimeUnit.SECONDS))
    }

    private fun awaitGame(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (System.nanoTime() < deadline) {
            var loaded = false
            scenario.onActivity { activity -> loaded = findBrowser(activity.window.decorView)?.url == WebViewAssetUrl }
            if (loaded && evaluate(scenario, "document.readyState === 'complete' && document.documentElement.dataset.appReady === 'true'") == "true") return
            Thread.sleep(100)
        }
        fail("Packaged game did not finish origin migration and load within 60 seconds")
    }

    private fun evaluate(scenario: ActivityScenario<MainActivity>, script: String): String {
        val result = CountDownLatch(1)
        var value = ""
        scenario.onActivity { activity ->
            findBrowser(activity.window.decorView)!!.evaluateJavascript(script) {
                value = it
                result.countDown()
            }
        }
        assertTrue("JavaScript completed", result.await(30, TimeUnit.SECONDS))
        return value
    }

    private fun json(scenario: ActivityScenario<MainActivity>, script: String): JSONObject =
        JSONObject(JSONTokener(evaluate(scenario, script)).nextValue() as String)

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Android screenshot", bitmap)
        FileOutputStream(File(context.getExternalFilesDir(null), name)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun findBrowser(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findBrowser(view.getChildAt(i))?.let { return it }
        return null
    }

    companion object { private const val WebViewAssetUrl = "https://appassets.androidplatform.net/assets/index.html" }
}
